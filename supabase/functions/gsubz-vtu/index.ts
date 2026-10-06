// Supabase Edge Function: Gsubz VTU Service
// Routes VTU requests (Airtime, Data, Bills, Cable TV) through Gsubz API
// Secrets required: GSUBZ_API_KEY (and optionally GSUBZ_USERNAME)

import { serve } from "https://deno.land/std@0.177.0/http/server.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-api-key, vtu-api-key, x-vtu-api-key, x-vtu-bearer",
  "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
};

interface VtuRequest {
  service: string;
  provider: string;
  recipient: string;
  phone?: string;
  mobile_number?: string;
  amount: number;
  planId?: string;
  plan?: string;
  meterNumber?: string;
  meter_number?: string;
  meterType?: string; // prepaid or postpaid
  smartcardNumber?: string;
  smart_card_number?: string;
  reference?: string;
  gsubz_api_key?: string;
  gsubzApiKey?: string;
  api_key?: string;
  apiKey?: string;
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const authHeader = req.headers.get("authorization") || req.headers.get("Authorization") || "";
    const xApiKey = req.headers.get("x-api-key") || req.headers.get("vtu-api-key") || req.headers.get("x-vtu-api-key") || "";

    let body: any = {};
    try {
      body = await req.json();
    } catch {
      body = {};
    }

    const gsubzApiKey =
      Deno.env.get("GSUBZ_API_KEY") ||
      Deno.env.get("GSUBZ_TOKEN") ||
      body.gsubz_api_key ||
      body.gsubzApiKey ||
      (xApiKey.startsWith("VTU_") ? null : xApiKey) ||
      body.apiKey ||
      body.api_key;

    if (!gsubzApiKey) {
      return new Response(
        JSON.stringify({
          success: false,
          error: "GSUBZ_API_KEY secret is not configured in Supabase project secrets.",
          isConfigured: false,
          hint: "Set GSUBZ_API_KEY in your Supabase secrets or pass it in the request."
        }),
        {
          headers: { ...corsHeaders, "Content-Type": "application/json" },
          status: 400,
        }
      );
    }
    const service = (body.service || "AIRTIME").toUpperCase();
    const provider = body.provider || "MTN";
    const recipient = body.recipient || body.phone || body.mobile_number || "";
    const amount = Number(body.amount) || 0;
    const planId = body.planId || body.plan || "1";
    const meterNumber = body.meterNumber || body.meter_number || recipient;
    const smartcardNumber = body.smartcardNumber || body.smart_card_number || recipient;
    const txRef = body.reference || `GSUBZ-${Date.now()}-${Math.floor(100000 + Math.random() * 900000)}`;

    // Map network names to Gsubz network IDs
    const networkIdMap: Record<string, number> = {
      MTN: 1,
      GLO: 2,
      AIRTEL: 3,
      NINEMOBILE: 4,
      "9MOBILE": 4,
      ETISALAT: 4,
    };

    const normProvider = provider.toUpperCase();
    const networkId = networkIdMap[normProvider] || 1;

    let gsubzEndpoint = "https://gsubz.com/api/airtime/";
    let gsubzPayload: Record<string, unknown> = {};

    switch (service) {
      case "AIRTIME":
        gsubzEndpoint = "https://gsubz.com/api/topup/";
        gsubzPayload = {
          network: networkId,
          amount: Math.round(amount),
          mobile_number: recipient,
          Ported_number: true,
          airtime_type: "VTU",
        };
        break;

      case "DATA":
        gsubzEndpoint = "https://gsubz.com/api/data/";
        gsubzPayload = {
          network: networkId,
          mobile_number: recipient,
          plan: planId || "1",
          Ported_number: true,
        };
        break;

      case "ELECTRICITY":
      case "POWER":
        gsubzEndpoint = "https://gsubz.com/api/billpayment/";
        gsubzPayload = {
          disco_name: provider,
          amount: Math.round(amount),
          meter_number: meterNumber,
          MeterType: 1, // Prepaid
        };
        break;

      case "CABLE_TV":
      case "CABLE":
        gsubzEndpoint = "https://gsubz.com/api/cablesub/";
        gsubzPayload = {
          cablename: provider,
          cableplan: planId,
          smart_card_number: smartcardNumber,
        };
        break;

      case "EDUCATION":
      case "WAEC":
      case "JAMB":
        gsubzEndpoint = "https://gsubz.com/api/epin/";
        gsubzPayload = {
          exam_name: normProvider.includes("JAMB") ? "JAMB" : "WAEC",
          quantity: 1,
          phone: recipient,
        };
        break;

      default:
        gsubzEndpoint = "https://gsubz.com/api/topup/";
        gsubzPayload = {
          network: networkId,
          amount: Math.round(amount),
          mobile_number: recipient,
        };
        break;
    }

    // Invoke Gsubz external API
    const gsubzResponse = await fetch(gsubzEndpoint, {
      method: "POST",
      headers: {
        Authorization: `Token ${gsubzApiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(gsubzPayload),
    });

    const responseText = await gsubzResponse.text();
    let responseData: any = {};
    try {
      responseData = JSON.parse(responseText);
    } catch {
      responseData = { raw: responseText };
    }

    const rawStatus = (responseData.status || responseData.Status || "").toLowerCase();
    const isSuccess =
      gsubzResponse.ok &&
      rawStatus !== "fail" &&
      rawStatus !== "failed" &&
      rawStatus !== "error";

    const token =
      responseData.token ||
      responseData.pin ||
      responseData.meter_token ||
      responseData.purchased_code ||
      responseData.token_generated ||
      null;

    return new Response(
      JSON.stringify({
        success: isSuccess,
        status: isSuccess ? "SUCCESSFUL" : "FAILED",
        reference: txRef,
        gsubzRef: responseData.id || responseData.ident || responseData.ref || txRef,
        message: responseData.message || responseData.msg || (isSuccess ? "Transaction processed successfully via Gsubz" : "Failed to process Gsubz transaction"),
        token: token,
        service,
        provider: normProvider,
        recipient,
        amount,
        rawResponse: responseData,
      }),
      {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
        status: 200,
      }
    );
  } catch (error: any) {
    return new Response(
      JSON.stringify({
        success: false,
        error: error.message || "Failed to route VTU service to Gsubz",
      }),
      {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
        status: 500,
      }
    );
  }
});
