import React, { useMemo, useState } from 'react';
import { RefreshCw, Search, Wallet } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { formatNaira } from '../data/catalog';

const FILTERS = [
  { id: 'ALL', label: 'All' },
  { id: 'AIRTIME', label: 'Airtime' },
  { id: 'DATA', label: 'Data' },
  { id: 'BILLS', label: 'Bills & PINs' },
  { id: 'WALLET_FUNDING', label: 'Deposits' },
] as const;

export const TransactionsPage: React.FC = () => {
  const { transactions, isRefreshingWallet, refreshUserData, setSelectedReceipt } = useApp();
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<(typeof FILTERS)[number]['id']>('ALL');

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return transactions.filter((tx) => {
      if (filter === 'BILLS') {
        if (!['ELECTRICITY', 'CABLE_TV', 'EDUCATION'].includes(tx.serviceType)) return false;
      } else if (filter !== 'ALL' && tx.serviceType !== filter) {
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
        <button
          type="button"
          onClick={() => refreshUserData(true)}
          className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-xs font-bold text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:bg-[#102A43] dark:text-slate-200"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${isRefreshingWallet ? 'animate-spin' : ''}`} />
          Sync History
        </button>
      </div>

      {/* Search & Filters */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative flex-1">
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
              <button
                key={tx.reference}
                type="button"
                onClick={() => setSelectedReceipt(tx)}
                className="flex w-full items-center justify-between rounded-2xl border border-slate-200 bg-white p-4 text-left transition hover:border-[#00875A]/40 hover:shadow-sm dark:border-slate-700 dark:bg-[#102A43]"
              >
                <div className="flex items-center gap-3.5">
                  <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-[#00875A]/15 text-sm font-extrabold text-[#00875A]">
                    {(tx.provider || tx.title || 'T')[0].toUpperCase()}
                  </div>
                  <div>
                    <p className="text-sm font-bold text-slate-900 dark:text-white">{tx.title}</p>
                    <p className="text-xs text-slate-500 dark:text-slate-400">
                      {tx.recipient ? `${tx.recipient} • ` : ''}
                      {new Date(tx.timestamp).toLocaleString('en-NG', {
                        dateStyle: 'medium',
                        timeStyle: 'short',
                      })}
                    </p>
                    <p className="mt-0.5 font-mono text-[10px] text-slate-400">{tx.reference}</p>
                  </div>
                </div>
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
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
};
