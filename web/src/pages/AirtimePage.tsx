import React, { useMemo, useState } from 'react';
import { History, Phone, ShieldCheck, Sparkles } from 'lucide-react';
import { NetworkBadge } from '../components/BrandBadges';
import { useApp } from '../context/AppContext';
import {
  detectNetworkFromPhone,
  formatCompactNaira,
  formatNaira,
  NETWORK_LIST,
  NETWORKS,
  normalizeNigerianPhone,
} from '../data/catalog';
import { NetworkId } from '../types/vtu';

const QUICK_AMOUNTS = [100, 200, 500, 1000, 2000, 5000];

export const AirtimePage: React.FC = () => {
  const {
    profile,
    transactions,
    airtimePricing,
    isProcessingPurchase,
    runProtectedPurchase,
    showToast,
  } = useApp();

  const [selectedNetwork, setSelectedNetwork] = useState<NetworkId>(
    () => detectNetworkFromPhone(profile?.phone || '') || 'MTN'
  );
  const [phone, setPhone] = useState(profile?.phone || '');
  const [amount, setAmount] = useState('500');

  const recentContacts = useMemo(() => {
    const seen = new Set<string>();
    const list: { phone: string; network: NetworkId | null }[] = [];
    for (const tx of transactions) {
      if (tx.serviceType === 'AIRTIME' && tx.recipient) {
        const normalized = normalizeNigerianPhone(tx.recipient);
        if (normalized.length >= 10 && !seen.has(normalized)) {
          seen.add(normalized);
          list.push({
            phone: normalized,
            network: detectNetworkFromPhone(normalized),
          });
          if (list.length >= 5) break;
        }
      }
    }
    return list;
  }, [transactions]);

  const handlePhoneChange = (val: string) => {
    setPhone(val);
    const detected = detectNetworkFromPhone(val);
    if (detected) setSelectedNetwork(detected);
  };

  const pricing = airtimePricing[selectedNetwork];
  const numericAmount = Number(amount) || 0;
  const discount = Math.round(((numericAmount * pricing.cashbackPercent) / 100) * 100) / 100;
  const payable = Math.max(0, Math.round((numericAmount - discount) * 100) / 100);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const cleanPhone = normalizeNigerianPhone(phone);
    if (cleanPhone.length < 10 || cleanPhone.length > 14) {
      showToast('Enter a valid Nigerian phone number.', 'error');
      return;
    }
    if (numericAmount < 50) {
      showToast('Minimum airtime purchase is ₦50.', 'error');
      return;
    }

    runProtectedPurchase({
      serviceType: 'AIRTIME',
      providerName: NETWORKS[selectedNetwork].displayName,
      serviceId: pricing.serviceId,
      network: selectedNetwork,
      amount: numericAmount,
      recipient: cleanPhone,
      title: `${NETWORKS[selectedNetwork].displayName} Airtime Top-up`,
    });
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-2xl font-extrabold text-slate-900 dark:text-white">
            Buy Airtime
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-300">
            Instant recharge with live cashback discount on every network
          </p>
        </div>
        <div className="rounded-xl bg-[#E8F8F2] px-3 py-1.5 text-xs font-bold text-[#00875A] dark:bg-emerald-950/60 dark:text-emerald-300">
          Wallet: {formatNaira(profile?.walletBalance ?? 0)}
        </div>
      </div>

      {/* Network Selection Cards */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {NETWORK_LIST.map((net) => {
          const isSelected = selectedNetwork === net.id;
          const pct = airtimePricing[net.id].cashbackPercent;
          return (
            <button
              key={net.id}
              type="button"
              onClick={() => setSelectedNetwork(net.id)}
              className={`relative flex flex-col items-center rounded-2xl border p-4 transition ${
                isSelected
                  ? 'border-[#00875A] bg-[#E8F8F2]/80 shadow-md dark:bg-emerald-950/40'
                  : 'border-slate-200 bg-white hover:border-slate-300 dark:border-slate-700 dark:bg-[#102A43]'
              }`}
            >
              <span className="absolute right-2 top-2 rounded-full bg-[#00875A] px-2 py-0.5 text-[10px] font-extrabold text-white">
                {pct}% Off
              </span>
              <NetworkBadge network={net.id} size="lg" />
              <span className="mt-2.5 text-sm font-bold text-slate-900 dark:text-white">
                {net.displayName}
              </span>
            </button>
          );
        })}
      </div>

      {/* Purchase Form */}
      <form
        onSubmit={handleSubmit}
        className="space-y-5 rounded-3xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-[#102A43]"
      >
        <div>
          <div className="mb-1.5 flex items-center justify-between">
            <label className="text-xs font-bold text-slate-700 dark:text-slate-200">
              Recipient Phone Number
            </label>
            {profile?.phone && (
              <button
                type="button"
                onClick={() => handlePhoneChange(profile.phone)}
                className="text-xs font-bold text-[#00875A] hover:underline"
              >
                Use My Number
              </button>
            )}
          </div>
          <div className="relative">
            <Phone className="absolute left-3.5 top-3.5 h-4 w-4 text-slate-400" />
            <input
              type="tel"
              required
              value={phone}
              onChange={(e) => handlePhoneChange(e.target.value)}
              placeholder="08031234567"
              className="w-full rounded-xl border border-slate-200 bg-slate-50 py-3 pl-10 pr-24 text-sm font-bold text-slate-900 outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
            />
            <span className="absolute right-3 top-2.5 inline-flex items-center gap-1 rounded-lg bg-slate-200/80 px-2 py-1 text-[10px] font-bold text-slate-700 dark:bg-slate-800 dark:text-slate-200">
              <NetworkBadge network={selectedNetwork} size="sm" />
              {NETWORKS[selectedNetwork].displayName}
            </span>
          </div>

          {recentContacts.length > 0 && (
            <div className="mt-3">
              <p className="mb-1.5 flex items-center gap-1 text-[11px] font-bold text-slate-500 dark:text-slate-400">
                <History className="h-3 w-3" /> Recent Contacts
              </p>
              <div className="flex flex-wrap gap-1.5">
                {recentContacts.map((c) => (
                  <button
                    key={c.phone}
                    type="button"
                    onClick={() => handlePhoneChange(c.phone)}
                    className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-[11px] font-bold text-slate-700 hover:border-[#00875A] hover:text-[#00875A] dark:border-[#334E68] dark:bg-[#0A192F] dark:text-slate-200"
                  >
                    {c.network && <NetworkBadge network={c.network} size="sm" />}
                    {c.phone}
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>

        <div>
          <label className="mb-2 block text-xs font-bold text-slate-700 dark:text-slate-200">
            Quick Amounts
          </label>
          <div className="grid grid-cols-3 gap-2 sm:grid-cols-6">
            {QUICK_AMOUNTS.map((amt) => (
              <button
                key={amt}
                type="button"
                onClick={() => setAmount(String(amt))}
                className={`rounded-xl border py-2 text-xs font-bold transition ${
                  amount === String(amt)
                    ? 'border-[#00875A] bg-[#00875A] text-white'
                    : 'border-slate-200 text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800'
                }`}
              >
                ₦{amt.toLocaleString('en-US')}
              </button>
            ))}
          </div>
        </div>

        <div>
          <label className="mb-1.5 block text-xs font-bold text-slate-700 dark:text-slate-200">
            Amount (₦)
          </label>
          <input
            type="number"
            min={50}
            required
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="Enter amount (min ₦50)"
            className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-lg font-extrabold text-slate-900 outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
          />
        </div>

        {numericAmount > 0 && (
          <div className="space-y-1.5 rounded-2xl border border-emerald-500/30 bg-[#E8F8F2] p-4 dark:bg-emerald-950/40">
            <div className="flex items-center justify-between text-xs font-semibold text-slate-700 dark:text-slate-200">
              <span>Airtime Value</span>
              <span>{formatCompactNaira(numericAmount)}</span>
            </div>
            <div className="flex items-center justify-between text-xs font-bold text-[#00875A] dark:text-emerald-300">
              <span className="inline-flex items-center gap-1">
                <Sparkles className="h-3.5 w-3.5" />
                {NETWORKS[selectedNetwork].displayName} Cashback ({pricing.cashbackPercent}%)
              </span>
              <span>-{formatCompactNaira(discount)}</span>
            </div>
            <div className="flex items-center justify-between border-t border-emerald-500/20 pt-2 text-sm font-extrabold text-slate-900 dark:text-white">
              <span>You Pay</span>
              <span className="text-[#00875A] dark:text-[#00B074]">
                {formatCompactNaira(payable)}
              </span>
            </div>
          </div>
        )}

        <button
          type="submit"
          disabled={isProcessingPurchase || numericAmount < 50}
          className="flex w-full items-center justify-center gap-2 rounded-xl bg-[#00875A] py-3.5 text-sm font-bold text-white shadow-lg shadow-emerald-900/15 transition hover:bg-[#005A3C] disabled:opacity-50"
        >
          <ShieldCheck className="h-4 w-4" />
          {isProcessingPurchase
            ? 'Processing Airtime...'
            : `Authorize & Pay ${formatCompactNaira(payable || numericAmount)}`}
        </button>
      </form>
    </div>
  );
};
