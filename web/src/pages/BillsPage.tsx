import React, { useEffect, useMemo, useState } from 'react';
import { Bolt, GraduationCap, RefreshCw, Tv } from 'lucide-react';
import { ProviderDotBadge } from '../components/BrandBadges';
import { useApp } from '../context/AppContext';
import {
  CABLE_PROVIDERS,
  EDUCATION_EXAMS,
  ELECTRICITY_DISCOS,
  formatCompactNaira,
  formatNaira,
} from '../data/catalog';
import { fetchLiveCableProviders, fetchLiveEducationExams } from '../services/vtuService';
import { CableProvider, EducationExam } from '../types/vtu';

export const BillsPage: React.FC = () => {
  const { billsTab, profile, transactions, isProcessingPurchase, runProtectedPurchase, showToast } =
    useApp();
  const [activeTab, setActiveTab] = useState(billsTab);

  // Live catalog pricing
  const [cableProviders, setCableProviders] = useState<CableProvider[]>(CABLE_PROVIDERS);
  const [educationExams, setEducationExams] = useState<EducationExam[]>(EDUCATION_EXAMS);
  const [syncingPrices, setSyncingPrices] = useState(false);

  // Electricity state
  const [selectedDisco, setSelectedDisco] = useState(ELECTRICITY_DISCOS[0]);
  const [meterType, setMeterType] = useState<'prepaid' | 'postpaid'>('prepaid');
  const [meterNumber, setMeterNumber] = useState('');
  const [elecAmount, setElecAmount] = useState('2000');

  // Cable TV state
  const [selectedCableId, setSelectedCableId] = useState(CABLE_PROVIDERS[0].id);
  const [selectedBouquetId, setSelectedBouquetId] = useState(CABLE_PROVIDERS[0].bouquets[0].id);
  const [smartcardNumber, setSmartcardNumber] = useState('');

  // Education state
  const [selectedExamId, setSelectedExamId] = useState(EDUCATION_EXAMS[0].id);
  const [eduRecipient, setEduRecipient] = useState(profile?.phone || '');

  const selectedCable = useMemo(
    () => cableProviders.find((p) => p.id === selectedCableId) || cableProviders[0],
    [cableProviders, selectedCableId]
  );

  const selectedBouquet = useMemo(
    () =>
      selectedCable.bouquets.find((b) => b.id === selectedBouquetId) || selectedCable.bouquets[0],
    [selectedCable, selectedBouquetId]
  );

  const selectedExam = useMemo(
    () => educationExams.find((ex) => ex.id === selectedExamId) || educationExams[0],
    [educationExams, selectedExamId]
  );

  const recentMeters = useMemo(() => {
    const seen = new Set<string>();
    const list: { recipient: string; provider: string }[] = [];
    for (const tx of transactions) {
      if (tx.serviceType === 'ELECTRICITY' && tx.recipient && !seen.has(tx.recipient)) {
        seen.add(tx.recipient);
        list.push({ recipient: tx.recipient, provider: tx.provider });
        if (list.length >= 5) break;
      }
    }
    return list;
  }, [transactions]);

  const recentSmartcards = useMemo(() => {
    const seen = new Set<string>();
    const list: { recipient: string; provider: string }[] = [];
    for (const tx of transactions) {
      if (tx.serviceType === 'CABLE_TV' && tx.recipient && !seen.has(tx.recipient)) {
        seen.add(tx.recipient);
        list.push({ recipient: tx.recipient, provider: tx.provider });
        if (list.length >= 5) break;
      }
    }
    return list;
  }, [transactions]);

  const recentEduRecipients = useMemo(() => {
    const seen = new Set<string>();
    const list: string[] = [];
    for (const tx of transactions) {
      if (tx.serviceType === 'EDUCATION' && tx.recipient && !seen.has(tx.recipient)) {
        seen.add(tx.recipient);
        list.push(tx.recipient);
        if (list.length >= 5) break;
      }
    }
    return list;
  }, [transactions]);

  const syncLiveBillsCatalog = async (notify = false) => {
    setSyncingPrices(true);
    try {
      const [liveCable, liveEdu] = await Promise.all([
        fetchLiveCableProviders(CABLE_PROVIDERS),
        fetchLiveEducationExams(EDUCATION_EXAMS),
      ]);
      setCableProviders(liveCable);
      setEducationExams(liveEdu);
      if (notify) showToast('Live Cable TV & Education prices synced!', 'success');
    } finally {
      setSyncingPrices(false);
    }
  };

  useEffect(() => {
    syncLiveBillsCatalog(false);
  }, []);

  useEffect(() => {
    setActiveTab(billsTab);
  }, [billsTab]);

  const handleCableChange = (provider: CableProvider) => {
    setSelectedCableId(provider.id);
    if (provider.bouquets.length > 0) {
      setSelectedBouquetId(provider.bouquets[0].id);
    }
  };

  const electricityMin = Math.min(500, selectedDisco.minAmount);

  const handlePayElectricity = (e: React.FormEvent) => {
    e.preventDefault();
    const cleanMeter = meterNumber.trim();
    const amt = Number(elecAmount);
    if (cleanMeter.length < 8) {
      showToast('Enter a valid meter number (at least 8 digits).', 'error');
      return;
    }
    if (!Number.isFinite(amt) || amt < electricityMin) {
      showToast(`Minimum amount for ${selectedDisco.shortName} is ₦${electricityMin}.`, 'error');
      return;
    }

    runProtectedPurchase({
      serviceType: 'ELECTRICITY',
      providerName: selectedDisco.shortName,
      serviceId: selectedDisco.gsubzServiceId,
      amount: amt,
      recipient: cleanMeter,
      meterType,
      planCode: meterType,
      title: `${selectedDisco.shortName} ${meterType === 'prepaid' ? 'Prepaid' : 'Postpaid'} Power`,
    });
  };

  const handlePayCable = (e: React.FormEvent) => {
    e.preventDefault();
    const cleanIuc = smartcardNumber.trim();
    if (cleanIuc.length < 7) {
      showToast('Enter a valid Smartcard / IUC / Phone number.', 'error');
      return;
    }
    runProtectedPurchase({
      serviceType: 'CABLE_TV',
      providerName: selectedCable.name,
      serviceId: selectedCable.gsubzServiceId,
      amount: selectedBouquet.price,
      recipient: cleanIuc,
      planCode: selectedBouquet.planCode,
      planLabel: selectedBouquet.name,
      title: `${selectedCable.name} ${selectedBouquet.name}`,
    });
  };

  const handlePayEducation = (e: React.FormEvent) => {
    e.preventDefault();
    const cleanPhone = eduRecipient.trim();
    if (cleanPhone.length < 10) {
      showToast('Enter a valid recipient phone number or profile code.', 'error');
      return;
    }
    runProtectedPurchase({
      serviceType: 'EDUCATION',
      providerName: selectedExam.shortName,
      serviceId: selectedExam.gsubzServiceId,
      amount: selectedExam.price,
      recipient: cleanPhone,
      planCode: selectedExam.planCode,
      planLabel: selectedExam.name,
      title: `${selectedExam.shortName} e-PIN`,
    });
  };

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-2xl font-extrabold text-slate-900 dark:text-white">
            Bills &amp; Utilities
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-300">
            Instant electricity meter tokens, Cable TV bouquets, and Examination PINs
          </p>
        </div>
        <div className="flex items-center gap-2">
          {(activeTab === 'CABLE_TV' || activeTab === 'EDUCATION') && (
            <button
              type="button"
              onClick={() => syncLiveBillsCatalog(true)}
              className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-xs font-bold text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:bg-[#102A43] dark:text-slate-200"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${syncingPrices ? 'animate-spin' : ''}`} />
              Refresh Prices
            </button>
          )}
          <div className="rounded-xl bg-[#E8F8F2] px-3 py-1.5 text-xs font-bold text-[#00875A] dark:bg-emerald-950/60 dark:text-emerald-300">
            Wallet: {formatNaira(profile?.walletBalance ?? 0)}
          </div>
        </div>
      </div>

      {/* Category Tabs */}
      <div className="grid grid-cols-3 gap-2 rounded-2xl border border-slate-200 bg-white p-1.5 dark:border-slate-700 dark:bg-[#102A43]">
        {[
          { id: 'ELECTRICITY' as const, label: 'Electricity', icon: Bolt },
          { id: 'CABLE_TV' as const, label: 'Cable TV', icon: Tv },
          { id: 'EDUCATION' as const, label: 'Education PINs', icon: GraduationCap },
        ].map((tab) => {
          const active = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              type="button"
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center justify-center gap-2 rounded-xl py-2.5 text-xs font-bold transition ${
                active
                  ? 'bg-[#00875A] text-white shadow-sm'
                  : 'text-slate-600 hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-slate-800'
              }`}
            >
              <tab.icon className="h-4 w-4" />
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* ELECTRICITY */}
      {activeTab === 'ELECTRICITY' && (
        <form
          onSubmit={handlePayElectricity}
          className="space-y-5 rounded-3xl border border-slate-200 bg-white p-6 dark:border-slate-700 dark:bg-[#102A43]"
        >
          <div>
            <label className="mb-2 block text-xs font-bold text-slate-700 dark:text-slate-200">
              Select Distribution Company (DisCo)
            </label>
            <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-5">
              {ELECTRICITY_DISCOS.map((disco) => {
                const active = selectedDisco.id === disco.id;
                return (
                  <button
                    key={disco.id}
                    type="button"
                    onClick={() => setSelectedDisco(disco)}
                    className={`flex flex-col items-start rounded-2xl border p-3 text-left transition ${
                      active
                        ? 'border-[#00875A] bg-[#E8F8F2] dark:bg-emerald-950/50'
                        : 'border-slate-200 hover:border-slate-300 dark:border-slate-700'
                    }`}
                  >
                    <ProviderDotBadge label={disco.shortName} color={disco.brandColor} size="sm" />
                    <p className="mt-2 text-xs font-extrabold text-slate-900 dark:text-white">
                      {disco.shortName}
                    </p>
                    <p className="line-clamp-1 text-[10px] text-slate-500 dark:text-slate-400">
                      {disco.stateCoverage}
                    </p>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="mb-1.5 block text-xs font-bold text-slate-700 dark:text-slate-200">
                Meter Type
              </label>
              <div className="grid grid-cols-2 gap-2">
                {(['prepaid', 'postpaid'] as const).map((t) => (
                  <button
                    key={t}
                    type="button"
                    onClick={() => setMeterType(t)}
                    className={`rounded-xl border py-2.5 text-xs font-bold capitalize transition ${
                      meterType === t
                        ? 'border-[#00875A] bg-[#00875A] text-white'
                        : 'border-slate-200 text-slate-700 dark:border-slate-700 dark:text-slate-300'
                    }`}
                  >
                    {t}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <label className="mb-1.5 block text-xs font-bold text-slate-700 dark:text-slate-200">
                Meter Number
              </label>
              <input
                type="text"
                inputMode="numeric"
                required
                value={meterNumber}
                onChange={(e) => setMeterNumber(e.target.value.replace(/\D/g, ''))}
                placeholder="Enter meter number"
                className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-2.5 font-mono text-sm font-bold outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
              />
              {recentMeters.length > 0 && (
                <div className="mt-2 flex flex-wrap gap-1.5">
                  {recentMeters.map((m) => (
                    <button
                      key={m.recipient}
                      type="button"
                      onClick={() => setMeterNumber(m.recipient)}
                      className="rounded-lg border border-slate-200 bg-slate-50 px-2 py-1 font-mono text-[11px] font-bold text-slate-700 hover:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-slate-200"
                    >
                      {m.recipient} ({m.provider})
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>

          <div>
            <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
              <label className="text-xs font-bold text-slate-700 dark:text-slate-200">
                Amount (min ₦{electricityMin.toLocaleString()})
              </label>
              <div className="flex flex-wrap gap-1.5">
                {[500, 1000, 2000, 5000, 10000].map((v) => (
                  <button
                    key={v}
                    type="button"
                    onClick={() => setElecAmount(String(v))}
                    className="rounded-lg border border-slate-200 px-2.5 py-1 text-[11px] font-bold hover:border-[#00875A] dark:border-slate-700 dark:text-slate-200"
                  >
                    ₦{v.toLocaleString()}
                  </button>
                ))}
              </div>
            </div>
            <input
              type="number"
              min={electricityMin}
              required
              value={elecAmount}
              onChange={(e) => setElecAmount(e.target.value)}
              className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-base font-extrabold outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
            />
          </div>

          <button
            type="submit"
            disabled={isProcessingPurchase}
            className="w-full rounded-xl bg-[#00875A] py-3.5 text-sm font-bold text-white shadow-md hover:bg-[#005A3C] disabled:opacity-50"
          >
            {isProcessingPurchase
              ? 'Processing...'
              : `Pay ${formatCompactNaira(Number(elecAmount) || 0)} (${selectedDisco.shortName})`}
          </button>
        </form>
      )}

      {/* CABLE TV */}
      {activeTab === 'CABLE_TV' && (
        <form
          onSubmit={handlePayCable}
          className="space-y-5 rounded-3xl border border-slate-200 bg-white p-6 dark:border-slate-700 dark:bg-[#102A43]"
        >
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            {cableProviders.map((prov) => {
              const active = selectedCable.id === prov.id;
              return (
                <button
                  key={prov.id}
                  type="button"
                  onClick={() => handleCableChange(prov)}
                  className={`flex items-center gap-3 rounded-2xl border p-3.5 text-left transition ${
                    active
                      ? 'border-[#00875A] bg-[#E8F8F2] dark:bg-emerald-950/50'
                      : 'border-slate-200 hover:border-slate-300 dark:border-slate-700'
                  }`}
                >
                  <ProviderDotBadge label={prov.name} color={prov.brandColor} size="sm" />
                  <div>
                    <p className="text-sm font-extrabold text-slate-900 dark:text-white">
                      {prov.name}
                    </p>
                    <p className="line-clamp-1 text-[10px] text-slate-500 dark:text-slate-400">
                      {prov.tagline}
                    </p>
                  </div>
                </button>
              );
            })}
          </div>

          <div>
            <label className="mb-1.5 block text-xs font-bold text-slate-700 dark:text-slate-200">
              {selectedCable.id === 'showmax'
                ? 'Account Phone Number'
                : 'Smartcard / IUC Number'}
            </label>
            <input
              type="text"
              required
              value={smartcardNumber}
              onChange={(e) => setSmartcardNumber(e.target.value)}
              placeholder={
                selectedCable.id === 'showmax'
                  ? 'e.g. 08031234567'
                  : 'Enter 10–12 digit Smartcard / IUC'
              }
              className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 font-mono text-sm font-bold outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
            />
            {recentSmartcards.length > 0 && (
              <div className="mt-2 flex flex-wrap gap-1.5">
                {recentSmartcards.map((sc) => (
                  <button
                    key={sc.recipient}
                    type="button"
                    onClick={() => setSmartcardNumber(sc.recipient)}
                    className="rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1 font-mono text-[11px] font-bold text-slate-700 hover:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-slate-200"
                  >
                    {sc.recipient} ({sc.provider})
                  </button>
                ))}
              </div>
            )}
          </div>

          <div>
            <label className="mb-2 block text-xs font-bold text-slate-700 dark:text-slate-200">
              Select Bouquet ({selectedCable.name})
            </label>
            <div className="grid gap-2.5 sm:grid-cols-2">
              {selectedCable.bouquets.map((bq) => {
                const active = selectedBouquet.id === bq.id;
                return (
                  <button
                    key={bq.id}
                    type="button"
                    onClick={() => setSelectedBouquetId(bq.id)}
                    className={`flex items-center justify-between rounded-2xl border p-3.5 text-left transition ${
                      active
                        ? 'border-[#00875A] bg-[#E8F8F2] dark:bg-emerald-950/50'
                        : 'border-slate-200 hover:border-slate-300 dark:border-slate-700'
                    }`}
                  >
                    <div>
                      <p className="text-sm font-bold text-slate-900 dark:text-white">{bq.name}</p>
                      <p className="text-[11px] text-slate-500 dark:text-slate-400">
                        {bq.channelsCount}+ Channels
                      </p>
                    </div>
                    <span className="text-sm font-extrabold text-[#00875A]">
                      {formatCompactNaira(bq.price)}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>

          <button
            type="submit"
            disabled={isProcessingPurchase}
            className="w-full rounded-xl bg-[#00875A] py-3.5 text-sm font-bold text-white shadow-md hover:bg-[#005A3C] disabled:opacity-50"
          >
            {isProcessingPurchase
              ? 'Processing...'
              : `Subscribe ${selectedBouquet.name} — ${formatCompactNaira(selectedBouquet.price)}`}
          </button>
        </form>
      )}

      {/* EDUCATION */}
      {activeTab === 'EDUCATION' && (
        <form
          onSubmit={handlePayEducation}
          className="space-y-5 rounded-3xl border border-slate-200 bg-white p-6 dark:border-slate-700 dark:bg-[#102A43]"
        >
          <div className="grid gap-3 sm:grid-cols-2">
            {educationExams.map((exam) => {
              const active = selectedExam.id === exam.id;
              return (
                <button
                  key={exam.id}
                  type="button"
                  onClick={() => setSelectedExamId(exam.id)}
                  className={`flex items-start gap-3.5 rounded-2xl border p-4 text-left transition ${
                    active
                      ? 'border-[#00875A] bg-[#E8F8F2] dark:bg-emerald-950/50'
                      : 'border-slate-200 hover:border-slate-300 dark:border-slate-700'
                  }`}
                >
                  <ProviderDotBadge label={exam.shortName} color={exam.brandColor} />
                  <div className="flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-sm font-extrabold text-slate-900 dark:text-white">
                        {exam.shortName}
                      </p>
                      <span className="text-sm font-extrabold text-[#00875A]">
                        {formatCompactNaira(exam.price)}
                      </span>
                    </div>
                    <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                      {exam.description}
                    </p>
                  </div>
                </button>
              );
            })}
          </div>

          <div>
            <div className="mb-1.5 flex items-center justify-between">
              <label className="text-xs font-bold text-slate-700 dark:text-slate-200">
                Recipient Phone / JAMB Profile Code
              </label>
              {profile?.phone && (
                <button
                  type="button"
                  onClick={() => setEduRecipient(profile.phone)}
                  className="text-xs font-bold text-[#00875A] hover:underline"
                >
                  Use My Number
                </button>
              )}
            </div>
            <input
              type="text"
              required
              value={eduRecipient}
              onChange={(e) => setEduRecipient(e.target.value)}
              placeholder="08031234567"
              className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm font-bold outline-none focus:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-white"
            />
            {recentEduRecipients.length > 0 && (
              <div className="mt-2 flex flex-wrap gap-1.5">
                {recentEduRecipients.map((rec) => (
                  <button
                    key={rec}
                    type="button"
                    onClick={() => setEduRecipient(rec)}
                    className="rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1 font-mono text-[11px] font-bold text-slate-700 hover:border-[#00875A] dark:border-slate-700 dark:bg-slate-900/60 dark:text-slate-200"
                  >
                    {rec}
                  </button>
                ))}
              </div>
            )}
          </div>

          <button
            type="submit"
            disabled={isProcessingPurchase}
            className="w-full rounded-xl bg-[#00875A] py-3.5 text-sm font-bold text-white shadow-md hover:bg-[#005A3C] disabled:opacity-50"
          >
            {isProcessingPurchase
              ? 'Generating PIN...'
              : `Buy ${selectedExam.shortName} e-PIN — ${formatCompactNaira(selectedExam.price)}`}
          </button>
        </form>
      )}
    </div>
  );
};
