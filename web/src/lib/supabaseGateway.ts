const DEFAULT_SUPABASE_URL = 'https://yjymxdzdhvbdjramlipg.supabase.co';
const GATEWAY_PATH = '/functions/v1/swift-processor';
const SESSION_STORAGE_KEY = 'daniel_vtu_session_v1';

export const SESSION_EXPIRED_EVENT = 'danielvtu:session-expired';

export interface StoredSession {
  access_token: string;
  refresh_token: string;
  expires_at?: number;
  user?: {
    id: string;
    email?: string;
    user_metadata?: Record<string, unknown>;
  };
}

export interface GatewayResponse<T = unknown> {
  status: number;
  ok: boolean;
  data: T | null;
  rawText: string;
}

export function getSupabaseUrl(): string {
  const envUrl = (import.meta.env.VITE_SUPABASE_URL as string | undefined)?.trim();
  const base = envUrl && envUrl.length > 0 ? envUrl : DEFAULT_SUPABASE_URL;
  return base.replace(/\/+$/, '');
}

export function getOptionalAnonKey(): string {
  return ((import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined) || '').trim();
}

export function loadStoredSession(): StoredSession | null {
  try {
    const raw = localStorage.getItem(SESSION_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as StoredSession;
    if (!parsed?.access_token) return null;
    return parsed;
  } catch {
    return null;
  }
}

export function saveStoredSession(session: StoredSession | null): void {
  try {
    if (!session) {
      localStorage.removeItem(SESSION_STORAGE_KEY);
    } else {
      localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
    }
  } catch {
    // ignore storage quota errors
  }
}

export function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split('.');
    if (parts.length < 2) return null;
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    const decoded = atob(padded);
    return JSON.parse(decoded) as Record<string, unknown>;
  } catch {
    return null;
  }
}

export function isJwtExpired(token?: string | null, bufferSeconds = 45): boolean {
  if (!token || !token.startsWith('ey')) return true;
  const payload = decodeJwtPayload(token);
  const exp = typeof payload?.exp === 'number' ? payload.exp : 0;
  if (!exp) return false;
  const nowSec = Math.floor(Date.now() / 1000);
  return nowSec >= exp - bufferSeconds;
}

export function getUserIdFromToken(token?: string | null): string {
  if (!token) return '';
  const payload = decodeJwtPayload(token);
  return typeof payload?.sub === 'string' ? payload.sub : '';
}

export function getEmailFromToken(token?: string | null): string {
  if (!token) return '';
  const payload = decodeJwtPayload(token);
  return typeof payload?.email === 'string' ? payload.email : '';
}

function isPublicAuthPath(targetPath: string): boolean {
  const lower = targetPath.toLowerCase();
  return (
    lower.startsWith('/auth/v1/token') ||
    lower.startsWith('/auth/v1/otp') ||
    lower.startsWith('/auth/v1/verify') ||
    lower.startsWith('/auth/v1/signup') ||
    lower.startsWith('/auth/v1/recover')
  );
}

/**
 * Only virtual-account Edge Functions accept anonymous/gateway-injected fallback auth
 * if a legacy gateway rejects client JWTs (matching Android SupabaseAuthManager).
 * Protected user Edge Functions (`Generate-api-key`, `Gsubz-VTU-Services`) MUST always
 * authenticate using the signed-in user's valid JWT.
 */
function isVaFallbackEdgePath(targetPath: string): boolean {
  const lower = targetPath.toLowerCase();
  return (
    lower.includes('/functions/v1/create-dynamic-account') ||
    lower.includes('/functions/v1/create-permanent-account') ||
    lower.includes('/functions/v1/create-virtual-account')
  );
}

let refreshInFlight: Promise<StoredSession | null> | null = null;

export async function refreshSessionIfNeeded(force = false): Promise<StoredSession | null> {
  const current = loadStoredSession();
  if (!current) return null;
  if (!force && !isJwtExpired(current.access_token)) {
    return current;
  }
  if (!current.refresh_token) {
    return !isJwtExpired(current.access_token, 5) ? current : null;
  }

  if (refreshInFlight) return refreshInFlight;

  refreshInFlight = (async () => {
    try {
      const res = await rawGatewayFetch<StoredSession & { error?: string; error_description?: string }>(
        '/auth/v1/token?grant_type=refresh_token',
        {
          method: 'POST',
          body: { refresh_token: current.refresh_token },
          skipAuthHeader: true,
        }
      );
      if (res.ok && res.data?.access_token) {
        const next: StoredSession = {
          access_token: res.data.access_token,
          refresh_token: res.data.refresh_token || current.refresh_token,
          expires_at: res.data.expires_at,
          user: res.data.user || current.user,
        };
        saveStoredSession(next);
        return next;
      }
      return !isJwtExpired(current.access_token, 5) ? current : null;
    } catch {
      return !isJwtExpired(current.access_token, 5) ? current : null;
    } finally {
      refreshInFlight = null;
    }
  })();

  return refreshInFlight;
}

