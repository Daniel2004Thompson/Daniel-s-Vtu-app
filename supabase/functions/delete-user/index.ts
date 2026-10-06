// Supabase Edge Function: delete-user
// Permanently removes a user account from Supabase Authentication (`auth.users`)
// Using the service role key so they can register afresh with that same email.

import { serve } from "https://deno.land/std@0.177.0/http/server.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, DELETE, OPTIONS",
};

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") || "https://yjymxdzdhvbdjramlipg.supabase.co";
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY") || "";

    if (!serviceRoleKey) {
      return new Response(
        JSON.stringify({ error: "SUPABASE_SERVICE_ROLE_KEY is not configured in Edge Function secrets" }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" }, status: 500 }
      );
    }

    let targetUserId = "";
    try {
      const body = await req.json();
      targetUserId = body.userId || body.user_id || "";
    } catch {
      // Body may be omitted on DELETE
    }

    // If userId not provided in body, determine it from the user's Authorization Bearer token
    if (!targetUserId) {
      const authHeader = req.headers.get("Authorization");
      if (authHeader) {
        const token = authHeader.replace("Bearer ", "").trim();
        const userRes = await fetch(`${supabaseUrl}/auth/v1/user`, {
          headers: {
            Authorization: `Bearer ${token}`,
            apikey: anonKey || serviceRoleKey,
          },
        });
        if (userRes.ok) {
          const uData = await userRes.json();
          targetUserId = uData.id;
        }
      }
    }

    if (!targetUserId) {
      return new Response(
        JSON.stringify({ error: "Unable to identify user. Provide userId in body or Authorization token." }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" }, status: 400 }
      );
    }

    // Call Supabase Admin API to delete the user permanently from auth.users
    const adminDelRes = await fetch(`${supabaseUrl}/auth/v1/admin/users/${targetUserId}`, {
      method: "DELETE",
      headers: {
        Authorization: `Bearer ${serviceRoleKey}`,
        apikey: serviceRoleKey,
      },
    });

    const delText = await adminDelRes.text();
    if (!adminDelRes.ok) {
      return new Response(
        JSON.stringify({ error: `Admin delete failed: ${delText}` }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" }, status: adminDelRes.status }
      );
    }

    return new Response(
      JSON.stringify({
        success: true,
        message: "User permanently removed from Supabase Authentication",
      }),
      { headers: { ...corsHeaders, "Content-Type": "application/json" }, status: 200 }
    );
  } catch (error: any) {
    return new Response(
      JSON.stringify({ error: error.message || "Failed to delete user" }),
      { headers: { ...corsHeaders, "Content-Type": "application/json" }, status: 500 }
    );
  }
});
