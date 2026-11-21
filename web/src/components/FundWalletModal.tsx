import React, { useState } from 'react';
import {
  Building2,
  CheckCircle2,
  Copy,
  CreditCard,
  ShieldCheck,
  X,
  Zap,
} from 'lucide-react';
import { useApp } from '../context/AppContext';
import { formatNaira } from '../data/catalog';
import {
  createDynamicAccount,
  createOrFetchPermanentAccount,
  initializeFlutterwavePayment,
  verifyFlutterwavePayment,
} from '../services/walletService';
import { DynamicAccountInfo } from '../types/vtu';

export const FundWalletModal: React.FC = () => {
  const {
    showFundModal,
    fundInitialTab,
    closeFundModal,
    profile,
    refreshUserData,
    showToast,
  } = useApp();

  const [activeTab, setActiveTab] = useState<'card' | 'dynamic' | 'permanent'>(fundInitialTab);
  const [cardAmount, setCardAmount] = useState('2000');
  const [loadingCard, setLoadingCard] = useState(false);
  const [pendingTxRef, setPendingTxRef] = useState<string | null>(() =>
    localStorage.getItem('daniel_vtu_pending_flw_ref')
  );

  const [dynAmount, setDynAmount] = useState('1000');
  const [dynLoading, setDynLoading] = useState(false);
  const [dynAccount, setDynAccount] = useState<DynamicAccountInfo | null>(null);

  const [ninInput, setNinInput] = useState('');
  const [permLoading, setPermLoading] = useState(false);

  React.useEffect(() => {
    if (showFundModal) setActiveTab(fundInitialTab);
  }, [showFundModal, fundInitialTab]);

  if (!showFundModal || !profile) return null;

  const copyValue = async (val: string, label: string) => {
    try {
      await navigator.clipboard.writeText(val);
      showToast(`${label} copied!`, 'success');
    } catch {
      showToast(`Failed to copy ${label}`, 'error');
    }
  };

  const handleCardCheckout = async (e: React.FormEvent) => {
    e.preventDefault();
    const num = Number(cardAmount);
    if (!Number.isFinite(num) || num < 100) {
      showToast('Minimum deposit amount is ₦100.', 'error');
      return;
    }
    setLoadingCard(true);
    try {
      const res = await initializeFlutterwavePayment({
        amount: num,
        email: profile.email,
        name: profile.fullName,
        phone: profile.phone,
      });
      if (!res.ok) {
        showToast(res.error, 'error');
        return;
      }
      localStorage.setItem('daniel_vtu_pending_flw_ref', res.txRef);
      setPendingTxRef(res.txRef);
      window.open(res.paymentUrl, '_blank', 'noopener,noreferrer');
      showToast('Flutterwave checkout opened. Click Verify after paying.', 'info');
    } finally {
      setLoadingCard(false);
    }
  };

  const handleVerifyPending = async () => {
    if (!pendingTxRef) return;
    setLoadingCard(true);
    try {
      const res = await verifyFlutterwavePayment(pendingTxRef);
      if (res.ok) {
        localStorage.removeItem('daniel_vtu_pending_flw_ref');
        setPendingTxRef(null);
        showToast(`Payment verified! Wallet credited ${formatNaira(res.amount)}`, 'success');
        await refreshUserData(false);
      } else {
        showToast(res.message || 'Payment not yet confirmed.', 'info');
      }
    } finally {
      setLoadingCard(false);
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
        fullName: profile.fullName,
      });
      if (!res.ok) {
        showToast(res.error, 'error');
        return;
      }
      setDynAccount(res.account);
      showToast('Dynamic transfer account generated!', 'success');
      await refreshUserData(false);
    } finally {
      setDynLoading(false);
    }
  };

  const handleCreatePermanent = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanNin = ninInput.replace(/\D/g, '');
    if (cleanNin.length !== 11) {
      showToast('Please enter a valid 11-digit NIN.', 'error');
      return;
    }
    setPermLoading(true);
    try {
      const res = await createOrFetchPermanentAccount({
        userId: profile.id,
        email: profile.email,
        fullName: profile.fullName,
        phone: profile.phone,
        nin: cleanNin,
      });
      if (res.success && res.accountNumber) {
        showToast('Permanent virtual account activated!', 'success');
        await refreshUserData(false);
      } else {
        showToast(res.message, 'error');
      }
    } finally {
      setPermLoading(false);
    }
  };

  const quickAmounts = [500, 1000, 2000, 5000, 10000];
  const hasPermVa = Boolean(profile.virtualAccountNumber);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/65 p-4 backdrop-blur-sm">
      <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl dark:border-slate-700 dark:bg-[#102A43]">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-lg font-extrabold text-slate-900 dark:text-white">
              Fund Your Wallet
            </h3>
            <p className="text-xs text-slate-500 dark:text-slate-300">
              Instant deposits via Flutterwave checkout or bank transfer
            </p>
          </div>
          <button
            type="button"
            onClick={closeFundModal}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Method Tabs */}
        <div className="mt-4 grid grid-cols-3 gap-2 rounded-2xl bg-slate-100 p-1 dark:bg-slate-900/70">
          <button
            type="button"
            onClick={() => setActiveTab('card')}
            className={`flex items-center justify-center gap-1.5 rounded-xl py-2 text-xs font-bold transition ${
              activeTab === 'card'
                ? 'bg-white text-[#00875A] shadow-sm dark:bg-[#1B3A5A] dark:text-emerald-300'
                : 'text-slate-600 dark:text-slate-300'
            }`}
          >
            <CreditCard className="h-3.5 w-3.5" /> Card / Pay
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('dynamic')}
            className={`flex items-center justify-center gap-1.5 rounded-xl py-2 text-xs font-bold transition ${
              activeTab === 'dynamic'
                ? 'bg-white text-[#00875A] shadow-sm dark:bg-[#1B3A5A] dark:text-emerald-300'
                : 'text-slate-600 dark:text-slate-300'
            }`}
          >
            <Zap className="h-3.5 w-3.5" /> Instant Bank
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('permanent')}
            className={`flex items-center justify-center gap-1.5 rounded-xl py-2 text-xs font-bold transition ${
              activeTab === 'permanent'
                ? 'bg-white text-[#00875A] shadow-sm dark:bg-[#1B3A5A] dark:text-emerald-300'
                : 'text-slate-600 dark:text-slate-300'
            }`}
          >
            <Building2 className="h-3.5 w-3.5" /> Permanent
          </button>
        </div>

        {/* Tab 1: Flutterwave Card/Checkout */}
        {activeTab === 'card' && (
          <form onSubmit={handleCardCheckout} className="mt-5 space-y-4">
            <div className="flex flex-wrap gap-2">
              {quickAmounts.map((amt) => (
                <button
                  key={amt}
                  type="button"
                  onClick={() => setCardAmount(String(amt))}
                  className={`rounded-xl border px-3 py-1.5 text-xs font-bold transition ${
                    cardAmount === String(amt)
                      ? 'border-[#00875A] bg-[#E8F8F2] text-[#00875A] dark:bg-emerald-950/60 dark:text-emerald-300'
                      : 'border-slate-200 text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300'
                  }`}
                >
                  ₦{amt.toLocaleString('en-US')}
                </button>
              ))}
            </div>

            <div>
              <label className="mb-1 block text-xs font-semibold text-slate-600 dark:text-slate-300">
                Deposit Amount (₦)
              </label>
              <input
                type="number"
                min={100}
                value={cardAmount}
                onChange={(e) => setCardAmount(e.target.value)}
                className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-base font-bold text-slate-900 outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
              />
            </div>

            <button
              type="submit"
              disabled={loadingCard}
              className="w-full rounded-xl bg-[#00875A] py-3 text-sm font-bold text-white shadow-md shadow-emerald-900/15 transition hover:bg-[#005A3C] disabled:opacity-50"
            >
              {loadingCard ? 'Connecting to Flutterwave...' : 'Pay via Flutterwave Checkout'}
            </button>

            {pendingTxRef && (
              <div className="rounded-2xl border border-amber-400/40 bg-amber-50 p-3.5 dark:bg-amber-950/30">
                <p className="text-xs font-semibold text-amber-800 dark:text-amber-200">
                  Pending payment ref: <span className="font-mono">{pendingTxRef}</span>
                </p>
                <button
                  type="button"
                  onClick={handleVerifyPending}
                  disabled={loadingCard}
                  className="mt-2 rounded-lg bg-amber-600 px-3 py-1.5 text-xs font-bold text-white hover:bg-amber-700"
                >
                  Verify Completed Payment
                </button>
              </div>
            )}
          </form>
        )}

        {/* Tab 2: Dynamic Virtual Account */}
        {activeTab === 'dynamic' && (
          <div className="mt-5 space-y-4">
            <form onSubmit={handleGenerateDynamic} className="space-y-3">
              <div>
                <label className="mb-1 block text-xs font-semibold text-slate-600 dark:text-slate-300">
                  Transfer Amount (₦)
                </label>
                <div className="flex gap-2">
                  <input
                    type="number"
                    min={100}
                    value={dynAmount}
                    onChange={(e) => setDynAmount(e.target.value)}
                    className="flex-1 rounded-xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm font-bold text-slate-900 outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
                  />
                  <button
                    type="submit"
                    disabled={dynLoading}
                    className="rounded-xl bg-[#00875A] px-4 py-2.5 text-xs font-bold text-white hover:bg-[#005A3C] disabled:opacity-50"
                  >
                    {dynLoading ? 'Creating...' : 'Get Account'}
                  </button>
                </div>
              </div>
            </form>

            {(dynAccount || profile.dynamicAccountNumber) && (
              <div className="space-y-2.5 rounded-2xl border border-emerald-500/30 bg-[#E8F8F2] p-4 dark:bg-emerald-950/40">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold uppercase tracking-wider text-[#00875A]">
                    One-Time / Dynamic Transfer Account
                  </span>
                  <span className="rounded-full bg-[#00875A] px-2 py-0.5 text-[10px] font-bold text-white">
                    AUTO SYNC
                  </span>
                </div>
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-mono text-2xl font-extrabold text-slate-900 dark:text-white">
                      {dynAccount?.accountNumber || profile.dynamicAccountNumber}
                    </p>
                    <p className="text-xs font-semibold text-slate-600 dark:text-slate-300">
                      {dynAccount?.bankName || profile.dynamicBankName || 'Partner Bank'} •{' '}
                      {dynAccount?.accountName || 'Wallet Topup'}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() =>
                      copyValue(
                        dynAccount?.accountNumber || profile.dynamicAccountNumber || '',
                        'Account Number'
                      )
                    }
                    className="rounded-xl bg-white p-2.5 text-[#00875A] shadow-sm hover:bg-slate-50 dark:bg-slate-800"
                  >
                    <Copy className="h-4 w-4" />
                  </button>
                </div>
                {dynAccount?.note && (
                  <p className="text-[11px] text-slate-600 dark:text-slate-300">
                    {dynAccount.note}
                  </p>
                )}
              </div>
            )}
          </div>
        )}

        {/* Tab 3: Permanent Dedicated Virtual Account */}
        {activeTab === 'permanent' && (
          <div className="mt-5 space-y-4">
            {hasPermVa ? (
              <div className="space-y-3 rounded-2xl border border-emerald-500/30 bg-[#E8F8F2] p-4 dark:bg-emerald-950/40">
                <div className="flex items-center gap-2 text-xs font-bold text-[#00875A]">
                  <CheckCircle2 className="h-4 w-4" /> Dedicated Permanent Account Active
                </div>
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-mono text-2xl font-extrabold text-slate-900 dark:text-white">
                      {profile.virtualAccountNumber}
                    </p>
                    <p className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                      {profile.virtualBankName || 'Flutterwave MFB'}
                    </p>
                    <p className="text-xs text-slate-500 dark:text-slate-400">
                      {profile.virtualAccountName || profile.fullName}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() =>
                      copyValue(profile.virtualAccountNumber!, 'Permanent Account')
                    }
                    className="rounded-xl bg-white p-2.5 text-[#00875A] shadow-sm hover:bg-slate-50 dark:bg-slate-800"
                  >
                    <Copy className="h-4 w-4" />
                  </button>
                </div>
              </div>
            ) : (
              <form onSubmit={handleCreatePermanent} className="space-y-3">
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
                    onChange={(e) => setNinInput(e.target.value.replace(/\D/g, '').slice(0, 11))}
                    placeholder="12345678901"
                    className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 font-mono text-sm font-bold outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
                  />
                </div>
                <button
                  type="submit"
                  disabled={permLoading || ninInput.length !== 11}
                  className="w-full rounded-xl bg-[#00875A] py-3 text-sm font-bold text-white hover:bg-[#005A3C] disabled:opacity-50"
                >
                  {permLoading ? 'Verifying NIN...' : 'Activate Permanent Account'}
                </button>
              </form>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