export async function getValidAccessToken(): Promise<string | null> {
  const session = await refreshSessionIfNeeded(false);
  return session?.access_token || null;
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  headers?: Record<string, string>;
  accessTokenOverride?: string | null;
  skipAuthHeader?: boolean;
}

async function rawGatewayFetch<T = unknown>(
  targetPath: string,
  options: RequestOptions = {}
): Promise<GatewayResponse<T>> {
  const baseUrl = getSupabaseUrl();
  const anonKey = getOptionalAnonKey();
  const normalizedTarget = targetPath.startsWith('/') ? targetPath : `/${targetPath}`;
  const method = options.method || 'GET';

  const useDirect = anonKey.length > 0;
  const fetchUrl = useDirect ? `${baseUrl}${normalizedTarget}` : `${baseUrl}${GATEWAY_PATH}`;

  const headers: Record<string, string> = {
    'x-client-info': 'vtu-web-client/1.0',
    ...options.headers,
  };

  if (!useDirect) {
    headers['x-target-path'] = normalizedTarget;
  } else {
    headers['apikey'] = anonKey;
  }

  const isBinaryBody =
    options.body instanceof Blob ||
    options.body instanceof ArrayBuffer ||
    ArrayBuffer.isView(options.body);

  if (options.body !== undefined && !isBinaryBody && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }

  const suppressAuth = options.skipAuthHeader || isPublicAuthPath(normalizedTarget);

  if (!suppressAuth) {
    const token = options.accessTokenOverride || loadStoredSession()?.access_token;
    if (token && !isJwtExpired(token, 5)) {
      headers['Authorization'] = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
    } else if (useDirect) {
      headers['Authorization'] = `Bearer ${anonKey}`;
    }
  } else if (useDirect && !headers['Authorization']) {
    headers['Authorization'] = `Bearer ${anonKey}`;
  }

  let fetchBody: BodyInit | undefined;
  if (options.body !== undefined) {
    if (isBinaryBody) {
      fetchBody = options.body as BodyInit;
    } else {
      fetchBody = JSON.stringify(options.body);
    }
  }

  const response = await fetch(fetchUrl, {
    method,
    headers,
    body: fetchBody,
    cache: 'no-store',
  });

  const rawText = await response.text();
  let parsed: T | null = null;
  if (rawText) {
    try {
      parsed = JSON.parse(rawText) as T;
    } catch {
      parsed = null;
    }
  }

  return {
    status: response.status,
    ok: response.ok,
    data: parsed,
    rawText,
  };
}

export async function gatewayRequest<T = unknown>(
  targetPath: string,
  options: RequestOptions = {}
): Promise<GatewayResponse<T>> {
  const normalizedTarget = targetPath.startsWith('/') ? targetPath : `/${targetPath}`;
  const isProtected = !options.skipAuthHeader && !isPublicAuthPath(normalizedTarget);

  if (isProtected && !options.accessTokenOverride) {
    await refreshSessionIfNeeded(false);
  }

  let res = await rawGatewayFetch<T>(normalizedTarget, options);

  if (res.status === 401 && isProtected && !options.accessTokenOverride) {
    const refreshed = await refreshSessionIfNeeded(true);
    if (refreshed?.access_token) {
      res = await rawGatewayFetch<T>(normalizedTarget, {
        ...options,
        accessTokenOverride: refreshed.access_token,
      });
    }
  }

  // Only virtual-account Edge Functions fall back to gateway-injected anon auth on 401/403
  if (
    (res.status === 401 || res.status === 403) &&
    isVaFallbackEdgePath(normalizedTarget) &&
    !options.skipAuthHeader
  ) {
    res = await rawGatewayFetch<T>(normalizedTarget, {
      ...options,
      skipAuthHeader: true,
      accessTokenOverride: null,
    });
  } else if (res.status === 401 && isProtected) {
    const lower = res.rawText.toLowerCase();
    if (
      lower.includes('invalid jwt') ||
      lower.includes('jwt expired') ||
      lower.includes('invalid or expired session') ||
      lower.includes('not logged in')
    ) {
      window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT));
    }
  }

  return res;
}

