import {
  extractErrorMessage,
  gatewayRequest,
  getEmailFromToken,
  getOptionalAnonKey,
  getSupabaseUrl,
  getUserIdFromToken,
  loadStoredSession,
  sanitizeFriendlyError,
  uploadBinaryToStorage,
} from '../lib/supabaseGateway';
import {
  DynamicAccountInfo,
  PermanentAccountInfo,
  TransactionRecord,
  UserProfile,
} from '../types/vtu';
import { fetchAndMergeAuthUser } from './authService';

const PERMANENT_ACCOUNT_MERCHANT_PREFIX = 'Thompson Daniel';
const resetPermanentAccountUsers = new Set<string>();
const autoCheckedPermanentUsers = new Set<string>();

const DELETED_TX_KEY_PREFIX = 'daniel_vtu_deleted_txs_';
const CLEARED_TS_KEY_PREFIX = 'daniel_vtu_cleared_ts_';

export function resetSessionAccountCaches(): void {
  resetPermanentAccountUsers.clear();
  autoCheckedPermanentUsers.clear();
}

export function getDeletedTransactionRefs(userId: string): Set<string> {
  if (!userId) return new Set();
  try {
    const raw = localStorage.getItem(`${DELETED_TX_KEY_PREFIX}${userId}`);
    if (!raw) return new Set();
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? new Set(arr.map(String)) : new Set();
  } catch {
    return new Set();
  }
}

export function markTransactionDeleted(userId: string, reference: string): void {
  if (!userId || !reference) return;
  const current = getDeletedTransactionRefs(userId);
  current.add(reference);
  try {
    localStorage.setItem(`${DELETED_TX_KEY_PREFIX}${userId}`, JSON.stringify([...current]));
  } catch {
    // ignore quota errors
  }
}

export function getTransactionsClearedTimestamp(userId: string): number {
  if (!userId) return 0;
  try {
    const val = Number(localStorage.getItem(`${CLEARED_TS_KEY_PREFIX}${userId}`) || '0');
    return Number.isFinite(val) ? val : 0;
  } catch {
    return 0;
  }
}

export function markAllTransactionsCleared(userId: string): void {
  if (!userId) return;
  try {
    localStorage.setItem(`${CLEARED_TS_KEY_PREFIX}${userId}`, String(Date.now()));
  } catch {
    // ignore quota errors
  }
}

function cleanStr(val: unknown): string {
  if (typeof val !== 'string') return '';
  const t = val.trim().replace(/^"|"$/g, '').trim();
  if (!t || t.toLowerCase() === 'null' || t.toLowerCase() === 'nil' || t.toLowerCase() === 'undefined') {
    return '';
  }
  return t;
}

function cleanAccountNumber(val: unknown): string {
  const s = cleanStr(val);
  if (!s) return '';
  const digits = s.replace(/\D/g, '');
  if (digits.length >= 10 && digits.length <= 12) {
    return digits;
  }
  return '';
}

function cleanRawNameString(raw?: string | null): string {
  let clean = cleanStr(raw);
  if (!clean) return '';
  clean = clean.replace(/^(FLW\/|FLW-|FLUTTERWAVE\/)\s*/i, '').trim();
  const normalized = clean.split(/\s+/).filter(Boolean).join(' ');
  if (/[a-zA-Z]/.test(normalized) && normalized === normalized.toUpperCase()) {
    return normalized
      .split(' ')
      .map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
      .join(' ');
  }
  return normalized;
}

export function sanitizeFullName(raw?: string | null, email?: string | null): string {
  const isDanielEmail = (email || '').trim().toLowerCase() === 'danielkaladathompson@gmail.com';
  let cleaned = cleanRawNameString(raw);
  if (/^Thompson Daniel\s*\//i.test(cleaned)) {
    cleaned = cleanRawNameString(cleaned.split('/').slice(1).join('/'));
  }
  if (isDanielEmail) {
    if (
      !cleaned ||
      cleaned.toLowerCase() === 'thompson daniel' ||
      cleaned.toLowerCase() === 'daniel kalada thompson' ||
      cleaned.toLowerCase() === 'daniel thompson'
    ) {
      return 'Daniel Thompson';
    }
  }
  return cleaned;
}

