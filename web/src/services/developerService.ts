import {
  extractErrorMessage,
  gatewayRequest,
  getValidAccessToken,
} from '../lib/supabaseGateway';
import { ForumTopic, UserApiKey } from '../types/vtu';

const API_KEY_STORAGE_PREFIX = 'daniel_vtu_api_key_';
const WEBHOOK_STORAGE_PREFIX = 'daniel_vtu_webhook_';
const FORUM_STORAGE_KEY = 'daniel_vtu_forum_topics_v1';

function formatCreatedAt(raw: unknown): string {
  const s = typeof raw === 'string' ? raw.trim() : '';
  if (!s) return 'Active';
  const parsed = Date.parse(s);
  if (!Number.isNaN(parsed)) {
    return new Date(parsed).toLocaleString('en-NG', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }
  return s.slice(0, 10);
}

export async function syncApiKeyState(
  userId: string,
  userEmail: string
): Promise<UserApiKey | null> {
  if (!userId) return null;

  const selectCandidates = [
    'key_prefix,last4,is_active,created_at,rotated_at,webhook_url',
    'key_prefix,last4,is_active,created_at,rotated_at',
    'key_prefix,last4,is_active,created_at,updated_at',
    'key_prefix,last4,is_active,created_at',
    '*',
  ];

  let row: Record<string, unknown> | null = null;
  for (const cols of selectCandidates) {
    const res = await gatewayRequest<Record<string, unknown>[]>(
      `/rest/v1/api_clients?user_id=eq.${encodeURIComponent(userId)}&select=${cols}&limit=1`,
      { method: 'GET' }
    ).catch(() => null);
    if (res?.ok && Array.isArray(res.data)) {
      if (res.data.length === 0) {
        return null;
      }
      row = res.data[0];
      break;
    }
  }

  if (!row) {
    return null;
  }

  const keyPrefix = String(row.key_prefix || '');
  const last4 = String(row.last4 || '');
  const isActive = row.is_active !== false;
  const createdAt = formatCreatedAt(row.rotated_at || row.updated_at || row.created_at);

  const localKey = localStorage.getItem(`${API_KEY_STORAGE_PREFIX}${userId}`) || '';
  const hasValidLocalKey =
    localKey.length > 0 && (!last4 || localKey.endsWith(last4));
  const remoteWebhook = typeof row.webhook_url === 'string' ? row.webhook_url.trim() : '';
  const webhookUrl =
    remoteWebhook || localStorage.getItem(`${WEBHOOK_STORAGE_PREFIX}${userId}`) || '';

  return {
    userId,
    userEmail,
    apiKey: hasValidLocalKey ? localKey : '',
    publicKey: `vtu_pub_${userId.replace(/-/g, '').slice(0, 16)}`,
    createdAt,
    status: isActive ? 'ACTIVE' : 'INACTIVE',
    webhookUrl,
    keyPrefix: keyPrefix || (hasValidLocalKey ? localKey.slice(0, 8) : 'vtu_live_'),
    last4: last4 || (hasValidLocalKey ? localKey.slice(-4) : '****'),
    isActive,
    existsOnServer: true,
    hasLocalKey: hasValidLocalKey,
  };
}

function extractKeyFromResponse(data: unknown, rawText = ''): string {
  if (typeof data === 'string') {
    const trimmed = data.trim();
    if (trimmed.startsWith('vtu_')) return trimmed;
  }
  if (data && typeof data === 'object') {
    const obj = data as Record<string, unknown>;
    const fields = [
      'api_key',
      'apiKey',
      'key',
      'vtu_api_key',
      'secret_key',
      'raw_key',
      'plain_key',
      'full_key',
      'token',
    ];
    for (const field of fields) {
      const val = obj[field];
      if (typeof val === 'string' && val.trim() && val.trim().toLowerCase() !== 'null') {
        return val.trim();
      }
    }
    for (const container of ['data', 'result', 'api_client', 'client']) {
      const nested = obj[container];
      if (nested && typeof nested === 'object') {
        const found = extractKeyFromResponse(nested);
        if (found) return found;
      }
    }
  }
  const match = rawText.match(/vtu_(?:live|test|key)_[A-Za-z0-9_-]{8,}/);
  if (match) return match[0];
  return '';
}

export async function generateOrRegenerateApiKey(params: {
  userId: string;
  email: string;
  name: string;
  regenerate: boolean;
  currentKey?: string;
}): Promise<{ ok: true; apiKey: string } | { ok: false; error: string }> {
  const token = await getValidAccessToken();
  if (!token) {
    return {
      ok: false,
      error: 'Session expired. Please sign in again to generate an API key.',
    };
  }

  const payload: Record<string, unknown> = {
    user_id: params.userId,
    userId: params.userId,
    email: params.email.trim().toLowerCase(),
    name: params.name.trim(),
    fullName: params.name.trim(),
    action: params.regenerate ? 'regenerate' : 'generate',
    regenerate: params.regenerate,
    is_regenerate: params.regenerate,
    isRegenerate: params.regenerate,
    regenerate_key: params.regenerate,
  };
  if (params.currentKey) {
    payload.current_api_key = params.currentKey;
  }

  let lastError = 'Failed to generate API key. Please try again.';
  for (const fn of ['Generate-api-key', 'generate-api-key']) {
    const res = await gatewayRequest<Record<string, unknown>>(`/functions/v1/${fn}`, {
      method: 'POST',
      body: payload,
      accessTokenOverride: token,
    });
    if (res.ok) {
      const key = extractKeyFromResponse(res.data, res.rawText);
      if (key) {
        localStorage.setItem(`${API_KEY_STORAGE_PREFIX}${params.userId}`, key);
        return { ok: true, apiKey: key };
      }
      lastError = 'No API key found in server response.';
    } else {
      lastError = extractErrorMessage(res, lastError);
      if (res.status !== 404) break;
    }
  }

  return { ok: false, error: lastError };
}

export async function saveWebhookUrl(userId: string, webhookUrl: string): Promise<void> {
  const cleanUrl = webhookUrl.trim();
  localStorage.setItem(`${WEBHOOK_STORAGE_PREFIX}${userId}`, cleanUrl);
  if (!userId) return;
  try {
    await gatewayRequest(`/rest/v1/api_clients?user_id=eq.${encodeURIComponent(userId)}`, {
      method: 'PATCH',
      headers: { Prefer: 'return=minimal' },
      body: { webhook_url: cleanUrl || null },
    });
  } catch {
    // non-fatal if column not present on api_clients
  }
}

const SEEDED_FORUM_TOPICS: ForumTopic[] = [
  {
    id: 'topic_official_guide',
    authorName: 'VTU Support Team',
    authorEmail: 'support@danielvtu.com',
    title: 'Official Guide: Authenticating & using your VTU API key',
    content:
      'Welcome to the Daniel VTU Developer Ecosystem! Every registered account has a unique secret API key. Pass it in your HTTP header as:\nAuthorization: Bearer vtu_live_YOUR_KEY\n\nAll endpoints support real-time vending for MTN, Airtel, GLO, 9mobile airtime, SME data bundles, electricity tokens, and Cable TV recharge.',
    category: 'API & Authentication',
    timestamp: 'Pinned • Official',
    likesCount: 24,
    repliesCount: 2,
    isPinned: true,
    isLikedByMe: false,
    replies: [
      {
        id: 'rep_1',
        authorName: 'Chidi K.',
        authorRole: 'Verified Developer',
        content: 'Does the API support custom webhook retry limits when our listener server is down?',
        timestamp: '2d ago',
      },
      {
        id: 'rep_2',
        authorName: 'VTU Support Team',
        authorRole: 'Admin',
        content:
          '@Chidi K. Yes! Our webhook dispatcher retries up to 5 times with exponential backoff (1m, 5m, 15m, 30m, 1h).',
        timestamp: '2d ago',
      },
    ],
  },
  {
    id: 'topic_webhook_signatures',
    authorName: 'Fola Adebayo',
    authorEmail: 'fola@fintech.ng',
    title: 'Best practices for handling asynchronous VTU transaction callbacks',
    content:
      'For high-volume data vending, always return HTTP 200 OK immediately on your webhook endpoint before running heavy database updates, and verify the transaction reference against your order log.',
    category: 'Webhooks & Callbacks',
    timestamp: '1d ago',
    likesCount: 15,
    repliesCount: 1,
    isPinned: false,
    isLikedByMe: false,
    replies: [
      {
        id: 'rep_3',
        authorName: 'VTU Support Team',
        authorRole: 'Admin',
        content:
          'Spot on Fola! Responding quickly with HTTP 200 keeps your webhook queue clean and prevents duplicate retries.',
        timestamp: '1d ago',
      },
    ],
  },
];

export function loadForumTopics(): ForumTopic[] {
  try {
    const raw = localStorage.getItem(FORUM_STORAGE_KEY);
    if (!raw) return SEEDED_FORUM_TOPICS;
    const parsed = JSON.parse(raw) as ForumTopic[];
    return Array.isArray(parsed) && parsed.length > 0 ? parsed : SEEDED_FORUM_TOPICS;
  } catch {
    return SEEDED_FORUM_TOPICS;
  }
}

export function saveForumTopics(topics: ForumTopic[]): void {
  try {
    localStorage.setItem(FORUM_STORAGE_KEY, JSON.stringify(topics));
  } catch {
    // ignore storage limits
  }
}