export async function uploadBinaryToStorage(
  bucketPath: string,
  blob: Blob,
  contentType = 'image/jpeg'
): Promise<boolean> {
  const anonKey = getOptionalAnonKey();
  if (!anonKey) {
    // swift-processor proxy forwards JSON bodies; Storage direct upload requires direct Storage endpoint
    return false;
  }

  await refreshSessionIfNeeded(false);
  const cleanPath = bucketPath.replace(/^\/+/, '');
  for (const method of ['POST', 'PUT'] as const) {
    const res = await rawGatewayFetch(`/storage/v1/object/${cleanPath}`, {
      method,
      headers: {
        'x-upsert': 'true',
        'Content-Type': contentType,
      },
      body: blob,
    }).catch(() => null);
    if (res?.ok) return true;
  }
  return false;
}

export function extractErrorMessage(
  res: GatewayResponse<Record<string, unknown>> | unknown,
  fallback = 'Network connection bad. Please check your internet connection and try again.'
): string {
  if (res && typeof res === 'object' && 'rawText' in res) {
    const gw = res as GatewayResponse<Record<string, unknown>>;
    const d = gw.data;
    const nestedError =
      d?.error && typeof d.error === 'object' ? (d.error as Record<string, unknown>) : null;
    const detailsObj =
      d?.details && typeof d.details === 'object' ? (d.details as Record<string, unknown>) : null;
    const detailsError =
      detailsObj?.error && typeof detailsObj.error === 'object'
        ? (detailsObj.error as Record<string, unknown>)
        : null;

    const rawMsg =
      (typeof detailsError?.message === 'string' && detailsError.message) ||
      (typeof detailsObj?.message === 'string' && detailsObj.message) ||
      (typeof nestedError?.message === 'string' && nestedError.message) ||
      (typeof d?.msg === 'string' && d.msg) ||
      (typeof d?.error_description === 'string' && d.error_description) ||
      (typeof d?.message === 'string' && d.message) ||
      (typeof d?.error === 'string' && d.error) ||
      gw.rawText ||
      '';
    return sanitizeFriendlyError(rawMsg, fallback);
  }
  if (res instanceof Error) {
    return sanitizeFriendlyError(res.message, fallback);
  }
  return fallback;
}

export function sanitizeFriendlyError(
  raw: string,
  fallback = 'Network connection bad. Please check your internet connection and try again.'
): string {
  const lower = (raw || '').toLowerCase();
  if (!lower.trim()) return fallback;

  if (lower.includes('invalid login credentials') || lower.includes('invalid_credentials')) {
    return 'Invalid login credentials. Please check your email and password, or click Sign Up if you are new.';
  }
  if (lower.includes('user not found')) {
    return 'No account found with this email. Please sign up first.';
  }
  if (lower.includes('email not confirmed') || lower.includes('not_confirmed')) {
    return 'Email address has not been confirmed yet. Please verify your 6-digit code.';
  }
  if (
    lower.includes('user already registered') ||
    lower.includes('user_already_exists') ||
    lower.includes('already exists') ||
    lower.includes('already registered')
  ) {
    return 'This email address has already been registered.';
  }
  if (lower.includes('token has expired') || lower.includes('otp_expired') || lower.includes('code has expired')) {
    return 'The confirmation code has expired. Please request a new code.';
  }
  if (
    lower.includes('invalid token') ||
    lower.includes('otp_invalid') ||
    lower.includes('token is invalid') ||
    lower.includes('invalid code')
  ) {
    return 'Invalid 6-digit code. Please check the code sent to your email and try again.';
  }
  if (
    lower.includes('rate limit') ||
    lower.includes('over_request_rate_limit') ||
    lower.includes('too many requests')
  ) {
    return 'Too many attempts. Please wait a few moments before trying again.';
  }
  if (lower.includes('password should be at least') || lower.includes('weak_password')) {
    return 'Password must be at least 6 characters.';
  }
  if (
    lower.includes('failed to fetch') ||
    lower.includes('networkerror') ||
    lower.includes('502') ||
    lower.includes('503') ||
    lower.includes('504') ||
    lower.includes('bad gateway') ||
    lower.includes('service unavailable') ||
    lower.includes('gateway timeout')
  ) {
    return fallback;
  }

  const cleaned = raw
    .split('\n')[0]
    .replace(/Supabase|Gsubz|Edge\s*Function/gi, '')
    .trim();

  if (cleaned.length > 0 && cleaned.length < 140 && !/bearer|apikey/i.test(cleaned)) {
    return cleaned;
  }
  return fallback;
}