export function resolveAccountHolderName(
  rawAccountName?: string | null,
  fullName?: string | null,
  email?: string | null
): string {
  const rawTrimmed = cleanStr(rawAccountName);
  if (
    rawTrimmed.toLowerCase() === 'wallet topup' ||
    rawTrimmed.toLowerCase() === 'wallet top-up'
  ) {
    return 'Wallet Topup';
  }

  const rawWithoutFlw = rawTrimmed.replace(/^(FLW\/|FLW-|FLUTTERWAVE\/)\s*/i, '').trim();
  let extractedFromRaw = '';
  if (rawWithoutFlw) {
    if (rawWithoutFlw.includes('/')) {
      const [prefix, ...rest] = rawWithoutFlw.split('/');
      if (prefix.trim().toLowerCase() === PERMANENT_ACCOUNT_MERCHANT_PREFIX.toLowerCase()) {
        extractedFromRaw = cleanRawNameString(rest.join('/'));
      } else {
        extractedFromRaw = cleanRawNameString(rawWithoutFlw);
      }
    } else if (rawWithoutFlw.toLowerCase() !== PERMANENT_ACCOUNT_MERCHANT_PREFIX.toLowerCase()) {
      extractedFromRaw = cleanRawNameString(rawWithoutFlw);
    }
  }

  const rawFullNameCleaned = cleanRawNameString(fullName);
  const cleanedFullName = sanitizeFullName(fullName, email);
  const isDanielAccount =
    (email || '').trim().toLowerCase() === 'danielkaladathompson@gmail.com' ||
    rawFullNameCleaned.toLowerCase() === 'daniel kalada thompson' ||
    cleanedFullName.toLowerCase() === 'daniel kalada thompson' ||
    extractedFromRaw.toLowerCase() === 'daniel kalada thompson' ||
    (rawWithoutFlw.toLowerCase() === PERMANENT_ACCOUNT_MERCHANT_PREFIX.toLowerCase() &&
      (!cleanedFullName ||
        cleanedFullName.toLowerCase() === PERMANENT_ACCOUNT_MERCHANT_PREFIX.toLowerCase())) ||
    (extractedFromRaw.toLowerCase() === PERMANENT_ACCOUNT_MERCHANT_PREFIX.toLowerCase() &&
      (!cleanedFullName ||
        cleanedFullName.toLowerCase() === PERMANENT_ACCOUNT_MERCHANT_PREFIX.toLowerCase()));

  if (isDanielAccount) {
    return `${PERMANENT_ACCOUNT_MERCHANT_PREFIX}/ Daniel Kalada Thompson`;
  }

  const effectiveCustomerName =
    (extractedFromRaw &&
      extractedFromRaw.toLowerCase() !== PERMANENT_ACCOUNT_MERCHANT_PREFIX.toLowerCase() &&
      extractedFromRaw) ||
    (cleanedFullName &&
      cleanedFullName.toLowerCase() !== PERMANENT_ACCOUNT_MERCHANT_PREFIX.toLowerCase() &&
      cleanedFullName) ||
    '';

  if (!effectiveCustomerName) return '';
  return `${PERMANENT_ACCOUNT_MERCHANT_PREFIX}/ ${effectiveCustomerName}`;
}

function collectContainers(root: Record<string, unknown> | null): Record<string, unknown>[] {
  if (!root || typeof root !== 'object') return [];
  const dataObj =
    root.data && typeof root.data === 'object' ? (root.data as Record<string, unknown>) : null;
  const nestedDataObj =
    dataObj?.data && typeof dataObj.data === 'object'
      ? (dataObj.data as Record<string, unknown>)
      : null;
  const accountObj =
    (root.account && typeof root.account === 'object'
      ? (root.account as Record<string, unknown>)
      : null) ||
    (dataObj?.account && typeof dataObj.account === 'object'
      ? (dataObj.account as Record<string, unknown>)
      : null);
  const virtualAccountObj =
    (root.virtual_account && typeof root.virtual_account === 'object'
      ? (root.virtual_account as Record<string, unknown>)
      : null) ||
    (dataObj?.virtual_account && typeof dataObj.virtual_account === 'object'
      ? (dataObj.virtual_account as Record<string, unknown>)
      : null);
  const resultObj =
    (root.result && typeof root.result === 'object'
      ? (root.result as Record<string, unknown>)
      : null) ||
    (dataObj?.result && typeof dataObj.result === 'object'
      ? (dataObj.result as Record<string, unknown>)
      : null);

  return [root, dataObj, nestedDataObj, accountObj, virtualAccountObj, resultObj].filter(
    (c): c is Record<string, unknown> => c !== null
  );
}

function extractFieldFromContainers(
  containers: Record<string, unknown>[],
  keys: string[]
): string {
  for (const key of keys) {
    for (const c of containers) {
      const v = cleanStr(c[key]);
      if (v) return v;
    }
  }
  return '';
}

function extractAccountNameFromEdge(
  json: Record<string, unknown> | null,
  fallbackFullName: string,
  email: string
): string {
  const containers = collectContainers(json);
  const rawCandidate = extractFieldFromContainers(containers, [
    'account_name',
    'accountName',
    'permanent_account_name',
    'permanentAccountName',
    'virtual_account_name',
    'virtualAccountName',
    'account_holder_name',
    'accountHolderName',
    'customer_name',
    'customerName',
    'narration',
    'account_narration',
    'full_name',
    'fullName',
    'name',
  ]);
  if (rawCandidate) {
    const resolved = resolveAccountHolderName(rawCandidate, fallbackFullName, email);
    if (resolved) return resolved;
  }

  for (const c of containers) {
    const first = cleanStr(c.firstname || c.firstName || c.first_name);
    const last = cleanStr(c.lastname || c.lastName || c.last_name);
    if (first || last) {
      const resolved = resolveAccountHolderName(
        [first, last].filter(Boolean).join(' '),
        fallbackFullName,
        email
      );
      if (resolved) return resolved;
    }
  }

  for (const c of containers) {
    const note = cleanStr(c.note);
    const prefix = 'Please make a bank transfer to ';
    if (note.toLowerCase().startsWith(prefix.toLowerCase())) {
      const resolved = resolveAccountHolderName(
        note.slice(prefix.length).trim().replace(/\.$/, ''),
        fallbackFullName,
        email
      );
      if (resolved) return resolved;
    }
  }

  return resolveAccountHolderName(null, fallbackFullName, email);
}

