import React, { useState } from 'react';
import {
  Bell,
  Bolt,
  CheckCircle2,
  Home,
  Info,
  Landmark,
  Menu,
  Moon,
  Plus,
  Receipt,
  Shield,
  Smartphone,
  Sun,
  Terminal,
  Wifi,
  X,
  XCircle,
} from 'lucide-react';
import { AppLockOverlay } from './components/AppLockOverlay';
import { DanielVtuLogo } from './components/BrandBadges';
import { FundWalletModal } from './components/FundWalletModal';
import { PinAuthorizeModal, SetTransactionPinModal } from './components/PinPromptModal';
import { TransactionReceiptModal } from './components/TransactionReceiptModal';
import { UserAvatar } from './components/UserAvatar';
import { WithdrawModal } from './components/WithdrawModal';
import { AppPage, AppProvider, useApp } from './context/AppContext';
import { formatCompactNaira } from './data/catalog';
import { AirtimePage } from './pages/AirtimePage';
import { AuthPage } from './pages/AuthPage';
import { BillsPage } from './pages/BillsPage';
import { DataPage } from './pages/DataPage';
import { DeveloperHubPage } from './pages/DeveloperHubPage';
import { DynamicAccountPage } from './pages/DynamicAccountPage';
import { HomePage } from './pages/HomePage';
import { NotificationsPage } from './pages/NotificationsPage';
import { ProfileSecurityPage } from './pages/ProfileSecurityPage';
import { TransactionsPage } from './pages/TransactionsPage';

const NAV_ITEMS: { id: AppPage; label: string; icon: React.ComponentType<{ className?: string }> }[] = [
  { id: 'home', label: 'Home', icon: Home },
  { id: 'airtime', label: 'Airtime', icon: Smartphone },
  { id: 'data', label: 'Data', icon: Wifi },
  { id: 'bills', label: 'Bills', icon: Bolt },
  { id: 'dynamic', label: 'Dynamic Acct', icon: Landmark },
  { id: 'transactions', label: 'History', icon: Receipt },
  { id: 'developer', label: 'Developers', icon: Terminal },
  { id: 'security', label: 'Security', icon: Shield },
];

