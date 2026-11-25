import {
  AirtimePricing,
  CableProvider,
  DataCategoryId,
  EducationExam,
  ElectricityDisco,
  NetworkId,
  NetworkMeta,
} from '../types/vtu';

export const SUPPORT_CONTACT = {
  email: 'danielkaladathompson@gmail.com',
  phoneDisplay: '+234 902 731 4213',
  phoneRaw: '+2349027314213',
  whatsappDigits: '2349027314213',
  brandName: 'Daniel VTU',
};

export const NETWORKS: Record<NetworkId, NetworkMeta> = {
  MTN: {
    id: 'MTN',
    serviceId: 'mtn',
    displayName: 'MTN',
    shortLabel: 'MTN',
    brandColor: '#FFCC00',
    textColor: '#1E1E1E',
    prefixes: [
      '0703', '0704', '0706', '0707', '07025', '07026', '0803', '0806',
      '0810', '0813', '0814', '0816', '0903', '0906', '0913', '0916',
    ],
    dataServiceIds: ['mtn_sme', 'mtn_gifting'],
  },
  AIRTEL: {
    id: 'AIRTEL',
    serviceId: 'airtel',
    displayName: 'Airtel',
    shortLabel: 'AIRTEL',
    brandColor: '#E60000',
    textColor: '#FFFFFF',
    prefixes: [
      '0701', '0708', '0802', '0808', '0812', '0901',
      '0902', '0904', '0907', '0911', '0912',
    ],
    dataServiceIds: ['airtel_sme', 'airtel_gifting'],
  },
  GLO: {
    id: 'GLO',
    serviceId: 'glo',
    displayName: 'GLO',
    shortLabel: 'GLO',
    brandColor: '#28A745',
    textColor: '#FFFFFF',
    prefixes: ['0705', '0805', '0807', '0811', '0815', '0905', '0915'],
    dataServiceIds: ['glo_data', 'glo_sme'],
  },
  NINEMOBILE: {
    id: 'NINEMOBILE',
    serviceId: 'etisalat',
    displayName: '9mobile',
    shortLabel: '9MOBILE',
    brandColor: '#006A4E',
    textColor: '#FFFFFF',
    prefixes: ['0809', '0817', '0818', '0908', '0909'],
    dataServiceIds: ['etisalat_data'],
  },
};

export const NETWORK_LIST: NetworkMeta[] = [
  NETWORKS.MTN,
  NETWORKS.AIRTEL,
  NETWORKS.GLO,
  NETWORKS.NINEMOBILE,
];

export const DEFAULT_AIRTIME_PRICING: Record<NetworkId, AirtimePricing> = {
  MTN: { network: 'MTN', serviceId: 'mtn', cashbackPercent: 2.5 },
  AIRTEL: { network: 'AIRTEL', serviceId: 'airtel', cashbackPercent: 2.0 },
  GLO: { network: 'GLO', serviceId: 'glo', cashbackPercent: 3.0 },
  NINEMOBILE: { network: 'NINEMOBILE', serviceId: '9mobile', cashbackPercent: 3.0 },
};

export const DATA_CATEGORIES: { id: DataCategoryId; label: string }[] = [
  { id: 'sme', label: 'SME Data' },
  { id: 'corporate', label: 'Corporate' },
  { id: 'gifting', label: 'Gifting' },
  { id: 'monthly', label: 'Monthly' },
  { id: 'daily_weekly', label: 'Daily / Weekly' },
];

