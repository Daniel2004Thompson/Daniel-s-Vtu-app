import React, { useMemo, useState } from 'react';
import { RefreshCw, Search, Trash2, Wallet } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { formatNaira } from '../data/catalog';

const FILTERS = [
  { id: 'ALL', label: 'All' },
  { id: 'AIRTIME', label: 'Airtime' },
  { id: 'DATA', label: 'Data' },
  { id: 'ELECTRICITY', label: 'Electricity' },
  { id: 'CABLE_TV', label: 'Cable TV' },
  { id: 'EDUCATION', label: 'Education' },
  { id: 'WALLET_FUNDING', label: 'Wallet' },
] as const;

export const TransactionsPage: React.FC = () => {
  const {
    transactions,
    isRefreshingWallet,
    refreshUserData,
    setSelectedReceipt,
    deleteTransaction,
    clearAllTransactions,
  } = useApp();
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<(typeof FILTERS)[number]['id']>('ALL');
  const [confirmDeleteRef, setConfirmDeleteRef] = useState<string | null>(null);
  const [confirmClearAll, setConfirmClearAll] = useState(false);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return transactions.filter((tx) => {
      if (filter !== 'ALL' && tx.serviceType !== filter) {
        return false;
      }

      if (!q) return true;
      return (
        tx.title.toLowerCase().includes(q) ||
        tx.provider.toLowerCase().includes(q) ||
        tx.recipient.toLowerCase().includes(q) ||
        tx.reference.toLowerCase().includes(q)
      );
    });
  }, [transactions, filter, search]);

  return (
    <div className="mx-auto max-w-4xl space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold text-slate-900 dark:text-white">
            Transaction History
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-300">
            Click any record to view, copy, share, or print its official receipt
          </p>
        </div>
        <div className="flex items-center gap-2">
          {transactions.length > 0 && (
            <button
              type="button"
              onClick={() => setConfirmClearAll(true)}
              className="inline-flex items-center gap-1.5 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-xs font-bold text-red-600 hover:bg-red-100 dark:border-red-900/50 dark:bg-red-950/40 dark:text-red-300"
            >
              <Trash2 className="h-3.5 w-3.5" />
              Clear All
            </button>
          )}
          <button
            type="button"
            onClick={() => refreshUserData(true)}
            className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-xs font-bold text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:bg-[#102A43] dark:text-slate-200"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${isRefreshingWallet ? 'animate-spin' : ''}`} />
            Sync History
          </button>
        </div>
      </div>

      {/* Search & Filters */}
      <div className="flex flex-col gap-3">
        <div className="relative">
          <Search className="absolute left-3.5 top-3 h-4 w-4 text-slate-400" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by phone, provider, or reference..."
            className="w-full rounded-xl border border-slate-200 bg-white py-2.5 pl-10 pr-4 text-xs font-medium outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-[#102A43] dark:text-white"
          />
        </div>
        <div className="flex flex-wrap gap-1.5">
          {FILTERS.map((f) => (
            <button
              key={f.id}
              type="button"
              onClick={() => setFilter(f.id)}
              className={`rounded-xl px-3 py-2 text-xs font-bold transition ${
                filter === f.id
                  ? 'bg-[#00875A] text-white'
                  : 'border border-slate-200 bg-white text-slate-600 dark:border-slate-700 dark:bg-[#102A43] dark:text-slate-300'
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {filtered.length === 0 ? (
        <div className="rounded-3xl border border-dashed border-slate-300 bg-white p-12 text-center dark:border-slate-700 dark:bg-[#102A43]">
          <Wallet className="mx-auto h-10 w-10 text-slate-400" />
          <p className="mt-3 text-sm font-bold text-slate-700 dark:text-slate-200">
            No matching transactions
          </p>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Your airtime, data, electricity, cable, and deposit history will appear here.
          </p>
        </div>
      ) : (
        <div className="space-y-2.5">
          {filtered.map((tx) => {
            const st = tx.status.toUpperCase();
            const badgeColor =
              st === 'SUCCESSFUL'
                ? 'bg-emerald-100 text-[#00875A] dark:bg-emerald-950 dark:text-emerald-300'
                : st === 'PENDING'
                ? 'bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300'
                : 'bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300';

            return (
              <div
                key={tx.reference}
                className="flex items-center justify-between gap-2 rounded-2xl border border-slate-200 bg-white p-4 transition hover:border-[#00875A]/40 hover:shadow-sm dark:border-slate-700 dark:bg-[#102A43]"
              >
                <button
                  type="button"
                  onClick={() => setSelectedReceipt(tx)}
                  className="flex min-w-0 flex-1 items-center gap-3.5 text-left"
                >
                  <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-[#00875A]/15 text-sm font-extrabold text-[#00875A]">
                    {(tx.provider || tx.title || 'T')[0].toUpperCase()}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-bold text-slate-900 dark:text-white">
                      {tx.title}
                    </p>
                    <p className="truncate text-xs text-slate-500 dark:text-slate-400">
                      {tx.recipient ? `${tx.recipient} • ` : ''}
                      {new Date(tx.timestamp).toLocaleString('en-NG', {
                        dateStyle: 'medium',
                        timeStyle: 'short',
                      })}
                    </p>
                    <p className="mt-0.5 truncate font-mono text-[10px] text-slate-400">
                      {tx.reference}
                    </p>
                  </div>
                </button>
                <div className="flex shrink-0 items-center gap-3">
                  <div className="text-right">
                    <p className="text-sm font-extrabold text-slate-900 dark:text-white">
                      {formatNaira(tx.amount)}
                    </p>
                    <span
                      className={`mt-1 inline-block rounded-full px-2 py-0.5 text-[10px] font-bold ${badgeColor}`}
                    >
                      {tx.status}
                    </span>
                  </div>
                  <button
                    type="button"
                    aria-label="Delete transaction"
                    onClick={() => setConfirmDeleteRef(tx.reference)}
                    className="rounded-lg p-2 text-slate-400 transition hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950/40 dark:hover:text-red-300"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Confirm Single Delete */}
      {confirmDeleteRef && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/65 p-4 backdrop-blur-sm">
          <div className="w-full max-w-sm rounded-3xl border border-slate-200 bg-white p-6 dark:border-[#334E68] dark:bg-[#102A43]">
            <h3 className="text-base font-extrabold text-slate-900 dark:text-white">
              Remove Transaction?
            </h3>
            <p className="mt-2 text-xs text-slate-500 dark:text-slate-300">
              Remove this transaction record from your local history view? Your wallet balance is
              unaffected.
            </p>
            <div className="mt-5 flex gap-3">
              <button
                type="button"
                onClick={() => setConfirmDeleteRef(null)}
                className="flex-1 rounded-xl border border-slate-200 py-2.5 text-xs font-bold dark:border-[#334E68] dark:text-slate-300"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => {
                  deleteTransaction(confirmDeleteRef);
                  setConfirmDeleteRef(null);
                }}
                className="flex-1 rounded-xl bg-red-600 py-2.5 text-xs font-bold text-white"
              >
                Remove
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Confirm Clear All */}
      {confirmClearAll && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/65 p-4 backdrop-blur-sm">
          <div className="w-full max-w-sm rounded-3xl border border-slate-200 bg-white p-6 dark:border-[#334E68] dark:bg-[#102A43]">
            <h3 className="text-base font-extrabold text-red-600 dark:text-red-400">
              Clear All History?
            </h3>
            <p className="mt-2 text-xs text-slate-500 dark:text-slate-300">
              This clears all transaction records shown in your history list.
            </p>
            <div className="mt-5 flex gap-3">
              <button
                type="button"
                onClick={() => setConfirmClearAll(false)}
                className="flex-1 rounded-xl border border-slate-200 py-2.5 text-xs font-bold dark:border-[#334E68] dark:text-slate-300"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => {
                  clearAllTransactions();
                  setConfirmClearAll(false);
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
