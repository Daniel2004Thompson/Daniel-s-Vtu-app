import React, { useState } from 'react';
import {
  ArrowLeft,
  CheckCircle2,
  Eye,
  EyeOff,
  KeyRound,
  Lock,
  Mail,
  Phone,
  RefreshCw,
  ShieldCheck,
  User,
  Zap,
} from 'lucide-react';
import { DanielVtuLogo, NetworkBadge } from '../components/BrandBadges';
import { useApp } from '../context/AppContext';
import { detectNetworkFromPhone, NETWORKS } from '../data/catalog';
import {
  requestPasswordReset,
  resendSignInOtp,
  signInStep1,
  signUpWithEmail,
  updatePassword,
  verifyPasswordResetOtp,
  verifySignInOtp,
  verifySignUpOtp,
} from '../services/authService';

type AuthMode =
  | 'signin'
  | 'signin_otp'
  | 'signup'
  | 'signup_otp'
  | 'forgot'
  | 'forgot_otp'
  | 'forgot_new_pw';

export const AuthPage: React.FC = () => {
  const { onAuthSessionEstablished, showToast } = useApp();
  const [mode, setMode] = useState<AuthMode>('signin');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [otpCode, setOtpCode] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const [error, setError] = useState('');
  const [infoBanner, setInfoBanner] = useState('');

  const detectedPhoneNet = phone ? detectNetworkFromPhone(phone) : null;
  const isDuplicateEmailError = error.toLowerCase().includes('already been registered');

  const resetMessages = () => {
    setError('');
    setInfoBanner('');
  };

  const handleSignInSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    resetMessages();
    if (!email.trim() || !password) {
      setError('Enter your email and password.');
      return;
    }
    setLoading(true);
    try {
      const res = await signInStep1(email, password);
      if (!res.ok) {
        setError(res.error);
        return;
      }
      setOtpCode('');
      setInfoBanner(`A 6-digit verification code has been sent to ${email.trim()}.`);
      setMode('signin_otp');
    } finally {
      setLoading(false);
    }
  };

  const handleVerifySignInOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    resetMessages();
    if (otpCode.trim().length < 4) {
      setError('Enter the verification code sent to your email.');
      return;
    }
    setLoading(true);
    try {
      const res = await verifySignInOtp(email, otpCode);
      if (!res.ok) {
        setError(res.error);
        return;
      }
      showToast('Signed in successfully!', 'success');
      await onAuthSessionEstablished();
    } finally {
      setLoading(false);
    }
  };

  const handleResendOtp = async () => {
    setResending(true);
    resetMessages();
    try {
      const res = await resendSignInOtp(email);
      if (!res.ok) {
        setError(res.error);
        return;
      }
      setInfoBanner(`A fresh 6-digit verification code has been sent to ${email.trim()}.`);
      showToast('Verification code resent!', 'info');
    } finally {
      setResending(false);
    }
  };

  const handleSignUpSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    resetMessages();
    if (!fullName.trim() || !phone.trim() || !email.trim() || password.length < 6) {
      setError('Fill in all fields (password must be at least 6 characters).');
      return;
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }
    setLoading(true);
    try {
      const res = await signUpWithEmail({ email, password, fullName, phone });
      if (!res.ok) {
        setError(res.error);
        return;
      }
      setOtpCode('');
      setInfoBanner(`Enter the 6-digit confirmation code sent to ${email.trim()}.`);
      setMode('signup_otp');
    } finally {
      setLoading(false);
    }
  };

  const handleVerifySignUpOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    resetMessages();
    if (otpCode.trim().length < 4) {
      setError('Enter the 6-digit confirmation code.');
      return;
    }
    setLoading(true);
    try {
      const res = await verifySignUpOtp(email, otpCode);
      if (!res.ok) {
        setError(res.error);
        return;
      }
      showToast('Account verified! Welcome to Daniel VTU.', 'success');
      await onAuthSessionEstablished();
    } finally {
      setLoading(false);
    }
  };

  const handleForgotEmail = async (e: React.FormEvent) => {
    e.preventDefault();
    resetMessages();
    if (!email.trim()) {
      setError('Enter your registered email address.');
      return;
    }
    setLoading(true);
    try {
      const res = await requestPasswordReset(email);
      if (!res.ok) {
        setError(res.error);
        return;
      }
      setOtpCode('');
      setInfoBanner(`Password reset code sent to ${email.trim()}.`);
      setMode('forgot_otp');
    } finally {
      setLoading(false);
    }
  };

  const handleForgotOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    resetMessages();
    if (otpCode.trim().length < 4) {
      setError('Enter the recovery code sent to your email.');
      return;
    }
    setLoading(true);
    try {
      const res = await verifyPasswordResetOtp(email, otpCode);
      if (!res.ok) {
        setError(res.error);
        return;
      }
      setMode('forgot_new_pw');
    } finally {
      setLoading(false);
    }
  };

  const handleSetNewPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    resetMessages();
    if (newPassword.length < 6) {
      setError('New password must be at least 6 characters.');
      return;
    }
    setLoading(true);
    try {
      const res = await updatePassword(newPassword);
      if (!res.ok) {
        setError(res.error);
        return;
      }
      showToast('Password updated! You are signed in.', 'success');
      await onAuthSessionEstablished();
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#F7F9FC] dark:bg-[#0A192F] lg:grid lg:grid-cols-2">
      {/* Left Brand Showcase Panel */}
      <div className="relative hidden flex-col justify-between overflow-hidden bg-gradient-to-br from-[#0A192F] via-[#0E3C39] to-[#005A3C] p-12 text-white lg:flex">
        <div className="pointer-events-none absolute -right-24 -top-24 h-96 w-96 rounded-full border border-emerald-400/20" />
        <div className="pointer-events-none absolute -right-12 -top-12 h-72 w-72 rounded-full border border-emerald-400/15" />

        <div className="relative flex items-center gap-3">
          <DanielVtuLogo size={44} />
          <div>
            <span className="text-xl font-extrabold tracking-tight">Daniel VTU</span>
            <p className="text-xs font-medium text-emerald-300">
              Web &amp; Mobile Utility Platform
            </p>
          </div>
        </div>

        <div className="relative max-w-lg space-y-6">
          <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-400/30 bg-emerald-500/15 px-3 py-1 text-xs font-bold text-emerald-300">
            <Zap className="h-3.5 w-3.5" /> Instant Delivery • Zero Service Delays
          </span>
          <h1 className="text-4xl font-extrabold leading-tight tracking-tight">
            Seamless Airtime, Data Bundles, Bills &amp; Developer APIs in One Place.
          </h1>
          <p className="text-sm leading-relaxed text-slate-300">
            Fund your wallet with dedicated permanent or dynamic virtual bank accounts, enjoy instant
            cashback on airtime, print prepaid electricity tokens, and vend data at wholesale SME
            prices.
          </p>

          <div className="grid grid-cols-2 gap-4 pt-2">
            {[
              { title: 'Instant Cashback', desc: 'Save on every MTN, Airtel, GLO & 9mobile top-up' },
              { title: '2-Step OTP + PIN', desc: 'Zero-trust account & server-verified PIN gate' },
              { title: 'Dedicated Bank Acct', desc: 'Instant bank transfer deposits with auto-sync' },
              { title: 'Developer APIs', desc: 'Generate live keys & webhooks in seconds' },
            ].map((item) => (
              <div
                key={item.title}
                className="rounded-2xl border border-white/10 bg-white/5 p-4 backdrop-blur-sm"
              >
                <div className="flex items-center gap-2 text-xs font-bold text-emerald-300">
                  <CheckCircle2 className="h-4 w-4" />
                  {item.title}
                </div>
                <p className="mt-1 text-xs text-slate-300">{item.desc}</p>
              </div>
            ))}
          </div>
        </div>

        <p className="relative text-xs text-slate-400">
          © {new Date().getFullYear()} Daniel VTU • Powered by Live Nigerian Telecom &amp; Utility
          Gateways
        </p>
      </div>

      {/* Right Auth Card */}
      <div className="flex min-h-screen items-center justify-center px-4 py-10 sm:px-8">
        <div className="w-full max-w-md rounded-3xl border border-slate-200 bg-white p-7 shadow-xl shadow-slate-200/60 dark:border-slate-800 dark:bg-[#102A43] dark:shadow-none sm:p-8">
          <div className="mb-6 flex items-center gap-3 lg:hidden">
            <DanielVtuLogo size={40} />
            <div>
              <h1 className="text-lg font-extrabold text-slate-900 dark:text-white">
                Daniel VTU
              </h1>
              <p className="text-xs text-slate-500 dark:text-slate-300">
                Airtime, Data &amp; Utility Bills
              </p>
            </div>
          </div>

          {(mode === 'signin_otp' ||
            mode === 'signup_otp' ||
            mode === 'forgot' ||
            mode === 'forgot_otp' ||
            mode === 'forgot_new_pw') && (
            <button
              type="button"
              onClick={() => {
                resetMessages();
                setMode(mode === 'signup_otp' ? 'signup' : 'signin');
              }}
              className="mb-4 inline-flex items-center gap-1.5 text-xs font-bold text-[#00875A] hover:underline"
            >
              <ArrowLeft className="h-3.5 w-3.5" />{' '}
              {mode === 'signin_otp'
                ? 'Change Email / Back'
                : mode === 'signup_otp'
                ? 'Back to Sign Up'
                : 'Back to Sign In'}
            </button>
          )}

          {mode === 'signin' && (
            <>
              <h2 className="text-2xl font-extrabold text-slate-900 dark:text-white">
                Welcome Back
              </h2>
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-300">
                Sign in with your password and email verification code.
              </p>

              <form onSubmit={handleSignInSubmit} className="mt-6 space-y-4">
                <div>
                  <label className="mb-1 block text-xs font-semibold text-slate-600 dark:text-slate-300">
                    Email Address
                  </label>
                  <div className="relative">
                    <Mail className="absolute left-3.5 top-3.5 h-4 w-4 text-slate-400" />
                    <input
                      type="email"
                      required
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      placeholder="you@example.com"
                      className="w-full rounded-xl border border-slate-200 bg-slate-50 py-3 pl-10 pr-4 text-sm font-medium outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
                    />
                  </div>
                </div>

                <div>
                  <div className="mb-1 flex items-center justify-between">
                    <label className="text-xs font-semibold text-slate-600 dark:text-slate-300">
                      Password
                    </label>
                    <button
                      type="button"
                      onClick={() => {
                        resetMessages();
                        setMode('forgot');
                      }}
                      className="text-xs font-bold text-[#00875A] hover:underline"
                    >
                      Forgot Password?
                    </button>
                  </div>
                  <div className="relative">
                    <Lock className="absolute left-3.5 top-3.5 h-4 w-4 text-slate-400" />
                    <input
                      type={showPassword ? 'text' : 'password'}
                      required
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="••••••••"
                      className="w-full rounded-xl border border-slate-200 bg-slate-50 py-3 pl-10 pr-10 text-sm font-medium outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword((s) => !s)}
                      aria-label={showPassword ? 'Hide password' : 'Show password'}
                      className="absolute right-3 top-3 text-slate-400 hover:text-slate-600"
                    >
                      {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                </div>

                {error && (
                  <div className="rounded-xl bg-red-50 px-3.5 py-2.5 text-xs font-medium text-red-600 dark:bg-red-950/50 dark:text-red-300">
                    {error}
                  </div>
                )}

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full rounded-xl bg-[#00875A] py-3 text-sm font-bold text-white shadow-md shadow-emerald-900/15 transition hover:bg-[#005A3C] disabled:opacity-50"
                >
                  {loading ? 'Checking Credentials...' : 'Continue'}
                </button>
              </form>

              <p className="mt-6 text-center text-xs text-slate-500 dark:text-slate-300">
                Don&apos;t have an account?{' '}
                <button
                  type="button"
                  onClick={() => {
                    resetMessages();
                    setMode('signup');
                  }}
                  className="font-bold text-[#00875A] hover:underline"
                >
                  Sign Up
                </button>
              </p>
            </>
          )}

          {(mode === 'signin_otp' || mode === 'signup_otp') && (
            <>
              <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-2xl bg-[#E8F8F2] text-[#00875A] dark:bg-emerald-950">
                <ShieldCheck className="h-6 w-6" />
              </div>
              <h2 className="text-xl font-extrabold text-slate-900 dark:text-white">
                Verify Email Code
              </h2>
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-300">
                {infoBanner || `Enter the 6-digit verification code sent to ${email}.`}
              </p>

              <form
                onSubmit={mode === 'signin_otp' ? handleVerifySignInOtp : handleVerifySignUpOtp}
                className="mt-5 space-y-4"
              >
                <input
                  type="text"
                  inputMode="numeric"
                  maxLength={8}
                  autoFocus
                  value={otpCode}
                  onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ''))}
                  placeholder="6-digit code"
                  className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-center font-mono text-xl font-bold tracking-[0.4em] outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
                />

                {error && (
                  <div className="rounded-xl bg-red-50 px-3.5 py-2.5 text-xs font-medium text-red-600 dark:bg-red-950/50 dark:text-red-300">
                    {error}
                  </div>
                )}

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full rounded-xl bg-[#00875A] py-3 text-sm font-bold text-white shadow-md shadow-emerald-900/15 hover:bg-[#005A3C] disabled:opacity-50"
                >
                  {loading ? 'Verifying...' : 'Verify & Continue'}
                </button>

                {mode === 'signin_otp' && (
                  <div className="flex items-center justify-between pt-1 text-xs">
                    <button
                      type="button"
                      disabled={resending}
                      onClick={handleResendOtp}
                      className="inline-flex items-center gap-1 font-bold text-[#00875A] hover:underline disabled:opacity-50"
                    >
                      <RefreshCw className={`h-3.5 w-3.5 ${resending ? 'animate-spin' : ''}`} />
                      {resending ? 'Sending...' : 'Resend Code'}
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        resetMessages();
                        setMode('signin');
                      }}
                      className="font-semibold text-slate-500 hover:underline dark:text-slate-300"
                    >
                      Change Email?
                    </button>
                  </div>
                )}
              </form>
            </>
          )}

          {mode === 'signup' && (
            <>
              <h2 className="text-2xl font-extrabold text-slate-900 dark:text-white">
                Create Account
              </h2>
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-300">
                Start buying airtime, SME data &amp; paying bills in seconds.
              </p>

              <form onSubmit={handleSignUpSubmit} className="mt-5 space-y-3.5">
                <div>
                  <label className="mb-1 block text-xs font-semibold text-slate-600 dark:text-slate-300">
                    Full Name
                  </label>
                  <div className="relative">
                    <User className="absolute left-3.5 top-3.5 h-4 w-4 text-slate-400" />
                    <input
                      type="text"
                      required
                      value={fullName}
                      onChange={(e) => setFullName(e.target.value)}
                      placeholder="Daniel Thompson"
                      className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-4 text-sm outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
                    />
                  </div>
                </div>

                <div>
                  <div className="mb-1 flex items-center justify-between">
                    <label className="text-xs font-semibold text-slate-600 dark:text-slate-300">
                      Phone Number
                    </label>
                    {detectedPhoneNet && (
                      <span
                        className="inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-[10px] font-bold"
                        style={{
                          backgroundColor: NETWORKS[detectedPhoneNet].brandColor,
                          color: NETWORKS[detectedPhoneNet].textColor,
                        }}
                      >
                        <NetworkBadge network={detectedPhoneNet} size="sm" />
                        {NETWORKS[detectedPhoneNet].displayName}
                      </span>
                    )}
                  </div>
                  <div className="relative">
                    <Phone className="absolute left-3.5 top-3.5 h-4 w-4 text-slate-400" />
                    <input
                      type="tel"
                      required
                      value={phone}
                      onChange={(e) => setPhone(e.target.value)}
                      placeholder="08031234567"
                      className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-4 text-sm outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
                    />
                  </div>
                </div>

                <div>
                  <label className="mb-1 block text-xs font-semibold text-slate-600 dark:text-slate-300">
                    Email Address
                  </label>
                  <div className="relative">
                    <Mail className="absolute left-3.5 top-3.5 h-4 w-4 text-slate-400" />
                    <input
                      type="email"
                      required
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      placeholder="you@example.com"
                      className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-4 text-sm outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
                    />
                  </div>
                </div>

                <div>
                  <label className="mb-1 block text-xs font-semibold text-slate-600 dark:text-slate-300">
                    Password (min 6 characters)
                  </label>
                  <div className="relative">
                    <Lock className="absolute left-3.5 top-3.5 h-4 w-4 text-slate-400" />
                    <input
                      type={showPassword ? 'text' : 'password'}
                      required
                      minLength={6}
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="••••••••"
                      className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-10 text-sm outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword((s) => !s)}
                      aria-label={showPassword ? 'Hide password' : 'Show password'}
                      className="absolute right-3 top-2.5 text-slate-400"
                    >
                      {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                </div>

                <div>
                  <label className="mb-1 block text-xs font-semibold text-slate-600 dark:text-slate-300">
                    Confirm Password
                  </label>
                  <div className="relative">
                    <Lock className="absolute left-3.5 top-3.5 h-4 w-4 text-slate-400" />
                    <input
                      type={showConfirmPassword ? 'text' : 'password'}
                      required
                      minLength={6}
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      placeholder="Re-enter password"
                      className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-10 text-sm outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
                    />
                    <button
                      type="button"
                      onClick={() => setShowConfirmPassword((s) => !s)}
                      aria-label={showConfirmPassword ? 'Hide confirm password' : 'Show confirm password'}
                      className="absolute right-3 top-2.5 text-slate-400"
                    >
                      {showConfirmPassword ? (
                        <EyeOff className="h-4 w-4" />
                      ) : (
                        <Eye className="h-4 w-4" />
                      )}
                    </button>
                  </div>
                  {confirmPassword && confirmPassword !== password && (
                    <p className="mt-1 text-[11px] font-semibold text-red-500">
                      Passwords do not match
                    </p>
                  )}
                </div>

                {error && (
                  <div className="rounded-xl bg-red-50 px-3.5 py-2.5 text-xs font-medium text-red-600 dark:bg-red-950/50 dark:text-red-300">
                    <p>{error}</p>
                    {isDuplicateEmailError && (
                      <button
                        type="button"
                        onClick={() => {
                          resetMessages();
                          setMode('signin');
                        }}
                        className="mt-1 font-bold underline"
                      >
                        Go to Sign In instead →
                      </button>
                    )}
                  </div>
                )}

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full rounded-xl bg-[#00875A] py-3 text-sm font-bold text-white shadow-md shadow-emerald-900/15 hover:bg-[#005A3C] disabled:opacity-50"
                >
                  {loading ? 'Creating Account...' : 'Create Account'}
                </button>
              </form>

              <p className="mt-5 text-center text-xs text-slate-500 dark:text-slate-300">
                Already have an account?{' '}
                <button
                  type="button"
                  onClick={() => {
                    resetMessages();
                    setMode('signin');
                  }}
                  className="font-bold text-[#00875A] hover:underline"
                >
                  Sign In
                </button>
              </p>
            </>
          )}

          {mode === 'forgot' && (
            <>
              <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-2xl bg-[#E8F8F2] text-[#00875A] dark:bg-emerald-950">
                <KeyRound className="h-6 w-6" />
              </div>
              <h2 className="text-xl font-extrabold text-slate-900 dark:text-white">
                Reset Password
              </h2>
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-300">
                Enter your email to receive a password reset confirmation code.
              </p>

              <form onSubmit={handleForgotEmail} className="mt-5 space-y-4">
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@example.com"
                  className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
                />
                {error && (
                  <div className="rounded-xl bg-red-50 px-3.5 py-2.5 text-xs font-medium text-red-600 dark:bg-red-950/50 dark:text-red-300">
                    {error}
                  </div>
                )}
                <button
                  type="submit"
                  disabled={loading}
                  className="w-full rounded-xl bg-[#00875A] py-3 text-sm font-bold text-white hover:bg-[#005A3C] disabled:opacity-50"
                >
                  {loading ? 'Sending Code...' : 'Send Reset Code'}
                </button>
              </form>
            </>
          )}

          {mode === 'forgot_otp' && (
            <>
              <h2 className="text-xl font-extrabold text-slate-900 dark:text-white">
                Enter Recovery Code
              </h2>
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-300">{infoBanner}</p>
              <form onSubmit={handleForgotOtp} className="mt-5 space-y-4">
                <input
                  type="text"
                  inputMode="numeric"
                  maxLength={8}
                  autoFocus
                  value={otpCode}
                  onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ''))}
                  placeholder="6-digit code"
                  className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-center font-mono text-xl font-bold tracking-[0.4em] outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
                />
                {error && (
                  <div className="rounded-xl bg-red-50 px-3.5 py-2.5 text-xs font-medium text-red-600 dark:bg-red-950/50 dark:text-red-300">
                    {error}
                  </div>
                )}
                <button
                  type="submit"
                  disabled={loading}
                  className="w-full rounded-xl bg-[#00875A] py-3 text-sm font-bold text-white hover:bg-[#005A3C] disabled:opacity-50"
                >
                  {loading ? 'Verifying...' : 'Verify Code'}
                </button>
              </form>
            </>
          )}

          {mode === 'forgot_new_pw' && (
            <>
              <h2 className="text-xl font-extrabold text-slate-900 dark:text-white">
                Choose New Password
              </h2>
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-300">
                Enter a strong new password (at least 6 characters).
              </p>
              <form onSubmit={handleSetNewPassword} className="mt-5 space-y-4">
                <div className="relative">
                  <Lock className="absolute left-3.5 top-3.5 h-4 w-4 text-slate-400" />
                  <input
                    type={showNewPassword ? 'text' : 'password'}
                    minLength={6}
                    required
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    placeholder="New password"
                    className="w-full rounded-xl border border-slate-200 bg-slate-50 py-3 pl-10 pr-10 text-sm outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
                  />
                  <button
                    type="button"
                    onClick={() => setShowNewPassword((s) => !s)}
                    aria-label={showNewPassword ? 'Hide password' : 'Show password'}
                    className="absolute right-3 top-3 text-slate-400"
                  >
                    {showNewPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
                {error && (
                  <div className="rounded-xl bg-red-50 px-3.5 py-2.5 text-xs font-medium text-red-600 dark:bg-red-950/50 dark:text-red-300">
                    {error}
                  </div>
                )}
                <button
                  type="submit"
                  disabled={loading}
                  className="w-full rounded-xl bg-[#00875A] py-3 text-sm font-bold text-white hover:bg-[#005A3C] disabled:opacity-50"
                >
                  {loading ? 'Updating Password...' : 'Save New Password'}
                </button>
              </form>
            </>
          )}
        </div>
      </div>
    </div>
  );
};
