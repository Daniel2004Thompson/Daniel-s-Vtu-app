import React, { useState } from 'react';
import { Fingerprint, Lock, ShieldCheck, X } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { setTransactionPin, verifyTransactionPin } from '../services/walletService';
import { authenticateWithBiometric } from '../services/webAuthnService';

export const PinAuthorizeModal: React.FC = () => {
  const {
    profile,
    hasTransactionPin,
    isBiometricEnabled,
    pendingPinRequest,
    clearPendingPinRequest,
    showToast,
  } = useApp();
  const [pin, setPin] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [biometricLoading, setBiometricLoading] = useState(false);

  if (!pendingPinRequest) return null;

  const handleBiometricAuthorize = async () => {
    if (!profile?.id) return;
    setBiometricLoading(true);
    setError('');
    try {
      const res = await authenticateWithBiometric(profile.id);
      if (!res.ok) {
        setError(res.error);
        return;
      }
      const callback = pendingPinRequest.onAuthorized;
      clearPendingPinRequest();
      setPin('');
      showToast('Biometric authentication verified!', 'success');
      await callback();
    } finally {
      setBiometricLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (pin.length !== 4) {
      setError('Enter your 4-digit Transaction PIN.');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const res = await verifyTransactionPin(pin);
      if (!res.ok) {
        setError(res.error);
        setPin('');
        return;
      }
      const callback = pendingPinRequest.onAuthorized;
      clearPendingPinRequest();
      setPin('');
      await callback();
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/65 p-4 backdrop-blur-sm">
      <div className="w-full max-w-sm rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl dark:border-slate-700 dark:bg-[#102A43]">
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-[#E8F8F2] text-[#00875A] dark:bg-emerald-950 dark:text-emerald-400">
              <ShieldCheck className="h-6 w-6" />
            </div>
            <div>
              <h3 className="text-base font-bold text-slate-900 dark:text-white">
                {pendingPinRequest.title}
              </h3>
              <p className="text-xs text-slate-500 dark:text-slate-300">
                {pendingPinRequest.subtitle}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => {
              setPin('');
              setError('');
              clearPendingPinRequest();
            }}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-800"
            aria-label="Close"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {isBiometricEnabled && (
          <div className="mt-5">
            <button
              type="button"
              disabled={biometricLoading || loading}
              onClick={handleBiometricAuthorize}
              className="flex w-full items-center justify-center gap-2.5 rounded-2xl border border-[#00875A]/40 bg-[#E8F8F2] py-3.5 text-sm font-bold text-[#005A3C] transition hover:bg-emerald-100 disabled:opacity-50 dark:bg-emerald-950/60 dark:text-emerald-300 dark:hover:bg-emerald-950"
            >
              <Fingerprint className={`h-5 w-5 ${biometricLoading ? 'animate-pulse' : ''}`} />
              {biometricLoading ? 'Scanning Biometric Sensor...' : 'Authorize with Fingerprint / Face ID'}
            </button>

            {hasTransactionPin && (
              <div className="my-4 flex items-center gap-3 text-[11px] font-semibold uppercase tracking-wider text-slate-400">
                <span className="h-px flex-1 bg-slate-200 dark:bg-slate-700" />
                or enter 4-digit PIN
                <span className="h-px flex-1 bg-slate-200 dark:bg-slate-700" />
              </div>
            )}
          </div>
        )}

        {hasTransactionPin && (
          <form onSubmit={handleSubmit} className={`${isBiometricEnabled ? '' : 'mt-5'} space-y-4`}>
            <div>
              <label className="mb-1.5 block text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-300">
                4-Digit Transaction PIN
              </label>
              <input
                type="password"
                inputMode="numeric"
                maxLength={4}
                autoFocus={!isBiometricEnabled}
                value={pin}
                onChange={(e) => {
                  setError('');
                  setPin(e.target.value.replace(/\D/g, '').slice(0, 4));
                }}
                placeholder="••••"
                className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-center font-mono text-2xl font-bold tracking-[0.75em] text-slate-900 outline-none focus:border-[#00875A] focus:ring-2 focus:ring-[#00875A]/20 dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
              />
            </div>

            {error && (
              <div className="rounded-xl bg-red-50 px-3 py-2 text-xs font-medium text-red-600 dark:bg-red-950/50 dark:text-red-300">
                {error}
              </div>
            )}

            <div className="flex gap-3 pt-1">
              <button
                type="button"
                onClick={() => {
                  setPin('');
                  setError('');
                  clearPendingPinRequest();
                }}
                className="flex-1 rounded-xl border border-slate-200 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={loading || biometricLoading || pin.length !== 4}
                className="flex-1 rounded-xl bg-[#00875A] py-2.5 text-sm font-bold text-white shadow-md shadow-emerald-900/15 transition hover:bg-[#005A3C] disabled:opacity-50"
              >
                {loading ? 'Verifying...' : 'Authorize'}
              </button>
            </div>
          </form>
        )}

        {!hasTransactionPin && error && (
          <div className="mt-3 rounded-xl bg-red-50 px-3 py-2 text-xs font-medium text-red-600 dark:bg-red-950/50 dark:text-red-300">
            {error}
          </div>
        )}
      </div>
    </div>
  );
};

export const SetTransactionPinModal: React.FC = () => {
  const { showSetPinModal, closeSetPinModal, setHasTransactionPin, showToast } = useApp();
  const [pin, setPin] = useState('');
  const [confirmPin, setConfirmPin] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  if (!showSetPinModal) return null;

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (pin.length !== 4) {
      setError('PIN must be exactly 4 digits.');
      return;
    }
    if (pin !== confirmPin) {
      setError('PINs do not match.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      const res = await setTransactionPin(pin);
      if (!res.ok) {
        setError(res.error);
        return;
      }
      setHasTransactionPin(true);
      setPin('');
      setConfirmPin('');
      closeSetPinModal();
      showToast('4-digit Transaction PIN created!', 'success');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/65 p-4 backdrop-blur-sm">
      <div className="w-full max-w-md rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl dark:border-slate-700 dark:bg-[#102A43]">
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-[#E8F8F2] text-[#00875A] dark:bg-emerald-950 dark:text-emerald-400">
              <Lock className="h-5 w-5" />
            </div>
            <div>
              <h3 className="text-lg font-bold text-slate-900 dark:text-white">
                Set Transaction PIN
              </h3>
              <p className="text-xs text-slate-500 dark:text-slate-300">
                Required before buying airtime, data, bills, or withdrawing funds.
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={closeSetPinModal}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <form onSubmit={handleSave} className="mt-5 space-y-4">
          <div>
            <label className="mb-1 block text-xs font-semibold text-slate-600 dark:text-slate-300">
              Enter New 4-Digit PIN
            </label>
            <input
              type="password"
              inputMode="numeric"
              maxLength={4}
              value={pin}
              onChange={(e) => setPin(e.target.value.replace(/\D/g, '').slice(0, 4))}
              placeholder="••••"
              className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-center font-mono text-xl font-bold tracking-[0.7em] outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-semibold text-slate-600 dark:text-slate-300">
              Confirm 4-Digit PIN
            </label>
            <input
              type="password"
              inputMode="numeric"
              maxLength={4}
              value={confirmPin}
              onChange={(e) => setConfirmPin(e.target.value.replace(/\D/g, '').slice(0, 4))}
              placeholder="••••"
              className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-center font-mono text-xl font-bold tracking-[0.7em] outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
            />
          </div>

          {error && (
            <div className="rounded-xl bg-red-50 px-3 py-2 text-xs font-medium text-red-600 dark:bg-red-950/50 dark:text-red-300">
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={saving || pin.length !== 4 || confirmPin.length !== 4}
            className="w-full rounded-xl bg-[#00875A] py-3 text-sm font-bold text-white shadow-md shadow-emerald-900/15 transition hover:bg-[#005A3C] disabled:opacity-50"
          >
            {saving ? 'Saving PIN...' : 'Save Transaction PIN'}
          </button>
        </form>
      </div>
    </div>
  );
};