async function persistPermanentAccountToBackend(params: {
  userId: string;
  email: string;
  accountNumber: string;
  bankName: string;
  accountName: string;
}): Promise<void> {
  const { userId, email, accountNumber, bankName, accountName } = params;
  if (!userId || !accountNumber) return;

  await Promise.allSettled([
    gatewayRequest(`/rest/v1/users?id=eq.${encodeURIComponent(userId)}`, {
      method: 'PATCH',
      headers: { Prefer: 'return=minimal' },
      body: {
        permanent_account_number: accountNumber,
        ...(bankName ? { permanent_account_bank: bankName } : {}),
      },
    }),
    gatewayRequest(`/rest/v1/virtual_accounts`, {
      method: 'POST',
      headers: { Prefer: 'resolution=merge-duplicates,return=minimal' },
      body: {
        user_id: userId,
        ...(email ? { email: email.trim().toLowerCase() } : {}),
        account_number: accountNumber,
        bank_name: bankName || 'Flutterwave MFB',
        account_name: accountName || PERMANENT_ACCOUNT_MERCHANT_PREFIX,
      },
    }),
    gatewayRequest(`/auth/v1/user`, {
      method: 'PUT',
      body: {
        data: {
          permanent_account_number: accountNumber,
          permanent_account_bank: bankName || 'Flutterwave MFB',
          ...(accountName ? { permanent_account_name: accountName } : {}),
        },
      },
    }),
  ]);
}

export async function clearPermanentAccount(
  userId: string
): Promise<{ ok: true } | { ok: false; error: string }> {
  if (!userId) return { ok: false, error: 'Missing user ID.' };
  resetPermanentAccountUsers.add(userId);
  autoCheckedPermanentUsers.delete(userId);

  const [usersRes] = await Promise.all([
    gatewayRequest(`/rest/v1/users?id=eq.${encodeURIComponent(userId)}`, {
      method: 'PATCH',
      headers: { Prefer: 'return=minimal' },
      body: {
        permanent_account_number: null,
        permanent_account_bank: null,
      },
    }),
    gatewayRequest(`/rest/v1/virtual_accounts?user_id=eq.${encodeURIComponent(userId)}`, {
      method: 'DELETE',
    }).catch(() => null),
    gatewayRequest(`/auth/v1/user`, {
      method: 'PUT',
      body: {
        data: {
          permanent_account_number: null,
          permanent_account_bank: null,
          permanent_account_name: null,
        },
      },
    }).catch(() => null),
  ]);

  if (!usersRes.ok) {
    return {
      ok: false,
      error: extractErrorMessage(usersRes, 'Could not unlink permanent account right now.'),
    };
  }
  return { ok: true };
}

function isFailedOrRefunded(status: string, title: string, service: string): boolean {
  const combined = `${status} ${title} ${service}`.toLowerCase();
  return (
    combined.includes('fail') ||
    combined.includes('refund') ||
    combined.includes('declin') ||
    combined.includes('revers') ||
    combined.includes('error')
  );
}

function parseTransactionArray(raw: unknown): TransactionRecord[] {
  let arr: unknown[] = [];
  if (Array.isArray(raw)) {
    arr = raw;
  } else if (typeof raw === 'string' && raw.trim().startsWith('[')) {
    try {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) arr = parsed;
    } catch {
      return [];
    }
  } else {
    return [];
  }

  return arr
    .map((item, idx): TransactionRecord | null => {
      if (!item || typeof item !== 'object') return null;
      const obj = item as Record<string, unknown>;
      const rawStatus = cleanStr(obj.status || 'SUCCESSFUL').toUpperCase();
      const rawService = cleanStr(obj.service_type || obj.service || obj.type || 'VTU').toUpperCase();
      const rawTitle = cleanStr(obj.title || obj.narration || obj.description || '');
      const provider = cleanStr(obj.provider || obj.network || '');
      const recipient = cleanStr(obj.recipient || obj.phone || obj.meter_number || '');
      const reference =
        cleanStr(obj.reference || obj.tx_ref || obj.id) || `VTU-HIST-${idx}-${Date.now()}`;
      const amount = Number(obj.amount ?? obj.charged ?? 0);
      const cashback = Number(obj.cashback ?? obj.discount ?? obj.discountOrCashback ?? 0);
      const createdAt = cleanStr(obj.created_at || obj.createdAt || obj.timestamp);
      const parsedDate = createdAt ? Date.parse(createdAt) : NaN;
      const ts = Number.isFinite(parsedDate)
        ? parsedDate
        : typeof obj.timestamp === 'number'
        ? obj.timestamp
        : Date.now() - idx * 60000;

      const failed = isFailedOrRefunded(rawStatus, rawTitle, rawService);
      const normalizedStatus = failed
        ? 'FAILED'
        : rawStatus === 'PENDING' || rawStatus === 'PROCESSING'
        ? 'PENDING'
        : 'SUCCESSFUL';

      const isFunding =
        rawService.includes('FUND') ||
        rawService.includes('CREDIT') ||
        rawService.includes('DEPOSIT') ||
        rawTitle.toLowerCase().includes('wallet deposit') ||
        rawTitle.toLowerCase().includes('credit');

      return {
        id: reference,
        reference: reference.replace(/GSUBZ-ERR-|GSUBZ-/gi, 'VTU-'),
        serviceType: isFunding ? 'WALLET_FUNDING' : rawService,
        title: isFunding ? 'credit' : rawTitle || `${provider} ${rawService}`.trim() || 'Transaction',
        provider,
        recipient,
        customerName: cleanStr(obj.customer_name || obj.customerName) || undefined,
        amount: Number.isFinite(amount) ? amount : 0,
        discountOrCashback: Number.isFinite(cashback) ? cashback : 0,
        status: normalizedStatus,
        tokenOrDetails: cleanStr(obj.token || obj.pin || obj.details || obj.description) || undefined,
        createdAt: createdAt || new Date(ts).toISOString(),
        timestamp: ts,
      };
    })
    .filter((tx): tx is TransactionRecord => tx !== null)
    .sort((a, b) => b.timestamp - a.timestamp);
}

