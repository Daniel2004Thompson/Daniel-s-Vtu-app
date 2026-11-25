import React, { useState } from 'react';
import {
  ArrowLeft,
  CheckCircle2,
  Copy,
  Info,
  Landmark,
  Radio,
  RefreshCw,
  ShieldCheck,
  UserCheck,
  Zap,
} from 'lucide-react';
import { useApp } from '../context/AppContext';
import { formatCompactNaira, formatNaira } from '../data/catalog';
import { createDynamicAccount, sanitizeFullName } from '../services/walletService';
import { DynamicAccountInfo } from '../types/vtu';

const QUICK_AMOUNTS = [500, 1000, 2000, 5000];

export const DynamicAccountPage: React.FC = () => {
  const {
    profile,
    setCurrentPage,
    refreshUserData,
    updateDynamicAccountLocal,
    isRefreshingWallet,
    showToast,
  } = useApp();

  const [amountInput, setAmountInput] = useState(
    profile?.dynamicAccountAmount ? String(profile.dynamicAccountAmount) : '1000'
  );
  const [generating, setGenerating] = useState(false);
  const [activeDynamic, setActiveDynamic] = useState<DynamicAccountInfo | null>(() =>
    profile?.dynamicAccountNumber
      ? {
          accountNumber: profile.dynamicAccountNumber,
          bankName: profile.dynamicBankName || 'Wema / Partner Bank',
          accountName: 'Wallet Topup',
          amount: profile.dynamicAccountAmount || 1000,
          note: profile.dynamicAccountNote,
          expiresAt: profile.dynamicAccountExpiresAt,
        }
      : null
  );

  if (!profile) return null;

  const displayFullName =
    sanitizeFullName(profile.fullName, profile.email) || profile.fullName || 'Daniel VTU User';
  const shownAccountNumber = activeDynamic?.accountNumber || profile.dynamicAccountNumber;
  const shownBankName = activeDynamic?.bankName || profile.dynamicBankName || 'Wema / Partner Bank';
  const shownAmount = activeDynamic?.amount || profile.dynamicAccountAmount || Number(amountInput) || 0;

  const copyText = async (val: string, label: string) => {
    try {
      await navigator.clipboard.writeText(val);
      showToast(`${label} copied!`, 'success');
    } catch {
      showToast(`Failed to copy ${label}`, 'error');
    }
  };

  const handleGenerate = async (e: React.FormEvent) => {
    e.preventDefault();
    const amt = Number(amountInput);
    if (!Number.isFinite(amt) || amt < 100) {
      showToast('Enter at least ₦100 to generate a dynamic account.', 'error');
      return;
    }

    setGenerating(true);
    try {
      const res = await createDynamicAccount({
        userId: profile.id,
        email: profile.email,
        amount: amt,
        fullName: displayFullName,
        phone: profile.phone,
      });
      if (!res.ok) {
        showToast(res.error, 'error');
        return;
      }
      setActiveDynamic(res.account);
      updateDynamicAccountLocal(res.account);
      showToast('Dynamic virtual account ready! Transfer now to fund wallet.', 'success');
      await refreshUserData(false);
    } finally {
      setGenerating(false);
    }
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
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
              Dynamic Virtual Account
            </h1>
            <p className="text-xs text-slate-500 dark:text-slate-300">
              Instant single-use bank transfer account with automatic live wallet sync
            </p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => refreshUserData(true)}
          className="inline-flex items-center gap-1.5 rounded-xl bg-[#E8F8F2] px-3 py-2 text-xs font-bold text-[#00875A] dark:bg-emerald-950/60 dark:text-emerald-300"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${isRefreshingWallet ? 'animate-spin' : ''}`} />
          Wallet: {formatNaira(profile.walletBalance)}
        </button>
      </div>

      {/* Navy -> Green Gradient Hero Banner matching DynamicAccountScreen.kt */}
      <div className="relative overflow-hidden rounded-3xl border border-emerald-400/35 bg-gradient-to-br from-[#0A192F] via-[#0E3C39] to-[#00875A] p-6 text-white shadow-xl">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-white/15 backdrop-blur-sm">
              <Zap className="h-6 w-6 text-emerald-300" />
            </div>
            <div>
              <h2 className="text-lg font-extrabold">One-Time Transfer Account</h2>
              <p className="text-xs text-emerald-100/90">
                No NIN KYC wait • Instant settlement on bank transfer confirmation
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <span className="inline-flex items-center gap-1 rounded-full bg-white/15 px-3 py-1 text-[11px] font-bold text-white">
              <Zap className="h-3 w-3 text-emerald-300" /> Instant Generation
            </span>
            <span className="inline-flex items-center gap-1.5 rounded-full bg-[#00B875] px-3 py-1 text-[11px] font-bold text-white shadow-sm">
              <Radio className="h-3 w-3 animate-pulse" /> Realtime Live
            </span>
          </div>
        </div>

        {/* Linked Profile Card */}
        <div className="mt-5 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-white/15 bg-white/10 p-4 backdrop-blur-sm">
          <div className="flex items-center gap-3">
            <UserCheck className="h-5 w-5 text-emerald-300" />
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-wider text-emerald-200">
                Linked Profile
              </p>
              <p className="text-sm font-bold text-white">{profile.email}</p>
            </div>
          </div>
          <span className="rounded-lg bg-black/25 px-2.5 py-1 font-mono text-[11px] text-emerald-100">
            ID: {profile.id.slice(0, 12)}…
          </span>
        </div>
      </div>

      {/* Active Generated Dynamic Account Card OR Amount Form */}
      {shownAccountNumber && (
        <div className="space-y-4 rounded-3xl border border-[#00B875]/45 bg-white p-6 shadow-md dark:bg-[#102A43]">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-2 text-xs font-extrabold uppercase tracking-wider text-[#00875A] dark:text-[#00B074]">
              <Landmark className="h-4 w-4" /> Your Dynamic Dedicated Account
            </div>
            <span className="rounded-full bg-[#00B875]/15 px-3 py-1 text-[11px] font-extrabold text-[#00875A] dark:text-[#00B074]">
              ACTIVE • WALLET TOPUP
            </span>
          </div>

          <div className="rounded-2xl border border-emerald-500/30 bg-[#E8F8F2] p-5 dark:bg-emerald-950/40">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div>
                <p className="text-[11px] font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                  Account Number
                </p>
                <p className="mt-1 font-mono text-3xl font-extrabold tracking-wide text-slate-900 dark:text-white">
                  {shownAccountNumber}
                </p>
              </div>
              <button
                type="button"
                onClick={() => copyText(shownAccountNumber, 'Account Number')}
                className="inline-flex items-center gap-2 rounded-xl bg-[#00875A] px-4 py-2.5 text-xs font-bold text-white shadow-sm hover:bg-[#005A3C]"
              >
                <Copy className="h-4 w-4" /> Copy Account Number
              </button>
            </div>

            <div className="mt-4 grid gap-3 border-t border-emerald-500/20 pt-4 text-xs sm:grid-cols-3">
              <div>
                <p className="text-slate-500 dark:text-slate-400">Bank Name</p>
                <p className="mt-0.5 font-bold text-slate-900 dark:text-white">{shownBankName}</p>
              </div>
              <div>
                <p className="text-slate-500 dark:text-slate-400">Account Name</p>
                <p className="mt-0.5 font-bold text-slate-900 dark:text-white">Wallet Topup</p>
              </div>
              <div>
                <p className="text-slate-500 dark:text-slate-400">Target Top-up Amount</p>
                <p className="mt-0.5 font-extrabold text-[#00875A] dark:text-[#00B074]">
                  {formatCompactNaira(shownAmount)}
                </p>
              </div>
            </div>
          </div>

          {/* Realtime Listener Alert */}
          <div className="flex items-start gap-3 rounded-2xl border border-emerald-500/30 bg-emerald-50 p-4 text-xs text-emerald-900 dark:bg-emerald-950/50 dark:text-emerald-200">
            <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-[#00B074]" />
            <div>
              <p className="font-bold">Realtime Listener Active</p>
              <p className="mt-0.5 opacity-90">
                Wallet balance polls every 3.5 seconds while on this page and updates automatically
                as soon as your bank transfer arrives.
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={() => setActiveDynamic(null)}
            className="w-full rounded-xl border border-slate-200 py-2.5 text-xs font-bold text-slate-700 hover:bg-slate-50 dark:border-[#334E68] dark:text-slate-200 dark:hover:bg-[#1B3A5A]"
          >
            Generate for a Different Amount
          </button>
        </div>
      )}

      {/* Generate Form */}
      <form
        onSubmit={handleGenerate}
        className="space-y-5 rounded-3xl border border-slate-200 bg-white p-6 dark:border-[#334E68]/80 dark:bg-[#102A43]"
      >
        <div>
          <h3 className="text-base font-extrabold text-slate-900 dark:text-white">
            {shownAccountNumber ? 'Create Another Transfer Account' : 'Choose Deposit Amount'}
          </h3>
          <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-300">
            Select a preset or enter custom Naira amount (minimum ₦100).
          </p>
        </div>

        <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-4">
          {QUICK_AMOUNTS.map((amt) => (
            <button
              key={amt}
              type="button"
              onClick={() => setAmountInput(String(amt))}
              className={`rounded-xl border py-2.5 text-xs font-extrabold transition ${
                amountInput === String(amt)
                  ? 'border-[#00875A] bg-[#00875A] text-white shadow-sm'
                  : 'border-slate-200 text-slate-700 hover:bg-slate-50 dark:border-[#334E68] dark:text-slate-200 dark:hover:bg-[#1B3A5A]'
              }`}
            >
              ₦{amt.toLocaleString('en-US')}
            </button>
          ))}
        </div>

        <div>
          <label className="mb-1.5 block text-xs font-bold text-slate-700 dark:text-slate-200">
            Custom Amount (₦)
          </label>
          <input
            type="number"
            min={100}
            required
            value={amountInput}
            onChange={(e) => setAmountInput(e.target.value)}
            placeholder="e.g. 2500"
            className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-base font-extrabold text-slate-900 outline-none focus:border-[#00875A] dark:border-[#334E68] dark:bg-[#0A192F]/80 dark:text-white"
          />
        </div>

        <button
          type="submit"
          disabled={generating || (Number(amountInput) || 0) < 100}
          className="flex w-full items-center justify-center gap-2 rounded-xl bg-[#00875A] py-3.5 text-sm font-bold text-white shadow-md shadow-emerald-900/15 hover:bg-[#005A3C] disabled:opacity-50"
        >
          <Zap className="h-4 w-4" />
          {generating
            ? 'Generating Virtual Account...'
            : `Create Dynamic Account (${formatCompactNaira(Number(amountInput) || 0)})`}
        </button>

        {/* How Dynamic Accounts Work */}
        <div className="space-y-2 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-xs dark:border-[#334E68] dark:bg-[#0A192F]/70">
          <div className="flex items-center gap-1.5 font-bold text-slate-900 dark:text-white">
            <Info className="h-4 w-4 text-[#00B074]" /> How Dynamic Accounts Work:
          </div>
          <ul className="space-y-1.5 pl-1 text-slate-600 dark:text-slate-300">
            <li className="flex items-start gap-2">
              <ShieldCheck className="mt-0.5 h-3.5 w-3.5 shrink-0 text-[#00B074]" />
              Generates an instant single-use virtual bank account linked to your email.
            </li>
            <li className="flex items-start gap-2">
              <ShieldCheck className="mt-0.5 h-3.5 w-3.5 shrink-0 text-[#00B074]" />
              Transfer the exact amount shown from any Nigerian bank app or USSD.
            </li>
            <li className="flex items-start gap-2">
              <ShieldCheck className="mt-0.5 h-3.5 w-3.5 shrink-0 text-[#00B074]" />
              Your wallet balance updates automatically within seconds of payment confirmation.
            </li>
          </ul>
        </div>
      </form>
    </div>
  );
};
