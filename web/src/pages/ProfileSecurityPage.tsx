import React, { useEffect, useState } from 'react';
import {
  AlertTriangle,
  ArrowDownToLine,
  ArrowRight,
  Bell,
  Building2,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Copy,
  Edit3,
  Fingerprint,
  Info,
  Key,
  KeyRound,
  Lock,
  LogOut,
  Mail,
  MessageCircle,
  Moon,
  Phone,
  RefreshCw,
  Shield,
  ShieldCheck,
  Sun,
  Terminal,
  Trash2,
} from 'lucide-react';
import { NetworkBadge } from '../components/BrandBadges';
import { UserAvatar } from '../components/UserAvatar';
import { useApp } from '../context/AppContext';
import { detectNetworkFromPhone, NETWORKS, SUPPORT_CONTACT } from '../data/catalog';
import {
  deleteAccountPermanently,
  requestEmailChange,
  updatePassword,
  verifyEmailChangeOtp,
} from '../services/authService';
import {
  changeTransactionPin,
  clearPermanentAccount,
  createOrFetchPermanentAccount,
  resolveAccountHolderName,
  sanitizeFullName,
} from '../services/walletService';
import {
  authenticateWithBiometric,
  disableBiometricCredential,
  isPlatformAuthenticatorAvailable,
  isWebAuthnSupported,
  registerBiometricCredential,
} from '../services/webAuthnService';