async function fetchUsersRowWithFallback(
  filter: string,
  email: string
): Promise<Record<string, unknown> | null> {
  const selectCandidates = [
    '*',
    'id,email,phone,wallet_balance,cashback_balance,permanent_account_number,permanent_account_bank,transaction_history,avatar_url',
    'id,email,phone,wallet_balance,permanent_account_number,permanent_account_bank,transaction_history,avatar_url',
    'id,email,phone,wallet_balance,transaction_history',
  ];

  for (const cols of selectCandidates) {
    const res = await gatewayRequest<Record<string, unknown>[]>(
      `/rest/v1/users?select=${cols}&${filter}&limit=1`,
      { method: 'GET' }
    ).catch(() => null);
    if (res?.ok && Array.isArray(res.data) && res.data.length > 0) {
      return res.data[0];
    }
  }

  if (email && !filter.startsWith('email=')) {
    for (const cols of selectCandidates) {
      const byEmail = await gatewayRequest<Record<string, unknown>[]>(
        `/rest/v1/users?select=${cols}&email=ilike.${encodeURIComponent(email)}&limit=1`,
        { method: 'GET' }
      ).catch(() => null);
      if (byEmail?.ok && Array.isArray(byEmail.data) && byEmail.data.length > 0) {
        return byEmail.data[0];
      }
    }
  }

  return null;
}

