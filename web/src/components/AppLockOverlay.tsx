import React, { useState } from 'react';
import { Fingerprint, Lock, LogOut } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { verifyTransactionPin } from '../services/walletService';
import { authenticateWithBiometric } from '../services/webAuthnService';
import { DanielVtuLogo } from './BrandBadges';
import { PinDigitsInput } from './PinPromptModal';

export const AppLockOverlay: React.FC = () => {
  const {
    profile,
    isAppLocked,
    hasTransactionPin,
    isBiometricEnabled,
    unlockApp,
    logout,
    showToast,
  } = useApp();

  const [showPinEntry, setShowPinEntry] = useState(!isBiometricEnabled);
  const [pin, setPin] = useState('');
  const [error, setError] = useState('');
  const [verifying, setVerifying] = useState(false);

  if (!isAppLocked || !profile) return null;

  const handleBiometricUnlock = async () => {
    setVerifying(true);
    setError('');
    try {
      const res = await authenticateWithBiometric(profile.id);
      if (!res.ok) {
        setError(res.error);
        return;
      }
      showToast('App unlocked with Biometrics', 'success');
      unlockApp();
    } finally {
      setVerifying(false);
    }
  };

  const handlePinSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (pin.length !== 4) {
      setError('Enter your 4-digit Transaction PIN.');
      return;
    }
    setVerifying(true);
    setError('');
    try {
      const res = await verifyTransactionPin(pin);
      if (!res.ok) {
        setError(res.error);
        setPin('');
        return;
      }
      setPin('');
      unlockApp();
    } finally {
      setVerifying(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[70] flex flex-col items-center justify-center bg-gradient-to-b from-[#0A192F] via-[#102A43] to-[#005A3C] p-6 text-white">
      <div className="w-full max-w-sm rounded-3xl border border-white/15 bg-[#0A192F]/80 p-7 text-center shadow-2xl backdrop-blur-md">
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-[#00875A]/20 ring-1 ring-emerald-400/35">
          <Lock className="h-8 w-8 text-[#00B074]" />
        </div>

        <div className="mt-4 flex items-center justify-center gap-2">
          <DanielVtuLogo size={26} />
          <h2 className="text-xl font-extrabold tracking-tight">Daniel VTU is Locked</h2>
        </div>
        <p className="mt-1.5 text-xs text-slate-300">
          Verify your identity to access your wallet and utility dashboard.
        </p>

        {isBiometricEnabled && (
          <button
            type="button"
            disabled={verifying}
            onClick={handleBiometricUnlock}
            className="mt-6 flex w-full items-center justify-center gap-2.5 rounded-2xl bg-[#00875A] py-3.5 text-sm font-bold text-white shadow-lg transition hover:bg-[#00B074] disabled:opacity-50"
          >
            <Fingerprint className="h-5 w-5" />
            {verifying ? 'Scanning...' : 'Unlock with Biometric'}
          </button>
        )}

        {hasTransactionPin && (
          <div className="mt-4">
            {!showPinEntry ? (
              <button
                type="button"
                onClick={() => setShowPinEntry(true)}
                className="w-full rounded-2xl border border-white/20 py-3 text-xs font-bold text-white/90 hover:bg-white/10"
              >
                Unlock with 4-Digit PIN
              </button>
            ) : (
              <form onSubmit={handlePinSubmit} className="mt-3 space-y-4 text-left">
                <PinDigitsInput
                  label="Enter 4-Digit Transaction PIN"
                  value={pin}
                  autoFocus={!isBiometricEnabled}
                  onChange={(v) => {
                    setError('');
                    setPin(v);
                  }}
                />
                <button
                  type="submit"
                  disabled={verifying || pin.length !== 4}
                  className="w-full rounded-xl bg-[#00875A] py-3 text-sm font-bold text-white hover:bg-[#00B074] disabled:opacity-50"
                >
                  {verifying ? 'Verifying...' : 'Unlock App'}
                </button>
              </form>
            )}
          </div>
        )}

        {!hasTransactionPin && !isBiometricEnabled && (
          <button
            type="button"
            onClick={unlockApp}
            className="mt-6 w-full rounded-xl bg-[#00875A] py-3 text-sm font-bold text-white"
          >
            Continue to Dashboard
          </button>
        )}

        {error && (
          <div className="mt-4 rounded-xl bg-red-950/70 px-3 py-2 text-xs font-medium text-red-200">
            {error}
          </div>
        )}

        <button
          type="button"
          onClick={logout}
          className="mt-5 inline-flex items-center gap-1.5 text-xs font-bold text-slate-300 hover:text-white"
        >
          <LogOut className="h-3.5 w-3.5" /> Sign Out Instead
        </button>
      </div>
    </div>
  );
};
