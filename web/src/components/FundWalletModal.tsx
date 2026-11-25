import React, { useState } from 'react';
import {
  ArrowRight,
  Building2,
  CheckCircle2,
  Copy,
  ShieldCheck,
  X,
  Zap,
} from 'lucide-react';
import { useApp } from '../context/AppContext';
import { formatCompactNaira } from '../data/catalog';
import {
  createDynamicAccount,
  createOrFetchPermanentAccount,
  resolveAccountHolderName,
  sanitizeFullName,
} from '../services/walletService';
import { DynamicAccountInfo, FundTab } from '../types/vtu';

export const FundWalletModal: React.FC = () => {
  const {
    showFundModal,
    fundInitialTab,
    closeFundModal,
    setCurrentPage,
    profile,
    refreshUserData,
    updatePermanentAccountLocal,
    updateDynamicAccountLocal,
    showToast,
  } = useApp();

  const [activeTab, setActiveTab] = useState<FundTab>(fundInitialTab);
  const [dynAmount, setDynAmount] = useState('1000');
  const [dynLoading, setDynLoading] = useState(false);
  const [dynAccount, setDynAccount] = useState<DynamicAccountInfo | null>(null);

  const [ninInput, setNinInput] = useState('');
  const [permLoading, setPermLoading] = useState(false);
  const [permError, setPermError] = useState<string | null>(null);

  React.useEffect(() => {
    if (showFundModal) {
      setActiveTab(fundInitialTab);
      setPermError(null);
    }
  }, [showFundModal, fundInitialTab]);

  if (!showFundModal || !profile) return null;

  const displayFullName =
    sanitizeFullName(profile.fullName, profile.email) || profile.fullName || 'Daniel VTU User';
  const permHolderName = profile.virtualAccountNumber
    ? resolveAccountHolderName(profile.virtualAccountName, profile.fullName, profile.email) ||
      displayFullName
    : '';

  const copyValue = async (val: string, label: string) => {
    try {
      await navigator.clipboard.writeText(val);
      showToast(`${label} copied!`, 'success');
    } catch {
      showToast(`Failed to copy ${label}`, 'error');
    }
  };

  const handleGenerateDynamic = async (e: React.FormEvent) => {
    e.preventDefault();
    const num = Number(dynAmount);
    if (!Number.isFinite(num) || num < 100) {
      showToast('Enter at least ₦100 for dynamic bank transfer.', 'error');
      return;
    }
    setDynLoading(true);
    try {
      const res = await createDynamicAccount({
        userId: profile.id,
        email: profile.email,
        amount: num,
        fullName: displayFullName,
        phone: profile.phone,
      });
      if (!res.ok) {
        showToast(res.error, 'error');
        return;
      }
      setDynAccount(res.account);
      updateDynamicAccountLocal(res.account);
      showToast('Dynamic transfer account generated!', 'success');
      await refreshUserData(false);
    } finally {
      setDynLoading(false);
    }
  };

  const handleCreatePermanent = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanNin = ninInput.replace(/\D/g, '').slice(0, 11);
    if (cleanNin.length !== 11) {
      setPermError('Please enter a valid 11-digit NIN.');
      return;
    }
    setPermLoading(true);
    setPermError(null);
    try {
      const res = await createOrFetchPermanentAccount({
        userId: profile.id,
        email: profile.email,
        fullName: displayFullName,
        phone: profile.phone,
        nin: cleanNin,
      });
      if (res.success && res.accountNumber) {
        updatePermanentAccountLocal(
          res.accountNumber,
          res.bankName || 'Flutterwave MFB',
          res.accountName || displayFullName
        );
        setNinInput('');
        showToast(`Permanent Account Created: ${res.accountNumber}`, 'success');
        await refreshUserData(false);
      } else {
        setPermError(res.message);
        showToast(res.message, 'error');
      }
    } finally {
      setPermLoading(false);
    }
  };

  const quickAmounts = [500, 1000, 2000, 5000, 10000];
  const hasPermVa = Boolean(profile.virtualAccountNumber);
  const shownDynNumber = dynAccount?.accountNumber || profile.dynamicAccountNumber;
  const shownDynBank = dynAccount?.bankName || profile.dynamicBankName || 'Wema / Partner Bank';
  const shownDynAmount = dynAccount?.amount || profile.dynamicAccountAmount;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/65 p-4 backdrop-blur-sm">
      <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl dark:border-[#334E68] dark:bg-[#102A43]">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-lg font-extrabold text-slate-900 dark:text-white">
              Fund Your Wallet
            </h3>
            <p className="text-xs text-slate-500 dark:text-slate-300">
              Instant deposits via Dynamic or Permanent Bank Transfer
            </p>
          </div>
          <button
            type="button"
            onClick={closeFundModal}
            aria-label="Close modal"
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* 2 Funding Tabs matching Android FundWalletSheet.kt */}
        <div className="mt-4 grid grid-cols-2 gap-2 rounded-2xl bg-slate-100 p-1 dark:bg-[#0A192F]/80">
          <button
            type="button"
            onClick={() => setActiveTab('dynamic')}
            className={`flex items-center justify-center gap-1.5 rounded-xl py-2.5 text-xs font-bold transition ${
              activeTab === 'dynamic'
                ? 'bg-white text-[#00875A] shadow-sm dark:bg-[#1B3A5A] dark:text-[#00B074]'
                : 'text-slate-600 dark:text-slate-300'
            }`}
          >
            <Zap className="h-3.5 w-3.5" /> Dynamic Account
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('permanent')}
            className={`flex items-center justify-center gap-1.5 rounded-xl py-2.5 text-xs font-bold transition ${
              activeTab === 'permanent'
                ? 'bg-white text-[#00875A] shadow-sm dark:bg-[#1B3A5A] dark:text-[#00B074]'
                : 'text-slate-600 dark:text-slate-300'
            }`}
          >
            <Building2 className="h-3.5 w-3.5" /> Permanent Account
          </button>
        </div>

        {/* Tab 1: Dynamic Virtual Account */}
        {activeTab === 'dynamic' && (
          <div className="mt-5 space-y-4">
            <form onSubmit={handleGenerateDynamic} className="space-y-4">
              <div>
                <p className="mb-2 text-xs font-semibold text-slate-600 dark:text-slate-300">
                  Quick Amount
                </p>
                <div className="flex flex-wrap gap-2">
                  {quickAmounts.map((amt) => (
                    <button
                      key={amt}
                      type="button"
                      onClick={() => setDynAmount(String(amt))}
                      className={`rounded-xl border px-3 py-1.5 text-xs font-bold transition ${
                        dynAmount === String(amt)
                          ? 'border-[#00875A] bg-[#E8F8F2] text-[#00875A] dark:bg-emerald-950/60 dark:text-emerald-300'
                          : 'border-slate-200 text-slate-600 hover:bg-slate-50 dark:border-[#334E68] dark:text-slate-300'
                      }`}
                    >
                      ₦{amt.toLocaleString('en-US')}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label className="mb-1 block text-xs font-semibold text-slate-600 dark:text-slate-300">
                  Amount to Deposit (₦)
                </label>
                <div className="flex gap-2">
                  <input
                    type="number"
                    min={100}
                    required
                    value={dynAmount}
                    onChange={(e) => setDynAmount(e.target.value)}
                    placeholder="Enter amount (e.g. 2000)"
                    className="flex-1 rounded-xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm font-bold text-slate-900 outline-none focus:border-[#00875A] dark:border-[#334E68] dark:bg-[#0A192F]/80 dark:text-white"
                  />
                  <button
                    type="submit"
                    disabled={dynLoading}
                    className="rounded-xl bg-[#00875A] px-4 py-2.5 text-xs font-bold text-white hover:bg-[#005A3C] disabled:opacity-50"
                  >
                    {dynLoading ? 'Creating...' : 'Create Account'}
                  </button>
                </div>
              </div>
            </form>

            {shownDynNumber && (
              <div className="space-y-2.5 rounded-2xl border border-[#00B875]/40 bg-[#E8F8F2] p-4 dark:bg-emerald-950/40">
                <div className="flex items-center justify-between">
                  <span className="text-[11px] font-extrabold uppercase tracking-wider text-[#00875A] dark:text-[#00B074]">
                    Dynamic Dedicated Account
                  </span>
                  <span className="rounded-full bg-[#00B875] px-2 py-0.5 text-[10px] font-bold text-white">
                    AUTO SYNC
                  </span>
                </div>
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-mono text-2xl font-extrabold text-slate-900 dark:text-white">
                      {shownDynNumber}
                    </p>
                    <p className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                      {shownDynBank} • Wallet Topup
                    </p>
                    {shownDynAmount ? (
                      <p className="mt-0.5 text-xs font-bold text-[#00875A] dark:text-[#00B074]">
                        Target Transfer: {formatCompactNaira(shownDynAmount)}
                      </p>
                    ) : null}
                  </div>
                  <button
                    type="button"
                    onClick={() => copyValue(shownDynNumber, 'Account Number')}
                    className="rounded-xl bg-white p-2.5 text-[#00875A] shadow-sm hover:bg-slate-50 dark:bg-[#1B3A5A] dark:text-[#00B074]"
                  >
                    <Copy className="h-4 w-4" />
                  </button>
                </div>
              </div>
            )}

            <button
              type="button"
              onClick={() => {
                closeFundModal();
                setCurrentPage('dynamic');
              }}
              className="flex w-full items-center justify-center gap-1.5 rounded-xl border border-[#00875A]/40 py-2.5 text-xs font-bold text-[#00875A] hover:bg-[#E8F8F2] dark:text-[#00B074] dark:hover:bg-emerald-950/40"
            >
              Open Full Dynamic Account Page <ArrowRight className="h-3.5 w-3.5" />
            </button>
          </div>
        )}

        {/* Tab 2: Permanent Dedicated Virtual Account */}
        {activeTab === 'permanent' && (
          <div className="mt-5 space-y-4">
            {hasPermVa ? (
              <div className="space-y-3 rounded-2xl border border-emerald-500/35 bg-[#E8F8F2] p-4 dark:bg-emerald-950/40">
                <div className="flex items-center gap-2 text-xs font-bold text-[#00875A] dark:text-[#00B074]">
                  <CheckCircle2 className="h-4 w-4" /> Dedicated Permanent Account Active
                </div>
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-mono text-2xl font-extrabold text-slate-900 dark:text-white">
                      {profile.virtualAccountNumber}
                    </p>
                    <p className="text-xs font-semibold text-[#FB9129]">
                      {profile.virtualBankName || 'Flutterwave MFB'}
                    </p>
                    <p className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                      {permHolderName}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() =>
                      copyValue(profile.virtualAccountNumber!, 'Permanent Account')
                    }
                    className="rounded-xl bg-white p-2.5 text-[#00875A] shadow-sm hover:bg-slate-50 dark:bg-[#1B3A5A] dark:text-[#00B074]"
                  >
                    <Copy className="h-4 w-4" />
                  </button>
                </div>
                <p className="text-[11px] text-slate-600 dark:text-slate-300">
                  Any bank transfer sent to this account credits your Daniel VTU wallet
                  automatically in real time.
                </p>
              </div>
            ) : (
              <form onSubmit={handleCreatePermanent} className="space-y-3.5">
                <div className="rounded-2xl border border-amber-400/40 bg-amber-50 p-3.5 text-xs text-amber-900 dark:bg-amber-950/30 dark:text-amber-200">
                  <div className="flex items-center gap-1.5 font-bold">
                    <ShieldCheck className="h-4 w-4" /> NIN Verification Required
                  </div>
                  <p className="mt-1 opacity-90">
                    Verify your 11-digit National Identification Number (NIN) to generate a
                    permanent dedicated Nigerian bank account for unlimited wallet transfers.
                  </p>
                </div>
                <div>
                  <label className="mb-1 block text-xs font-semibold text-slate-600 dark:text-slate-300">
                    11-Digit NIN
                  </label>
                  <input
                    type="text"
                    inputMode="numeric"
                    maxLength={11}
                    value={ninInput}
                    onChange={(e) => {
                      setNinInput(e.target.value.replace(/\D/g, '').slice(0, 11));
                      setPermError(null);
                    }}
                    placeholder="Enter 11-digit NIN"
                    className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 font-mono text-sm font-bold outline-none focus:border-[#00875A] dark:border-[#334E68] dark:bg-[#0A192F]/80 dark:text-white"
                  />
                </div>
                {permError && (
                  <p className="rounded-xl bg-red-50 px-3 py-2 text-xs font-medium text-red-600 dark:bg-red-950/40 dark:text-red-300">
                    {permError}
                  </p>
                )}
                <button
                  type="submit"
                  disabled={permLoading || ninInput.length !== 11}
                  className="w-full rounded-xl bg-[#00875A] py-3 text-sm font-bold text-white hover:bg-[#005A3C] disabled:opacity-50"
                >
                  {permLoading ? 'Verifying NIN...' : 'Verify NIN & Create Permanent Account'}
                </button>
              </form>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
