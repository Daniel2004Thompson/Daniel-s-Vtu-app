// Supabase Edge Function: Flutterwave Payment Gateway
// Handles Payment Initialization, Verification, and Dedicated Virtual Account Generation
// Secrets required: FLUTTERWAVE_SECRET_KEY (and optionally FLUTTERWAVE_PUBLIC_KEY)

import { serve } from "https://deno.land/std@0.177.0/http/server.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
};

interface PaymentRequest {
  action: "initialize" | "verify" | "virtual_account";
  amount?: number;
  email?: string;
  name?: string;
  phone?: string;
  nin?: string;
  tx_ref?: string;
  transaction_id?: string;
  redirect_url?: string;
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const flwSecretKey = Deno.env.get("FLUTTERWAVE_SECRET_KEY");
    if (!flwSecretKey) {
      return new Response(
        JSON.stringify({
          success: false,
          error: "FLUTTERWAVE_SECRET_KEY secret is not configured in Supabase project secrets.",
          isConfigured: false,
        }),
        {
          headers: { ...corsHeaders, "Content-Type": "application/json" },
          status: 400,
        }
      );
    }

    const body: PaymentRequest = await req.json();
    const { action, amount, email, name, phone, tx_ref, transaction_id, redirect_url } = body;

    const reference = tx_ref || `FLW-VTU-${Date.now()}-${Math.floor(100000 + Math.random() * 900000)}`;

    if (action === "initialize") {
      // Initialize Standard Flutterwave Checkout Link
      const payload = {
        tx_ref: reference,
        amount: String(amount || 1000),
        currency: "NGN",
        redirect_url: redirect_url || "https://vtu-app.flutterwave.com/callback",
        customer: {
          email: email || "customer@vtu.com",
          phonenumber: phone || "08012345678",
          name: name || "Daniel VTU User",
        },
        customizations: {
          title: "Daniel VTU Wallet Top-up",
          description: `Credit ₦${amount || 1000} to VTU balance`,
          logo: "https://raw.githubusercontent.com/flutterwave/flutterwave-android/master/logo.png",
        },
      };

      const flwRes = await fetch("https://api.flutterwave.com/v3/payments", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${flwSecretKey}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(payload),
      });

      const resData = await flwRes.json();
      if (resData.status === "success" && resData.data?.link) {
        return new Response(
          JSON.stringify({
            success: true,
            payment_url: resData.data.link,
            tx_ref: reference,
            message: "Flutterwave checkout initialized successfully",
          }),
          {
            headers: { ...corsHeaders, "Content-Type": "application/json" },
            status: 200,
          }
        );
      } else {
        return new Response(
          JSON.stringify({
            success: false,
            error: resData.message || "Failed to initialize Flutterwave payment",
            details: resData,
          }),
          {
            headers: { ...corsHeaders, "Content-Type": "application/json" },
            status: 400,
          }
        );
      }
    } else if (action === "verify") {
      // Verify payment by reference or transaction id
      const verifyEndpoint = transaction_id
        ? `https://api.flutterwave.com/v3/transactions/${transaction_id}/verify`
        : `https://api.flutterwave.com/v3/transactions/verify_by_reference?tx_ref=${encodeURIComponent(reference)}`;

      const flwRes = await fetch(verifyEndpoint, {
        method: "GET",
        headers: {
          Authorization: `Bearer ${flwSecretKey}`,
          "Content-Type": "application/json",
        },
      });

      const resData = await flwRes.json();
      const isSuccessful =
        resData.status === "success" &&
        resData.data?.status === "successful";

      return new Response(
        JSON.stringify({
          success: isSuccessful,
          status: resData.data?.status || "failed",
          amount: resData.data?.amount,
          currency: resData.data?.currency || "NGN",
          tx_ref: resData.data?.tx_ref || reference,
          flw_ref: resData.data?.flw_ref,
          customer: resData.data?.customer,
          message: resData.message || (isSuccessful ? "Payment verified successfully" : "Payment verification failed"),
        }),
        {
          headers: { ...corsHeaders, "Content-Type": "application/json" },
          status: 200,
        }
      );
    } else if (action === "virtual_account") {
      // Generate Flutterwave Virtual Account for automated transfer without requiring BVN
      const names = (name || "").trim().split(" ").filter((p: string) => p.length > 0);
      const firstName = names[0] || "";
      const lastName = names.length > 1 ? names.slice(1).join(" ") : firstName;

      const userNameFormatted = (name || firstName || "").trim().toUpperCase();
      const vaPayload: Record<string, any> = {
        email: email || "",
        is_permanent: true,
        tx_ref: reference,
        phonenumber: phone || "",
        firstname: firstName,
        lastname: lastName,
        narration: (name || firstName).trim(),
      };
      if (body.nin) {
        vaPayload.nin = body.nin;
      }

      const flwRes = await fetch("https://api.flutterwave.com/v3/virtual-account-numbers", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${flwSecretKey}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(vaPayload),
      });

      const resData = await flwRes.json();
      const rawBank = resData.data?.bank_name || "";
      const bankName = rawBank;
      const acctName = resData.data?.account_name || userNameFormatted;

      return new Response(
        JSON.stringify({
          success: resData.status === "success",
          data: {
            account_number: resData.data?.account_number || resData.data?.order_ref,
            bank_name: bankName,
            account_name: acctName,
            flw_ref: resData.data?.flw_ref,
          },
          message: resData.message || (resData.status === "success" ? "Virtual account generated successfully" : "Failed to generate virtual account"),
        }),
        {
          headers: { ...corsHeaders, "Content-Type": "application/json" },
          status: 200,
        }
      );
    } else {
      return new Response(
        JSON.stringify({ success: false, error: `Invalid action: ${action}` }),
        {
          headers: { ...corsHeaders, "Content-Type": "application/json" },
          status: 400,
        }
      );
    }
  } catch (error: any) {
    return new Response(
      JSON.stringify({
        success: false,
        error: error.message || "Failed to process Flutterwave request",
      }),
      {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
        status: 500,
      }
    );
  }
});
