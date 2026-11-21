import {
  DEFAULT_AIRTIME_PRICING,
  NETWORKS,
  normalizeNigerianPhone,
} from '../data/catalog';
import { extractErrorMessage, gatewayRequest } from '../lib/supabaseGateway';
import {
  AirtimePricing,
  DataCategoryId,
  DataPlanItem,
  NetworkId,
  PurchaseExecutionPayload,
  TransactionRecord,
  VtuPriceRow,
} from '../types/vtu';

export interface PurchaseExecutionResult {
  success: boolean;
  pending: boolean;
  message: string;
  newBalance?: number;
  transaction: TransactionRecord;
}

function cleanDisplayDetail(raw?: string | null): string | undefined {
  if (!raw) return undefined;
  const cleaned = raw
    .replace(/\(\s*Gsubz\s*Live\s*\)/gi, '')
    .replace(/via\s+Gsubz-VTU-Services/gi, '')
    .replace(/Gsubz-VTU-Services|Gsubz|Supabase|Edge\s*Function/gi, '')
    .trim();
  return cleaned.length > 0 ? cleaned : undefined;
}

export async function fetchLiveAirtimePricing(): Promise<Record<NetworkId, AirtimePricing>> {
  const result: Record<NetworkId, AirtimePricing> = { ...DEFAULT_AIRTIME_PRICING };

  try {
    const res = await gatewayRequest<Record<string, unknown>[]>(
      '/rest/v1/vtu_prices?select=service_id,plan,cashback_percent,active&plan=eq.',
      { method: 'GET' }
    );
    if (!res.ok || !Array.isArray(res.data)) return result;

    for (const row of res.data) {
      if (row.active === false) continue;
      const serviceId = String(row.service_id || '').trim().toLowerCase();
      const pct = Number(row.cashback_percent);
      if (!serviceId || !Number.isFinite(pct) || pct < 0) continue;

      if (serviceId.includes('mtn')) {
        result.MTN = { network: 'MTN', serviceId, cashbackPercent: pct };
      } else if (serviceId.includes('airtel')) {
        result.AIRTEL = { network: 'AIRTEL', serviceId, cashbackPercent: pct };
      } else if (serviceId.includes('glo')) {
        result.GLO = { network: 'GLO', serviceId, cashbackPercent: pct };
      } else if (serviceId.includes('9mobile') || serviceId.includes('etisalat')) {
        result.NINEMOBILE = { network: 'NINEMOBILE', serviceId, cashbackPercent: pct };
      }
    }
  } catch {
    // Keep defaults when offline
  }
  return result;
}

export async function fetchLiveDataPlans(
  network: NetworkId,
  category: DataCategoryId
): Promise<DataPlanItem[]> {
  const serviceIds = NETWORKS[network].dataServiceIds;
  const inFilter = serviceIds.join(',');
  const encodedCat = encodeURIComponent(category);

  const url = `/rest/v1/vtu_prices?select=service_id,plan,price,label,validity_days,categories,active&active=eq.true&service_id=in.(${inFilter})&categories=cs.%7B${encodedCat}%7D&order=price.asc`;

  const res = await gatewayRequest<VtuPriceRow[]>(url, { method: 'GET' });
  if (!res.ok || !Array.isArray(res.data)) {
    throw new Error(extractErrorMessage(res, 'Could not load live data plans.'));
  }

  return res.data
    .filter((row) => row.active !== false && Number(row.price) > 0 && row.plan)
    .sort((a, b) => Number(a.price) - Number(b.price))
    .map((row) => {
      const days = row.validity_days;
      const validity =
        typeof days === 'number' && days > 0
          ? `${days} ${days === 1 ? 'Day' : 'Days'}`
          : 'Instant';
      return {
        id: `${row.service_id}_${row.plan}_${category}`,
        network,
        category,
        serviceId: row.service_id,
        planCode: String(row.plan),
        label: (row.label && row.label.trim()) || String(row.plan),
        validity,
        price: Number(row.price),
      };
    });
}

function buildEdgeServiceType(payload: PurchaseExecutionPayload): string {
  switch (payload.serviceType) {
    case 'AIRTIME':
      return 'airtime';
    case 'DATA':
      return 'data';
    case 'ELECTRICITY':
      return 'electricity';
    case 'CABLE_TV':
      return 'cable';
    case 'EDUCATION':
      return 'education';
  }
}