export const ELECTRICITY_DISCOS: ElectricityDisco[] = [
  {
    id: 'ikedc',
    name: 'Ikeja Electric (IKEDC)',
    shortName: 'IKEDC',
    stateCoverage: 'Lagos Mainland & Environs',
    brandColor: '#C4161C',
    minAmount: 1000,
    gsubzServiceId: 'ikeja-electric',
  },
  {
    id: 'ekedc',
    name: 'Eko Electric (EKEDC)',
    shortName: 'EKEDC',
    stateCoverage: 'Lagos Island & Lekki/VI',
    brandColor: '#1E1B6A',
    minAmount: 1000,
    gsubzServiceId: 'eko-electric',
  },
  {
    id: 'aedc',
    name: 'Abuja Electricity (AEDC)',
    shortName: 'AEDC',
    stateCoverage: 'FCT Abuja, Nasarawa, Kogi, Niger',
    brandColor: '#070D6E',
    minAmount: 1000,
    gsubzServiceId: 'abuja-electric',
  },
  {
    id: 'ibedc',
    name: 'Ibadan Electricity (IBEDC)',
    shortName: 'IBEDC',
    stateCoverage: 'Oyo, Ogun, Osun, Kwara',
    brandColor: '#003359',
    minAmount: 1000,
    gsubzServiceId: 'ibadan-electric',
  },
  {
    id: 'phed',
    name: 'Port Harcourt Electric (PHED)',
    shortName: 'PHED',
    stateCoverage: 'Rivers, Bayelsa, Cross River, Akwa Ibom',
    brandColor: '#164194',
    minAmount: 1000,
    gsubzServiceId: 'portharcourt-electric',
  },
  {
    id: 'eedc',
    name: 'Enugu Electricity (EEDC)',
    shortName: 'EEDC',
    stateCoverage: 'Enugu, Abia, Anambra, Imo, Ebonyi',
    brandColor: '#E30613',
    minAmount: 1000,
    gsubzServiceId: 'enugu-electric',
  },
  {
    id: 'kedco',
    name: 'Kano Electricity (KEDCO)',
    shortName: 'KEDCO',
    stateCoverage: 'Kano, Katsina, Jigawa',
    brandColor: '#1F2278',
    minAmount: 1000,
    gsubzServiceId: 'kano-electric',
  },
  {
    id: 'jed',
    name: 'Jos Electricity (JED)',
    shortName: 'JED',
    stateCoverage: 'Plateau, Bauchi, Benue, Gombe',
    brandColor: '#0E5A8A',
    minAmount: 1000,
    gsubzServiceId: 'jos-electric',
  },
  {
    id: 'kaedco',
    name: 'Kaduna Electricity (KAEDCO)',
    shortName: 'KAEDCO',
    stateCoverage: 'Kaduna, Kebbi, Sokoto, Zamfara',
    brandColor: '#006838',
    minAmount: 1000,
    gsubzServiceId: 'kaduna-electric',
  },
  {
    id: 'bedc',
    name: 'Benin Electricity (BEDC)',
    shortName: 'BEDC',
    stateCoverage: 'Edo, Delta, Ondo, Ekiti',
    brandColor: '#8B1D41',
    minAmount: 1000,
    gsubzServiceId: 'benin-electric',
  },
];

