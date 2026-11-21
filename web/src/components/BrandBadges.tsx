import React from 'react';
import { NETWORKS } from '../data/catalog';
import { NetworkId } from '../types/vtu';

export const DanielVtuLogo: React.FC<{ size?: number }> = ({ size = 36 }) => (
  <div
    style={{ width: size, height: size }}
    className="flex shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-[#00B074] to-[#005A3C] text-white shadow-md shadow-emerald-900/20"
  >
    <svg
      viewBox="0 0 24 24"
      fill="none"
      className="h-5 w-5"
      stroke="currentColor"
      strokeWidth={2.4}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" fill="currentColor" />
    </svg>
  </div>
);

export const NetworkBadge: React.FC<{
  network: NetworkId;
  size?: 'sm' | 'md' | 'lg';
}> = ({ network, size = 'md' }) => {
  const meta = NETWORKS[network];
  const dims =
    size === 'sm'
      ? 'h-6 w-6 text-[9px]'
      : size === 'lg'
      ? 'h-11 w-11 text-xs'
      : 'h-9 w-9 text-[10px]';

  const label =
    network === 'MTN'
      ? 'MTN'
      : network === 'AIRTEL'
      ? 'AIR'
      : network === 'GLO'
      ? 'GLO'
      : '9M';

  return (
    <span
      className={`${dims} inline-flex shrink-0 items-center justify-center rounded-full font-extrabold tracking-tight shadow-sm ring-2 ring-white/80 dark:ring-slate-800`}
      style={{ backgroundColor: meta.brandColor, color: meta.textColor }}
      title={meta.displayName}
    >
      {label}
    </span>
  );
};

export const ProviderDotBadge: React.FC<{
  label: string;
  color: string;
  size?: 'sm' | 'md';
}> = ({ label, color, size = 'md' }) => {
  const dims = size === 'sm' ? 'h-7 w-7 text-[9px]' : 'h-10 w-10 text-[11px]';
  return (
    <span
      className={`${dims} inline-flex shrink-0 items-center justify-center rounded-xl font-extrabold text-white shadow-sm`}
      style={{ backgroundColor: color }}
    >
      {label.slice(0, 4).toUpperCase()}
    </span>
  );
};
