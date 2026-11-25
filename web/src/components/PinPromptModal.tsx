import React, { useEffect, useRef, useState } from 'react';
import { Fingerprint, Lock, ShieldCheck, X } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { setTransactionPin, verifyTransactionPin } from '../services/walletService';
import { authenticateWithBiometric } from '../services/webAuthnService';

interface PinDigitsInputProps {
  value: string;
  onChange: (val: string) => void;
  autoFocus?: boolean;
  onComplete?: (pin: string) => void;
  label?: string;
}

export const PinDigitsInput: React.FC<PinDigitsInputProps> = ({
  value,
  onChange,
  autoFocus = false,
  onComplete,
  label,
}) => {
  const inputRefs = useRef<(HTMLInputElement | null)[]>([]);
  const digits = Array.from({ length: 4 }, (_, i) => value[i] || '');

  useEffect(() => {
    if (autoFocus) {
      window.setTimeout(() => {
        inputRefs.current[0]?.focus();
      }, 40);
    }
  }, [autoFocus]);

  const handleChangeAt = (index: number, raw: string) => {
    const clean = raw.replace(/\D/g, '');
    if (!clean) {
      const next = value.slice(0, index) + value.slice(index + 1);
      onChange(next.slice(0, 4));
      return;
    }

    if (clean.length > 1) {
      // Handle paste or autofill
      const merged = clean.slice(0, 4);
      onChange(merged);
      const focusIdx = Math.min(3, merged.length - 1);
      inputRefs.current[focusIdx]?.focus();
      if (merged.length === 4 && onComplete) onComplete(merged);
      return;
    }

    const arr = [value[0] || '', value[1] || '', value[2] || '', value[3] || ''];
    arr[index] = clean;
    const nextPin = arr.join('').slice(0, 4);
    onChange(nextPin);

    if (index < 3) {
      inputRefs.current[index + 1]?.focus();
    } else if (nextPin.length === 4 && onComplete) {
      onComplete(nextPin);
    }
  };

  const handleKeyDown = (index: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace' && !digits[index] && index > 0) {
      const arr = [value[0] || '', value[1] || '', value[2] || '', value[3] || ''];
      arr[index - 1] = '';
      onChange(arr.join(''));
      inputRefs.current[index - 1]?.focus();
    }
  };

  return (
    <div>
      {label && (
        <label className="mb-2 block text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-300">
          {label}
        </label>
      )}
      <div className="grid grid-cols-4 gap-3">
        {digits.map((digit, i) => (
          <input
            key={i}
            ref={(el) => {
              inputRefs.current[i] = el;
            }}
            type="password"
            inputMode="numeric"
            maxLength={4}
            value={digit}
            onChange={(e) => handleChangeAt(i, e.target.value)}
            onKeyDown={(e) => handleKeyDown(i, e)}
            aria-label={`PIN digit ${i + 1}`}
            className={`h-14 w-full rounded-2xl border text-center font-mono text-2xl font-extrabold outline-none transition ${
              digit
                ? 'border-[#00875A] bg-[#E8F8F2]/60 text-slate-900 ring-2 ring-[#00875A]/20 dark:bg-emerald-950/50 dark:text-white'
                : 'border-slate-200 bg-slate-50 text-slate-900 focus:border-[#00875A] dark:border-[#334E68] dark:bg-[#0A192F]/80 dark:text-white'
            }`}
          />
        ))}
      </div>
    </div>
  );
};

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

  const submitPin = async (pinValue: string) => {
    if (pinValue.length !== 4) {
      setError('Enter your 4-digit Transaction PIN.');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const res = await verifyTransactionPin(pinValue);
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

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    await submitPin(pin);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/65 p-4 backdrop-blur-sm">
      <div className="w-full max-w-sm rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl dark:border-[#334E68] dark:bg-[#102A43]">
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
            <PinDigitsInput
              label="4-Digit Transaction PIN"
              value={pin}
              autoFocus={!isBiometricEnabled}
              onChange={(val) => {
                setError('');
                setPin(val);
              }}
            />

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
      <div className="w-full max-w-md rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl dark:border-[#334E68] dark:bg-[#102A43]">
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
          <PinDigitsInput
            label="Enter New 4-Digit PIN"
            value={pin}
            autoFocus
            onChange={(val) => {
              setError('');
              setPin(val);
            }}
          />

          <PinDigitsInput
            label="Confirm 4-Digit PIN"
            value={confirmPin}
            onChange={(val) => {
              setError('');
              setConfirmPin(val);
            }}
          />

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
