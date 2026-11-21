import React, { useState } from 'react';
import {
  ArrowRight,
  Bolt,
  Copy,
  Eye,
  EyeOff,
  Fingerprint,
  GraduationCap,
  Headset,
  Mail,
  MessageCircle,
  Phone,
  Plus,
  RefreshCw,
  ShieldCheck,
  Smartphone,
  Sparkles,
  Terminal,
  Tv,
  Wallet,
  Wifi,
} from 'lucide-react';
import { NetworkBadge } from '../components/BrandBadges';
import { UserAvatar } from '../components/UserAvatar';
import { useApp } from '../context/AppContext';
import {
  detectNetworkFromPhone,
  formatNaira,
  NETWORKS,
  SUPPORT_CONTACT,
} from '../data/catalog';
import { resolveAccountHolderName, sanitizeFullName } from '../services/walletService';

export const HomePage: React.FC = () => {
  const {
    profile,
    transactions,
    airtimePricing,
    isBiometricEnabled,
    isRefreshingWallet,
    refreshUserData,
    setCurrentPage,
    navigateToBills,
    openFundModal,
    setSelectedReceipt,
    showToast,
  } = useApp();

  const [showBalance, setShowBalance] = useState(true);
  const [showSupportModal, setShowSupportModal] = useState(false);

  const detectedNet = profile?.phone ? detectNetworkFromPhone(profile.phone) : null;
  const bestCashback = Math.min(
    ...Object.values(airtimePricing).map((p) => p.cashbackPercent)
  );

  const displayFullName =
    sanitizeFullName(profile?.fullName, profile?.email) ||
    profile?.fullName ||
    'Daniel VTU User';

  const hasPermVa = Boolean(profile?.virtualAccountNumber);
  const hasDynVa = Boolean(profile?.dynamicAccountNumber);
  const permHolderName = hasPermVa
    ? resolveAccountHolderName(
        profile?.virtualAccountName,
        profile?.fullName,
        profile?.email
      ) || displayFullName
    : '';

  const copyText = async (value: string, label: string) => {
    try {
      await navigator.clipboard.writeText(value);
      showToast(`${label} ${value} copied!`, 'success');
    } catch {
      showToast(`Could not copy ${label}`, 'error');
    }
  };

  return (
    <div className="space-y-6">
      {/* Greeting & Detected Network Pill (matches Android HomeScreen header) */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <button
          type="button"
          onClick={() => setCurrentPage('security')}
          className="flex items-center gap-3 text-left"
        >
          <UserAvatar
            avatarUrl={profile?.avatarUrl}
            fullName={displayFullName}
            sizeClass="h-12 w-12"
            textClass="text-base"
            className="ring-2 ring-emerald-500/40"
          />
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-lg font-extrabold text-slate-900 dark:text-white sm:text-2xl">
                Hello, {displayFullName}
              </h1>
              {isBiometricEnabled && (
                <Fingerprint
                  className="h-4 w-4 text-[#00875A] dark:text-[#00B074]"
                  aria-label="Biometric Secured"
                />
              )}
            </div>
            {profile?.phone ? (
              <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-slate-500 dark:text-slate-300">
                {detectedNet && <NetworkBadge network={detectedNet} size="sm" />}
                <span className="font-semibold">{profile.phone}</span>
                {detectedNet && (
                  <span
                    className="inline-flex items-center gap-1.5 rounded-lg px-2 py-0.5 text-[10px] font-bold"
                    style={{
                      backgroundColor: NETWORKS[detectedNet].brandColor,
                      color: NETWORKS[detectedNet].textColor,
                    }}
                  >
                    {NETWORKS[detectedNet].displayName}
                  </span>
                )}
              </div>
            ) : (
              <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-300">
                VTU &amp; Utility Services
              </p>
            )}
          </div>
        </button>
      </div>

      {/* Hero Wallet Card + Virtual Accounts */}
      <div className="grid gap-5 lg:grid-cols-12">
        <div className="relative overflow-hidden rounded-[26px] border border-emerald-400/55 bg-gradient-to-r from-[#0B2235] via-[#0E3C39] to-[#14644A] p-6 text-white shadow-xl lg:col-span-7">
          {/* Concentric Rings Watermark */}
          <svg
            className="pointer-events-none absolute -right-10 top-1/2 h-64 w-64 -translate-y-1/2 opacity-30"
            viewBox="0 0 220 220"
            fill="none"
          >
            <circle cx="110" cy="110" r="36" fill="#082A23" fillOpacity="0.45" />
            <circle cx="110" cy="110" r="36" stroke="#4ADE80" strokeOpacity="0.45" />
            <circle cx="110" cy="110" r="58" stroke="#4ADE80" strokeOpacity="0.3" />
            <circle cx="110" cy="110" r="82" stroke="#4ADE80" strokeOpacity="0.2" />
            <circle cx="110" cy="110" r="106" stroke="#4ADE80" strokeOpacity="0.12" />
            <path
              d="M114 82L98 112h12l-4 26 16-30h-12l4-26z"
              fill="#4ADE80"
              fillOpacity="0.5"
            />
          </svg>

          <div className="relative flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-[#22C57B]">
                <Wallet className="h-4 w-4 text-white" />
              </div>
              <span className="text-xs font-semibold text-[#D5E6E1]">Available Balance</span>
              <button
                type="button"
                onClick={() => setShowBalance((s) => !s)}
                className="rounded-lg p-1 text-white/85 hover:bg-white/10"
                title="Toggle Balance Visibility"
              >
                {showBalance ? <Eye className="h-4 w-4" /> : <EyeOff className="h-4 w-4" />}
              </button>
              <button
                type="button"
                onClick={() => refreshUserData(true)}
                className="rounded-lg p-1 text-white/85 hover:bg-white/10"
                title="Sync Live Balance"
              >
                <RefreshCw
                  className={`h-4 w-4 ${isRefreshingWallet ? 'animate-spin text-emerald-300' : ''}`}
                />
              </button>
            </div>

            <span className="rounded-full border border-[#8C6D2D]/70 bg-[#2B3324]/90 px-3 py-1 text-xs font-bold text-[#F6B93B]">
              Bonus:{(profile?.cashbackBalance ?? 0).toFixed(1)}
            </span>
          </div>

          <div className="relative mt-4">
            <p className="text-3xl font-extrabold tracking-tight sm:text-4xl">
              {showBalance ? formatNaira(profile?.walletBalance ?? 0) : '₦ • • • • • •'}
            </p>
            <div className="mt-2.5 inline-flex items-center gap-1.5 rounded-full border border-[#2E9E75]/65 bg-[#104035]/85 px-3 py-1 text-xs font-medium text-[#D1FAE5]">
              <ShieldCheck className="h-3.5 w-3.5 text-[#34D889]" />
              Protected
            </div>
          </div>

          <div className="relative mt-5 flex gap-3 border-t border-white/10 pt-4">
            <button
              type="button"
              onClick={() => openFundModal('card')}
              className="flex flex-1 items-center justify-center gap-2 rounded-2xl border border-[#38D399]/55 bg-[#189E65] py-2.5 text-sm font-bold text-white shadow-md transition hover:bg-[#148655]"
            >
              <Plus className="h-4 w-4" /> Fund Wallet
            </button>
            <button
              type="button"
              onClick={() => setShowSupportModal(true)}
              className="flex flex-1 items-center justify-center gap-2 rounded-2xl border border-[#5EEAD4]/45 bg-[#134A3E]/70 py-2.5 text-sm font-bold text-white transition hover:bg-[#165749]"
            >
              <Headset className="h-4 w-4" /> Support
            </button>
          </div>
        </div>

        {/* Dedicated & Dynamic Virtual Accounts Column */}
        <div className="flex flex-col justify-between gap-3.5 lg:col-span-5">
          {/* Permanent Virtual Account (matches HomeScreen.kt lines 786-890) */}
          <div
            onClick={() => openFundModal('permanent')}
            className={`cursor-pointer rounded-2xl border p-4 transition hover:shadow-md ${
              hasPermVa
                ? 'border-slate-200 bg-white dark:border-[#334E68] dark:bg-[#1B3A5A]/80'
                : 'border-[#FB9129]/60 bg-[#FFF3E0] dark:border-[#FB9129]/45 dark:bg-[#2B1D0E]'
            }`}
          >
            <div className="flex items-center justify-between gap-3">
              <div className="flex min-w-0 items-center gap-3">
                <div
                  className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-[11px] font-black text-white ${
                    hasPermVa ? 'bg-[#FB9129]' : 'bg-[#E65100]'
                  }`}
                >
                  {hasPermVa ? 'FW' : 'KYC'}
                </div>
                <div className="min-w-0">
                  {hasPermVa ? (
                    <>
                      <p className="text-xs text-slate-600 dark:text-slate-300">
                        <span className="font-medium">Permanent Acct: </span>
                        <span className="font-mono text-sm font-extrabold text-slate-900 dark:text-white">
                          {profile?.virtualAccountNumber}
                        </span>
                      </p>
                      <p className="text-xs font-semibold text-[#FB9129]">
                        {profile?.virtualBankName || 'Flutterwave MFB'}
                      </p>
                      {permHolderName && (
                        <p className="truncate text-xs font-semibold text-[#00875A] dark:text-[#00B074]">
                          Name: {permHolderName}
                        </p>
                      )}
                    </>
                  ) : (
                    <>
                      <p className="text-xs font-extrabold text-[#E65100] dark:text-[#FFB74D]">
                        No permanent account yet. Verify your NIN to create one
                      </p>
                      <p className="mt-0.5 text-[11px] text-slate-600 dark:text-slate-300">
                        Tap to enter NIN &amp; get permanent account
                      </p>
                    </>
                  )}
                </div>
              </div>

              {hasPermVa ? (
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    copyText(profile!.virtualAccountNumber!, 'Permanent account');
                  }}
                  aria-label="Copy permanent account number"
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-slate-100 text-[#00875A] hover:bg-slate-200 dark:bg-[#102A43] dark:text-[#00B074]"
                >
                  <Copy className="h-4 w-4" />
                </button>
              ) : (
                <span className="shrink-0 rounded-full bg-[#FB9129] px-3 py-1.5 text-xs font-bold text-white shadow-sm">
                  Activate
                </span>
              )}
            </div>
          </div>

          {/* Dynamic Virtual Account (matches HomeScreen.kt lines 903-1018) */}
          <div
            onClick={() => openFundModal('dynamic')}
            className="cursor-pointer rounded-2xl border border-[#00B875]/45 bg-[#00B875]/12 p-4 transition hover:bg-[#00B875]/18"
          >
            <div className="flex items-center justify-between gap-3">
              <div className="flex min-w-0 items-center gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#00B875] text-white">
                  <Bolt className="h-5 w-5" />
                </div>
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="text-sm font-bold text-slate-900 dark:text-white">
                      {hasDynVa
                        ? `Dynamic Acct: ${profile?.dynamicAccountNumber}`
                        : 'Create Dynamic Account'}
                    </p>
                    <span className="rounded bg-[#00B875] px-1.5 py-0.5 text-[9px] font-extrabold text-white">
                      {hasDynVa ? 'ACTIVE' : 'INSTANT'}
                    </span>
                  </div>
                  <p className="mt-0.5 text-xs text-slate-600 dark:text-slate-300">
                    {hasDynVa
                      ? `${profile?.dynamicBankName} • Direct Bank transfer • Live Auto sync`
                      : 'Instant virtual bank account • Direct Bank transfer • Auto sync'}
                  </p>
                </div>
              </div>
              <ArrowRight className="h-4 w-4 shrink-0 text-[#00B875]" />
            </div>
          </div>
        </div>
      </div>

      {/* Quick Services — Exact Android 2-col top + 3-col bottom layout (zero orphan gap) */}
      <div>
        <h2 className="mb-3 text-base font-extrabold text-slate-900 dark:text-white">
          Quick Services
        </h2>
        <div className="space-y-3">
          {/* Row 1: Airtime & Data Bundles */}
          <div className="grid grid-cols-2 gap-3">
            {[
              {
                title: 'Airtime',
                subtitle: `Instant ${bestCashback}% Off`,
                icon: Smartphone,
                tintBg: 'bg-[#00875A]/15 text-[#00875A] dark:text-[#00B074]',
                onClick: () => setCurrentPage('airtime'),
              },
              {
                title: 'Data Bundles',
                subtitle: 'SME & Corporate',
                icon: Wifi,
                tintBg: 'bg-[#06B6D4]/15 text-[#06B6D4]',
                onClick: () => setCurrentPage('data'),
              },
            ].map((svc) => (
              <button
                key={svc.title}
                type="button"
                onClick={svc.onClick}
                className="group flex items-center gap-3.5 rounded-2xl border border-slate-200 bg-white p-4 text-left shadow-sm transition hover:-translate-y-0.5 hover:border-[#00875A]/50 hover:shadow-md dark:border-[#334E68]/70 dark:bg-[#102A43]"
              >
                <div
                  className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl ${svc.tintBg}`}
                >
                  <svc.icon className="h-5 w-5" />
                </div>
                <div className="min-w-0">
                  <p className="text-sm font-bold text-slate-900 group-hover:text-[#00B074] dark:text-white">
                    {svc.title}
                  </p>
                  <p className="truncate text-xs text-slate-500 dark:text-slate-300">
                    {svc.subtitle}
                  </p>
                </div>
              </button>
            ))}
          </div>

          {/* Row 2: Electricity, Cable TV & Education */}
          <div className="grid grid-cols-3 gap-3">
            {[
              {
                title: 'Electricity',
                subtitle: 'Prepaid Token',
                icon: Bolt,
                tintBg: 'bg-[#F59E0B]/15 text-[#F59E0B]',
                onClick: () => navigateToBills('ELECTRICITY'),
              },
              {
                title: 'Cable TV',
                subtitle: 'DSTV, GOtv',
                icon: Tv,
                tintBg: 'bg-[#E11D48]/15 text-[#E11D48]',
                onClick: () => navigateToBills('CABLE_TV'),
              },
              {
                title: 'Education',
                subtitle: 'WAEC & JAMB',
                icon: GraduationCap,
                tintBg: 'bg-[#7C3AED]/15 text-[#A78BFA]',
                onClick: () => navigateToBills('EDUCATION'),
              },
            ].map((svc) => (
              <button
                key={svc.title}
                type="button"
                onClick={svc.onClick}
                className="group flex flex-col items-start rounded-2xl border border-slate-200 bg-white p-3.5 text-left shadow-sm transition hover:-translate-y-0.5 hover:border-[#00875A]/50 hover:shadow-md dark:border-[#334E68]/70 dark:bg-[#102A43] sm:p-4"
              >
                <div
                  className={`flex h-10 w-10 items-center justify-center rounded-2xl ${svc.tintBg}`}
                >
                  <svc.icon className="h-5 w-5" />
                </div>
                <p className="mt-2.5 text-xs font-bold text-slate-900 group-hover:text-[#00B074] dark:text-white sm:text-sm">
                  {svc.title}
                </p>
                <p className="truncate text-[11px] text-slate-500 dark:text-slate-300 sm:text-xs">
                  {svc.subtitle}
                </p>
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Recent Transactions + Side Cards */}
      <div className="grid gap-6 lg:grid-cols-12">
        <div className="space-y-3 lg:col-span-7">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-extrabold text-slate-900 dark:text-white">
              Recent Transactions
            </h2>
            <button
              type="button"
              onClick={() => setCurrentPage('transactions')}
              className="inline-flex items-center gap-1 text-xs font-bold text-[#00875A] hover:underline dark:text-[#00B074]"
            >
              See All <ArrowRight className="h-3.5 w-3.5" />
            </button>
          </div>

          {transactions.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center dark:border-[#334E68] dark:bg-[#102A43]">
              <Wallet className="mx-auto h-9 w-9 text-slate-400" />
              <p className="mt-2 text-sm font-bold text-slate-700 dark:text-slate-200">
                No transactions yet
              </p>
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                Recharge airtime or buy data to see records here.
              </p>
            </div>
          ) : (
            <div className="space-y-2.5">
              {transactions.slice(0, 6).map((tx) => {
                const statusUpper = tx.status.toUpperCase();
                const statusClass =
                  statusUpper === 'SUCCESSFUL'
                    ? 'text-[#00875A] dark:text-[#10B981]'
                    : statusUpper === 'PENDING'
                    ? 'text-amber-500'
                    : 'text-red-500';
                const letter = (tx.provider || tx.title || 'T')[0].toUpperCase();

                return (
                  <button
                    key={tx.reference}
                    type="button"
                    onClick={() => setSelectedReceipt(tx)}
                    className="flex w-full items-center justify-between rounded-2xl border border-slate-200 bg-white p-3.5 text-left transition hover:border-[#00875A]/45 hover:shadow-sm dark:border-[#334E68]/70 dark:bg-[#102A43]"
                  >
                    <div className="flex items-center gap-3">
                      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#00875A]/15 text-sm font-extrabold text-[#00875A] dark:text-[#00B074]">
                        {letter}
                      </div>
                      <div>
                        <p className="text-sm font-bold text-slate-900 dark:text-white">
                          {tx.title}
                        </p>
                        <p className="text-xs text-slate-500 dark:text-slate-400">
                          {tx.recipient ? `${tx.recipient} • ` : ''}
                          {new Date(tx.timestamp).toLocaleDateString('en-NG', {
                            month: 'short',
                            day: 'numeric',
                            hour: '2-digit',
                            minute: '2-digit',
                          })}
                        </p>
                      </div>
                    </div>
                    <div className="text-right">
                      <p className="text-sm font-extrabold text-slate-900 dark:text-white">
                        {formatNaira(tx.amount)}
                      </p>
                      <p className={`text-[11px] font-bold ${statusClass}`}>{tx.status}</p>
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* Side Column: Developer Hub + Zero Transaction Fees Banner + Contact Support */}
        <div className="space-y-4 lg:col-span-5">
          {/* Developer Hub Banner */}
          <div
            onClick={() => setCurrentPage('developer')}
            className="cursor-pointer rounded-2xl border border-[#1B3A5A] bg-[#0A192F] p-4 text-white shadow-lg transition hover:bg-[#102A43]"
          >
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#00875A]">
                  <Terminal className="h-5 w-5 text-white" />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <h3 className="text-sm font-bold">Developer Hub &amp; Forum</h3>
                    <span className="rounded bg-[#F59E0B] px-1.5 py-0.5 text-[9px] font-extrabold text-black">
                      API KEYS
                    </span>
                  </div>
                  <p className="text-xs text-slate-300">
                    Generate your secret API key • Webhooks &amp; Forum
                  </p>
                </div>
              </div>
              <ArrowRight className="h-4 w-4 shrink-0 text-[#00B074]" />
            </div>
          </div>

          {/* Zero Transaction Fees Promotional Banner (matches HomeScreen.kt lines 1254-1297) */}
          <div className="flex items-center gap-3.5 rounded-2xl border border-slate-200 bg-slate-100 p-4 dark:border-[#334E68]/70 dark:bg-[#1B3A5A]">
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-[#00875A] to-[#0A192F] text-emerald-300 shadow-inner">
              <Sparkles className="h-7 w-7" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-slate-900 dark:text-white">
                Zero Transaction Fees
              </h3>
              <p className="mt-0.5 text-xs leading-relaxed text-slate-600 dark:text-slate-300">
                Enjoy instant biometric authorizations, real-time receipt generation &amp; cashback.
              </p>
            </div>
          </div>

          {/* Dedicated Contact Support Card */}
          <div className="rounded-2xl border border-slate-200 bg-white p-5 dark:border-[#334E68]/70 dark:bg-[#102A43]">
            <h3 className="text-sm font-bold text-slate-900 dark:text-white">
              Need Help? Contact Support
            </h3>
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-300">
              Our support desk assists with deposits, pending orders, and developer keys.
            </p>
            <div className="mt-4 space-y-2.5 text-xs">
              <a
                href={`mailto:${SUPPORT_CONTACT.email}`}
                className="flex items-center gap-2.5 rounded-xl bg-slate-50 px-3 py-2.5 font-semibold text-slate-700 hover:bg-[#E8F8F2] hover:text-[#00875A] dark:bg-[#0A192F]/70 dark:text-slate-200"
              >
                <Mail className="h-4 w-4 text-[#00B074]" />
                {SUPPORT_CONTACT.email}
              </a>
              <a
                href={`tel:${SUPPORT_CONTACT.phoneRaw}`}
                className="flex items-center gap-2.5 rounded-xl bg-slate-50 px-3 py-2.5 font-semibold text-slate-700 hover:bg-[#E8F8F2] hover:text-[#00875A] dark:bg-[#0A192F]/70 dark:text-slate-200"
              >
                <Phone className="h-4 w-4 text-[#00B074]" />
                {SUPPORT_CONTACT.phoneDisplay}
              </a>
              <a
                href={`https://wa.me/${SUPPORT_CONTACT.whatsappDigits}`}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center justify-center gap-2 rounded-xl bg-[#00875A] py-2.5 font-bold text-white hover:bg-[#005A3C]"
              >
                <MessageCircle className="h-4 w-4" /> Chat on WhatsApp
              </a>
            </div>
          </div>
        </div>
      </div>

      {/* Support Dialog */}
      {showSupportModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/65 p-4 backdrop-blur-sm">
          <div className="w-full max-w-sm rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl dark:border-[#334E68] dark:bg-[#102A43]">
            <h3 className="text-lg font-extrabold text-slate-900 dark:text-white">
              Daniel VTU Support
            </h3>
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-300">
              Reach us directly via email, call, or WhatsApp.
            </p>
            <div className="mt-4 space-y-2.5 text-xs">
              <a
                href={`mailto:${SUPPORT_CONTACT.email}`}
                className="flex items-center gap-2.5 rounded-xl border border-slate-200 p-3 font-semibold dark:border-[#334E68] dark:text-white"
              >
                <Mail className="h-4 w-4 text-[#00B074]" /> {SUPPORT_CONTACT.email}
              </a>
              <a
                href={`tel:${SUPPORT_CONTACT.phoneRaw}`}
                className="flex items-center gap-2.5 rounded-xl border border-slate-200 p-3 font-semibold dark:border-[#334E68] dark:text-white"
              >
                <Phone className="h-4 w-4 text-[#00B074]" /> {SUPPORT_CONTACT.phoneDisplay}
              </a>
              <a
                href={`https://wa.me/${SUPPORT_CONTACT.whatsappDigits}`}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center justify-center gap-2 rounded-xl bg-[#00875A] py-2.5 font-bold text-white"
              >
                <MessageCircle className="h-4 w-4" /> Open WhatsApp
              </a>
            </div>
            <button
              type="button"
              onClick={() => setShowSupportModal(false)}
              className="mt-4 w-full rounded-xl border border-slate-200 py-2 text-xs font-bold text-slate-600 dark:border-[#334E68] dark:text-slate-300"
            >
              Close
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
