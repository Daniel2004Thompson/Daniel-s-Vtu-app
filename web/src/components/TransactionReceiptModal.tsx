import React from 'react';
import {
  CheckCircle2,
  Clock,
  Copy,
  Printer,
  Share2,
  X,
  XCircle,
} from 'lucide-react';
import { useApp } from '../context/AppContext';
import { detectNetworkFromTextOrPhone, formatCompactNaira, formatNaira } from '../data/catalog';
import { NetworkBadge } from './BrandBadges';

export const TransactionReceiptModal: React.FC = () => {
  const { selectedReceipt, setSelectedReceipt, showToast } = useApp();
  if (!selectedReceipt) return null;

  const tx = selectedReceipt;
  const statusUpper = tx.status.toUpperCase();
  const isSuccess = statusUpper === 'SUCCESSFUL' || statusUpper === 'SUCCESS' || statusUpper === 'COMPLETED';
  const isPending = statusUpper === 'PENDING' || statusUpper === 'PROCESSING';
  const isAirtime = tx.serviceType === 'AIRTIME';

  const airtimeVal = tx.airtimeValue ?? tx.amount;
  const cashbackVal = tx.discountOrCashback || 0;
  const chargedVal = tx.charged ?? Math.max(0, airtimeVal - cashbackVal);

  const detectedNet = detectNetworkFromTextOrPhone(tx.provider, tx.recipient);
  const formattedDate = new Date(tx.timestamp).toLocaleString('en-NG', {
    timeZone: 'Africa/Lagos',
    dateStyle: 'medium',
    timeStyle: 'short',
  });

  const copyText = async (value: string, label: string) => {
    try {
      await navigator.clipboard.writeText(value);
      showToast(`${label} copied to clipboard`, 'success');
    } catch {
      showToast(`Could not copy ${label}`, 'error');
    }
  };

  const shareReceipt = async () => {
    const lines = [
      '=== DANIEL VTU RECEIPT ===',
      `Service: ${tx.serviceType.replace('_', ' ')}`,
      tx.provider ? `Provider: ${tx.provider}` : '',
      tx.recipient ? `Recipient: ${tx.recipient}` : '',
      tx.customerName ? `Customer: ${tx.customerName}` : '',
      isAirtime
        ? `Airtime: ${formatCompactNaira(airtimeVal)}\nCashback: ${formatCompactNaira(
            cashbackVal
          )}\nYou paid: ${formatCompactNaira(chargedVal)}`
        : `Amount: ${formatNaira(tx.amount)}`,
      tx.tokenOrDetails ? `Token / PIN: ${tx.tokenOrDetails}` : '',
      `Ref: ${tx.reference}`,
      `Date: ${formattedDate}`,
      `Status: ${tx.status}`,
      '==========================',
    ]
      .filter(Boolean)
      .join('\n');

    if (navigator.share) {
      try {
        await navigator.share({
          title: 'Daniel VTU Transaction Receipt',
          text: lines,
        });
        return;
      } catch {
        // fallback to clipboard
      }
    }
    await copyText(lines, 'Receipt');
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 p-4 backdrop-blur-sm">
      <div
        id="printable-receipt"
        className="max-h-[90vh] w-full max-w-md overflow-y-auto rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl dark:border-slate-700 dark:bg-[#102A43]"
      >
        <div className="flex items-center justify-between">
          <h3 className="text-base font-bold text-slate-900 dark:text-white">
            Transaction Receipt
          </h3>
          <button
            type="button"
            onClick={() => setSelectedReceipt(null)}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 print:hidden"
            aria-label="Close receipt"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="mt-4 flex flex-col items-center text-center">
          <div
            className={`flex h-16 w-16 items-center justify-center rounded-full ${
              isSuccess
                ? 'bg-emerald-100 text-[#00875A] dark:bg-emerald-950'
                : isPending
                ? 'bg-amber-100 text-amber-600 dark:bg-amber-950'
                : 'bg-red-100 text-red-600 dark:bg-red-950'
            }`}
          >
            {isSuccess ? (
              <CheckCircle2 className="h-9 w-9" />
            ) : isPending ? (
              <Clock className="h-9 w-9" />
            ) : (
              <XCircle className="h-9 w-9" />
            )}
          </div>

          <p
            className={`mt-2 text-sm font-bold ${
              isSuccess
                ? 'text-[#00875A]'
                : isPending
                ? 'text-amber-500'
                : 'text-red-500'
            }`}
          >
            {isPending
              ? 'Purchase is being confirmed'
              : isSuccess
              ? 'Payment Successful'
              : 'Payment Failed'}
          </p>

          {isAirtime && (isSuccess || isPending) ? (
            <div className="mt-3 w-full space-y-1.5 rounded-2xl border border-emerald-500/30 bg-[#E8F8F2] p-4 text-left dark:bg-emerald-950/40">
              <div className="flex justify-between text-sm font-bold text-slate-800 dark:text-slate-100">
                <span>Airtime</span>
                <span>{formatCompactNaira(airtimeVal)}</span>
              </div>
              <div className="flex justify-between text-sm font-bold text-[#00875A] dark:text-emerald-400">
                <span>Cashback</span>
                <span>{formatCompactNaira(cashbackVal)}</span>
              </div>
              <div className="flex justify-between border-t border-emerald-500/20 pt-1.5 text-base font-extrabold text-[#005A3C] dark:text-emerald-300">
                <span>You paid</span>
                <span>{formatCompactNaira(chargedVal)}</span>
              </div>
            </div>
          ) : (
            <div className="mt-2">
              <p className="text-3xl font-extrabold text-slate-900 dark:text-white">
                {formatNaira(tx.amount)}
              </p>
              {tx.discountOrCashback > 0 && (
                <p className="text-xs font-semibold text-[#00875A]">
                  Cashback/Discount: {formatNaira(tx.discountOrCashback)}
                </p>
              )}
            </div>
          )}
        </div>

        {tx.tokenOrDetails && !isAirtime && (
          <div className="mt-4 rounded-2xl border border-emerald-500/30 bg-[#E8F8F2] p-4 text-center dark:bg-emerald-950/40">
            <p className="text-[11px] font-bold uppercase tracking-wider text-[#00875A]">
              {tx.serviceType === 'ELECTRICITY'
                ? 'Prepaid Meter Token'
                : tx.serviceType === 'EDUCATION'
                ? 'Examination PIN'
                : 'Transaction Details'}
            </p>
            <p className="mt-1 break-all font-mono text-lg font-extrabold text-slate-900 dark:text-white">
              {tx.tokenOrDetails}
            </p>
            {isSuccess &&
              (tx.serviceType === 'ELECTRICITY' || tx.serviceType === 'EDUCATION') && (
                <button
                  type="button"
                  onClick={() => copyText(tx.tokenOrDetails!, 'Token')}
                  className="mt-2 inline-flex items-center gap-1.5 rounded-lg bg-[#00875A] px-3 py-1.5 text-xs font-bold text-white hover:bg-[#005A3C]"
                >
                  <Copy className="h-3.5 w-3.5" /> Copy Token
                </button>
              )}
          </div>
        )}

        <div className="mt-4 space-y-2.5 rounded-2xl bg-slate-50 p-4 text-xs dark:bg-slate-900/60">
          <div className="flex items-center justify-between">
            <span className="text-slate-500 dark:text-slate-400">Service</span>
            <span className="font-semibold text-slate-900 dark:text-white">
              {tx.serviceType.replace('_', ' ')}
            </span>
          </div>
          {tx.provider && (
            <div className="flex items-center justify-between">
              <span className="text-slate-500 dark:text-slate-400">Provider</span>
              <span className="flex items-center gap-1.5 font-semibold text-slate-900 dark:text-white">
                {detectedNet && <NetworkBadge network={detectedNet} size="sm" />}
                {tx.provider}
              </span>
            </div>
          )}
          {tx.recipient && (
            <div className="flex items-center justify-between">
              <span className="text-slate-500 dark:text-slate-400">Recipient</span>
              <span className="font-semibold text-slate-900 dark:text-white">
                {tx.recipient}
              </span>
            </div>
          )}
          {tx.customerName && (
            <div className="flex items-center justify-between">
              <span className="text-slate-500 dark:text-slate-400">Customer</span>
              <span className="font-semibold text-slate-900 dark:text-white">
                {tx.customerName}
              </span>
            </div>
          )}
          <div className="flex items-center justify-between border-t border-slate-200 pt-2.5 dark:border-slate-700">
            <span className="text-slate-500 dark:text-slate-400">Reference</span>
            <button
              type="button"
              onClick={() => copyText(tx.reference, 'Reference')}
              className="inline-flex items-center gap-1 font-mono font-bold text-slate-900 hover:text-[#00875A] dark:text-white"
            >
              {tx.reference}
              <Copy className="h-3 w-3" />
            </button>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-slate-500 dark:text-slate-400">Date &amp; Time</span>
            <span className="font-semibold text-slate-900 dark:text-white">
              {formattedDate}
            </span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-slate-500 dark:text-slate-400">Payment Method</span>
            <span className="font-semibold text-slate-900 dark:text-white">
              Daniel VTU Wallet
            </span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-slate-500 dark:text-slate-400">Status</span>
            <span
              className={`font-bold ${
                isSuccess ? 'text-[#00875A]' : isPending ? 'text-amber-500' : 'text-red-500'
              }`}
            >
              {tx.status}
            </span>
          </div>
        </div>

        <div className="mt-5 flex gap-2.5 print:hidden">
          <button
            type="button"
            onClick={shareReceipt}
            className="flex flex-1 items-center justify-center gap-1.5 rounded-xl border border-slate-200 py-2.5 text-xs font-bold text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800"
          >
            <Share2 className="h-4 w-4" /> Share
          </button>
          <button
            type="button"
            onClick={() => window.print()}
            className="flex flex-1 items-center justify-center gap-1.5 rounded-xl border border-slate-200 py-2.5 text-xs font-bold text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800"
          >
            <Printer className="h-4 w-4" /> Print
          </button>
          <button
            type="button"
            onClick={() => setSelectedReceipt(null)}
            className="flex-1 rounded-xl bg-[#00875A] py-2.5 text-xs font-bold text-white hover:bg-[#005A3C]"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};