export const CABLE_PROVIDERS: CableProvider[] = [
  {
    id: 'dstv',
    name: 'DStv',
    brandColor: '#005DAA',
    tagline: 'So much more',
    gsubzServiceId: 'dstv',
    bouquets: [
      { id: 'dstv_padi', name: 'DStv Padi', price: 4400, channelsCount: 45, planCode: 'dstv-padi' },
      { id: 'dstv_yanga', name: 'DStv Yanga', price: 6000, channelsCount: 85, planCode: 'dstv-yanga' },
      { id: 'dstv_confam', name: 'DStv Confam', price: 11000, channelsCount: 120, planCode: 'dstv-confam' },
      { id: 'dstv_compact', name: 'DStv Compact', price: 19000, channelsCount: 145, planCode: 'dstv79' },
      { id: 'dstv_compact_plus', name: 'DStv Compact Plus', price: 30000, channelsCount: 160, planCode: 'dstv7' },
      { id: 'dstv_premium', name: 'DStv Premium', price: 44500, channelsCount: 175, planCode: 'dstv3' },
      { id: 'dstv_premium_asia', name: 'DStv Premium-Asia', price: 50500, channelsCount: 185, planCode: 'dstv10' },
      { id: 'dstv_premium_french', name: 'DStv Premium-French', price: 69000, channelsCount: 195, planCode: 'dstv9' },
    ],
  },
  {
    id: 'gotv',
    name: 'GOtv',
    brandColor: '#ED1C24',
    tagline: 'Live it. Love it.',
    gsubzServiceId: 'gotv',
    bouquets: [
      { id: 'gotv_smallie', name: 'GOtv Smallie (Monthly)', price: 1900, channelsCount: 35, planCode: 'gotv-smallie' },
      { id: 'gotv_jinja', name: 'GOtv Jinja', price: 3900, channelsCount: 47, planCode: 'gotv-jinja' },
      { id: 'gotv_jolli', name: 'GOtv Jolli', price: 5800, channelsCount: 68, planCode: 'gotv-jolli' },
      { id: 'gotv_max', name: 'GOtv Max', price: 8500, channelsCount: 75, planCode: 'gotv-max' },
      { id: 'gotv_supa', name: 'GOtv Supa (Monthly)', price: 11400, channelsCount: 80, planCode: 'gotv-supa' },
      { id: 'gotv_supa_plus', name: 'GOtv Supa Plus (Monthly)', price: 16800, channelsCount: 85, planCode: 'gotv-supa-plus' },
      { id: 'gotv_smallie_quarterly', name: 'GOtv Smallie (Quarterly)', price: 5100, channelsCount: 35, planCode: 'gotv-smallie-3months' },
      { id: 'gotv_smallie_yearly', name: 'GOtv Smallie (Yearly)', price: 15000, channelsCount: 35, planCode: 'gotv-smallie-1year' },
    ],
  },
  {
    id: 'startimes',
    name: 'StarTimes',
    brandColor: '#0077C0',
    tagline: 'Enjoy Digital Life',
    gsubzServiceId: 'startimes',
    bouquets: [
      { id: 'star_nova', name: 'Nova Monthly', price: 2100, channelsCount: 38, planCode: 'nova' },
      { id: 'star_basic', name: 'Basic (Antenna) Monthly', price: 4000, channelsCount: 60, planCode: 'basic' },
      { id: 'star_smart', name: 'Basic (Dish) / Smart Monthly', price: 5100, channelsCount: 70, planCode: 'smart' },
      { id: 'star_classic', name: 'Classic (Antenna) Monthly', price: 6000, channelsCount: 80, planCode: 'classic' },
      { id: 'star_super', name: 'Super (Dish) Monthly', price: 9800, channelsCount: 95, planCode: 'super' },
      { id: 'star_nova_weekly', name: 'Nova Weekly', price: 700, channelsCount: 38, planCode: 'nova-weekly' },
      { id: 'star_basic_weekly', name: 'Basic Weekly', price: 1400, channelsCount: 60, planCode: 'basic-weekly' },
      { id: 'star_smart_weekly', name: 'Smart Weekly', price: 1700, channelsCount: 70, planCode: 'smart-weekly' },
    ],
  },
  {
    id: 'showmax',
    name: 'Showmax',
    brandColor: '#E91E63',
    tagline: 'Stream Original Series & Live Football',
    gsubzServiceId: 'showmax',
    bouquets: [
      { id: 'showmax_mobile', name: 'Showmax Mobile Only', price: 1600, channelsCount: 50, planCode: 'mobile_only_3' },
      { id: 'showmax_full', name: 'Showmax Full', price: 3500, channelsCount: 80, planCode: 'full_3' },
      { id: 'showmax_sports_mobile', name: 'Showmax Sports Mobile Only', price: 4000, channelsCount: 95, planCode: 'sports_mobile_only_3' },
      { id: 'showmax_sports_full', name: 'Showmax Sports Full', price: 6500, channelsCount: 120, planCode: 'sports_full_3' },
    ],
  },
];