export async function fetchUserProfileAndHistory(options?: {
  syncPermanentAccount?: boolean;
}): Promise<{
  profile: UserProfile | null;
  transactions: TransactionRecord[];
}> {
  const session = loadStoredSession();
  const userId = session?.user?.id || getUserIdFromToken(session?.access_token);
  const email = session?.user?.email || getEmailFromToken(session?.access_token);
  if (!userId && !email) {
    return { profile: null, transactions: [] };
  }

  const filter = userId
    ? `id=eq.${encodeURIComponent(userId)}`
    : `email=ilike.${encodeURIComponent(email)}`;

  const profileFilter = email
    ? `email=ilike.${encodeURIComponent(email)}`
    : `id=eq.${encodeURIComponent(userId)}`;

  const [liveAuthUser, row, profileRes, vaTableByIdRes, vaTableByEmailRes] = await Promise.all([
    fetchAndMergeAuthUser().catch(() => session?.user || null),
    fetchUsersRowWithFallback(filter, email),
    gatewayRequest<Record<string, unknown>[]>(
      `/rest/v1/profiles?select=id,email,full_name,phone,avatar_url&${profileFilter}&limit=1`,
      { method: 'GET' }
    ).catch(() => null),
    userId
      ? gatewayRequest<Record<string, unknown>[]>(
          `/rest/v1/virtual_accounts?user_id=eq.${encodeURIComponent(userId)}&limit=1`,
          { method: 'GET' }
        ).catch(() => null)
      : Promise.resolve(null),
    email
      ? gatewayRequest<Record<string, unknown>[]>(
          `/rest/v1/virtual_accounts?email=ilike.${encodeURIComponent(email)}&limit=1`,
          { method: 'GET' }
        ).catch(() => null)
      : Promise.resolve(null),
  ]);

  let profileRow: Record<string, unknown> | null =
    profileRes && profileRes.ok && Array.isArray(profileRes.data) && profileRes.data.length > 0
      ? profileRes.data[0]
      : null;

  if (!profileRow && userId && email) {
    const profileById = await gatewayRequest<Record<string, unknown>[]>(
      `/rest/v1/profiles?select=id,email,full_name,phone,avatar_url&id=eq.${encodeURIComponent(userId)}&limit=1`,
      { method: 'GET' }
    ).catch(() => null);
    if (profileById && profileById.ok && Array.isArray(profileById.data) && profileById.data.length > 0) {
      profileRow = profileById.data[0];
    }
  }

  const vaRow: Record<string, unknown> | null =
    (vaTableByIdRes && vaTableByIdRes.ok && Array.isArray(vaTableByIdRes.data) && vaTableByIdRes.data.length > 0
      ? vaTableByIdRes.data[0]
      : null) ||
    (vaTableByEmailRes && vaTableByEmailRes.ok && Array.isArray(vaTableByEmailRes.data) && vaTableByEmailRes.data.length > 0
      ? vaTableByEmailRes.data[0]
      : null);

  const meta = ((liveAuthUser || session?.user)?.user_metadata || {}) as Record<string, unknown>;
  const resolvedId = cleanStr(row?.id || profileRow?.id) || userId;
  const resolvedEmail = cleanStr(row?.email || profileRow?.email) || email;

  const rawFullName =
    cleanStr(profileRow?.full_name || row?.full_name || row?.fullname || row?.name) ||
    cleanStr(meta.full_name || meta.fullname || meta.name) ||
    (resolvedEmail ? resolvedEmail.split('@')[0] : 'Daniel VTU User');
  const fullName = sanitizeFullName(rawFullName, resolvedEmail) || rawFullName;

  const phone = cleanStr(profileRow?.phone || row?.phone || meta.phone || meta.phone_number);
  const ninHash =
    cleanStr(row?.nin || row?.nin_hash || meta.nin || meta.nin_hash) || undefined;

  const isResetInSession = resolvedId ? resetPermanentAccountUsers.has(resolvedId) : false;

  let virtualAccountNumber = isResetInSession
    ? ''
    : cleanAccountNumber(
        row?.permanent_account_number ||
          row?.virtual_account_number ||
          row?.account_number ||
          vaRow?.account_number ||
          meta.permanent_account_number ||
          meta.virtual_account_number
      );

  let virtualBankName = isResetInSession
    ? ''
    : cleanStr(
        row?.permanent_account_bank ||
          row?.bank_name ||
          row?.virtual_bank_name ||
          row?.virtual_bank ||
          vaRow?.bank_name ||
          meta.permanent_account_bank ||
          meta.virtual_bank_name
      );

  const rawVaName = cleanStr(
    row?.permanent_account_name ||
      row?.virtual_account_name ||
      row?.account_name ||
      vaRow?.account_name ||
      meta.permanent_account_name ||
      meta.virtual_account_name
  );

  let virtualAccountName = virtualAccountNumber
    ? resolveAccountHolderName(rawVaName, fullName, resolvedEmail) || fullName
    : '';

  // Mirror Android PermanentAccountViewModel.checkExisting():
  // When no permanent account is found in DB yet (or once per session), query Create-Permanent-Account
  // so existing Flutterwave permanent accounts are restored automatically and persisted.
  const shouldSyncPerm =
    options?.syncPermanentAccount !== false &&
    !isResetInSession &&
    Boolean(resolvedId && resolvedEmail) &&
    (!virtualAccountNumber || !autoCheckedPermanentUsers.has(resolvedId));

  if (shouldSyncPerm && resolvedId) {
    autoCheckedPermanentUsers.add(resolvedId);
    try {
      const edgePerm = await createOrFetchPermanentAccount({
        userId: resolvedId,
        email: resolvedEmail,
        fullName,
        phone,
        nin: ninHash && /^\d{11}$/.test(ninHash) ? ninHash : undefined,
        silentSync: true,
      });
      if (edgePerm.success && edgePerm.accountNumber) {
        virtualAccountNumber = edgePerm.accountNumber;
        if (edgePerm.bankName) virtualBankName = edgePerm.bankName;
        if (edgePerm.accountName) virtualAccountName = edgePerm.accountName;
      }
    } catch {
      // ignore silent check errors
    }
  }

  // Strict avatar resolution priority matching Android ProfilePictureManager.kt:
  // 1) public.profiles.avatar_url -> 2) public.users.avatar_url -> 3) Auth metadata -> 4) Storage fallback when anonKey set
  let avatarUrl =
    cleanStr(profileRow?.avatar_url) ||
    cleanStr(row?.avatar_url) ||
    cleanStr(meta.avatar_url || meta.avatar_data_uri) ||
    undefined;

  if (!avatarUrl && resolvedId && getOptionalAnonKey()) {
    avatarUrl = `${getSupabaseUrl()}/storage/v1/object/public/avatars/${encodeURIComponent(
      resolvedId
    )}/avatar.jpg`;
  }

  const profile: UserProfile = {
    id: resolvedId,
    email: resolvedEmail,
    fullName,
    phone,
    walletBalance: Number(row?.wallet_balance ?? 0) || 0,
    cashbackBalance: Number(row?.cashback_balance ?? 0) || 0,
    avatarUrl,
    ninHash,
    ninVerified: Boolean(ninHash || virtualAccountNumber),
    virtualAccountNumber: virtualAccountNumber || undefined,
    virtualBankName: virtualAccountNumber ? virtualBankName || 'Flutterwave MFB' : undefined,
    virtualAccountName: virtualAccountNumber ? virtualAccountName || undefined : undefined,
    dynamicAccountNumber: cleanAccountNumber(row?.dynamic_account_number) || undefined,
    dynamicBankName: cleanStr(row?.dynamic_bank_name) || undefined,
  };

  let transactions = parseTransactionArray(row?.transaction_history);

  try {
    const rpcRes = await gatewayRequest<unknown>('/rest/v1/rpc/get_my_transactions', {
      method: 'POST',
      body: { p_limit: 40 },
    });
    if (rpcRes.ok && Array.isArray(rpcRes.data) && rpcRes.data.length > 0) {
      const rpcTx = parseTransactionArray(rpcRes.data);
      const seen = new Set<string>();
      transactions = [...rpcTx, ...transactions].filter((t) => {
        if (seen.has(t.reference)) return false;
        seen.add(t.reference);
        return true;
      });
    }
  } catch {
    // RPC optional if transaction_history column already has data
  }

  const deletedRefs = getDeletedTransactionRefs(resolvedId);
  const clearedTs = getTransactionsClearedTimestamp(resolvedId);
  if (deletedRefs.size > 0 || clearedTs > 0) {
    transactions = transactions.filter(
      (tx) => !deletedRefs.has(tx.reference) && tx.timestamp > clearedTs
    );
  }

  return { profile, transactions };
}

