import {
  extractErrorMessage,
  gatewayRequest,
  getEmailFromToken,
  getUserIdFromToken,
  loadStoredSession,
} from '../lib/supabaseGateway';
import {
  DynamicAccountInfo,
  PermanentAccountInfo,
  TransactionRecord,
  UserProfile,
} from '../types/vtu';

function cleanStr(val: unknown): string {
  if (typeof val !== 'string') return '';
  const t = val.trim();
  if (!t || t.toLowerCase() === 'null' || t.toLowerCase() === 'nil') return '';
  return t;
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

export async function fetchUserProfileAndHistory(): Promise<{
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
    : `email=eq.${encodeURIComponent(email)}`;

  const profileFilter = email
    ? `email=ilike.${encodeURIComponent(email)}`
    : `id=eq.${encodeURIComponent(userId)}`;

  const [res, profileRes] = await Promise.all([
    gatewayRequest<Record<string, unknown>[]>(
      `/rest/v1/users?select=*&${filter}&limit=1`,
      { method: 'GET' }
    ),
    gatewayRequest<Record<string, unknown>[]>(
      `/rest/v1/profiles?select=id,email,full_name,phone,avatar_url&${profileFilter}&limit=1`,
      { method: 'GET' }
    ).catch(() => null),
  ]);

  let row: Record<string, unknown> | null =
    res.ok && Array.isArray(res.data) && res.data.length > 0 ? res.data[0] : null;

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

  if (!row && email) {
    const byEmail = await gatewayRequest<Record<string, unknown>[]>(
      `/rest/v1/users?select=*&email=eq.${encodeURIComponent(email)}&limit=1`,
      { method: 'GET' }
    );
    if (byEmail.ok && Array.isArray(byEmail.data) && byEmail.data.length > 0) {
      row = byEmail.data[0];
    }
  }

  const meta = (session?.user?.user_metadata || {}) as Record<string, unknown>;
  const fullName =
    cleanStr(profileRow?.full_name || row?.full_name || row?.fullname || row?.name) ||
    cleanStr(meta.full_name || meta.fullname || meta.name) ||
    (email ? email.split('@')[0] : 'Daniel VTU User');

  const phone = cleanStr(profileRow?.phone || row?.phone || meta.phone || meta.phone_number);

  const profile: UserProfile = {
    id: cleanStr(row?.id || profileRow?.id) || userId,
    email: cleanStr(row?.email || profileRow?.email) || email,
    fullName,
    phone,
    walletBalance: Number(row?.wallet_balance ?? 0) || 0,
    cashbackBalance: Number(row?.cashback_balance ?? 0) || 0,
    avatarUrl: cleanStr(profileRow?.avatar_url) || cleanStr(row?.avatar_url) || undefined,
    virtualAccountNumber: cleanStr(row?.virtual_account_number || row?.account_number) || undefined,
    virtualBankName: cleanStr(row?.virtual_bank_name || row?.bank_name) || undefined,
    virtualAccountName: cleanStr(row?.virtual_account_name || row?.account_name) || undefined,
    dynamicAccountNumber: cleanStr(row?.dynamic_account_number) || undefined,
    dynamicBankName: cleanStr(row?.dynamic_bank_name) || undefined,
  };

  let transactions = parseTransactionArray(row?.transaction_history);

  try {
    const rpcRes = await gatewayRequest<unknown>('/rest/v1/rpc/get_my_transactions', {
      method: 'POST',
      body: { p_limit: 25 },
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

  return { profile, transactions };
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

export async function initializeFlutterwavePayment(params: {
  amount: number;
  email: string;
  name: string;
  phone?: string;
}): Promise<{ ok: true; paymentUrl: string; txRef: string } | { ok: false; error: string }> {
  const txRef = `FLW-VTU-${Date.now()}-${Math.floor(1000 + Math.random() * 9000)}`;
  const redirectUrl = `${window.location.origin}/?payment_verify=1`;

  const res = await gatewayRequest<Record<string, unknown>>('/functions/v1/flutterwave-payment', {
    method: 'POST',
    body: {
      action: 'initialize',
      amount: params.amount,
      email: params.email,
      name: params.name,
      ...(params.phone ? { phone: params.phone } : {}),
      tx_ref: txRef,
      redirect_url: redirectUrl,
    },
  });

  if (res.ok && res.data) {
    const link = cleanStr(res.data.payment_url);
    if (link.startsWith('https://')) {
      return { ok: true, paymentUrl: link, txRef };
    }
  }

  return {
    ok: false,
    error: extractErrorMessage(res, 'Unable to initialize payment with server.'),
  };
}

export async function verifyFlutterwavePayment(
  txRef: string
): Promise<{ ok: boolean; status: string; amount: number; message: string }> {
  const res = await gatewayRequest<Record<string, unknown>>('/functions/v1/flutterwave-payment', {
    method: 'POST',
    body: { action: 'verify', tx_ref: txRef },
  });

  if (res.ok && res.data) {
    const status = cleanStr(res.data.status || '');
    const success = Boolean(res.data.success) && status.toLowerCase() === 'successful';
    return {
      ok: success,
      status: status || (success ? 'successful' : 'pending'),
      amount: Number(res.data.amount || 0),
      message: cleanStr(res.data.message) || 'Payment verification completed.',
    };
  }

  return {
    ok: false,
    status: 'failed',
    amount: 0,
    message: extractErrorMessage(res, 'Unable to verify payment right now.'),
  };
}

export async function createDynamicAccount(params: {
  userId: string;
  email: string;
  amount: number;
  fullName: string;
}): Promise<{ ok: true; account: DynamicAccountInfo } | { ok: false; error: string }> {
  const parts = params.fullName.trim().split(/\s+/);
  const firstName = parts[0] || '';
  const lastName = parts.slice(1).join(' ');
  const txRef = `DYN-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

  const payload = {
    ...(params.userId ? { userId: params.userId, user_id: params.userId } : {}),
    email: params.email.trim(),
    amount: params.amount % 1 === 0 ? Math.round(params.amount) : params.amount,
    firstname: firstName,
    lastname: lastName,
    currency: 'NGN',
    tx_ref: txRef,
  };

  for (const fn of ['Create-Dynamic-Account', 'create-dynamic-account']) {
    const res = await gatewayRequest<Record<string, unknown>>(`/functions/v1/${fn}`, {
      method: 'POST',
      body: payload,
    });
    const root = res.data || {};
    const dataObj = (root.data && typeof root.data === 'object' ? root.data : {}) as Record<
      string,
      unknown
    >;

    const accountNumber =
      cleanStr(dataObj.account_number) ||
      cleanStr(dataObj.accountNumber) ||
      cleanStr(root.account_number) ||
      cleanStr(root.accountNumber);

    if (accountNumber) {
      const bankName =
        cleanStr(dataObj.account_bank_name) ||
        cleanStr(dataObj.bank_name) ||
        cleanStr(root.bank_name) ||
        'Wema / Partner Bank';

      return {
        ok: true,
        account: {
          accountNumber,
          bankName,
          accountName: 'Wallet Topup',
          amount: Number(dataObj.amount || params.amount) || params.amount,
          expiresAt: cleanStr(dataObj.account_expiration_datetime) || undefined,
          note: cleanStr(dataObj.note) || undefined,
        },
      };
    }
  }

  return {
    ok: false,
    error: 'Could not generate dynamic account right now. Please try again.',
  };
}

export async function createOrFetchPermanentAccount(params: {
  userId: string;
  email: string;
  fullName: string;
  phone: string;
  nin?: string;
}): Promise<PermanentAccountInfo> {
  const parts = params.fullName.trim().split(/\s+/);
  const firstName = parts[0] || '';
  const lastName = parts.slice(1).join(' ') || firstName;
  const cleanNin = (params.nin || '').replace(/\D/g, '').slice(0, 11);

  const res = await gatewayRequest<Record<string, unknown>>(
    '/functions/v1/Create-Permanent-Account',
    {
      method: 'POST',
      body: {
        ...(params.userId ? { userId: params.userId, user_id: params.userId } : {}),
        email: params.email.trim(),
        firstName,
        firstname: firstName,
        lastName,
        lastname: lastName,
        phone: params.phone.trim(),
        ...(cleanNin.length === 11 ? { nin: cleanNin } : {}),
      },
    }
  );

  const json = res.data || {};
  const dataObj = (json.data && typeof json.data === 'object' ? json.data : {}) as Record<
    string,
    unknown
  >;

  const accountNumber =
    cleanStr(json.accountNumber) ||
    cleanStr(json.account_number) ||
    cleanStr(dataObj.account_number) ||
    cleanStr(dataObj.accountNumber);

  const bankName =
    cleanStr(json.bankName) ||
    cleanStr(json.bank_name) ||
    cleanStr(dataObj.bank_name) ||
    cleanStr(dataObj.bankName) ||
    'Flutterwave MFB';

  const accountName =
    cleanStr(json.accountName) ||
    cleanStr(json.account_name) ||
    cleanStr(dataObj.account_name) ||
    params.fullName;

  if (res.ok && accountNumber) {
    return {
      success: true,
      accountNumber,
      bankName,
      accountName,
      message: cleanStr(json.message) || 'Permanent account active',
      isKycVerified: true,
      isKycRequired: false,
    };
  }

  return {
    success: false,
    message:
      cleanStr(json.message) ||
      cleanStr(json.error) ||
      'No permanent account yet. Verify your 11-digit NIN to create one.',
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