export const EDUCATION_EXAMS: EducationExam[] = [
  {
    id: 'waec_pin',
    name: 'WAEC Registration & Result Checker PIN',
    shortName: 'WAEC',
    description: 'Instant e-PIN with Serial number to check WASSCE results online',
    price: 5100,
    brandColor: '#23338B',
    gsubzServiceId: 'waec',
    planCode: 'WAEC',
  },
  {
    id: 'neco_token',
    name: 'NECO Result Checker PIN',
    shortName: 'NECO',
    description: 'Official token for checking SSCE internal & external results',
    price: 2100,
    brandColor: '#009E49',
    gsubzServiceId: 'neco',
    planCode: 'NECO',
  },
  {
    id: 'nabteb_pin',
    name: 'NABTEB Result Checker PIN',
    shortName: 'NABTEB',
    description: 'National Business and Technical Examinations Board result checker card',
    price: 900,
    brandColor: '#007A33',
    gsubzServiceId: 'nabteb',
    planCode: 'NABTEB',
  },
  {
    id: 'jamb_utme',
    name: 'JAMB UTME Profile Registration e-PIN',
    shortName: 'JAMB',
    description: 'Official JAMB UTME profile confirmation code and registration PIN',
    price: 6200,
    brandColor: '#00873E',
    gsubzServiceId: 'jamb',
    planCode: 'JAMB',
  },
];

export function normalizeNigerianPhone(phone: string): string {
  const clean = phone.trim().replace(/[\s\-()]/g, '');
  if (clean.startsWith('+2340')) return '0' + clean.slice(5);
  if (clean.startsWith('+234')) return '0' + clean.slice(4);
  if (clean.startsWith('002340')) return '0' + clean.slice(6);
  if (clean.startsWith('00234')) return '0' + clean.slice(5);
  if (clean.startsWith('2340') && clean.length >= 7) return '0' + clean.slice(4);
  if (clean.startsWith('234') && clean.length >= 6) return '0' + clean.slice(3);
  if (clean.length === 10 && !clean.startsWith('0') && /^[789]\d{9}$/.test(clean)) {
    return '0' + clean;
  }
  return clean;
}

export function detectNetworkFromPhone(phone: string): NetworkId | null {
  const local = normalizeNigerianPhone(phone);
  if (local.length < 4) return null;
  if (local.length >= 5) {
    const prefix5 = local.slice(0, 5);
    const match5 = NETWORK_LIST.find((net) => net.prefixes.includes(prefix5));
    if (match5) return match5.id;
  }
  const prefix4 = local.slice(0, 4);
  const found = NETWORK_LIST.find((net) => net.prefixes.includes(prefix4));
  return found?.id ?? null;
}

export function detectNetworkFromTextOrPhone(text?: string, phone?: string): NetworkId | null {
  if (phone) {
    const byPhone = detectNetworkFromPhone(phone);
    if (byPhone) return byPhone;
  }
  if (text) {
    const lower = text.toLowerCase();
    if (lower.includes('airtel')) return 'AIRTEL';
    if (lower.includes('mtn')) return 'MTN';
    if (lower.includes('glo')) return 'GLO';
    if (lower.includes('9mobile') || lower.includes('etisalat') || lower.includes('t2 mobile')) {
      return 'NINEMOBILE';
    }
    const match = text.match(/\b(\+?234|0)\d{9,10}\b/);
    if (match) return detectNetworkFromPhone(match[0]);
  }
  return null;
}

export function formatNaira(amount: number, decimals = 2): string {
  const safe = Number.isFinite(amount) ? amount : 0;
  return new Intl.NumberFormat('en-NG', {
    style: 'currency',
    currency: 'NGN',
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(safe);
}

export function formatCompactNaira(amount: number): string {
  const safe = Number.isFinite(amount) ? amount : 0;
  const rounded = Math.round(safe * 100) / 100;
  const hasDecimals = Math.abs(rounded % 1) > 0.001;
  return `₦${rounded.toLocaleString('en-US', {
    minimumFractionDigits: hasDecimals ? 2 : 0,
    maximumFractionDigits: 2,
  })}`;
}
