import React, { useState } from 'react';
import {
  ArrowLeft,
  Bell,
  Bolt,
  CheckCheck,
  Copy,
  Smartphone,
  Trash2,
  Wallet,
  Wifi,
} from 'lucide-react';
import { useApp } from '../context/AppContext';
import { formatNaira } from '../data/catalog';

export const NotificationsPage: React.FC = () => {
  const {
    notifications,
    unreadNotificationCount,
    setCurrentPage,
    markNotificationRead,
    markAllNotificationsRead,
    clearAllNotifications,
    showToast,
  } = useApp();

  const [confirmClear, setConfirmClear] = useState(false);

  const copyToken = async (token: string) => {
    try {
      await navigator.clipboard.writeText(token);
      showToast('Token / PIN copied!', 'success');
    } catch {
      showToast('Failed to copy token', 'error');
    }
  };

  const renderIcon = (serviceType: string) => {
    const s = serviceType.toUpperCase();
    if (s.includes('AIRTIME')) return <Smartphone className="h-4 w-4" />;
    if (s.includes('DATA')) return <Wifi className="h-4 w-4" />;
    if (s.includes('WALLET') || s.includes('CREDIT')) return <Wallet className="h-4 w-4" />;
    if (s.includes('ELECTRIC') || s.includes('CABLE') || s.includes('EDU')) {
      return <Bolt className="h-4 w-4" />;
    }
    return <Bell className="h-4 w-4" />;
  };

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => setCurrentPage('home')}
            className="rounded-xl border border-slate-200 bg-white p-2 text-slate-600 hover:bg-slate-50 dark:border-[#334E68] dark:bg-[#102A43] dark:text-slate-200"
            aria-label="Back to Home"
          >
            <ArrowLeft className="h-4 w-4" />
          </button>
          <div>
            <h1 className="text-2xl font-extrabold text-slate-900 dark:text-white">
              Transaction Alerts
            </h1>
            <p className="text-xs text-slate-500 dark:text-slate-300">
              {unreadNotificationCount > 0
                ? `${unreadNotificationCount} unread notification${unreadNotificationCount > 1 ? 's' : ''}`
                : 'All caught up with real-time alerts'}
            </p>
          </div>
        </div>

        {notifications.length > 0 && (
          <div className="flex items-center gap-2">
            {unreadNotificationCount > 0 && (
              <button
                type="button"
                onClick={markAllNotificationsRead}
                className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-bold text-[#00875A] hover:bg-slate-50 dark:border-[#334E68] dark:bg-[#102A43] dark:text-[#00B074]"
              >
                <CheckCheck className="h-3.5 w-3.5" /> Mark All Read
              </button>
            )}
            <button
              type="button"
              onClick={() => setConfirmClear(true)}
              className="inline-flex items-center gap-1.5 rounded-xl border border-red-200 bg-white px-3 py-2 text-xs font-bold text-red-600 hover:bg-red-50 dark:border-red-900/50 dark:bg-[#102A43] dark:text-red-400"
            >
              <Trash2 className="h-3.5 w-3.5" /> Clear All
            </button>
          </div>
        )}
      </div>

      {notifications.length === 0 ? (
        <div className="rounded-3xl border border-dashed border-slate-300 bg-white p-12 text-center dark:border-[#334E68] dark:bg-[#102A43]">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-slate-100 text-slate-400 dark:bg-[#1B3A5A]">
            <Bell className="h-7 w-7" />
          </div>
          <p className="mt-3 text-sm font-bold text-slate-800 dark:text-white">
            No alerts yet
          </p>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Wallet deposits, airtime/data purchases, and electricity tokens will notify you here in
            real time.
          </p>
        </div>
      ) : (
        <div className="space-y-2.5">
          {notifications.map((n) => (
            <div
              key={n.id}
              onClick={() => !n.isRead && markNotificationRead(n.id)}
              className={`cursor-pointer rounded-2xl border p-4 transition ${
                n.isRead
                  ? 'border-slate-200 bg-white dark:border-[#334E68]/70 dark:bg-[#102A43]'
                  : 'border-[#00875A]/45 bg-[#E8F8F2]/55 dark:border-[#00B074]/40 dark:bg-emerald-950/35'
              }`}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-start gap-3">
                  <div
                    className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full ${
                      n.isRead
                        ? 'bg-slate-100 text-slate-600 dark:bg-[#1B3A5A] dark:text-slate-300'
                        : 'bg-[#00875A] text-white'
                    }`}
                  >
                    {renderIcon(n.serviceType)}
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <p className="text-sm font-bold text-slate-900 dark:text-white">{n.title}</p>
                      {!n.isRead && <span className="h-2 w-2 rounded-full bg-[#00B074]" />}
                    </div>
                    <p className="mt-0.5 text-xs text-slate-600 dark:text-slate-300">{n.message}</p>
                    {n.token && (
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          copyToken(n.token!);
                        }}
                        className="mt-2 inline-flex items-center gap-1.5 rounded-lg bg-[#00875A]/15 px-2.5 py-1 font-mono text-[11px] font-bold text-[#00875A] hover:bg-[#00875A]/25 dark:text-[#00B074]"
                      >
                        Token: {n.token} <Copy className="h-3 w-3" />
                      </button>
                    )}
                    <p className="mt-1.5 text-[11px] text-slate-400">
                      {new Date(n.timestamp).toLocaleString('en-NG', {
                        dateStyle: 'medium',
                        timeStyle: 'short',
                      })}
                    </p>
                  </div>
                </div>
                {n.amount > 0 && (
                  <span className="shrink-0 text-sm font-extrabold text-[#00875A] dark:text-[#00B074]">
                    {formatNaira(n.amount)}
                  </span>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {confirmClear && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/65 p-4 backdrop-blur-sm">
          <div className="w-full max-w-sm rounded-3xl border border-slate-200 bg-white p-6 dark:border-[#334E68] dark:bg-[#102A43]">
            <h3 className="text-base font-extrabold text-slate-900 dark:text-white">
              Clear All Notifications?
            </h3>
            <p className="mt-2 text-xs text-slate-500 dark:text-slate-300">
              All notification alerts on this device will be permanently removed.
            </p>
            <div className="mt-5 flex gap-3">
              <button
                type="button"
                onClick={() => setConfirmClear(false)}
                className="flex-1 rounded-xl border border-slate-200 py-2.5 text-xs font-bold dark:border-[#334E68] dark:text-slate-300"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => {
                  clearAllNotifications();
                  setConfirmClear(false);
                }}
                className="flex-1 rounded-xl bg-red-600 py-2.5 text-xs font-bold text-white"
              >
                Clear All
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
