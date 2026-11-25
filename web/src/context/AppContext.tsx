import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { DEFAULT_AIRTIME_PRICING } from '../data/catalog';
import {
  getValidAccessToken,
  loadStoredSession,
  saveStoredSession,
  SESSION_EXPIRED_EVENT,
} from '../lib/supabaseGateway';
import { signOutUser } from '../services/authService';
import { executeVtuPurchase, fetchLiveAirtimePricing } from '../services/vtuService';
import {
  checkHasTransactionPin,
  fetchUserProfileAndHistory,
  markAllTransactionsCleared,
  markTransactionDeleted,
  resetSessionAccountCaches,
} from '../services/walletService';
import { hasBiometricEnabled } from '../services/webAuthnService';
import {
  AirtimePricing,
  AppPage,
  DynamicAccountInfo,
  FundTab,
  InAppNotification,
  NetworkId,
  PurchaseExecutionPayload,
  TransactionRecord,
  UserProfile,
} from '../types/vtu';

export type { AppPage, FundTab };

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
  isAppLocked: boolean;
  profile: UserProfile | null;
  transactions: TransactionRecord[];
  notifications: InAppNotification[];
  unreadNotificationCount: number;
  airtimePricing: Record<NetworkId, AirtimePricing>;
  hasTransactionPin: boolean;
  isBiometricEnabled: boolean;
  isAppLockEnabled: boolean;
  isNotificationsEnabled: boolean;
  currentPage: AppPage;
  billsTab: BillsInitialTab;
  darkMode: boolean;
  isRefreshingWallet: boolean;
  isProcessingPurchase: boolean;
  selectedReceipt: TransactionRecord | null;
  showFundModal: boolean;
  fundInitialTab: FundTab;
  showWithdrawModal: boolean;
  showSetPinModal: boolean;
  pendingPinRequest: PendingPinRequest | null;
  toasts: ToastMessage[];
  setCurrentPage: (page: AppPage) => void;
  navigateToBills: (tab: BillsInitialTab) => void;
  toggleDarkMode: () => void;
  setAppLockEnabled: (val: boolean) => void;
  lockAppNow: () => void;
  unlockApp: () => void;
  setNotificationsEnabled: (val: boolean) => void;
  pushNotification: (n: Omit<InAppNotification, 'id' | 'timestamp' | 'isRead'>) => void;
  markNotificationRead: (id: string) => void;
  markAllNotificationsRead: () => void;
  clearAllNotifications: () => void;
  deleteTransaction: (reference: string) => void;
  clearAllTransactions: () => void;
  showToast: (text: string, type?: 'success' | 'error' | 'info') => void;
  dismissToast: (id: string) => void;
  refreshUserData: (showSuccessToast?: boolean) => Promise<void>;
  updatePermanentAccountLocal: (accountNumber: string, bankName: string, accountName: string) => void;
  clearPermanentAccountLocal: () => void;
  updateDynamicAccountLocal: (account: DynamicAccountInfo) => void;
  updateAvatarUrlLocal: (avatarUrl?: string) => void;
  onAuthSessionEstablished: () => Promise<void>;
  logout: () => Promise<void>;
  setSelectedReceipt: (tx: TransactionRecord | null) => void;
  openFundModal: (tab?: FundTab) => void;
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
const NOTIF_STORAGE_PREFIX = 'daniel_vtu_notifications_list_';