const MainShell: React.FC = () => {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const {
    isBooting,
    isAuthenticated,
    profile,
    currentPage,
    setCurrentPage,
    darkMode,
    toggleDarkMode,
    openFundModal,
    unreadNotificationCount,
    toasts,
    dismissToast,
  } = useApp();

  const handleNavigate = (page: AppPage) => {
    setCurrentPage(page);
    setMobileMenuOpen(false);
  };

  if (isBooting) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center bg-[#F7F9FC] dark:bg-[#0A192F]">
        <DanielVtuLogo size={56} />
        <p className="mt-4 text-sm font-bold text-slate-600 dark:text-slate-300">
          Loading Daniel VTU...
        </p>
      </div>
    );
  }

  const activeNavLabel =
    currentPage === 'notifications'
      ? 'Transaction Alerts'
      : NAV_ITEMS.find((n) => n.id === currentPage)?.label || 'Home';

  return (
    <>
      {!isAuthenticated ? (
        <AuthPage />
      ) : (
        <div className="min-h-screen bg-[#F7F9FC] dark:bg-[#0A192F] lg:flex">
          {/* Desktop Sidebar */}
          <aside className="hidden w-64 shrink-0 flex-col border-r border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-[#102A43] lg:flex">
            <div className="flex items-center gap-3 px-2">
              <DanielVtuLogo size={38} />
              <div>
                <p className="text-base font-extrabold text-slate-900 dark:text-white">
                  Daniel VTU
                </p>
                <p className="text-[11px] font-semibold text-[#00875A]">Instant Vending</p>
              </div>
            </div>

            <nav className="mt-8 flex-1 space-y-1.5">
              {NAV_ITEMS.map((item) => {
                const active = currentPage === item.id;
                return (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => handleNavigate(item.id)}
                    className={`flex w-full items-center gap-3 rounded-xl px-3.5 py-2.5 text-sm font-bold transition ${
                      active
                        ? 'bg-[#E8F8F2] text-[#00875A] dark:bg-emerald-950/70 dark:text-emerald-300'
                        : 'text-slate-600 hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-slate-800/60'
                    }`}
                  >
                    <item.icon className="h-4 w-4" />
                    {item.label}
                  </button>
                );
              })}
            </nav>

            <div className="rounded-2xl border border-emerald-500/25 bg-[#E8F8F2] p-4 dark:bg-emerald-950/40">
              <p className="text-[11px] font-semibold text-slate-600 dark:text-slate-300">
                Wallet Balance
              </p>
              <p className="mt-0.5 text-lg font-extrabold text-[#005A3C] dark:text-emerald-300">
                {formatCompactNaira(profile?.walletBalance ?? 0)}
              </p>
              <button
                type="button"
                onClick={() => openFundModal('dynamic')}
                className="mt-3 flex w-full items-center justify-center gap-1.5 rounded-xl bg-[#00875A] py-2 text-xs font-bold text-white hover:bg-[#005A3C]"
              >
                <Plus className="h-3.5 w-3.5" /> Add Money
              </button>
            </div>
          </aside>

          {/* Main Content Area */}
          <div className="flex min-h-screen flex-1 flex-col">
            {/* Top Bar */}
            <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/90 backdrop-blur-md dark:border-slate-800 dark:bg-[#102A43]/90">
              <div className="flex items-center justify-between px-4 py-3 sm:px-6">
                <div className="flex items-center gap-2.5 lg:hidden">
                  <button
                    type="button"
                    onClick={() => setMobileMenuOpen((o) => !o)}
                    className="rounded-xl border border-slate-200 p-2 text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800"
                    aria-label="Toggle navigation menu"
                  >
                    {mobileMenuOpen ? <X className="h-4 w-4" /> : <Menu className="h-4 w-4" />}
                  </button>
                  <DanielVtuLogo size={30} />
                  <span className="text-base font-extrabold text-slate-900 dark:text-white">
                    Daniel VTU
                  </span>
                </div>

                <div className="hidden text-xs font-semibold text-slate-500 dark:text-slate-300 lg:block">
                  {activeNavLabel}
                </div>

                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => openFundModal('dynamic')}
                    className="inline-flex items-center gap-1 rounded-xl bg-[#E8F8F2] px-3 py-1.5 text-xs font-extrabold text-[#00875A] dark:bg-emerald-950/60 dark:text-emerald-300 lg:hidden"
                  >
                    {formatCompactNaira(profile?.walletBalance ?? 0)}
                  </button>
                  <button
                    type="button"
                    onClick={() => handleNavigate('notifications')}
                    aria-label="Transaction Alerts"
                    className="relative rounded-xl border border-slate-200 p-2 text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800"
                  >
                    <Bell className="h-4 w-4" />
                    {unreadNotificationCount > 0 && (
                      <span className="absolute -right-1 -top-1 flex h-4 min-w-[16px] items-center justify-center rounded-full bg-red-500 px-1 text-[9px] font-extrabold text-white">
                        {unreadNotificationCount > 9 ? '9+' : unreadNotificationCount}
                      </span>
                    )}
                  </button>
                  <button
                    type="button"
                    onClick={toggleDarkMode}
                    className="rounded-xl border border-slate-200 p-2 text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800"
                    aria-label="Toggle theme"
                  >
                    {darkMode ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
                  </button>
                  <button
                    type="button"
                    onClick={() => handleNavigate('security')}
                    className="rounded-full transition hover:opacity-90"
                    title="Profile & Security"
                  >
                    <UserAvatar
                      avatarUrl={profile?.avatarUrl}
                      fullName={profile?.fullName}
                      sizeClass="h-9 w-9"
                      textClass="text-xs"
                    />
                  </button>
                </div>
              </div>

              {/* Responsive Website Menu Drawer (for < lg screens, replacing mobile bottom bar) */}
              {mobileMenuOpen && (
                <nav className="border-t border-slate-100 bg-white px-4 py-3 dark:border-slate-800 dark:bg-[#102A43] lg:hidden">
                  <div className="grid grid-cols-2 gap-1.5 sm:grid-cols-3">
                    {NAV_ITEMS.map((item) => {
                      const active = currentPage === item.id;
                      return (
                        <button
                          key={item.id}
                          type="button"
                          onClick={() => handleNavigate(item.id)}
                          className={`flex items-center gap-2 rounded-xl px-3 py-2.5 text-xs font-bold transition ${
                            active
                              ? 'bg-[#E8F8F2] text-[#00875A] dark:bg-emerald-950/70 dark:text-emerald-300'
                              : 'text-slate-600 hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-slate-800/60'
                          }`}
                        >
                          <item.icon className="h-4 w-4" />
                          {item.label}
                        </button>
                      );
                    })}
                  </div>
                </nav>
              )}
            </header>

            <main className="mx-auto w-full max-w-6xl flex-1 p-4 sm:p-6 lg:p-8">
              {currentPage === 'home' && <HomePage />}
              {currentPage === 'airtime' && <AirtimePage />}
              {currentPage === 'data' && <DataPage />}
              {currentPage === 'bills' && <BillsPage />}
              {currentPage === 'dynamic' && <DynamicAccountPage />}
              {currentPage === 'transactions' && <TransactionsPage />}
              {currentPage === 'notifications' && <NotificationsPage />}
              {currentPage === 'developer' && <DeveloperHubPage />}
              {currentPage === 'security' && <ProfileSecurityPage />}
            </main>

            <footer className="border-t border-slate-200 bg-white/60 px-6 py-4 text-center text-xs text-slate-500 dark:border-slate-800 dark:bg-[#102A43]/60 dark:text-slate-400">
              © {new Date().getFullYear()} Daniel VTU • Instant Airtime, Data, Utility Bills &amp; Developer APIs
            </footer>
          </div>

          {/* Modals & Overlays */}
          <PinAuthorizeModal />
          <SetTransactionPinModal />
          <TransactionReceiptModal />
          <FundWalletModal />
          <WithdrawModal />
          <AppLockOverlay />
        </div>
      )}

      {/* Global Toast Container */}
      <div className="fixed right-4 top-4 z-[60] flex w-full max-w-xs flex-col gap-2">
        {toasts.map((t) => (
          <div
            key={t.id}
            className={`flex items-start gap-2.5 rounded-2xl border p-3.5 text-xs font-semibold shadow-lg backdrop-blur-md ${
              t.type === 'success'
                ? 'border-emerald-500/30 bg-emerald-950/90 text-emerald-100'
                : t.type === 'error'
                ? 'border-red-500/30 bg-red-950/90 text-red-100'
                : 'border-slate-700 bg-slate-900/90 text-slate-100'
            }`}
          >
            {t.type === 'success' ? (
              <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-400" />
            ) : t.type === 'error' ? (
              <XCircle className="mt-0.5 h-4 w-4 shrink-0 text-red-400" />
            ) : (
              <Info className="mt-0.5 h-4 w-4 shrink-0 text-cyan-400" />
            )}
            <span className="flex-1">{t.text}</span>
            <button
              type="button"
              onClick={() => dismissToast(t.id)}
              className="text-white/60 hover:text-white"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          </div>
        ))}
      </div>
    </>
  );
};

export const App: React.FC = () => (
  <AppProvider>
    <MainShell />
  </AppProvider>
);

export default App;
