import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { DEFAULT_AIRTIME_PRICING } from '../data/catalog';
import {
  getValidAccessToken,
  loadStoredSession,
  saveStoredSession,
} from '../lib/supabaseGateway';
import { signOutUser } from '../services/authService';
import { executeVtuPurchase, fetchLiveAirtimePricing } from '../services/vtuService';
import {
  checkHasTransactionPin,
  fetchUserProfileAndHistory,
  verifyFlutterwavePayment,
} from '../services/walletService';
import { hasBiometricEnabled } from '../services/webAuthnService';
import {
  AirtimePricing,
  NetworkId,
  PurchaseExecutionPayload,
  TransactionRecord,
  UserProfile,
} from '../types/vtu';

export type AppPage =
  | 'home'
  | 'airtime'
  | 'data'
  | 'bills'
  | 'transactions'
  | 'developer'
  | 'security';

export type BillsInitialTab = 'ELECTRICITY' | 'CABLE_TV' | 'EDUCATION';

export interface ToastMessage {
  id: string;
  type: 'success' | 'error' | 'info';
  text: string;
}

interface PendingPinRequest {
  title: string;
  subtitle: string;
  onAuthorized: () => Promise<void> | void;
}

interface AppContextValue {
  isBooting: boolean;
  isAuthenticated: boolean;
  profile: UserProfile | null;
  transactions: TransactionRecord[];
  airtimePricing: Record<NetworkId, AirtimePricing>;
  hasTransactionPin: boolean;
  isBiometricEnabled: boolean;
  currentPage: AppPage;
  billsTab: BillsInitialTab;
  darkMode: boolean;
  isRefreshingWallet: boolean;
  isProcessingPurchase: boolean;
  selectedReceipt: TransactionRecord | null;
  showFundModal: boolean;
  fundInitialTab: 'card' | 'dynamic' | 'permanent';
  showWithdrawModal: boolean;
  showSetPinModal: boolean;
  pendingPinRequest: PendingPinRequest | null;
  toasts: ToastMessage[];
  setCurrentPage: (page: AppPage) => void;
  navigateToBills: (tab: BillsInitialTab) => void;
  toggleDarkMode: () => void;
  showToast: (text: string, type?: 'success' | 'error' | 'info') => void;
  dismissToast: (id: string) => void;
  refreshUserData: (showSuccessToast?: boolean) => Promise<void>;
  onAuthSessionEstablished: () => Promise<void>;
  logout: () => Promise<void>;
  setSelectedReceipt: (tx: TransactionRecord | null) => void;
  openFundModal: (tab?: 'card' | 'dynamic' | 'permanent') => void;
  closeFundModal: () => void;
  openWithdrawModal: () => void;
  closeWithdrawModal: () => void;
  openSetPinModal: () => void;
  closeSetPinModal: () => void;
  setHasTransactionPin: (val: boolean) => void;
  setBiometricEnabled: (val: boolean) => void;
  requestPinAuthorization: (
    title: string,
    subtitle: string,
    onAuthorized: () => Promise<void> | void
  ) => void;
  clearPendingPinRequest: () => void;
  runProtectedPurchase: (payload: PurchaseExecutionPayload) => void;
}

const AppContext = createContext<AppContextValue | null>(null);

const THEME_KEY = 'daniel_vtu_dark_mode';