async function persistAvatarUrlEverywhere(
  userId: string,
  email: string,
  avatarValue: string | null
): Promise<boolean> {
  const payload = { avatar_url: avatarValue };
  const results = await Promise.allSettled([
    gatewayRequest('/rest/v1/rpc/set_my_avatar', { method: 'POST', body: {} }),
    userId
      ? gatewayRequest(`/rest/v1/profiles?id=eq.${encodeURIComponent(userId)}`, {
          method: 'PATCH',
          headers: { Prefer: 'return=representation' },
          body: payload,
        })
      : Promise.resolve(null),
    email
      ? gatewayRequest(`/rest/v1/profiles?email=ilike.${encodeURIComponent(email)}`, {
          method: 'PATCH',
          headers: { Prefer: 'return=representation' },
          body: payload,
        })
      : Promise.resolve(null),
    userId
      ? gatewayRequest(`/rest/v1/users?id=eq.${encodeURIComponent(userId)}`, {
          method: 'PATCH',
          headers: { Prefer: 'return=representation' },
          body: payload,
        })
      : Promise.resolve(null),
    email
      ? gatewayRequest(`/rest/v1/users?email=ilike.${encodeURIComponent(email)}`, {
          method: 'PATCH',
          headers: { Prefer: 'return=representation' },
          body: payload,
        })
      : Promise.resolve(null),
    gatewayRequest('/auth/v1/user', {
      method: 'PUT',
      body: {
        data: {
          avatar_url: avatarValue,
          avatar_base64: null,
          avatar_data_uri: null,
        },
      },
    }),
  ]);

  return results.some((r) => r.status === 'fulfilled' && r.value && r.value.ok);
}

function resizeImageFileToDataUri(file: File, maxDim = 256, quality = 0.72): Promise<{
  dataUri: string;
  blob: Blob;
}> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    const objectUrl = URL.createObjectURL(file);
    img.onload = () => {
      try {
        const scale = Math.min(1, maxDim / Math.max(img.width, img.height));
        const width = Math.max(1, Math.round(img.width * scale));
        const height = Math.max(1, Math.round(img.height * scale));
        const canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          URL.revokeObjectURL(objectUrl);
          reject(new Error('Canvas context unavailable'));
          return;
        }
        ctx.drawImage(img, 0, 0, width, height);
        const dataUri = canvas.toDataURL('image/jpeg', quality);
        canvas.toBlob(
          (blob) => {
            URL.revokeObjectURL(objectUrl);
            if (!blob) {
              reject(new Error('Failed to encode image'));
              return;
            }
            resolve({ dataUri, blob });
          },
          'image/jpeg',
          quality
        );
      } catch (err) {
        URL.revokeObjectURL(objectUrl);
        reject(err);
      }
    };
    img.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      reject(new Error('Invalid image file selected'));
    };
    img.src = objectUrl;
  });
}

export async function uploadUserAvatar(params: {
  userId: string;
  email: string;
  file: File;
}): Promise<{ ok: true; avatarUrl: string } | { ok: false; error: string }> {
  const { userId, email, file } = params;
  if (!userId) return { ok: false, error: 'Missing user ID.' };

  try {
    const { dataUri, blob } = await resizeImageFileToDataUri(file, 256, 0.72);
    let finalUrl = dataUri;

    const uploadedStorage = await uploadBinaryToStorage(
      `avatars/${encodeURIComponent(userId)}/avatar.jpg`,
      blob,
      'image/jpeg'
    );
    if (uploadedStorage && getOptionalAnonKey()) {
      finalUrl = `${getSupabaseUrl()}/storage/v1/object/public/avatars/${encodeURIComponent(
        userId
      )}/avatar.jpg?t=${Date.now()}`;
    }

    const persisted = await persistAvatarUrlEverywhere(userId, email, finalUrl);
    if (!persisted) {
      return { ok: false, error: 'Could not save profile picture to your account.' };
    }
    await fetchAndMergeAuthUser();
    return { ok: true, avatarUrl: finalUrl };
  } catch (err) {
    return {
      ok: false,
      error: err instanceof Error ? err.message : 'Could not process image file.',
    };
  }
}