export async function executeVtuPurchase(params: {
  userId: string;
  email: string;
  currentDashboardBalance: number;
  payload: PurchaseExecutionPayload;
}): Promise<PurchaseExecutionResult> {
  const { userId, email, currentDashboardBalance, payload } = params;
  const isPhoneBased = payload.serviceType === 'AIRTIME' || payload.serviceType === 'DATA';
  const cleanRecipient = isPhoneBased
    ? normalizeNigerianPhone(payload.recipient)
    : payload.recipient.trim();

  const serviceKind = buildEdgeServiceType(payload);
  const resolvedServiceId = payload.serviceId.trim();
  const networkStr = payload.network
    ? payload.network === 'NINEMOBILE'
      ? '9mobile'
      : payload.network.toLowerCase()
    : resolvedServiceId;

  const baseBody: Record<string, unknown> = {
    user_id: userId,
    email,
    service_type: serviceKind,
    service: serviceKind,
    service_id: resolvedServiceId,
    serviceID: resolvedServiceId,
    network: networkStr,
    amount: Math.round(payload.amount * 100) / 100,
    phone: cleanRecipient,
    plan: payload.planCode || '',
    narration: payload.title,
  };

  if (payload.planCode) {
    baseBody.plan_id = payload.planCode;
    baseBody.variation_code = payload.planCode;
  }

  if (payload.serviceType === 'ELECTRICITY') {
    baseBody.meter_type = payload.meterType || 'prepaid';
    baseBody.customerID = cleanRecipient;
    baseBody.customer_id = cleanRecipient;
    baseBody.meter_number = cleanRecipient;
  } else if (payload.serviceType === 'CABLE_TV') {
    baseBody.customerID = cleanRecipient;
    baseBody.customer_id = cleanRecipient;
    baseBody.iuc = cleanRecipient;
    baseBody.smartcard_number = cleanRecipient;
  }

  let res = await gatewayRequest<Record<string, unknown>>('/functions/v1/Gsubz-VTU-Services', {
    method: 'POST',
    body: baseBody,
  });

  // Fallback retry matching Android GsubzVtuService if edge validator rejects "education"
  if (
    res.status === 400 &&
    payload.serviceType === 'EDUCATION' &&
    res.rawText.toLowerCase().includes('invalid service type')
  ) {
    res = await gatewayRequest<Record<string, unknown>>('/functions/v1/Gsubz-VTU-Services', {
      method: 'POST',
      body: { ...baseBody, service: 'data' },
    });
  }

  const body = (res.data || {}) as Record<string, unknown>;
  const gsubzData = (body.gsubz_data || {}) as Record<string, unknown>;
  const content = (gsubzData.content || body.content || {}) as Record<string, unknown>;

  const statusText = String(body.status || gsubzData.status || content.status || '').toUpperCase();
  const isPending =
    res.status === 202 ||
    body.pending === true ||
    statusText === 'PENDING' ||
    statusText === 'PROCESSING';

  const isSuccess =
    res.ok &&
    !isPending &&
    body.success !== false &&
    statusText !== 'FAILED' &&
    statusText !== 'ERROR';

  const newBalCandidate =
    typeof body.new_balance === 'number'
      ? body.new_balance
      : typeof body.wallet_balance === 'number'
      ? body.wallet_balance
      : undefined;

  const tokenCandidate =
    (typeof content.token === 'string' && content.token) ||
    (typeof content.pin === 'string' && content.pin) ||
    (typeof content.purchased_code === 'string' && content.purchased_code) ||
    (typeof body.token === 'string' && body.token) ||
    (typeof body.pin === 'string' && body.pin) ||
    '';

  const refCandidate =
    (typeof content.transactionID === 'string' && content.transactionID) ||
    (typeof content.requestID === 'string' && content.requestID) ||
    (typeof body.transactionID === 'string' && body.transactionID) ||
    `VTU-${Date.now()}-${Math.floor(1000 + Math.random() * 9000)}`;

  const airtimeValue =
    typeof body.airtimeValue === 'number' ? body.airtimeValue : payload.amount;
  const cashback = typeof body.cashback === 'number' ? body.cashback : 0;
  const charged =
    typeof body.charged === 'number'
      ? body.charged
      : Math.max(0, airtimeValue - cashback);

  let message =
    (typeof body.message === 'string' && body.message) ||
    (typeof body.description === 'string' && body.description) ||
    (typeof gsubzData.description === 'string' && gsubzData.description) ||
    (typeof body.error === 'string' && body.error) ||
    '';

  const lowerMsg = message.toLowerCase();
  if (!isSuccess && !isPending) {
    if (lowerMsg.includes('insufficient')) {
      message =
        currentDashboardBalance < payload.amount
          ? 'Insufficient wallet balance. Please top up your account.'
          : 'Purchase failed';
    } else if (!message || /gsubz|supabase|edge\s*function/i.test(message)) {
      message = res.ok ? 'Purchase failed' : extractErrorMessage(res, 'Purchase failed');
    }
  } else if (isPending) {
    message = 'Purchase is being confirmed';
  } else {
    message = cleanDisplayDetail(message) || `${payload.title} completed successfully`;
  }

  const txRecord: TransactionRecord = {
    id: refCandidate,
    reference: refCandidate.replace(/GSUBZ-ERR-|GSUBZ-/gi, 'VTU-'),
    serviceType: payload.serviceType,
    title: payload.title,
    provider: payload.providerName,
    recipient: cleanRecipient,
    customerName: payload.customerName,
    amount: payload.serviceType === 'AIRTIME' ? airtimeValue : payload.amount,
    discountOrCashback: cashback,
    airtimeValue: payload.serviceType === 'AIRTIME' ? airtimeValue : undefined,
    charged: payload.serviceType === 'AIRTIME' ? charged : undefined,
    status: isPending ? 'PENDING' : isSuccess ? 'SUCCESSFUL' : 'FAILED',
    tokenOrDetails:
      tokenCandidate ||
      cleanDisplayDetail(
        typeof content.description === 'string'
          ? content.description
          : typeof body.description === 'string'
          ? body.description
          : undefined
      ),
    createdAt: new Date().toISOString(),
    timestamp: Date.now(),
  };

  return {
    success: isSuccess,
    pending: isPending,
    message,
    newBalance: newBalCandidate,
    transaction: txRecord,
  };
}