export const AppProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [isBooting, setIsBooting] = useState(true);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [transactions, setTransactions] = useState<TransactionRecord[]>([]);
  const [airtimePricing, setAirtimePricing] =
    useState<Record<NetworkId, AirtimePricing>>(DEFAULT_AIRTIME_PRICING);
  const [hasTransactionPin, setHasTransactionPin] = useState(false);
  const [isBiometricEnabled, setIsBiometricEnabled] = useState(false);

  const [currentPage, setCurrentPage] = useState<AppPage>('home');
  const [billsTab, setBillsTab] = useState<BillsInitialTab>('ELECTRICITY');
  const [darkMode, setDarkMode] = useState<boolean>(() => {
    try {
      return localStorage.getItem(THEME_KEY) === 'true';
    } catch {
      return false;
    }
  });

  const [isRefreshingWallet, setIsRefreshingWallet] = useState(false);
  const [isProcessingPurchase, setIsProcessingPurchase] = useState(false);
  const [selectedReceipt, setSelectedReceipt] = useState<TransactionRecord | null>(null);
  const [showFundModal, setShowFundModal] = useState(false);
  const [fundInitialTab, setFundInitialTab] = useState<'card' | 'dynamic' | 'permanent'>('card');
  const [showWithdrawModal, setShowWithdrawModal] = useState(false);
  const [showSetPinModal, setShowSetPinModal] = useState(false);
  const [pendingPinRequest, setPendingPinRequest] = useState<PendingPinRequest | null>(null);
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  const pollingRef = useRef<number | null>(null);

  useEffect(() => {
    const root = document.documentElement;
    if (darkMode) root.classList.add('dark');
    else root.classList.remove('dark');
    try {
      localStorage.setItem(THEME_KEY, String(darkMode));
    } catch {
      // ignore
    }
  }, [darkMode]);

  const dismissToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const showToast = useCallback(
    (text: string, type: 'success' | 'error' | 'info' = 'info') => {
      const id = `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
      setToasts((prev) => [...prev.slice(-3), { id, type, text }]);
      window.setTimeout(() => {
        dismissToast(id);
      }, 4500);
    },
    [dismissToast]
  );

  const refreshUserData = useCallback(
    async (showSuccessToast = false) => {
      setIsRefreshingWallet(true);
      try {
        const [{ profile: nextProfile, transactions: nextTx }, liveAirtime, pinExists] =
          await Promise.all([
            fetchUserProfileAndHistory(),
            fetchLiveAirtimePricing(),
            checkHasTransactionPin(),
          ]);

        if (nextProfile) {
          setProfile(nextProfile);
          setIsBiometricEnabled(hasBiometricEnabled(nextProfile.id));
          setTransactions((prev) => {
            if (nextTx.length === 0) return prev;
            const seen = new Set(nextTx.map((t) => t.reference));
            const merged = [...nextTx, ...prev.filter((p) => !seen.has(p.reference))];
            return merged.sort((a, b) => b.timestamp - a.timestamp);
          });
        }
        setAirtimePricing(liveAirtime);
        setHasTransactionPin(pinExists);

        if (showSuccessToast && nextProfile) {
          showToast(
            `Balance synced: ₦${nextProfile.walletBalance.toLocaleString('en-US', {
              minimumFractionDigits: 2,
              maximumFractionDigits: 2,
            })}`,
            'success'
          );
        }
      } catch {
        if (showSuccessToast) {
          showToast('Could not refresh wallet balance right now.', 'error');
        }
      } finally {
        setIsRefreshingWallet(false);
      }
    },
    [showToast]
  );

  const handleFlutterwaveCallback = useCallback(async () => {
    const params = new URLSearchParams(window.location.search);
    const txRef =
      params.get('tx_ref') || localStorage.getItem('daniel_vtu_pending_flw_ref') || '';
    const isVerifyFlag =
      params.get('payment_verify') === '1' || params.get('status') === 'successful';

    if (!txRef || !isVerifyFlag) return;

    localStorage.removeItem('daniel_vtu_pending_flw_ref');
    window.history.replaceState({}, document.title, window.location.pathname);

    showToast('Verifying Flutterwave payment...', 'info');
    const result = await verifyFlutterwavePayment(txRef);
    if (result.ok) {
      showToast(
        `Wallet credited with ₦${result.amount.toLocaleString('en-US')}!`,
        'success'
      );
      await refreshUserData(false);
    } else {
      showToast(result.message || 'Payment verification pending.', 'info');
    }
  }, [refreshUserData, showToast]);

  const onAuthSessionEstablished = useCallback(async () => {
    setIsAuthenticated(true);
    await refreshUserData(false);
    const pinExists = await checkHasTransactionPin();
    setHasTransactionPin(pinExists);
    if (!pinExists) {
      setShowSetPinModal(true);
    }
    await handleFlutterwaveCallback();
  }, [handleFlutterwaveCallback, refreshUserData]);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const stored = loadStoredSession();
        if (stored) {
          const token = await getValidAccessToken();
          if (token && active) {
            await onAuthSessionEstablished();
          } else {
            saveStoredSession(null);
          }
        }
      } finally {
        if (active) setIsBooting(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [onAuthSessionEstablished]);

  // Background wallet balance sync every 12 seconds when authenticated
  useEffect(() => {
    if (!isAuthenticated) {
      if (pollingRef.current) {
        window.clearInterval(pollingRef.current);
        pollingRef.current = null;
      }
      return;
    }

    pollingRef.current = window.setInterval(() => {
      fetchUserProfileAndHistory()
        .then(({ profile: nextProfile, transactions: nextTx }) => {
          if (nextProfile) {
            setProfile((prev) =>
              prev ? { ...prev, ...nextProfile } : nextProfile
            );
          }
          if (nextTx.length > 0) {
            setTransactions((prev) => {
              const seen = new Set(nextTx.map((t) => t.reference));
              return [...nextTx, ...prev.filter((p) => !seen.has(p.reference))].sort(
                (a, b) => b.timestamp - a.timestamp
              );
            });
          }
        })
        .catch(() => {});
    }, 12000);

    return () => {
      if (pollingRef.current) {
        window.clearInterval(pollingRef.current);
        pollingRef.current = null;
      }
    };
  }, [isAuthenticated]);

  const logout = useCallback(async () => {
    await signOutUser();
    setIsAuthenticated(false);
    setProfile(null);
    setTransactions([]);
    setHasTransactionPin(false);
    setIsBiometricEnabled(false);
    setPendingPinRequest(null);
    setSelectedReceipt(null);
    setCurrentPage('home');
  }, []);

  const navigateToBills = useCallback((tab: BillsInitialTab) => {
    setBillsTab(tab);
    setCurrentPage('bills');
  }, []);

  const requestPinAuthorization = useCallback(
    (title: string, subtitle: string, onAuthorized: () => Promise<void> | void) => {
      if (!hasTransactionPin && !isBiometricEnabled) {
        showToast('Please create your 4-digit Transaction PIN or enable Biometrics first.', 'info');
        setShowSetPinModal(true);
        return;
      }
      setPendingPinRequest({ title, subtitle, onAuthorized });
    },
    [hasTransactionPin, isBiometricEnabled, showToast]
  );

  const runProtectedPurchase = useCallback(
    (payload: PurchaseExecutionPayload) => {
      if (!profile) {
        showToast('Please wait for your profile to finish loading.', 'error');
        return;
      }
      if (profile.walletBalance < payload.amount) {
        showToast('Insufficient wallet balance. Please fund your wallet.', 'error');
        return;
      }

      requestPinAuthorization(
        `Authorize ${payload.title}`,
        `Amount: ₦${payload.amount.toLocaleString('en-US', { minimumFractionDigits: 2 })} • ${payload.recipient}`,
        async () => {
          setIsProcessingPurchase(true);
          try {
            const result = await executeVtuPurchase({
              userId: profile.id,
              email: profile.email,
              currentDashboardBalance: profile.walletBalance,
              payload,
            });

            setTransactions((prev) => [result.transaction, ...prev]);
            setSelectedReceipt(result.transaction);

            if (typeof result.newBalance === 'number') {
              setProfile((prev) =>
                prev ? { ...prev, walletBalance: result.newBalance! } : prev
              );
            }

            if (result.success) {
              showToast(result.message, 'success');
            } else if (result.pending) {
              showToast('Purchase is being confirmed.', 'info');
            } else {
              showToast(result.message || 'Purchase failed', 'error');
            }

            await refreshUserData(false);
          } catch (err) {
            showToast(
              err instanceof Error ? err.message : 'Purchase failed. Please try again.',
              'error'
            );
          } finally {
            setIsProcessingPurchase(false);
          }
        }
      );
    },
    [profile, refreshUserData, requestPinAuthorization, showToast]
  );

  return (
    <AppContext.Provider
      value={{
        isBooting,
        isAuthenticated,
        profile,
        transactions,
        airtimePricing,
        hasTransactionPin,
        isBiometricEnabled,
        currentPage,
        billsTab,
        darkMode,
        isRefreshingWallet,
        isProcessingPurchase,
        selectedReceipt,
        showFundModal,
        fundInitialTab,
        showWithdrawModal,
        showSetPinModal,
        pendingPinRequest,
        toasts,
        setCurrentPage,
        navigateToBills,
        toggleDarkMode: () => setDarkMode((d) => !d),
        showToast,
        dismissToast,
        refreshUserData,
        onAuthSessionEstablished,
        logout,
        setSelectedReceipt,
        openFundModal: (tab = 'card') => {
          setFundInitialTab(tab);
          setShowFundModal(true);
        },
        closeFundModal: () => setShowFundModal(false),
        openWithdrawModal: () => setShowWithdrawModal(true),
        closeWithdrawModal: () => setShowWithdrawModal(false),
        openSetPinModal: () => setShowSetPinModal(true),
        closeSetPinModal: () => setShowSetPinModal(false),
        setHasTransactionPin,
        setBiometricEnabled: setIsBiometricEnabled,
        requestPinAuthorization,
        clearPendingPinRequest: () => setPendingPinRequest(null),
        runProtectedPurchase,
      }}
    >
      {children}
    </AppContext.Provider>
  );
};

export function useApp(): AppContextValue {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp must be used inside AppProvider');
  return ctx;
}