export async function setUserAvatarUrl(params: {
  userId: string;
  email: string;
  imageUrl: string;
}): Promise<{ ok: true; avatarUrl: string } | { ok: false; error: string }> {
  const cleanUrl = params.imageUrl.trim();
  if (!/^https?:\/\/.+/i.test(cleanUrl)) {
    return { ok: false, error: 'Please enter a valid https:// image URL.' };
  }
  const ok = await persistAvatarUrlEverywhere(params.userId, params.email, cleanUrl);
  if (!ok) {
    return { ok: false, error: 'Could not save avatar URL right now.' };
  }
  await fetchAndMergeAuthUser();
  return { ok: true, avatarUrl: cleanUrl };
}

export async function removeUserAvatar(params: {
  userId: string;
  email: string;
}): Promise<{ ok: true } | { ok: false; error: string }> {
  await gatewayRequest('/rest/v1/rpc/remove_my_avatar', {
    method: 'POST',
    body: {},
  }).catch(() => null);
  await persistAvatarUrlEverywhere(params.userId, params.email, null);
  await fetchAndMergeAuthUser();
  return { ok: true };
}

export async function checkHasTransactionPin(): Promise<boolean> {
  try {
    const res = await gatewayRequest<boolean | { result?: boolean }>(
      '/rest/v1/rpc/has_transaction_pin',
      { method: 'POST', body: {} }
    );
    if (!res.ok) return false;
    if (typeof res.data === 'boolean') return res.data;
    if (res.rawText.trim() === 'true') return true;
    return false;
  } catch {
    return false;
  }
}

export async function setTransactionPin(
  pin: string
): Promise<{ ok: true } | { ok: false; error: string }> {
  const res = await gatewayRequest<unknown>('/rest/v1/rpc/set_transaction_pin', {
    method: 'POST',
    body: { p_pin: pin },
  });
  if (!res.ok) {
    return { ok: false, error: extractErrorMessage(res, 'Could not save PIN.') };
  }
  return { ok: true };
}

export async function verifyTransactionPin(
  pin: string
): Promise<{ ok: true } | { ok: false; error: string }> {
  const res = await gatewayRequest<unknown>('/rest/v1/rpc/verify_transaction_pin', {
    method: 'POST',
    body: { p_pin: pin },
  });
  const lower = res.rawText.toLowerCase();
  if (lower.includes('locked')) {
    return { ok: false, error: 'Too many wrong attempts. Try again in 15 minutes.' };
  }
  if (!res.ok || res.data === false || res.rawText.trim() === 'false') {
    return { ok: false, error: 'Incorrect Transaction PIN. Please try again.' };
  }
  return { ok: true };
}

export async function changeTransactionPin(
  oldPin: string,
  newPin: string
): Promise<{ ok: true } | { ok: false; error: string }> {
  const res = await gatewayRequest<unknown>('/rest/v1/rpc/change_transaction_pin', {
    method: 'POST',
    body: { p_old: oldPin, p_new: newPin },
  });
  const lower = res.rawText.toLowerCase();
  if (lower.includes('locked')) {
    return { ok: false, error: 'Too many wrong attempts. Try again in 15 minutes.' };
  }
  if (!res.ok || res.data === false || res.rawText.trim() === 'false') {
    return { ok: false, error: extractErrorMessage(res, 'Current PIN is incorrect.') };
  }
  return { ok: true };
}

