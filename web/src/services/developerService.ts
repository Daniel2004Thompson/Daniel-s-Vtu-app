import { extractErrorMessage, gatewayRequest } from '../lib/supabaseGateway';
import { ForumTopic, UserApiKey } from '../types/vtu';

const API_KEY_STORAGE_PREFIX = 'daniel_vtu_api_key_';
const WEBHOOK_STORAGE_PREFIX = 'daniel_vtu_webhook_';
const FORUM_STORAGE_KEY = 'daniel_vtu_forum_topics_v1';

export async function syncApiKeyState(
  userId: string,
  userEmail: string
): Promise<UserApiKey | null> {
  if (!userId) return null;

  const res = await gatewayRequest<Record<string, unknown>[]>(
    `/rest/v1/api_clients?user_id=eq.${encodeURIComponent(
      userId
    )}&select=key_prefix,last4,is_active,created_at,rotated_at&limit=1`,
    { method: 'GET' }
  );

  if (!res.ok || !Array.isArray(res.data) || res.data.length === 0) {
    return null;
  }

  const row = res.data[0];
  const keyPrefix = String(row.key_prefix || '');
  const last4 = String(row.last4 || '');
  const isActive = row.is_active !== false;
  const createdAt = String(row.rotated_at || row.created_at || 'Active');

  const localKey = localStorage.getItem(`${API_KEY_STORAGE_PREFIX}${userId}`) || '';
  const hasValidLocalKey =
    localKey.length > 0 && (!last4 || localKey.endsWith(last4));
  const webhookUrl = localStorage.getItem(`${WEBHOOK_STORAGE_PREFIX}${userId}`) || '';

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

function extractKeyFromResponse(data: Record<string, unknown> | null): string {
  if (!data) return '';
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
    const val = data[field];
    if (typeof val === 'string' && val.trim() && val.trim().toLowerCase() !== 'null') {
      return val.trim();
    }
  }
  for (const container of ['data', 'result', 'api_client', 'client']) {
    const nested = data[container];
    if (nested && typeof nested === 'object') {
      const found = extractKeyFromResponse(nested as Record<string, unknown>);
      if (found) return found;
    }
  }
  return '';
}

export async function generateOrRegenerateApiKey(params: {
  userId: string;
  email: string;
  name: string;
  regenerate: boolean;
  currentKey?: string;
}): Promise<{ ok: true; apiKey: string } | { ok: false; error: string }> {
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
    });
    if (res.ok) {
      const key = extractKeyFromResponse(res.data);
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

export function saveWebhookUrlLocal(userId: string, webhookUrl: string): void {
  localStorage.setItem(`${WEBHOOK_STORAGE_PREFIX}${userId}`, webhookUrl.trim());
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
