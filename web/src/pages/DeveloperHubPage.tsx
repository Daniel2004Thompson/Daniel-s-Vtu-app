import React, { useEffect, useState } from 'react';
import {
  Code2,
  Copy,
  Eye,
  EyeOff,
  Heart,
  Key,
  MessageSquare,
  Plus,
  RefreshCw,
  Send,
  Terminal,
} from 'lucide-react';
import { useApp } from '../context/AppContext';
import {
  generateOrRegenerateApiKey,
  loadForumTopics,
  saveForumTopics,
  saveWebhookUrlLocal,
  syncApiKeyState,
} from '../services/developerService';
import { ForumTopic, UserApiKey } from '../types/vtu';

export const DeveloperHubPage: React.FC = () => {
  const { profile, showToast } = useApp();
  const [activeTab, setActiveTab] = useState<'credentials' | 'forum' | 'docs'>('credentials');

  // Credentials state
  const [apiKeyState, setApiKeyState] = useState<UserApiKey | null>(null);
  const [loadingKey, setLoadingKey] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [revealed, setRevealed] = useState(false);
  const [webhookInput, setWebhookInput] = useState('');
  const [confirmRegen, setConfirmRegen] = useState(false);

  // Forum state
  const [topics, setTopics] = useState<ForumTopic[]>(() => loadForumTopics());
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [replyByTopic, setReplyByTopic] = useState<Record<string, string>>({});
  const [newTopicOpen, setNewTopicOpen] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newContent, setNewContent] = useState('');
  const [newCategory, setNewCategory] = useState('API & Authentication');

  // Docs state
  const [docLang, setDocLang] = useState<'curl' | 'python' | 'node' | 'fetch'>('curl');

  useEffect(() => {
    if (!profile?.id) return;
    setLoadingKey(true);
    syncApiKeyState(profile.id, profile.email)
      .then((res) => {
        setApiKeyState(res);
        setWebhookInput(res?.webhookUrl || '');
      })
      .finally(() => setLoadingKey(false));
  }, [profile?.id, profile?.email]);

  const copyText = async (val: string, label: string) => {
    try {
      await navigator.clipboard.writeText(val);
      showToast(`${label} copied!`, 'success');
    } catch {
      showToast(`Failed to copy ${label}`, 'error');
    }
  };

  const handleGenerateKey = async (regenerate: boolean) => {
    if (!profile) return;
    setGenerating(true);
    try {
      const res = await generateOrRegenerateApiKey({
        userId: profile.id,
        email: profile.email,
        name: profile.fullName,
        regenerate,
        currentKey: apiKeyState?.apiKey,
      });
      if (!res.ok) {
        showToast(res.error, 'error');
        return;
      }
      const synced = await syncApiKeyState(profile.id, profile.email);
      setApiKeyState(synced);
      setRevealed(true);
      setConfirmRegen(false);
      showToast(regenerate ? 'New API Key regenerated!' : 'API Key generated!', 'success');
    } finally {
      setGenerating(false);
    }
  };

  const handleSaveWebhook = (e: React.FormEvent) => {
    e.preventDefault();
    if (!profile) return;
    saveWebhookUrlLocal(profile.id, webhookInput);
    setApiKeyState((prev) => (prev ? { ...prev, webhookUrl: webhookInput.trim() } : prev));
    showToast('Webhook URL saved!', 'success');
  };

  const updateTopics = (next: ForumTopic[]) => {
    setTopics(next);
    saveForumTopics(next);
  };

  const toggleLike = (topicId: string) => {
    updateTopics(
      topics.map((t) =>
        t.id === topicId
          ? {
              ...t,
              isLikedByMe: !t.isLikedByMe,
              likesCount: !t.isLikedByMe ? t.likesCount + 1 : Math.max(0, t.likesCount - 1),
            }
          : t
      )
    );
  };

  const submitReply = (topicId: string) => {
    const text = (replyByTopic[topicId] || '').trim();
    if (!text) return;
    updateTopics(
      topics.map((t) =>
        t.id === topicId
          ? {
              ...t,
              repliesCount: t.repliesCount + 1,
              replies: [
                ...t.replies,
                {
                  id: `rep_${Date.now()}`,
                  authorName: profile?.fullName || 'Developer',
                  authorRole: 'Verified Developer',
                  content: text,
                  timestamp: 'Just now',
                },
              ],
            }
          : t
      )
    );
    setReplyByTopic((prev) => ({ ...prev, [topicId]: '' }));
    showToast('Reply posted!', 'success');
  };

  const submitNewTopic = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTitle.trim() || !newContent.trim()) return;
    const topic: ForumTopic = {
      id: `topic_${Date.now()}`,
      authorName: profile?.fullName || 'Developer',
      authorEmail: profile?.email || '',
      title: newTitle.trim(),
      content: newContent.trim(),
      category: newCategory,
      timestamp: 'Just now',
      likesCount: 0,
      repliesCount: 0,
      isPinned: false,
      isLikedByMe: false,
      replies: [],
    };
    updateTopics([topic, ...topics]);
    setNewTitle('');
    setNewContent('');
    setNewTopicOpen(false);
    showToast('Topic posted to Developers Forum!', 'success');
  };

  const keyForDocs = apiKeyState?.apiKey || 'vtu_live_YOUR_SECRET_KEY';
  const snippets: Record<typeof docLang, string> = {
    curl: `curl -X POST https://api.danielvtu.com/v1/airtime/topup \\\n  -H "Authorization: Bearer ${keyForDocs}" \\\n  -H "Content-Type: application/json" \\\n  -d '{"network":"MTN","phone":"08031234567","amount":1000}'`,
    python: `import requests\n\nres = requests.post(\n  "https://api.danielvtu.com/v1/airtime/topup",\n  headers={"Authorization": "Bearer ${keyForDocs}"},\n  json={"network": "MTN", "phone": "08031234567", "amount": 1000},\n)\nprint(res.json())`,
    node: `const axios = require('axios');\n\nconst { data } = await axios.post(\n  'https://api.danielvtu.com/v1/airtime/topup',\n  { network: 'MTN', phone: '08031234567', amount: 1000 },\n  { headers: { Authorization: 'Bearer ${keyForDocs}' } }\n);\nconsole.log(data);`,
    fetch: `const res = await fetch('https://api.danielvtu.com/v1/airtime/topup', {\n  method: 'POST',\n  headers: {\n    Authorization: 'Bearer ${keyForDocs}',\n    'Content-Type': 'application/json',\n  },\n  body: JSON.stringify({ network: 'MTN', phone: '08031234567', amount: 1000 }),\n});\nconsole.log(await res.json());`,
  };

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div>
        <h1 className="text-2xl font-extrabold text-slate-900 dark:text-white">
          Developer Hub &amp; Forum
        </h1>
        <p className="text-xs text-slate-500 dark:text-slate-300">
          Manage your live VTU secret key, webhook listeners, API quickstart, and community Q&amp;A
        </p>
      </div>

      {/* Sub-navigation Tabs */}
      <div className="grid grid-cols-3 gap-2 rounded-2xl border border-slate-200 bg-white p-1.5 dark:border-slate-700 dark:bg-[#102A43]">
        {[
          { id: 'credentials' as const, label: 'API Credentials', icon: Key },
          { id: 'forum' as const, label: 'Community Forum', icon: MessageSquare },
          { id: 'docs' as const, label: 'API Docs', icon: Code2 },
        ].map((tab) => (
          <button
            key={tab.id}
            type="button"
            onClick={() => setActiveTab(tab.id)}
            className={`flex items-center justify-center gap-2 rounded-xl py-2.5 text-xs font-bold transition ${
              activeTab === tab.id
                ? 'bg-[#00875A] text-white shadow-sm'
                : 'text-slate-600 hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-slate-800'
            }`}
          >
            <tab.icon className="h-4 w-4" /> {tab.label}
          </button>
        ))}
      </div>

      {/* TAB 1: API CREDENTIALS */}
      {activeTab === 'credentials' && (
        <div className="space-y-5">
          <div className="flex items-center gap-4 rounded-2xl bg-[#0A192F] p-5 text-white">
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-[#00875A]">
              <Terminal className="h-6 w-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-base font-bold">{profile?.fullName || 'VTU Developer'}</h2>
                <span className="rounded bg-[#00875A]/30 px-2 py-0.5 text-[10px] font-bold text-emerald-300">
                  VERIFIED
                </span>
              </div>
              <p className="text-xs text-slate-300">{profile?.email}</p>
              <p className="mt-0.5 text-[11px] font-semibold text-cyan-400">
                Environment: LIVE • Rate Limit: 60 req/min
              </p>
            </div>
          </div>

          <div className="rounded-3xl border border-slate-200 bg-white p-6 dark:border-slate-700 dark:bg-[#102A43]">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Key className="h-5 w-5 text-[#00875A]" />
                <h3 className="text-base font-extrabold text-slate-900 dark:text-white">
                  Live Secret API Key
                </h3>
              </div>
              {apiKeyState?.existsOnServer && (
                <span className="rounded-full bg-[#E8F8F2] px-2.5 py-0.5 text-[10px] font-bold text-[#00875A] dark:bg-emerald-950">
                  {apiKeyState.status}
                </span>
              )}
            </div>

            {loadingKey ? (
              <div className="mt-4 h-12 animate-pulse rounded-xl bg-slate-100 dark:bg-slate-800" />
            ) : !apiKeyState?.existsOnServer ? (
              <p className="mt-3 text-xs text-slate-500 dark:text-slate-300">
                You haven&apos;t generated an API key yet. Click below to create a unique secret
                key for automating Airtime, Data, Electricity, and Cable TV requests.
              </p>
            ) : apiKeyState.hasLocalKey ? (
              <div className="mt-4 space-y-3">
                <div className="flex items-center justify-between gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2.5 dark:border-slate-700 dark:bg-slate-900/70">
                  <code className="flex-1 truncate font-mono text-xs font-bold text-slate-900 dark:text-emerald-300">
                    {revealed
                      ? apiKeyState.apiKey
                      : `${apiKeyState.keyPrefix}••••••••••••••••${apiKeyState.last4}`}
                  </code>
                  <button
                    type="button"
                    onClick={() => setRevealed((r) => !r)}
                    className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-200 dark:hover:bg-slate-800"
                  >
                    {revealed ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                  <button
                    type="button"
                    onClick={() => copyText(apiKeyState.apiKey, 'API Key')}
                    className="rounded-lg p-1.5 text-[#00875A] hover:bg-emerald-50 dark:hover:bg-slate-800"
                  >
                    <Copy className="h-4 w-4" />
                  </button>
                </div>
                <p className="text-[11px] text-slate-500 dark:text-slate-400">
                  Created: {apiKeyState.createdAt} • Pass as{' '}
                  <code className="font-mono">Authorization: Bearer &lt;API_KEY&gt;</code>
                </p>
              </div>
            ) : (
              <div className="mt-4 space-y-2">
                <div className="rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2.5 font-mono text-xs font-bold dark:border-slate-700 dark:bg-slate-900/70 dark:text-white">
                  {apiKeyState.keyPrefix}…{apiKeyState.last4}
                </div>
                <p className="text-xs text-slate-500 dark:text-slate-300">
                  An API key exists on your account, but for security the full secret is only shown
                  on the browser where it was created. Regenerate below if you need a fresh key here.
                </p>
              </div>
            )}

            <div className="mt-5">
              <button
                type="button"
                disabled={generating}
                onClick={() =>
                  apiKeyState?.existsOnServer ? setConfirmRegen(true) : handleGenerateKey(false)
                }
                className="inline-flex items-center gap-2 rounded-xl bg-[#00875A] px-5 py-2.5 text-xs font-bold text-white shadow-sm hover:bg-[#005A3C] disabled:opacity-50"
              >
                <RefreshCw className={`h-3.5 w-3.5 ${generating ? 'animate-spin' : ''}`} />
                {generating
                  ? 'Working...'
                  : apiKeyState?.existsOnServer
                  ? 'Regenerate Secret Key'
                  : 'Generate Live API Key'}
              </button>
            </div>
          </div>

          <form
            onSubmit={handleSaveWebhook}
            className="rounded-3xl border border-slate-200 bg-white p-6 dark:border-slate-700 dark:bg-[#102A43]"
          >
            <h3 className="text-sm font-extrabold text-slate-900 dark:text-white">
              Webhook Callback URL
            </h3>
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-300">
              Receive real-time delivery status updates for asynchronous orders.
            </p>
            <div className="mt-3 flex gap-2">
              <input
                type="url"
                value={webhookInput}
                onChange={(e) => setWebhookInput(e.target.value)}
                placeholder="https://yourserver.com/webhooks/daniel-vtu"
                className="flex-1 rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2.5 font-mono text-xs outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
              />
              <button
                type="submit"
                className="rounded-xl bg-[#0A192F] px-4 py-2.5 text-xs font-bold text-white hover:bg-[#1B3A5A]"
              >
                Save URL
              </button>
            </div>
          </form>
        </div>
      )}

      {/* TAB 2: DEVELOPERS FORUM */}
      {activeTab === 'forum' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-extrabold text-slate-900 dark:text-white">
              Community Discussions
            </h2>
            <button
              type="button"
              onClick={() => setNewTopicOpen(true)}
              className="inline-flex items-center gap-1.5 rounded-xl bg-[#00875A] px-3.5 py-2 text-xs font-bold text-white hover:bg-[#005A3C]"
            >
              <Plus className="h-3.5 w-3.5" /> New Topic
            </button>
          </div>

          {topics.map((topic) => {
            const expanded = expandedId === topic.id;
            return (
              <div
                key={topic.id}
                className="rounded-2xl border border-slate-200 bg-white p-5 dark:border-slate-700 dark:bg-[#102A43]"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="rounded-full bg-[#E8F8F2] px-2.5 py-0.5 text-[10px] font-bold text-[#00875A] dark:bg-emerald-950">
                    {topic.category}
                  </span>
                  <span className="text-[11px] text-slate-400">{topic.timestamp}</span>
                </div>
                <h3 className="mt-2 text-sm font-extrabold text-slate-900 dark:text-white">
                  {topic.title}
                </h3>
                <p className="mt-1 whitespace-pre-line text-xs leading-relaxed text-slate-600 dark:text-slate-300">
                  {topic.content}
                </p>

                <div className="mt-4 flex items-center justify-between border-t border-slate-100 pt-3 text-xs dark:border-slate-800">
                  <div className="flex items-center gap-4">
                    <button
                      type="button"
                      onClick={() => toggleLike(topic.id)}
                      className={`inline-flex items-center gap-1 font-bold ${
                        topic.isLikedByMe ? 'text-red-500' : 'text-slate-500 dark:text-slate-300'
                      }`}
                    >
                      <Heart className="h-4 w-4" /> {topic.likesCount}
                    </button>
                    <button
                      type="button"
                      onClick={() => setExpandedId(expanded ? null : topic.id)}
                      className="inline-flex items-center gap-1 font-bold text-slate-500 dark:text-slate-300"
                    >
                      <MessageSquare className="h-4 w-4" /> {topic.repliesCount} replies
                    </button>
                  </div>
                  <button
                    type="button"
                    onClick={() => setExpandedId(expanded ? null : topic.id)}
                    className="font-bold text-[#00875A] hover:underline"
                  >
                    {expanded ? 'Hide Thread' : 'View Thread'}
                  </button>
                </div>

                {expanded && (
                  <div className="mt-4 space-y-2.5 border-t border-slate-100 pt-3 dark:border-slate-800">
                    {topic.replies.map((rep) => (
                      <div
                        key={rep.id}
                        className="rounded-xl bg-slate-50 p-3 text-xs dark:bg-slate-900/60"
                      >
                        <div className="flex items-center justify-between">
                          <span className="font-bold text-slate-900 dark:text-white">
                            {rep.authorName}{' '}
                            <span className="ml-1 rounded bg-emerald-100 px-1.5 py-0.5 text-[9px] text-[#00875A] dark:bg-emerald-950">
                              {rep.authorRole}
                            </span>
                          </span>
                          <span className="text-[10px] text-slate-400">{rep.timestamp}</span>
                        </div>
                        <p className="mt-1 text-slate-600 dark:text-slate-300">{rep.content}</p>
                      </div>
                    ))}
                    <div className="flex gap-2 pt-1">
                      <input
                        type="text"
                        value={replyByTopic[topic.id] || ''}
                        onChange={(e) =>
                          setReplyByTopic((prev) => ({ ...prev, [topic.id]: e.target.value }))
                        }
                        placeholder="Write a developer reply..."
                        className="flex-1 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-xs outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
                      />
                      <button
                        type="button"
                        onClick={() => submitReply(topic.id)}
                        className="rounded-xl bg-[#00875A] p-2 text-white hover:bg-[#005A3C]"
                      >
                        <Send className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* TAB 3: API DOCS */}
      {activeTab === 'docs' && (
        <div className="space-y-5">
          <div className="overflow-hidden rounded-2xl bg-[#0F172A] text-white shadow-lg">
            <div className="flex items-center justify-between bg-[#1E293B] px-4 py-2.5">
              <div className="flex gap-1.5">
                {(['curl', 'python', 'node', 'fetch'] as const).map((lang) => (
                  <button
                    key={lang}
                    type="button"
                    onClick={() => setDocLang(lang)}
                    className={`rounded-lg px-3 py-1 text-xs font-bold uppercase ${
                      docLang === lang ? 'bg-[#00875A] text-white' : 'text-slate-400'
                    }`}
                  >
                    {lang}
                  </button>
                ))}
              </div>
              <button
                type="button"
                onClick={() => copyText(snippets[docLang], 'Code snippet')}
                className="rounded-lg p-1.5 text-slate-300 hover:bg-slate-700"
              >
                <Copy className="h-4 w-4" />
              </button>
            </div>
            <pre className="overflow-x-auto p-4 font-mono text-xs leading-relaxed text-emerald-300">
              {snippets[docLang]}
            </pre>
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            {[
              {
                method: 'GET',
                path: '/v1/wallet/balance',
                desc: 'Fetch current live wallet balance and currency for your API account.',
              },
              {
                method: 'POST',
                path: '/v1/airtime/topup',
                desc: 'Instant airtime dispatch to MTN, Airtel, GLO, or 9mobile with cashback.',
              },
              {
                method: 'POST',
                path: '/v1/data/purchase',
                desc: 'Vend SME and Corporate data bundles with real-time delivery.',
              },
              {
                method: 'POST',
                path: '/v1/bills/electricity',
                desc: 'Verify meter and dispense prepaid electricity token instantly.',
              },
            ].map((ep) => (
              <div
                key={ep.path}
                className="rounded-2xl border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-[#102A43]"
              >
                <div className="flex items-center gap-2">
                  <span
                    className={`rounded px-2 py-0.5 text-[10px] font-extrabold text-white ${
                      ep.method === 'GET' ? 'bg-sky-600' : 'bg-[#00875A]'
                    }`}
                  >
                    {ep.method}
                  </span>
                  <code className="font-mono text-xs font-bold text-slate-900 dark:text-white">
                    {ep.path}
                  </code>
                </div>
                <p className="mt-2 text-xs text-slate-500 dark:text-slate-300">{ep.desc}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Confirm Regenerate Modal */}
      {confirmRegen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/65 p-4 backdrop-blur-sm">
          <div className="w-full max-w-sm rounded-3xl border border-slate-200 bg-white p-6 dark:border-slate-700 dark:bg-[#102A43]">
            <h3 className="text-base font-extrabold text-slate-900 dark:text-white">
              Regenerate Secret API Key?
            </h3>
            <p className="mt-2 text-xs text-slate-500 dark:text-slate-300">
              Your existing key will stop working immediately. Any server or script using the old
              key will get HTTP 401 until updated.
            </p>
            <div className="mt-5 flex gap-3">
              <button
                type="button"
                onClick={() => setConfirmRegen(false)}
                className="flex-1 rounded-xl border border-slate-200 py-2.5 text-xs font-bold text-slate-600 dark:border-slate-700 dark:text-slate-300"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => handleGenerateKey(true)}
                className="flex-1 rounded-xl bg-[#00875A] py-2.5 text-xs font-bold text-white"
              >
                Regenerate
              </button>
            </div>
          </div>
        </div>
      )}

      {/* New Forum Topic Modal */}
      {newTopicOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/65 p-4 backdrop-blur-sm">
          <form
            onSubmit={submitNewTopic}
            className="w-full max-w-md space-y-4 rounded-3xl border border-slate-200 bg-white p-6 dark:border-slate-700 dark:bg-[#102A43]"
          >
            <h3 className="text-base font-extrabold text-slate-900 dark:text-white">
              Create Forum Topic
            </h3>
            <select
              value={newCategory}
              onChange={(e) => setNewCategory(e.target.value)}
              className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-xs font-bold dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
            >
              <option>API &amp; Authentication</option>
              <option>Airtime &amp; Data API</option>
              <option>Webhooks &amp; Callbacks</option>
              <option>Troubleshooting</option>
            </select>
            <input
              type="text"
              required
              value={newTitle}
              onChange={(e) => setNewTitle(e.target.value)}
              placeholder="Topic title..."
              className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2.5 text-xs font-bold outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
            />
            <textarea
              required
              rows={4}
              value={newContent}
              onChange={(e) => setNewContent(e.target.value)}
              placeholder="Provide details, snippets, or questions..."
              className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2.5 text-xs outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
            />
            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => setNewTopicOpen(false)}
                className="flex-1 rounded-xl border border-slate-200 py-2.5 text-xs font-bold text-slate-600 dark:border-slate-700 dark:text-slate-300"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="flex-1 rounded-xl bg-[#00875A] py-2.5 text-xs font-bold text-white"
              >
                Post Topic
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
