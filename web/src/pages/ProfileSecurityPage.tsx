import React, { useEffect, useState } from 'react';
import {
  ArrowDownToLine,
  Fingerprint,
  KeyRound,
  Lock,
  LogOut,
  Mail,
  Moon,
  Shield,
  Sun,
  Terminal,
  Trash2,
} from 'lucide-react';
import { NetworkBadge } from '../components/BrandBadges';
import { UserAvatar } from '../components/UserAvatar';
import { useApp } from '../context/AppContext';
import { detectNetworkFromPhone, NETWORKS } from '../data/catalog';
import {
  deleteAccountPermanently,
  requestEmailChange,
  updatePassword,
  verifyEmailChangeOtp,
} from '../services/authService';
import { changeTransactionPin } from '../services/walletService';
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
    darkMode,
    toggleDarkMode,
    openSetPinModal,
    setBiometricEnabled,
    openWithdrawModal,
    setCurrentPage,
    logout,
    refreshUserData,
    showToast,
  } = useApp();

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

  const detectedNet = profile.phone ? detectNetworkFromPhone(profile.phone) : null;

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
        fullName: profile.fullName,
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
      {/* Profile Hero */}
      <div className="flex flex-col items-center rounded-3xl bg-[#0A192F] p-7 text-center text-white shadow-lg">
        <UserAvatar
          avatarUrl={profile.avatarUrl}
          fullName={profile.fullName}
          sizeClass="h-20 w-20"
          textClass="text-2xl"
          className="shadow-md ring-2 ring-white/80"
        />
        <h1 className="mt-3 text-xl font-extrabold">{profile.fullName}</h1>
        <p className="text-xs text-slate-300">{profile.email}</p>
        {profile.phone && (
          <div className="mt-2 inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-semibold">
            {detectedNet && <NetworkBadge network={detectedNet} size="sm" />}
            <span>{profile.phone}</span>
            {detectedNet && (
              <span
                className="rounded px-1.5 py-0.5 text-[10px] font-bold"
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
      </div>

      {/* Security & Preferences */}
      <div className="divide-y divide-slate-100 rounded-3xl border border-slate-200 bg-white dark:divide-slate-800 dark:border-slate-700 dark:bg-[#102A43]">
        <div className="flex items-center justify-between p-4">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#E8F8F2] text-[#00875A] dark:bg-emerald-950">
              <Lock className="h-5 w-5" />
            </div>
            <div>
              <p className="text-sm font-bold text-slate-900 dark:text-white">Transaction PIN</p>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                {hasTransactionPin
                  ? 'Server-verified 4-digit PIN is active'
                  : 'Create a 4-digit PIN to authorize purchases'}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => (hasTransactionPin ? setShowChangePin(true) : openSetPinModal())}
            className="rounded-xl bg-[#00875A] px-3.5 py-2 text-xs font-bold text-white hover:bg-[#005A3C]"
          >
            {hasTransactionPin ? 'Change PIN' : 'Create PIN'}
          </button>
        </div>

        {/* WebAuthn Biometric Authentication */}
        <div className="flex flex-wrap items-center justify-between gap-3 p-4">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#E8F8F2] text-[#00875A] dark:bg-emerald-950">
              <Fingerprint className="h-5 w-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <p className="text-sm font-bold text-slate-900 dark:text-white">
                  Biometric Authentication (WebAuthn)
                </p>
                {isBiometricEnabled && (
                  <span className="rounded-full bg-[#E8F8F2] px-2 py-0.5 text-[10px] font-bold text-[#00875A] dark:bg-emerald-950 dark:text-emerald-300">
                    ACTIVE
                  </span>
                )}
              </div>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                {isBiometricEnabled
                  ? 'Fingerprint / Face ID enrolled for transaction approvals on this device'
                  : biometricSupported
                  ? 'Use built-in Fingerprint, Face ID, or Windows Hello to authorize purchases'
                  : !isWebAuthnSupported()
                  ? 'Requires HTTPS or localhost in a WebAuthn-compatible browser'
                  : 'No built-in platform biometric sensor detected on this device'}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {isBiometricEnabled ? (
              <>
                <button
                  type="button"
                  disabled={biometricBusy}
                  onClick={handleTestBiometric}
                  className="rounded-xl border border-slate-200 px-3 py-2 text-xs font-bold text-slate-700 hover:bg-slate-50 disabled:opacity-50 dark:border-slate-700 dark:text-slate-200"
                >
                  {biometricBusy ? 'Checking...' : 'Test Sensor'}
                </button>
                <button
                  type="button"
                  disabled={biometricBusy}
                  onClick={handleDisableBiometric}
                  className="rounded-xl bg-red-50 px-3 py-2 text-xs font-bold text-red-600 hover:bg-red-100 disabled:opacity-50 dark:bg-red-950/50 dark:text-red-300"
                >
                  Disable
                </button>
              </>
            ) : (
              <button
                type="button"
                disabled={biometricBusy || !biometricSupported}
                onClick={handleEnableBiometric}
                className="rounded-xl bg-[#00875A] px-3.5 py-2 text-xs font-bold text-white hover:bg-[#005A3C] disabled:opacity-50"
              >
                {biometricBusy ? 'Enrolling...' : 'Enable Biometrics'}
              </button>
            )}
          </div>
        </div>

        <div className="flex items-center justify-between p-4">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-200">
              {darkMode ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
            </div>
            <div>
              <p className="text-sm font-bold text-slate-900 dark:text-white">Appearance</p>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                {darkMode ? 'Navy Dark Mode' : 'Crisp Light Mode'}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={toggleDarkMode}
            className="rounded-xl border border-slate-200 px-3.5 py-2 text-xs font-bold text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-200"
          >
            Switch to {darkMode ? 'Light' : 'Dark'}
          </button>
        </div>

        <button
          type="button"
          onClick={openWithdrawModal}
          className="flex w-full items-center justify-between p-4 text-left hover:bg-slate-50 dark:hover:bg-slate-800/40"
        >
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-amber-500/15 text-amber-600">
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
          <span className="text-xs font-bold text-[#00875A]">Open</span>
        </button>

        <button
          type="button"
          onClick={() => setCurrentPage('developer')}
          className="flex w-full items-center justify-between p-4 text-left hover:bg-slate-50 dark:hover:bg-slate-800/40"
        >
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-cyan-500/15 text-cyan-600">
              <Terminal className="h-5 w-5" />
            </div>
            <div>
              <p className="text-sm font-bold text-slate-900 dark:text-white">
                Developer Hub &amp; API Keys
              </p>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Generate live secret keys &amp; configure webhooks
              </p>
            </div>
          </div>
          <span className="text-xs font-bold text-[#00875A]">Open</span>
        </button>

        <button
          type="button"
          onClick={() => setShowEmailModal(true)}
          className="flex w-full items-center justify-between p-4 text-left hover:bg-slate-50 dark:hover:bg-slate-800/40"
        >
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-500/15 text-indigo-600">
              <Mail className="h-5 w-5" />
            </div>
            <div>
              <p className="text-sm font-bold text-slate-900 dark:text-white">Change Email</p>
              <p className="text-xs text-slate-500 dark:text-slate-400">{profile.email}</p>
            </div>
          </div>
          <span className="text-xs font-bold text-[#00875A]">Update</span>
        </button>

        <button
          type="button"
          onClick={() => setShowPwModal(true)}
          className="flex w-full items-center justify-between p-4 text-left hover:bg-slate-50 dark:hover:bg-slate-800/40"
        >
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-violet-500/15 text-violet-600">
              <KeyRound className="h-5 w-5" />
            </div>
            <div>
              <p className="text-sm font-bold text-slate-900 dark:text-white">Update Password</p>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Change your sign-in password
              </p>
            </div>
          </div>
          <span className="text-xs font-bold text-[#00875A]">Update</span>
        </button>
      </div>

      {/* Sign Out & Danger Zone */}
      <div className="grid gap-3 sm:grid-cols-2">
        <button
          type="button"
          onClick={logout}
          className="flex items-center justify-center gap-2 rounded-2xl border border-slate-200 bg-white py-3.5 text-sm font-bold text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:bg-[#102A43] dark:text-white"
        >
          <LogOut className="h-4 w-4" /> Sign Out
        </button>

        <button
          type="button"
          onClick={() => setShowDeleteConfirm(true)}
          className="flex items-center justify-center gap-2 rounded-2xl border border-red-200 bg-red-50 py-3.5 text-sm font-bold text-red-600 hover:bg-red-100 dark:border-red-900/50 dark:bg-red-950/40 dark:text-red-300"
        >
          <Trash2 className="h-4 w-4" /> Delete Account
        </button>
      </div>

      {/* Change PIN Modal */}
      {showChangePin && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/65 p-4 backdrop-blur-sm">
          <form
            onSubmit={handleChangePin}
            className="w-full max-w-sm space-y-4 rounded-3xl border border-slate-200 bg-white p-6 dark:border-slate-700 dark:bg-[#102A43]"
          >
            <div className="flex items-center gap-2">
              <Shield className="h-5 w-5 text-[#00875A]" />
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
              className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-center font-mono text-lg font-bold tracking-[0.5em] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
            />
            <input
              type="password"
              inputMode="numeric"
              maxLength={4}
              value={newPin}
              onChange={(e) => setNewPin(e.target.value.replace(/\D/g, '').slice(0, 4))}
              placeholder="New 4-digit PIN"
              className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-center font-mono text-lg font-bold tracking-[0.5em] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
            />
            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => setShowChangePin(false)}
                className="flex-1 rounded-xl border border-slate-200 py-2.5 text-xs font-bold dark:border-slate-700 dark:text-slate-300"
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
            className="w-full max-w-sm space-y-4 rounded-3xl border border-slate-200 bg-white p-6 dark:border-slate-700 dark:bg-[#102A43]"
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
                className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
              />
            ) : (
              <input
                type="text"
                inputMode="numeric"
                required
                value={emailOtp}
                onChange={(e) => setEmailOtp(e.target.value.replace(/\D/g, ''))}
                placeholder="6-digit code"
                className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-center font-mono text-lg font-bold tracking-[0.4em] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
              />
            )}
            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => {
                  setShowEmailModal(false);
                  setEmailStep('input');
                }}
                className="flex-1 rounded-xl border border-slate-200 py-2.5 text-xs font-bold dark:border-slate-700 dark:text-slate-300"
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
            className="w-full max-w-sm space-y-4 rounded-3xl border border-slate-200 bg-white p-6 dark:border-slate-700 dark:bg-[#102A43]"
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
              className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
            />
            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => setShowPwModal(false)}
                className="flex-1 rounded-xl border border-slate-200 py-2.5 text-xs font-bold dark:border-slate-700 dark:text-slate-300"
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
          <div className="w-full max-w-sm rounded-3xl border border-slate-200 bg-white p-6 dark:border-slate-700 dark:bg-[#102A43]">
            <h3 className="text-base font-extrabold text-red-600">Delete Account Permanently?</h3>
            <p className="mt-2 text-xs text-slate-500 dark:text-slate-300">
              This permanently removes your profile, transaction history, and credentials. This
              action cannot be undone.
            </p>
            <div className="mt-5 flex gap-3">
              <button
                type="button"
                onClick={() => setShowDeleteConfirm(false)}
                className="flex-1 rounded-xl border border-slate-200 py-2.5 text-xs font-bold dark:border-slate-700 dark:text-slate-300"
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