function loadNotificationsForUser(userId: string): InAppNotification[] {
  if (!userId) return [];
  try {
    const raw = localStorage.getItem(`${NOTIF_STORAGE_PREFIX}${userId}`);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function saveNotificationsForUser(userId: string, list: InAppNotification[]): void {
  if (!userId) return;
  try {
    localStorage.setItem(`${NOTIF_STORAGE_PREFIX}${userId}`, JSON.stringify(list.slice(0, 60)));
  } catch {
    // ignore storage quota errors
  }
}

function mergeProfilePreservingSessionAccounts(
  prev: UserProfile | null,
  nextProfile: UserProfile
): UserProfile {
  if (!prev || prev.id !== nextProfile.id) return nextProfile;
  return {
    ...prev,
    ...nextProfile,
    avatarUrl: nextProfile.avatarUrl ?? prev.avatarUrl,
    virtualAccountNumber: nextProfile.virtualAccountNumber ?? prev.virtualAccountNumber,
    virtualBankName: nextProfile.virtualBankName ?? prev.virtualBankName,
    virtualAccountName: nextProfile.virtualAccountName ?? prev.virtualAccountName,
    dynamicAccountNumber: nextProfile.dynamicAccountNumber ?? prev.dynamicAccountNumber,
    dynamicBankName: nextProfile.dynamicBankName ?? prev.dynamicBankName,
    dynamicAccountAmount: nextProfile.dynamicAccountAmount ?? prev.dynamicAccountAmount,
    dynamicAccountNote: nextProfile.dynamicAccountNote ?? prev.dynamicAccountNote,
    dynamicAccountExpiresAt: nextProfile.dynamicAccountExpiresAt ?? prev.dynamicAccountExpiresAt,
  };
}

export const AppProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [isBooting, setIsBooting] = useState(true);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isAppLocked, setIsAppLocked] = useState(false);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [transactions, setTransactions] = useState<TransactionRecord[]>([]);
  const [notifications, setNotifications] = useState<InAppNotification[]>([]);
  const [airtimePricing, setAirtimePricing] =
    useState<Record<NetworkId, AirtimePricing>>(DEFAULT_AIRTIME_PRICING);
  const [hasTransactionPin, setHasTransactionPin] = useState(false);
  const [isBiometricEnabled, setIsBiometricEnabled] = useState(false);
  const [isAppLockEnabled, setIsAppLockEnabled] = useState<boolean>(() => {
    try {
      return localStorage.getItem('daniel_vtu_app_lock') === 'true';
    } catch {
      return false;
    }
  });
  const [isNotificationsEnabled, setIsNotificationsEnabled] = useState<boolean>(() => {
    try {
      return localStorage.getItem('daniel_vtu_notifications') !== 'false';
    } catch {
      return true;
    }
  });

  const [currentPage, setCurrentPage] = useState<AppPage>('home');
  const [billsTab, setBillsTab] = useState<BillsInitialTab>('ELECTRICITY');
  const [darkMode, setDarkMode] = useState<boolean>(() => {
    try {
      const saved = localStorage.getItem(THEME_KEY);
      return saved === null ? true : saved === 'true';
    } catch {
      return true;
    }
  });

  const [isRefreshingWallet, setIsRefreshingWallet] = useState(false);
  const [isProcessingPurchase, setIsProcessingPurchase] = useState(false);
  const [selectedReceipt, setSelectedReceipt] = useState<TransactionRecord | null>(null);
  const [showFundModal, setShowFundModal] = useState(false);
  const [fundInitialTab, setFundInitialTab] = useState<FundTab>('dynamic');
  const [showWithdrawModal, setShowWithdrawModal] = useState(false);
  const [showSetPinModal, setShowSetPinModal] = useState(false);
  const [pendingPinRequest, setPendingPinRequest] = useState<PendingPinRequest | null>(null);
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  const pollingRef = useRef<number | null>(null);
  const lastKnownBalanceRef = useRef<{ userId: string; balance: number } | null>(null);
  const notifEnabledRef = useRef(isNotificationsEnabled);
  notifEnabledRef.current = isNotificationsEnabled;

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

  const pushNotificationForUser = useCallback(
    (userId: string, item: Omit<InAppNotification, 'id' | 'timestamp' | 'isRead'>) => {
      if (!userId || !notifEnabledRef.current) return;
      const entry: InAppNotification = {
        ...item,
        id: `notif_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
        timestamp: Date.now(),
        isRead: false,
      };
      setNotifications((prev) => {
        const next = [entry, ...prev].slice(0, 60);
        saveNotificationsForUser(userId, next);
        return next;
      });
    },
    []
  );

  const pushNotification = useCallback(
    (item: Omit<InAppNotification, 'id' | 'timestamp' | 'isRead'>) => {
      if (profile?.id) {
        pushNotificationForUser(profile.id, item);
      }
    },
    [profile?.id, pushNotificationForUser]
  );

  const markNotificationRead = useCallback(
    (id: string) => {
      setNotifications((prev) => {
        const next = prev.map((n) => (n.id === id ? { ...n, isRead: true } : n));
        if (profile?.id) saveNotificationsForUser(profile.id, next);
        return next;
      });
    },
    [profile?.id]
  );

  const markAllNotificationsRead = useCallback(() => {
    setNotifications((prev) => {
      const next = prev.map((n) => ({ ...n, isRead: true }));
      if (profile?.id) saveNotificationsForUser(profile.id, next);
      return next;
    });
  }, [profile?.id]);

  const clearAllNotifications = useCallback(() => {
    setNotifications([]);
    if (profile?.id) saveNotificationsForUser(profile.id, []);
  }, [profile?.id]);

  const deleteTransaction = useCallback(
    (reference: string) => {
      if (!profile?.id) return;
      markTransactionDeleted(profile.id, reference);
      setTransactions((prev) => prev.filter((t) => t.reference !== reference));
      if (selectedReceipt?.reference === reference) {
        setSelectedReceipt(null);
      }
      showToast('Transaction deleted.', 'info');
    },
    [profile?.id, selectedReceipt?.reference, showToast]
  );

  const clearAllTransactions = useCallback(() => {
    if (!profile?.id) return;
    markAllTransactionsCleared(profile.id);
    setTransactions([]);
    setSelectedReceipt(null);
    showToast('Transaction history cleared.', 'info');
  }, [profile?.id, showToast]);

  const updatePermanentAccountLocal = useCallback(
    (accountNumber: string, bankName: string, accountName: string) => {
      setProfile((prev) =>
        prev
          ? {
              ...prev,
              ninVerified: true,
              virtualAccountNumber: accountNumber,
              virtualBankName: bankName || prev.virtualBankName || 'Flutterwave MFB',
              virtualAccountName: accountName || prev.virtualAccountName || prev.fullName,
            }
          : prev
      );
    },
    []
  );

  const clearPermanentAccountLocal = useCallback(() => {
    setProfile((prev) =>
      prev
        ? {
            ...prev,
            virtualAccountNumber: undefined,
            virtualBankName: undefined,
            virtualAccountName: undefined,
          }
        : prev
    );
  }, []);

  const updateDynamicAccountLocal = useCallback((account: DynamicAccountInfo) => {
    setProfile((prev) =>
      prev
        ? {
            ...prev,
            dynamicAccountNumber: account.accountNumber,
            dynamicBankName: account.bankName,
            dynamicAccountAmount: account.amount,
            dynamicAccountNote: account.note,
            dynamicAccountExpiresAt: account.expiresAt,
          }
        : prev
    );
  }, []);

  const updateAvatarUrlLocal = useCallback((avatarUrl?: string) => {
    setProfile((prev) => (prev ? { ...prev, avatarUrl } : prev));
  }, []);

  const checkAndNotifyDeposit = useCallback(
    (nextProfile: UserProfile) => {
      const prev = lastKnownBalanceRef.current;
      if (prev && prev.userId === nextProfile.id && nextProfile.walletBalance > prev.balance + 0.01) {
        const diff = nextProfile.walletBalance - prev.balance;
        const formatted = `₦${diff.toLocaleString('en-US', {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        })}`;
        showToast(`Wallet credited with ${formatted}!`, 'success');
        pushNotificationForUser(nextProfile.id, {
          title: 'Wallet Deposit Received',
          message: `Your Daniel VTU wallet was credited with ${formatted}.`,
          serviceType: 'WALLET_FUNDING',
          amount: diff,
        });
      }
      lastKnownBalanceRef.current = {
        userId: nextProfile.id,
        balance: nextProfile.walletBalance,
      };
    },
    [pushNotificationForUser, showToast]
  );

  const refreshUserData = useCallback(
    async (showSuccessToast = false) => {
      setIsRefreshingWallet(true);
      try {
        const [{ profile: nextProfile, transactions: nextTx }, liveAirtime, pinExists] =
          await Promise.all([
            fetchUserProfileAndHistory({ syncPermanentAccount: true }),
            fetchLiveAirtimePricing(),
            checkHasTransactionPin(),
          ]);

        if (nextProfile) {
          checkAndNotifyDeposit(nextProfile);
          setProfile((prev) => mergeProfilePreservingSessionAccounts(prev, nextProfile));
          setIsBiometricEnabled(hasBiometricEnabled(nextProfile.id));
          setNotifications((prev) =>
            prev.length > 0 ? prev : loadNotificationsForUser(nextProfile.id)
          );
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
    [checkAndNotifyDeposit, showToast]
  );

  const onAuthSessionEstablished = useCallback(async () => {
    resetSessionAccountCaches();
    lastKnownBalanceRef.current = null;
    setIsAuthenticated(true);
    await refreshUserData(false);
    const pinExists = await checkHasTransactionPin();
    setHasTransactionPin(pinExists);
    if (!pinExists) {
      setShowSetPinModal(true);
    }
  }, [refreshUserData]);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const stored = loadStoredSession();
        if (stored) {
          const token = await getValidAccessToken();
          if (token && active) {
            const lockSaved = localStorage.getItem('daniel_vtu_app_lock') === 'true';
            if (lockSaved) {
              setIsAppLocked(true);
            }
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

  // Listen for unrecoverable 401 session expiration events from protected calls
  useEffect(() => {
    const handler = () => {
      if (!isAuthenticated) return;
      saveStoredSession(null);
      resetSessionAccountCaches();
      setIsAuthenticated(false);
      setIsAppLocked(false);
      setProfile(null);
      setTransactions([]);
      setNotifications([]);
      setPendingPinRequest(null);
      showToast('Your session expired. Please sign in again.', 'info');
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, handler);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handler);
  }, [isAuthenticated, showToast]);

  // Background wallet balance sync every 12 seconds when authenticated (3s when on Dynamic Account page)
  useEffect(() => {
    if (!isAuthenticated) {
      if (pollingRef.current) {
        window.clearInterval(pollingRef.current);
        pollingRef.current = null;
      }
      return;
    }

    const intervalMs = currentPage === 'dynamic' ? 3500 : 12000;
    pollingRef.current = window.setInterval(() => {
      fetchUserProfileAndHistory({ syncPermanentAccount: false })
        .then(({ profile: nextProfile, transactions: nextTx }) => {
          if (nextProfile) {
            checkAndNotifyDeposit(nextProfile);
            setProfile((prev) => mergeProfilePreservingSessionAccounts(prev, nextProfile));
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
    }, intervalMs);

    return () => {
      if (pollingRef.current) {
        window.clearInterval(pollingRef.current);
        pollingRef.current = null;
      }
    };
  }, [isAuthenticated, currentPage, checkAndNotifyDeposit]);

  const logout = useCallback(async () => {
    await signOutUser();
    resetSessionAccountCaches();
    lastKnownBalanceRef.current = null;
    setIsAuthenticated(false);
    setIsAppLocked(false);
    setProfile(null);
    setTransactions([]);
    setNotifications([]);
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
              lastKnownBalanceRef.current = {
                userId: profile.id,
                balance: result.newBalance,
              };
              setProfile((prev) =>
                prev ? { ...prev, walletBalance: result.newBalance! } : prev
              );
            }

            if (result.success) {
              showToast(result.message, 'success');
              pushNotificationForUser(profile.id, {
                title: `${payload.title} Successful`,
                message: result.transaction.tokenOrDetails
                  ? `${result.message} • Token: ${result.transaction.tokenOrDetails}`
                  : `${result.message} (${payload.recipient})`,
                serviceType: payload.serviceType,
                amount: result.transaction.amount,
                reference: result.transaction.reference,
                token: result.transaction.tokenOrDetails,
              });
            } else if (result.pending) {
              showToast('Purchase is being confirmed.', 'info');
              pushNotificationForUser(profile.id, {
                title: `${payload.title} Pending`,
                message: 'Your order is being processed by the network.',
                serviceType: payload.serviceType,
                amount: result.transaction.amount,
                reference: result.transaction.reference,
              });
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
    [profile, pushNotificationForUser, refreshUserData, requestPinAuthorization, showToast]
  );

  const unreadNotificationCount = notifications.reduce((acc, n) => acc + (n.isRead ? 0 : 1), 0);

  return (
    <AppContext.Provider
      value={{
        isBooting,
        isAuthenticated,
        isAppLocked,
        profile,
        transactions,
        notifications,
        unreadNotificationCount,
        airtimePricing,
        hasTransactionPin,
        isBiometricEnabled,
        isAppLockEnabled,
        isNotificationsEnabled,
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
        setAppLockEnabled: (val: boolean) => {
          setIsAppLockEnabled(val);
          try {
            localStorage.setItem('daniel_vtu_app_lock', String(val));
          } catch {
            // ignore
          }
        },
        lockAppNow: () => setIsAppLocked(true),
        unlockApp: () => setIsAppLocked(false),
        setNotificationsEnabled: (val: boolean) => {
          setIsNotificationsEnabled(val);
          try {
            localStorage.setItem('daniel_vtu_notifications', String(val));
          } catch {
            // ignore
          }
        },
        pushNotification,
        markNotificationRead,
        markAllNotificationsRead,
        clearAllNotifications,
        deleteTransaction,
        clearAllTransactions,
        showToast,
        dismissToast,
        refreshUserData,
        updatePermanentAccountLocal,
        clearPermanentAccountLocal,
        updateDynamicAccountLocal,
        updateAvatarUrlLocal,
        onAuthSessionEstablished,
        logout,
        setSelectedReceipt,
        openFundModal: (tab = 'dynamic') => {
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
