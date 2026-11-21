import React, { useEffect, useState } from 'react';
import { Phone, RefreshCw, Wifi } from 'lucide-react';
import { NetworkBadge } from '../components/BrandBadges';
import { useApp } from '../context/AppContext';
import {
  DATA_CATEGORIES,
  detectNetworkFromPhone,
  formatCompactNaira,
  formatNaira,
  NETWORK_LIST,
  NETWORKS,
  normalizeNigerianPhone,
} from '../data/catalog';
import { fetchLiveDataPlans } from '../services/vtuService';
import { DataCategoryId, DataPlanItem, NetworkId } from '../types/vtu';

export const DataPage: React.FC = () => {
  const { profile, isProcessingPurchase, runProtectedPurchase, showToast } = useApp();

  const [selectedNetwork, setSelectedNetwork] = useState<NetworkId>('MTN');
  const [selectedCategory, setSelectedCategory] = useState<DataCategoryId>('sme');
  const [phone, setPhone] = useState(profile?.phone || '');
  const [plans, setPlans] = useState<DataPlanItem[]>([]);
  const [loadingPlans, setLoadingPlans] = useState(true);
  const [planError, setPlanError] = useState('');
  const [selectedPlan, setSelectedPlan] = useState<DataPlanItem | null>(null);

  const loadPlans = async (net: NetworkId, cat: DataCategoryId) => {
    setLoadingPlans(true);
    setPlanError('');
    setSelectedPlan(null);
    try {
      const live = await fetchLiveDataPlans(net, cat);
      setPlans(live);
      if (live.length > 0) setSelectedPlan(live[0]);
    } catch (err) {
      setPlans([]);
      setPlanError(err instanceof Error ? err.message : 'Unable to load live data plans.');
    } finally {
      setLoadingPlans(false);
    }
  };

  useEffect(() => {
    loadPlans(selectedNetwork, selectedCategory);
  }, [selectedNetwork, selectedCategory]);

  const handlePhoneChange = (val: string) => {
    setPhone(val);
    const detected = detectNetworkFromPhone(val);
    if (detected) setSelectedNetwork(detected);
  };

  const handlePurchase = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedPlan) {
      showToast('Select a data plan first.', 'error');
      return;
    }
    const cleanPhone = normalizeNigerianPhone(phone);
    if (cleanPhone.length < 10 || cleanPhone.length > 14) {
      showToast('Enter a valid Nigerian phone number.', 'error');
      return;
    }

    runProtectedPurchase({
      serviceType: 'DATA',
      providerName: NETWORKS[selectedNetwork].displayName,
      serviceId: selectedPlan.serviceId,
      network: selectedNetwork,
      amount: selectedPlan.price,
      recipient: cleanPhone,
      planCode: selectedPlan.planCode,
      planLabel: selectedPlan.label,
      title: `${NETWORKS[selectedNetwork].displayName} ${selectedPlan.label}`,
    });
  };

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-2xl font-extrabold text-slate-900 dark:text-white">
            Buy Data Bundles
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-300">
            Live SME, Corporate, Gifting, and Daily/Weekly plans synced directly from server
          </p>
        </div>
        <div className="rounded-xl bg-[#E8F8F2] px-3 py-1.5 text-xs font-bold text-[#00875A] dark:bg-emerald-950/60 dark:text-emerald-300">
          Wallet: {formatNaira(profile?.walletBalance ?? 0)}
        </div>
      </div>

      {/* Network Selector */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {NETWORK_LIST.map((net) => {
          const active = selectedNetwork === net.id;
          return (
            <button
              key={net.id}
              type="button"
              onClick={() => setSelectedNetwork(net.id)}
              className={`flex items-center gap-3 rounded-2xl border p-3.5 transition ${
                active
                  ? 'border-[#00875A] bg-[#E8F8F2]/80 shadow-md dark:bg-emerald-950/40'
                  : 'border-slate-200 bg-white hover:border-slate-300 dark:border-slate-700 dark:bg-[#102A43]'
              }`}
            >
              <NetworkBadge network={net.id} size="md" />
              <span className="text-sm font-bold text-slate-900 dark:text-white">
                {net.displayName}
              </span>
            </button>
          );
        })}
      </div>

      {/* Phone Input */}
      <div className="rounded-2xl border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-[#102A43]">
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
            value={phone}
            onChange={(e) => handlePhoneChange(e.target.value)}
            placeholder="08031234567"
            className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-4 text-sm font-bold outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
          />
        </div>
      </div>

      {/* Category Tabs */}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap gap-2">
          {DATA_CATEGORIES.map((cat) => (
            <button
              key={cat.id}
              type="button"
              onClick={() => setSelectedCategory(cat.id)}
              className={`rounded-xl px-3.5 py-2 text-xs font-bold transition ${
                selectedCategory === cat.id
                  ? 'bg-[#00875A] text-white shadow-sm'
                  : 'border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:bg-[#102A43] dark:text-slate-300'
              }`}
            >
              {cat.label}
            </button>
          ))}
        </div>
        <button
          type="button"
          onClick={() => loadPlans(selectedNetwork, selectedCategory)}
          className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-bold text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:bg-[#102A43] dark:text-slate-200"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${loadingPlans ? 'animate-spin' : ''}`} />
          Refresh Prices
        </button>
      </div>

      {/* Live Plans Grid */}
      <form onSubmit={handlePurchase} className="space-y-5">
        {loadingPlans ? (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            {[1, 2, 3, 4, 5, 6].map((n) => (
              <div
                key={n}
                className="h-24 animate-pulse rounded-2xl border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-[#102A43]"
              />
            ))}
          </div>
        ) : planError ? (
          <div className="rounded-2xl border border-red-300 bg-red-50 p-6 text-center dark:border-red-900 dark:bg-red-950/40">
            <p className="text-sm font-bold text-red-700 dark:text-red-300">{planError}</p>
            <button
              type="button"
              onClick={() => loadPlans(selectedNetwork, selectedCategory)}
              className="mt-3 rounded-xl bg-red-600 px-4 py-2 text-xs font-bold text-white"
            >
              Retry
            </button>
          </div>
        ) : plans.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center dark:border-slate-700 dark:bg-[#102A43]">
            <Wifi className="mx-auto h-8 w-8 text-slate-400" />
            <p className="mt-2 text-sm font-bold text-slate-700 dark:text-slate-200">
              No active plans in this category right now
            </p>
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
              Try another category tab such as Corporate, Gifting, or Daily / Weekly.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            {plans.map((plan) => {
              const isSelected = selectedPlan?.id === plan.id;
              return (
                <button
                  key={plan.id}
                  type="button"
                  onClick={() => setSelectedPlan(plan)}
                  className={`flex flex-col items-start justify-between rounded-2xl border p-4 text-left transition ${
                    isSelected
                      ? 'border-[#00875A] bg-[#E8F8F2] shadow-md ring-2 ring-[#00875A]/20 dark:bg-emerald-950/50'
                      : 'border-slate-200 bg-white hover:border-[#00875A]/40 dark:border-slate-700 dark:bg-[#102A43]'
                  }`}
                >
                  <div>
                    <span className="inline-block rounded-md bg-slate-100 px-2 py-0.5 text-[10px] font-bold text-slate-600 dark:bg-slate-800 dark:text-slate-300">
                      {plan.validity}
                    </span>
                    <p className="mt-2 text-sm font-extrabold text-slate-900 dark:text-white">
                      {plan.label}
                    </p>
                  </div>
                  <p className="mt-3 text-base font-extrabold text-[#00875A] dark:text-emerald-400">
                    {formatCompactNaira(plan.price)}
                  </p>
                </button>
              );
            })}
          </div>
        )}

        {selectedPlan && (
          <div className="flex flex-col items-center justify-between gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-[#102A43] sm:flex-row">
            <div>
              <p className="text-xs text-slate-500 dark:text-slate-400">Selected Bundle</p>
              <p className="text-sm font-extrabold text-slate-900 dark:text-white">
                {NETWORKS[selectedNetwork].displayName} • {selectedPlan.label} (
                {selectedPlan.validity}) — {formatCompactNaira(selectedPlan.price)}
              </p>
            </div>
            <button
              type="submit"
              disabled={isProcessingPurchase}
              className="w-full rounded-xl bg-[#00875A] px-6 py-3 text-sm font-bold text-white shadow-md shadow-emerald-900/15 hover:bg-[#005A3C] disabled:opacity-50 sm:w-auto"
            >
              {isProcessingPurchase ? 'Processing...' : `Buy for ${formatCompactNaira(selectedPlan.price)}`}
            </button>
          </div>
        )}
      </form>
    </div>
  );
};
