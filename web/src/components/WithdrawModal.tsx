import React, { useState } from 'react';
import { ArrowDownToLine, X } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { formatNaira } from '../data/catalog';
import { debitWallet } from '../services/walletService';

export const WithdrawModal: React.FC = () => {
  const {
    showWithdrawModal,
    closeWithdrawModal,
    profile,
    requestPinAuthorization,
    refreshUserData,
    showToast,
  } = useApp();
  const [amount, setAmount] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (!showWithdrawModal || !profile) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const num = Number(amount);
    if (!Number.isFinite(num) || num < 100) {
      showToast('Minimum debit amount is ₦100.', 'error');
      return;
    }
    if (num > profile.walletBalance) {
      showToast('Insufficient wallet balance.', 'error');
      return;
    }

    closeWithdrawModal();
    requestPinAuthorization(
      'Authorize Wallet Debit',
      `Amount: ${formatNaira(num)}`,
      async () => {
        setSubmitting(true);
        try {
          const res = await debitWallet(num);
          if (!res.ok) {
            showToast(res.error, 'error');
            return;
          }
          showToast(`Wallet debited ${formatNaira(num)} successfully.`, 'success');
          setAmount('');
          await refreshUserData(false);
        } finally {
          setSubmitting(false);
        }
      }
    );
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/65 p-4 backdrop-blur-sm">
      <div className="w-full max-w-md rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl dark:border-slate-700 dark:bg-[#102A43]">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-[#E8F8F2] text-[#00875A] dark:bg-emerald-950">
              <ArrowDownToLine className="h-5 w-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-slate-900 dark:text-white">
                Debit / Withdraw Wallet
              </h3>
              <p className="text-xs text-slate-500 dark:text-slate-300">
                Available: {formatNaira(profile.walletBalance)}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={closeWithdrawModal}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="mt-5 space-y-4">
          <div>
            <label className="mb-1 block text-xs font-semibold text-slate-600 dark:text-slate-300">
              Amount to Withdraw (₦)
            </label>
            <input
              type="number"
              min={100}
              max={profile.walletBalance}
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="e.g. 1000"
              className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-base font-bold outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
            />
          </div>

          <button
            type="submit"
            disabled={submitting || !amount}
            className="w-full rounded-xl bg-[#00875A] py-3 text-sm font-bold text-white shadow-md shadow-emerald-900/15 hover:bg-[#005A3C] disabled:opacity-50"
          >
            {submitting ? 'Processing...' : 'Continue with Transaction PIN'}
          </button>
        </form>
      </div>
    </div>
  );
};
