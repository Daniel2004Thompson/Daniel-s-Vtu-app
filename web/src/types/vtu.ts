export type NetworkId = 'MTN' | 'AIRTEL' | 'GLO' | 'NINEMOBILE';

export type DataCategoryId = 'sme' | 'corporate' | 'gifting' | 'monthly' | 'daily_weekly';

export type ServiceTypeId =
  | 'AIRTIME'
  | 'DATA'
  | 'ELECTRICITY'
  | 'CABLE_TV'
  | 'EDUCATION'
  | 'WALLET_FUNDING'
  | 'WITHDRAWAL';

export interface NetworkMeta {
  id: NetworkId;
  serviceId: string;
  displayName: string;
  shortLabel: string;
  brandColor: string;
  textColor: string;
  prefixes: string[];
  dataServiceIds: string[];
}

export interface AirtimePricing {
  network: NetworkId;
  serviceId: string;
  cashbackPercent: number;
}

export interface VtuPriceRow {
  service_id: string;
  plan: string;
  price: number;
  label?: string | null;
  validity_days?: number | null;
  categories?: string[] | null;
  active?: boolean;
  cashback_percent?: number | null;
}

export interface DataPlanItem {
  id: string;
  network: NetworkId;
  category: DataCategoryId;
  serviceId: string;
  planCode: string;
  label: string;
  validity: string;
  price: number;
}

export interface ElectricityDisco {
  id: string;
  name: string;
  shortName: string;
  stateCoverage: string;
  brandColor: string;
  minAmount: number;
  gsubzServiceId: string;
}

export interface CableBouquet {
  id: string;
  name: string;
  price: number;
  channelsCount: number;
  planCode: string;
}

export interface CableProvider {
  id: string;
  name: string;
  brandColor: string;
  tagline: string;
  gsubzServiceId: string;
  bouquets: CableBouquet[];
}

export interface EducationExam {
  id: string;
  name: string;
  shortName: string;
  description: string;
  price: number;
  brandColor: string;
  gsubzServiceId: string;
  planCode: string;
}

export interface TransactionRecord {
  id: string;
  reference: string;
  serviceType: ServiceTypeId | string;
  title: string;
  provider: string;
  recipient: string;
  customerName?: string;
  amount: number;
  discountOrCashback: number;
  airtimeValue?: number;
  charged?: number;
  status: 'SUCCESSFUL' | 'PENDING' | 'FAILED' | string;
  tokenOrDetails?: string;
  createdAt?: string;
  timestamp: number;
}

export interface UserProfile {
  id: string;
  email: string;
  fullName: string;
  phone: string;
  walletBalance: number;
  cashbackBalance: number;
  avatarUrl?: string;
  virtualAccountNumber?: string;
  virtualBankName?: string;
  virtualAccountName?: string;
  dynamicAccountNumber?: string;
  dynamicBankName?: string;
}

export interface DynamicAccountInfo {
  accountNumber: string;
  bankName: string;
  accountName: string;
  amount: number;
  expiresAt?: string;
  note?: string;
}

export interface PermanentAccountInfo {
  success: boolean;
  accountNumber?: string;
  bankName?: string;
  accountName?: string;
  message: string;
  isKycRequired: boolean;
  isKycVerified: boolean;
}

export interface UserApiKey {
  userId: string;
  userEmail: string;
  apiKey: string;
  publicKey: string;
  createdAt: string;
  status: 'ACTIVE' | 'INACTIVE';
  webhookUrl: string;
  keyPrefix: string;
  last4: string;
  isActive: boolean;
  existsOnServer: boolean;
  hasLocalKey: boolean;
}

export interface ForumReply {
  id: string;
  authorName: string;
  authorRole: string;
  content: string;
  timestamp: string;
}

export interface ForumTopic {
  id: string;
  authorName: string;
  authorEmail: string;
  title: string;
  content: string;
  category: string;
  timestamp: string;
  likesCount: number;
  repliesCount: number;
  isPinned: boolean;
  isLikedByMe: boolean;
  replies: ForumReply[];
}

export interface PurchaseExecutionPayload {
  serviceType: 'AIRTIME' | 'DATA' | 'ELECTRICITY' | 'CABLE_TV' | 'EDUCATION';
  providerName: string;
  serviceId: string;
  network?: NetworkId;
  amount: number;
  recipient: string;
  planCode?: string;
  planLabel?: string;
  meterType?: 'prepaid' | 'postpaid';
  customerName?: string;
  title: string;
}