export const ProfileSecurityPage: React.FC = () => {
  const {
    profile,
    hasTransactionPin,
    isBiometricEnabled,
    isAppLockEnabled,
    isNotificationsEnabled,
    darkMode,
    toggleDarkMode,
    setAppLockEnabled,
    setNotificationsEnabled,
    openSetPinModal,
    setBiometricEnabled,
    openWithdrawModal,
    setCurrentPage,
    logout,
    refreshUserData,
    updatePermanentAccountLocal,
    clearPermanentAccountLocal,
    showToast,
  } = useApp();

  // Permanent NIN account state
  const [ninInput, setNinInput] = useState('');
  const [permLoading, setPermLoading] = useState(false);
  const [permError, setPermError] = useState<string | null>(null);
  const [showKycHelp, setShowKycHelp] = useState(false);
  const [showResetPermConfirm, setShowResetPermConfirm] = useState(false);

  // Change PIN form
  const [showChangePin, setShowChangePin] = useState(false);
  const [oldPin, setOldPin] = useState('');
  const [newPin, setNewPin] = useState('');
  const [savingPin, setSavingPin] = useState(false);

  // Change Email form
  const [showEmailModal, setShowEmailModal] = useState(false);
  const [newEmail, setNewEmail] = useState('');
  const [emailOtp, setEmailOtp] = useState('');
  const [emailStep, setEmailStep] = useState<'input' | 'otp'>('input');
  const [savingEmail, setSavingEmail] = useState(false);

  // Change Password form
  const [showPwModal, setShowPwModal] = useState(false);
  const [newPw, setNewPw] = useState('');
  const [savingPw, setSavingPw] = useState(false);

  // Delete account dialog
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deleting, setDeleting] = useState(false);

  // WebAuthn Biometric state
  const [biometricSupported, setBiometricSupported] = useState(false);
  const [biometricBusy, setBiometricBusy] = useState(false);

  useEffect(() => {
    let active = true;
    isPlatformAuthenticatorAvailable().then((avail) => {
      if (active) setBiometricSupported(avail);
    });
    return () => {
      active = false;
    };
  }, []);

  if (!profile) return null;

  const displayFullName =
    sanitizeFullName(profile.fullName, profile.email) || profile.fullName || 'Daniel VTU User';
  const detectedNet = profile.phone ? detectNetworkFromPhone(profile.phone) : null;
  const hasPermVa = Boolean(profile.virtualAccountNumber);
  const permBank = profile.virtualBankName || 'Flutterwave MFB';
  const permHolderName = hasPermVa
    ? resolveAccountHolderName(profile.virtualAccountName, profile.fullName, profile.email) ||
      displayFullName
    : '';

  const copyValue = async (val: string, label: string) => {
    try {
      await navigator.clipboard.writeText(val);
      showToast(`${label} copied to clipboard!`, 'success');
    } catch {
      showToast(`Could not copy ${label}`, 'error');
    }
  };

  const handleCreatePermanentAccount = async (e: React.FormEvent) => {
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
      }
    } finally {
      setPermLoading(false);
    }
  };

  const handleResetPermanentAccount = async () => {
    setShowResetPermConfirm(false);
    setPermLoading(true);
    try {
      const res = await clearPermanentAccount(profile.id);
      if (!res.ok) {
        showToast(res.error, 'error');
        return;
      }
      clearPermanentAccountLocal();
      showToast('Account unlinked. You can now re-link with your NIN.', 'info');
    } finally {
      setPermLoading(false);
    }
  };

  const handleChangePin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (oldPin.length !== 4 || newPin.length !== 4) {
      showToast('Both PINs must be 4 digits.', 'error');
      return;
    }
    setSavingPin(true);
    try {
      const res = await changeTransactionPin(oldPin, newPin);
      if (!res.ok) {
        showToast(res.error, 'error');
        return;
      }
      showToast('Transaction PIN updated!', 'success');
      setOldPin('');
      setNewPin('');
      setShowChangePin(false);
    } finally {
      setSavingPin(false);
    }
  };

  const handleEmailChangeSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSavingEmail(true);
    try {
      if (emailStep === 'input') {
        const res = await requestEmailChange(newEmail);
        if (!res.ok) {
          showToast(res.error, 'error');
          return;
        }
        setEmailStep('otp');
        showToast('Verification code sent to your new email.', 'info');
      } else {
        const res = await verifyEmailChangeOtp(newEmail, emailOtp);
        if (!res.ok) {
          showToast(res.error, 'error');
          return;
        }
        showToast('Email address updated!', 'success');
        setShowEmailModal(false);
        setEmailStep('input');
        setNewEmail('');
        setEmailOtp('');
        await refreshUserData(false);
      }
    } finally {
      setSavingEmail(false);
    }
  };

  const handlePasswordUpdate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (newPw.length < 6) {
      showToast('Password must be at least 6 characters.', 'error');
      return;
    }
    setSavingPw(true);
    try {
      const res = await updatePassword(newPw);
      if (!res.ok) {
        showToast(res.error, 'error');
        return;
      }
      showToast('Password updated successfully!', 'success');
      setNewPw('');
      setShowPwModal(false);
    } finally {
      setSavingPw(false);
    }
  };

  const handleDeleteAccount = async () => {
    setDeleting(true);
    try {
      const res = await deleteAccountPermanently();
      if (!res.ok) {
        showToast(res.error, 'error');
      } else {
        showToast('Account deleted permanently.', 'info');
      }
      await logout();
    } finally {
      setDeleting(false);
    }
  };

  const handleEnableBiometric = async () => {
    setBiometricBusy(true);
    try {
      const res = await registerBiometricCredential({
        userId: profile.id,
        email: profile.email,
        fullName: displayFullName,
      });
      if (!res.ok) {
        showToast(res.error, 'error');
        return;
      }
      setBiometricEnabled(true);
      showToast('Device biometric authentication enabled for transactions!', 'success');
    } finally {
      setBiometricBusy(false);
    }
  };

  const handleTestBiometric = async () => {
    setBiometricBusy(true);
    try {
      if (!isBiometricEnabled) {
        await handleEnableBiometric();
        return;
      }
      const res = await authenticateWithBiometric(profile.id);
      if (!res.ok) {
        showToast(res.error, 'error');
        return;
      }
      showToast('Biometric sensor verified successfully!', 'success');
    } finally {
      setBiometricBusy(false);
    }
  };

  const handleDisableBiometric = () => {
    disableBiometricCredential(profile.id);
    setBiometricEnabled(false);
    showToast('Device biometric authentication disabled.', 'info');
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      {/* Navy Profile Hero Card (matches ProfileSecurityScreen.kt & Screenshots 1 & 3) */}
      <div className="rounded-3xl border border-[#1B3A5A] bg-[#0A192F] p-6 text-white shadow-xl sm:p-7">
        <div className="flex flex-col items-center text-center">
          <UserAvatar
            avatarUrl={profile.avatarUrl}
            fullName={displayFullName}
            sizeClass="h-24 w-24"
            textClass="text-2xl"
            className="shadow-md ring-2 ring-white/85"
          />
          <h1 className="mt-3 text-2xl font-extrabold">{displayFullName}</h1>
          <p className="text-xs text-slate-300">{profile.email}</p>

          {profile.phone && (
            <div className="mt-2.5 flex flex-wrap items-center justify-center gap-2">
              {detectedNet && <NetworkBadge network={detectedNet} size="sm" />}
              <span className="text-sm font-semibold text-white/95">{profile.phone}</span>
              {detectedNet && (
                <span
                  className="inline-flex items-center gap-1 rounded-lg px-2.5 py-0.5 text-[11px] font-bold"
                  style={{
                    backgroundColor: NETWORKS[detectedNet].brandColor,
                    color: NETWORKS[detectedNet].textColor,
                  }}
                >
                  {NETWORKS[detectedNet].displayName}
                </span>
              )}
            </div>
          )}

          {/* Dedicated Virtual Account Summary Box inside Navy Hero */}
          <div className="mt-5 w-full rounded-2xl bg-white/10 p-4 text-left">
            <div className="flex items-center justify-between gap-3">
              <div className="min-w-0">
                <p className="text-[11px] font-medium text-white/70">Dedicated Virtual Account</p>
                {hasPermVa ? (
                  <>
                    <p className="mt-0.5 font-mono text-sm font-bold text-white sm:text-base">
                      {profile.virtualAccountNumber}{' '}
                      {permBank ? `(${permBank})` : ''}
                    </p>
                    {permHolderName && (
                      <p className="mt-0.5 text-xs font-semibold text-[#00B074]">
                        Acct Name: {permHolderName}
                      </p>
                    )}
                  </>
                ) : (
                  <p className="mt-0.5 font-mono text-sm font-bold text-[#FFCC80]">
                    No permanent account yet. Verify your NIN to create one
                  </p>
                )}
              </div>
              <Building2 className="h-5 w-5 shrink-0 text-[#00B074]" />
            </div>
          </div>
        </div>
      </div>

      {/* Permanent Funding Account (NIN) Section (matches Screenshots 1 & 3 + PermanentAccountSection) */}
      <div className="space-y-2.5">
        <div>
          <h2 className="text-base font-extrabold text-slate-900 dark:text-white">
            Permanent Funding Account (NIN)
          </h2>
          <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-300">
            Verify your 11-digit National Identity Number (NIN) to create or manage your permanent
            dedicated funding account.
          </p>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-5 dark:border-[#334E68]/80 dark:bg-[#102A43]">
          {hasPermVa ? (
            <div className="space-y-4">
              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[#00875A]/15 text-[#00B074]">
                  <CheckCircle2 className="h-5 w-5" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-slate-900 dark:text-white">
                    Permanent Dedicated Account
                  </h3>
                  <p className="text-xs font-semibold text-[#00875A] dark:text-[#00B074]">
                    {profile.ninVerified
                      ? 'NIN Verified • Dedicated Virtual Account Active'
                      : 'Dedicated Virtual Account • Active'}
                  </p>
                </div>
              </div>

              <div className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-[#334E68] dark:bg-[#0A192F]/80">
                <div>
                  <p className="text-[10px] font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                    BANK NAME
                  </p>
                  <p className="text-sm font-semibold text-slate-900 dark:text-white">
                    {permBank}
                  </p>
                </div>

                <div>
                  <p className="text-[10px] font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                    ACCOUNT NUMBER
                  </p>
                  <div className="flex items-center justify-between">
                    <p className="font-mono text-2xl font-extrabold text-[#00875A] dark:text-[#00B074]">
                      {profile.virtualAccountNumber}
                    </p>
                    <button
                      type="button"
                      onClick={() =>
                        copyValue(profile.virtualAccountNumber!, 'Account number')
                      }
                      className="rounded-xl bg-white p-2 text-[#00875A] shadow-sm hover:bg-slate-100 dark:bg-[#1B3A5A] dark:text-[#00B074]"
                    >
                      <Copy className="h-4 w-4" />
                    </button>
                  </div>
                </div>

                <div>
                  <p className="text-[10px] font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                    ACCOUNT NAME
                  </p>
                  <p className="text-sm font-semibold text-slate-900 dark:text-white">
                    {permHolderName || '—'}
                  </p>
                </div>
              </div>

              <p className="text-xs text-slate-500 dark:text-slate-300">
                Any transfer sent to this dedicated account credits your wallet balance
                automatically in real-time.
              </p>

              {/* KYC Troubleshooter Card */}
              <div className="rounded-xl border border-slate-200 bg-slate-50 p-3.5 dark:border-[#334E68] dark:bg-[#0A192F]/60">
                <div className="flex items-center gap-2 text-xs font-bold text-slate-900 dark:text-white">
                  <Info className="h-4 w-4 text-[#00B074]" />
                  Bank shows &apos;Recipient KYC incomplete&apos;?
                </div>
                <p className="mt-1 text-xs text-slate-500 dark:text-slate-300">
                  If banks say KYC verification is incomplete, your compliance may need re-linking
                  with your verified 11-digit NIN.
                </p>
                <button
                  type="button"
                  onClick={() => setShowKycHelp((v) => !v)}
                  className="mt-2 inline-flex items-center gap-1 text-xs font-bold text-[#00875A] dark:text-[#00B074]"
                >
                  {showKycHelp
                    ? 'Hide Flutterwave KYC Checklist'
                    : 'View Flutterwave KYC Checklist'}
                  {showKycHelp ? (
                    <ChevronUp className="h-3.5 w-3.5" />
                  ) : (
                    <ChevronDown className="h-3.5 w-3.5" />
                  )}
                </button>
                {showKycHelp && (
                  <p className="mt-2 whitespace-pre-line text-[11px] leading-relaxed text-slate-600 dark:text-slate-300">
                    {`1. Valid 11-Digit NIN: Ensure your 11-digit National Identification Number matches your registered name.\n2. Network Connection: Ensure you have a stable internet connection.\n3. Reset & Re-link: Tap below to unlink this account and generate a new one with your verified NIN.`}
                  </p>
                )}
              </div>

              <button
                type="button"
                disabled={permLoading}
                onClick={() => setShowResetPermConfirm(true)}
                className="flex w-full items-center justify-center gap-2 rounded-xl border border-red-300 py-2.5 text-xs font-bold text-red-600 hover:bg-red-50 disabled:opacity-50 dark:border-red-900/60 dark:text-red-400 dark:hover:bg-red-950/30"
              >
                <RefreshCw className="h-3.5 w-3.5" />
                Reset / Re-link with Verified NIN
              </button>
            </div>
          ) : (
            <form onSubmit={handleCreatePermanentAccount} className="space-y-3.5">
              <p className="text-sm font-bold text-slate-900 dark:text-white">
                No permanent account yet. Verify your NIN to create one
              </p>

              <div className="flex items-center gap-2 rounded-xl bg-[#00875A]/15 px-3 py-2.5 text-xs font-bold text-[#00875A] dark:text-[#00B074]">
                <ShieldCheck className="h-4 w-4 shrink-0" />
                National Identity Number (NIN) Verification
              </div>

              <div>
                <div className="relative">
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
                    className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 pr-14 font-mono text-sm font-bold text-slate-900 outline-none focus:border-[#00875A] dark:border-[#334E68] dark:bg-[#0A192F] dark:text-white"
                  />
                  <span
                    className={`absolute right-3.5 top-1/2 -translate-y-1/2 text-xs font-bold ${
                      ninInput.length === 11
                        ? 'text-[#00B074]'
                        : 'text-slate-400 dark:text-slate-400'
                    }`}
                  >
                    {ninInput.length}/11
                  </span>
                </div>
              </div>

              {permError && (
                <div className="rounded-xl border border-red-300 bg-red-50 p-3 text-xs text-red-700 dark:border-red-900/60 dark:bg-red-950/40 dark:text-red-300">
                  <div className="flex items-center gap-1.5 font-bold">
                    <AlertTriangle className="h-4 w-4 shrink-0" /> Verification Note
                  </div>
                  <p className="mt-1">{permError}</p>
                </div>
              )}

              <button
                type="submit"
                disabled={permLoading || ninInput.length !== 11}
                className="flex w-full items-center justify-center gap-2 rounded-xl bg-[#00875A] py-3 text-sm font-bold text-white shadow-sm hover:bg-[#005A3C] disabled:opacity-50"
              >
                {permLoading ? 'Verifying & Creating Account...' : 'Verify NIN & Get Account'}
              </button>
            </form>
          )}
        </div>
      </div>

      {/* Biometric & Security Controls (matches ProfileSecurityScreen.kt lines 449-718) */}
      <div className="space-y-2.5">
        <h2 className="text-base font-extrabold text-slate-900 dark:text-white">
          Biometric &amp; Security Controls
        </h2>

        <div className="divide-y divide-slate-100 rounded-2xl border border-slate-200 bg-white dark:divide-[#334E68]/50 dark:border-[#334E68]/80 dark:bg-[#102A43]">
          {/* Biometric Authorization (WebAuthn) */}
          <div className="flex flex-wrap items-center justify-between gap-3 p-4">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[#00875A]/15 text-[#00875A] dark:text-[#00B074]">
                <Fingerprint className="h-5 w-5" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <p className="text-sm font-bold text-slate-900 dark:text-white">
                    Biometric Authorization
                  </p>
                  {isBiometricEnabled && (
                    <span className="rounded-full bg-[#00875A]/15 px-2 py-0.5 text-[10px] font-bold text-[#00B074]">
                      ACTIVE
                    </span>
                  )}
                </div>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  {isBiometricEnabled
                    ? 'Fingerprint / Face ID enrolled for transactions'
                    : biometricSupported
                    ? 'Require Fingerprint or Face ID for transactions'
                    : !isWebAuthnSupported()
                    ? 'Requires HTTPS or localhost in a WebAuthn browser'
                    : 'No built-in platform biometric sensor detected'}
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2">
              {isBiometricEnabled ? (
                <button
                  type="button"
                  disabled={biometricBusy}
                  onClick={handleDisableBiometric}
                  className="rounded-xl bg-red-50 px-3 py-2 text-xs font-bold text-red-600 hover:bg-red-100 dark:bg-red-950/50 dark:text-red-300"
                >
                  Disable
                </button>
              ) : (
                <button
                  type="button"
                  disabled={biometricBusy || !biometricSupported}
                  onClick={handleEnableBiometric}
                  className="rounded-xl bg-[#00875A] px-3.5 py-2 text-xs font-bold text-white hover:bg-[#005A3C] disabled:opacity-50"
                >
                  {biometricBusy ? 'Enrolling...' : 'Enable'}
                </button>
              )}
            </div>
          </div>

          {/* App Lock on Launch */}
          <div className="flex items-center justify-between p-4">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-cyan-500/15 text-cyan-500">
                <Lock className="h-5 w-5" />
              </div>
              <div>
                <p className="text-sm font-bold text-slate-900 dark:text-white">
                  App Lock on Launch
                </p>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  Protect sensitive security &amp; wallet actions
                </p>
              </div>
            </div>
            <button
              type="button"
              role="switch"
              aria-checked={isAppLockEnabled}
              onClick={() => setAppLockEnabled(!isAppLockEnabled)}
              className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full transition-colors ${
                isAppLockEnabled ? 'bg-cyan-500' : 'bg-slate-300 dark:bg-slate-700'
              }`}
            >
              <span
                className={`inline-block h-5 w-5 translate-y-0.5 rounded-full bg-white shadow transition-transform ${
                  isAppLockEnabled ? 'translate-x-5' : 'translate-x-0.5'
                }`}
              />
            </button>
          </div>

          {/* Real-Time Notifications */}
          <div className="flex items-center justify-between p-4">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-amber-500/15 text-amber-500">
                <Bell className="h-5 w-5" />
              </div>
              <div>
                <p className="text-sm font-bold text-slate-900 dark:text-white">
                  Real-Time Notifications
                </p>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  Instant alerts with tokens &amp; receipts
                </p>
              </div>
            </div>
            <button
              type="button"
              role="switch"
              aria-checked={isNotificationsEnabled}
              onClick={() => setNotificationsEnabled(!isNotificationsEnabled)}
              className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full transition-colors ${
                isNotificationsEnabled ? 'bg-[#00875A]' : 'bg-slate-300 dark:bg-slate-700'
              }`}
            >
              <span
                className={`inline-block h-5 w-5 translate-y-0.5 rounded-full bg-white shadow transition-transform ${
                  isNotificationsEnabled ? 'translate-x-5' : 'translate-x-0.5'
                }`}
              />
            </button>
          </div>

          {/* Create Transaction PIN */}
          <div className="flex items-center justify-between p-4">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[#00875A]/15 text-[#00875A] dark:text-[#00B074]">
                <Key className="h-5 w-5" />
              </div>
              <div>
                <p className="text-sm font-bold text-slate-900 dark:text-white">
                  Create Transaction PIN
                </p>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  {hasTransactionPin
                    ? 'Transaction PIN is active'
                    : 'Set a new 4-digit transaction PIN'}
                </p>
              </div>
            </div>
            <button
              type="button"
              onClick={openSetPinModal}
              className="rounded-xl bg-[#00875A] px-3.5 py-2 text-xs font-bold text-white hover:bg-[#005A3C]"
            >
              Create PIN
            </button>
          </div>

          {/* Change PIN */}
          <button
            type="button"
            onClick={() => (hasTransactionPin ? setShowChangePin(true) : openSetPinModal())}
            className="flex w-full items-center justify-between p-4 text-left hover:bg-slate-50 dark:hover:bg-[#1B3A5A]/50"
          >
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-slate-100 text-slate-700 dark:bg-[#1B3A5A] dark:text-white">
                <Shield className="h-5 w-5" />
              </div>
              <div>
                <p className="text-sm font-bold text-slate-900 dark:text-white">Change PIN</p>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  Update your 4-digit transaction PIN
                </p>
              </div>
            </div>
            <Edit3 className="h-4 w-4 text-slate-400" />
          </button>

          {/* Appearance */}
          <div className="flex items-center justify-between p-4">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-slate-100 text-slate-700 dark:bg-[#1B3A5A] dark:text-slate-200">
                {darkMode ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
              </div>
              <div>
                <p className="text-sm font-bold text-slate-900 dark:text-white">Appearance</p>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  {darkMode ? 'Navy Dark Mode (Default)' : 'Crisp Light Mode'}
                </p>
              </div>
            </div>
            <button
              type="button"
              onClick={toggleDarkMode}
              className="rounded-xl border border-slate-200 px-3.5 py-2 text-xs font-bold text-slate-700 hover:bg-slate-50 dark:border-[#334E68] dark:text-slate-200"
            >
              Switch to {darkMode ? 'Light' : 'Dark'}
            </button>
          </div>

          {/* Debit / Withdraw */}
          <button
            type="button"
            onClick={openWithdrawModal}
            className="flex w-full items-center justify-between p-4 text-left hover:bg-slate-50 dark:hover:bg-[#1B3A5A]/50"
          >
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-amber-500/15 text-amber-500">
                <ArrowDownToLine className="h-5 w-5" />
              </div>
              <div>
                <p className="text-sm font-bold text-slate-900 dark:text-white">
                  Debit / Withdraw Wallet
                </p>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  Execute a PIN-authorized wallet debit
                </p>
              </div>
            </div>
            <span className="text-xs font-bold text-[#00B074]">Open</span>
          </button>
        </div>
      </div>

      {/* Account & Credentials (matches ProfileSecurityScreen.kt lines 721-848) */}
      <div className="space-y-2.5">
        <h2 className="text-base font-extrabold text-slate-900 dark:text-white">
          Account &amp; Credentials
        </h2>
        <div className="space-y-2.5 rounded-2xl border border-slate-200 bg-slate-100/70 p-3.5 dark:border-[#334E68]/60 dark:bg-[#1B3A5A]/50">
          <button
            type="button"
            onClick={() => setShowEmailModal(true)}
            className="flex w-full items-center justify-between rounded-xl bg-white p-3.5 text-left transition hover:shadow-sm dark:bg-[#102A43]"
          >
            <div className="flex items-center gap-3">
              <div className="flex h-9 w-9 items-center justify-center rounded-full bg-[#00875A]/15 text-[#00B074]">
                <Mail className="h-4 w-4" />
              </div>
              <div>
                <p className="text-sm font-bold text-slate-900 dark:text-white">
                  Change Email Address
                </p>
                <p className="text-xs text-slate-500 dark:text-slate-300">{profile.email}</p>
              </div>
            </div>
            <Edit3 className="h-4 w-4 text-slate-400" />
          </button>

          <button
            type="button"
            onClick={() => setShowPwModal(true)}
            className="flex w-full items-center justify-between rounded-xl bg-white p-3.5 text-left transition hover:shadow-sm dark:bg-[#102A43]"
          >
            <div className="flex items-center gap-3">
              <div className="flex h-9 w-9 items-center justify-center rounded-full bg-[#0A192F]/15 text-slate-700 dark:bg-[#1B3A5A] dark:text-white">
                <KeyRound className="h-4 w-4" />
              </div>
              <div>
                <p className="text-sm font-bold text-slate-900 dark:text-white">
                  Reset Account Password
                </p>
                <p className="text-xs text-slate-500 dark:text-slate-300">
                  Update your sign-in password securely
                </p>
              </div>
            </div>
            <ArrowRight className="h-4 w-4 text-slate-400" />
          </button>
        </div>
      </div>

      {/* Developer Portal & API Key Card (matches ProfileSecurityScreen.kt lines 853-918) */}
      <button
        type="button"
        onClick={() => setCurrentPage('developer')}
        className="flex w-full items-center justify-between rounded-2xl border border-[#00875A]/45 bg-white p-4 text-left transition hover:shadow-md dark:bg-[#102A43]"
      >
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[#00875A] text-white">
            <Terminal className="h-5 w-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <p className="text-sm font-bold text-slate-900 dark:text-white">
                Developer Hub &amp; Forum
              </p>
              <span className="rounded bg-[#F59E0B]/20 px-1.5 py-0.5 text-[9px] font-extrabold text-[#F59E0B]">
                API
              </span>
            </div>
            <p className="text-xs text-slate-500 dark:text-slate-300">
              Manage your API key, webhooks &amp; community discussions
            </p>
          </div>
        </div>
        <ArrowRight className="h-5 w-5 text-[#00B074]" />
      </button>

      {/* Contact Support Card */}
      <div className="rounded-2xl border border-slate-200 bg-white p-5 dark:border-[#334E68]/80 dark:bg-[#102A43]">
        <h3 className="text-sm font-bold text-slate-900 dark:text-white">
          Need Help? Contact Support
        </h3>
        <p className="mt-1 text-xs text-slate-500 dark:text-slate-300">
          Direct support for funding accounts, NIN verification, and developer keys.
        </p>
        <div className="mt-3 grid gap-2 sm:grid-cols-3 text-xs">
          <a
            href={`mailto:${SUPPORT_CONTACT.email}`}
            className="flex items-center gap-2 rounded-xl bg-slate-50 px-3 py-2.5 font-semibold text-slate-700 hover:text-[#00875A] dark:bg-[#0A192F]/70 dark:text-slate-200"
          >
            <Mail className="h-4 w-4 shrink-0 text-[#00B074]" />
            <span className="truncate">{SUPPORT_CONTACT.email}</span>
          </a>
          <a
            href={`tel:${SUPPORT_CONTACT.phoneRaw}`}
            className="flex items-center gap-2 rounded-xl bg-slate-50 px-3 py-2.5 font-semibold text-slate-700 hover:text-[#00875A] dark:bg-[#0A192F]/70 dark:text-slate-200"
          >
            <Phone className="h-4 w-4 shrink-0 text-[#00B074]" />
            {SUPPORT_CONTACT.phoneDisplay}
          </a>
          <a
            href={`https://wa.me/${SUPPORT_CONTACT.whatsappDigits}`}
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center justify-center gap-2 rounded-xl bg-[#00875A] px-3 py-2.5 font-bold text-white hover:bg-[#005A3C]"
          >
            <MessageCircle className="h-4 w-4 shrink-0" /> WhatsApp Support
          </a>
        </div>
      </div>

      {/* Test Biometric Sensor Dialog Button (matches ProfileSecurityScreen.kt lines 928-939) */}
      <button
        type="button"
        disabled={biometricBusy || !biometricSupported}
        onClick={handleTestBiometric}
        className="flex h-12 w-full items-center justify-center gap-2 rounded-2xl border border-slate-300 bg-white text-sm font-semibold text-slate-800 transition hover:bg-slate-50 disabled:opacity-50 dark:border-[#334E68] dark:bg-[#102A43] dark:text-white dark:hover:bg-[#1B3A5A]"
      >
        <Fingerprint className="h-5 w-5 text-[#00B074]" />
        {biometricBusy ? 'Checking Biometric Sensor...' : 'Test Biometric Sensor Dialog'}
      </button>

      {/* Sign Out & Delete Account Permanently */}
      <div className="space-y-3">
        <button
          type="button"
          onClick={logout}
          className="flex h-12 w-full items-center justify-center gap-2 rounded-2xl bg-red-50 text-sm font-bold text-red-600 hover:bg-red-100 dark:bg-red-950/50 dark:text-red-300"
        >
          <LogOut className="h-4 w-4" /> Session &amp; Sign Out
        </button>

        <button
          type="button"
          onClick={() => setShowDeleteConfirm(true)}
          className="flex h-12 w-full items-center justify-center gap-2 rounded-2xl bg-red-600 text-sm font-bold text-white hover:bg-red-700"
        >
          <Trash2 className="h-4 w-4" /> Delete Account Permanently
        </button>
      </div>

      <div className="py-2 text-center">
        <p className="text-xs font-bold text-slate-500 dark:text-slate-400">
          Daniel VTU • Version 1.0.0
        </p>
        <p className="text-[11px] text-slate-400 dark:text-slate-500">
          Secured with Supabase &amp; WebAuthn Biometrics
        </p>
      </div>

      {/* Reset Permanent Account Confirm Modal */}
      {showResetPermConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/65 p-4 backdrop-blur-sm">
          <div className="w-full max-w-sm rounded-3xl border border-slate-200 bg-white p-6 dark:border-[#334E68] dark:bg-[#102A43]">
            <h3 className="text-base font-extrabold text-slate-900 dark:text-white">
              Reset Permanent Account?
            </h3>
            <p className="mt-2 text-xs text-slate-500 dark:text-slate-300">
              This will unlink account {profile.virtualAccountNumber} and allow you to re-enter your
              verified 11-digit NIN to generate a fresh permanent account.
            </p>
            <div className="mt-5 flex gap-3">
              <button
                type="button"
                onClick={() => setShowResetPermConfirm(false)}
                className="flex-1 rounded-xl border border-slate-200 py-2.5 text-xs font-bold dark:border-[#334E68] dark:text-slate-300"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleResetPermanentAccount}
                className="flex-1 rounded-xl bg-red-600 py-2.5 text-xs font-bold text-white"
              >
                Reset Account
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Change PIN Modal */}
      {showChangePin && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/65 p-4 backdrop-blur-sm">
          <form
            onSubmit={handleChangePin}
            className="w-full max-w-sm space-y-4 rounded-3xl border border-slate-200 bg-white p-6 dark:border-[#334E68] dark:bg-[#102A43]"
          >
            <div className="flex items-center gap-2">
              <Shield className="h-5 w-5 text-[#00B074]" />
              <h3 className="text-base font-extrabold text-slate-900 dark:text-white">
                Change Transaction PIN
              </h3>
            </div>
            <input
              type="password"
              inputMode="numeric"
              maxLength={4}
              value={oldPin}
              onChange={(e) => setOldPin(e.target.value.replace(/\D/g, '').slice(0, 4))}
              placeholder="Current 4-digit PIN"
              className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-center font-mono text-lg font-bold tracking-[0.5em] dark:border-[#334E68] dark:bg-[#0A192F] dark:text-white"
            />
            <input
              type="password"
              inputMode="numeric"
              maxLength={4}
              value={newPin}
              onChange={(e) => setNewPin(e.target.value.replace(/\D/g, '').slice(0, 4))}
              placeholder="New 4-digit PIN"
              className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-center font-mono text-lg font-bold tracking-[0.5em] dark:border-[#334E68] dark:bg-[#0A192F] dark:text-white"
            />
            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => setShowChangePin(false)}
                className="flex-1 rounded-xl border border-slate-200 py-2.5 text-xs font-bold dark:border-[#334E68] dark:text-slate-300"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={savingPin}
                className="flex-1 rounded-xl bg-[#00875A] py-2.5 text-xs font-bold text-white"
              >
                {savingPin ? 'Saving...' : 'Update PIN'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Change Email Modal */}
      {showEmailModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/65 p-4 backdrop-blur-sm">
          <form
            onSubmit={handleEmailChangeSubmit}
            className="w-full max-w-sm space-y-4 rounded-3xl border border-slate-200 bg-white p-6 dark:border-[#334E68] dark:bg-[#102A43]"
          >
            <h3 className="text-base font-extrabold text-slate-900 dark:text-white">
              {emailStep === 'input' ? 'Change Email Address' : 'Verify New Email Code'}
            </h3>
            {emailStep === 'input' ? (
              <input
                type="email"
                required
                value={newEmail}
                onChange={(e) => setNewEmail(e.target.value)}
                placeholder="newemail@example.com"
                className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm dark:border-[#334E68] dark:bg-[#0A192F] dark:text-white"
              />
            ) : (
              <input
                type="text"
                inputMode="numeric"
                required
                value={emailOtp}
                onChange={(e) => setEmailOtp(e.target.value.replace(/\D/g, ''))}
                placeholder="6-digit code"
                className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-center font-mono text-lg font-bold tracking-[0.4em] dark:border-[#334E68] dark:bg-[#0A192F] dark:text-white"
              />
            )}
            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => {
                  setShowEmailModal(false);
                  setEmailStep('input');
                }}
                className="flex-1 rounded-xl border border-slate-200 py-2.5 text-xs font-bold dark:border-[#334E68] dark:text-slate-300"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={savingEmail}
                className="flex-1 rounded-xl bg-[#00875A] py-2.5 text-xs font-bold text-white"
              >
                {savingEmail ? 'Working...' : emailStep === 'input' ? 'Send Code' : 'Verify'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Update Password Modal */}
      {showPwModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/65 p-4 backdrop-blur-sm">
          <form
            onSubmit={handlePasswordUpdate}
            className="w-full max-w-sm space-y-4 rounded-3xl border border-slate-200 bg-white p-6 dark:border-[#334E68] dark:bg-[#102A43]"
          >
            <h3 className="text-base font-extrabold text-slate-900 dark:text-white">
              Update Password
            </h3>
            <input
              type="password"
              minLength={6}
              required
              value={newPw}
              onChange={(e) => setNewPw(e.target.value)}
              placeholder="New password (min 6 chars)"
              className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm dark:border-[#334E68] dark:bg-[#0A192F] dark:text-white"
            />
            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => setShowPwModal(false)}
                className="flex-1 rounded-xl border border-slate-200 py-2.5 text-xs font-bold dark:border-[#334E68] dark:text-slate-300"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={savingPw}
                className="flex-1 rounded-xl bg-[#00875A] py-2.5 text-xs font-bold text-white"
              >
                {savingPw ? 'Saving...' : 'Save Password'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Delete Account Confirmation */}
      {showDeleteConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/65 p-4 backdrop-blur-sm">
          <div className="w-full max-w-sm rounded-3xl border border-slate-200 bg-white p-6 dark:border-[#334E68] dark:bg-[#102A43]">
            <h3 className="text-base font-extrabold text-red-600 dark:text-red-400">
              Delete Account Permanently?
            </h3>
            <p className="mt-2 text-xs text-slate-500 dark:text-slate-300">
              This permanently removes your profile, transaction history, and credentials. This
              action cannot be undone.
            </p>
            <div className="mt-5 flex gap-3">
              <button
                type="button"
                onClick={() => setShowDeleteConfirm(false)}
                className="flex-1 rounded-xl border border-slate-200 py-2.5 text-xs font-bold dark:border-[#334E68] dark:text-slate-300"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={deleting}
                onClick={handleDeleteAccount}
                className="flex-1 rounded-xl bg-red-600 py-2.5 text-xs font-bold text-white"
              >
                {deleting ? 'Deleting...' : 'Yes, Delete'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