export async function createDynamicAccount(params: {
  userId: string;
  email: string;
  amount: number;
  fullName: string;
  phone?: string;
}): Promise<{ ok: true; account: DynamicAccountInfo } | { ok: false; error: string }> {
  const parts = params.fullName.trim().split(/\s+/).filter(Boolean);
  const firstName = parts[0] || 'Daniel';
  const lastName = parts.slice(1).join(' ') || firstName;
  const txRef = `DYN-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

  const payload = {
    ...(params.userId ? { userId: params.userId, user_id: params.userId } : {}),
    email: params.email.trim(),
    amount: params.amount % 1 === 0 ? Math.round(params.amount) : params.amount,
    firstname: firstName,
    lastname: lastName,
    ...(params.phone ? { phone: params.phone.trim() } : {}),
    currency: 'NGN',
    tx_ref: txRef,
  };

  let lastError = 'Could not generate dynamic account right now. Please try again.';
  for (const fn of ['Create-Dynamic-Account', 'create-dynamic-account']) {
    const res = await gatewayRequest<Record<string, unknown>>(`/functions/v1/${fn}`, {
      method: 'POST',
      body: payload,
    });
    const containers = collectContainers(res.data);

    const accountNumber = cleanAccountNumber(
      extractFieldFromContainers(containers, ['account_number', 'accountNumber'])
    );

    if (accountNumber) {
      const bankName =
        extractFieldFromContainers(containers, [
          'account_bank_name',
          'bank_name',
          'bankName',
        ]) || 'Wema / Partner Bank';

      const dataObj =
        res.data?.data && typeof res.data.data === 'object'
          ? (res.data.data as Record<string, unknown>)
          : {};

      return {
        ok: true,
        account: {
          accountNumber,
          bankName,
          accountName: 'Wallet Topup',
          amount: Number(dataObj.amount || params.amount) || params.amount,
          expiresAt:
            extractFieldFromContainers(containers, ['account_expiration_datetime']) || undefined,
          note: extractFieldFromContainers(containers, ['note', 'narration']) || undefined,
        },
      };
    }

    lastError = extractErrorMessage(res, lastError);
    if (res.status !== 404) break;
  }

  return {
    ok: false,
    error: lastError,
  };
}

export async function createOrFetchPermanentAccount(params: {
  userId: string;
  email: string;
  fullName: string;
  phone: string;
  nin?: string;
  silentSync?: boolean;
}): Promise<PermanentAccountInfo> {
  const sanitizedName = sanitizeFullName(params.fullName, params.email) || params.fullName.trim();
  const parts = sanitizedName.split(/\s+/).filter(Boolean);
  const firstName = parts[0] || '';
  const lastName = parts.slice(1).join(' ') || firstName;
  const cleanNin = (params.nin || '').replace(/\D/g, '').slice(0, 11);
  const cleanPhone = params.phone.replace(/\D/g, '');

  const payload: Record<string, unknown> = {
    ...(params.userId ? { userId: params.userId, user_id: params.userId } : {}),
    email: params.email.trim().toLowerCase(),
    ...(firstName ? { firstName, firstname: firstName } : {}),
    ...(lastName ? { lastName, lastname: lastName } : {}),
    ...(cleanPhone ? { phone: cleanPhone, phonenumber: cleanPhone } : {}),
    ...(cleanNin.length === 11
      ? { nin: cleanNin, id_number: cleanNin, id_type: 'NIN' }
      : {}),
  };

  let res = await gatewayRequest<Record<string, unknown>>(
    '/functions/v1/Create-Permanent-Account',
    {
      method: 'POST',
      body: payload,
    }
  );

  if (res.status === 404) {
    res = await gatewayRequest<Record<string, unknown>>(
      '/functions/v1/create-permanent-account',
      {
        method: 'POST',
        body: payload,
      }
    );
  }

  const json = res.data || {};
  const containers = collectContainers(json);

  const accountNumber = cleanAccountNumber(
    extractFieldFromContainers(containers, [
      'account_number',
      'accountNumber',
      'permanent_account_number',
      'virtual_account_number',
      'order_ref',
    ])
  );

  const bankName =
    extractFieldFromContainers(containers, [
      'bank_name',
      'bankName',
      'permanent_account_bank',
      'virtual_bank_name',
    ]) || 'Flutterwave MFB';

  const accountName = extractAccountNameFromEdge(json, sanitizedName, params.email);

  if (accountNumber) {
    if (params.userId) {
      resetPermanentAccountUsers.delete(params.userId);
      await persistPermanentAccountToBackend({
        userId: params.userId,
        email: params.email,
        accountNumber,
        bankName,
        accountName: accountName || sanitizedName,
      });
    }
    return {
      success: true,
      accountNumber,
      bankName,
      accountName: accountName || sanitizedName,
      message: cleanStr(json.message) || 'Permanent account active',
      isKycVerified: true,
      isKycRequired: false,
    };
  }

  const rawCombinedError = extractErrorMessage(
    res,
    'No permanent account yet. Verify your 11-digit NIN to create one.'
  );
  const lowerErr = rawCombinedError.toLowerCase();
  let friendlyMessage = rawCombinedError;
  if (lowerErr.includes('invalid nin') || lowerErr.includes('invalid identity')) {
    friendlyMessage =
      'Identity Verification Failed: The 11-digit NIN could not be verified by NIMC. Please double-check your 11-digit NIN and try again.';
  } else if (lowerErr.includes('nin is required') || lowerErr.includes('identity is required')) {
    friendlyMessage = 'A valid 11-digit NIN is required to create a permanent account.';
  } else if (!params.silentSync && res.status >= 500) {
    friendlyMessage = sanitizeFriendlyError(
      rawCombinedError,
      'Network connection bad. Please check your internet connection and try again.'
    );
  }

  return {
    success: false,
    message: friendlyMessage,
    isKycRequired: json.isKycRequired === true || cleanNin.length !== 11,
    isKycVerified: false,
  };
}

export async function debitWallet(
  amount: number
): Promise<{ ok: true } | { ok: false; error: string }> {
  const res = await gatewayRequest<unknown>('/rest/v1/rpc/debit_wallet', {
    method: 'POST',
    body: { amount },
  });
  if (!res.ok) {
    const lower = res.rawText.toLowerCase();
    if (lower.includes('insufficient')) {
      return { ok: false, error: 'Insufficient wallet balance.' };
    }
    return { ok: false, error: extractErrorMessage(res, 'Withdrawal failed.') };
  }
  return { ok: true };
}
