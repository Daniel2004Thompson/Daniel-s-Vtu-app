package com.example.data.remote

import com.example.auth.SupabaseProvider
import com.example.data.model.NetworkProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GsubzOrderResult(
    val isSuccess: Boolean,
    val isPending: Boolean = false,
    val status: String,
    val transactionId: String,
    val message: String,
    val amountPaid: Double,
    val airtimeValue: Double? = null,
    val cashback: Double? = null,
    val charged: Double? = null,
    val tokenOrPin: String? = null,
    val isLiveEdge: Boolean = false
)

class GsubzEdgeServiceClient(
    val supabaseUrl: String = defaultSupabaseUrl(),
    val supabaseAnonKey: String = defaultSupabaseAnonKey()
) {
    companion object {
        val DEFAULT_PROJECT_URL: String
            get() = SupabaseProvider.safeUrl
        val DEFAULT_ANON_KEY: String
            get() = SupabaseProvider.rawKey
        val EDGE_FUNCTION_URL: String
            get() = "${DEFAULT_PROJECT_URL}/functions/v1/Gsubz-VTU-Services"

        fun defaultSupabaseUrl(): String = SupabaseProvider.safeUrl

        fun defaultSupabaseAnonKey(): String = SupabaseProvider.rawKey
    }

    val isLiveConfigured: Boolean
        get() = SupabaseProvider.isConfigured

    /**
     * Resolves the active user JWT token from SupabaseProvider.client session or falls back to the anon key.
     */
    val resolvedAuthToken: String
        get() = SupabaseProvider.resolveSessionAccessToken()

    /**
     * Executes a VTU order strictly via the Supabase Edge Function `/functions/v1/Gsubz-VTU-Services`
     * using Ktor POST with the logged-in user's session access token via SupabaseProvider.client.
     */
    suspend fun executeVtuOrder(
        serviceType: String,
        provider: String,
        recipient: String,
        amount: Double,
        planId: String? = null,
        meterNumber: String? = null,
        smartcardNumber: String? = null,
        serviceIdOverride: String? = null,
        userToken: String? = null,
        userId: String? = null,
        userEmail: String? = null,
        userApiKey: String? = null
    ): GsubzOrderResult = withContext(Dispatchers.IO) {
        val effectiveToken = userToken ?: resolvedAuthToken
        val mappedServiceId = if (serviceType.equals("AIRTIME", ignoreCase = true)) {
            serviceIdOverride?.takeIf { it.isNotBlank() }
                ?: GsubzVtuService.resolveAirtimeServiceId(provider, effectiveToken)
        } else {
            serviceIdOverride?.takeIf { it.isNotBlank() }
                ?: mapToGsubzServiceId(serviceType, provider)
        }

        val effectivePlan = if (serviceType.equals("AIRTIME", ignoreCase = true)) {
            ""
        } else {
            planId ?: ""
        }

        val effectiveCustomerId = when (serviceType) {
            "ELECTRICITY" -> meterNumber ?: recipient
            "CABLE_TV" -> smartcardNumber ?: recipient
            else -> recipient
        }

        return@withContext GsubzVtuService.purchaseVtuService(
            serviceID = mappedServiceId,
            plan = effectivePlan,
            amount = amount,
            phone = recipient,
            customerID = effectiveCustomerId,
            userId = userId,
            userEmail = userEmail,
            userToken = effectiveToken,
            supabaseAnonKey = supabaseAnonKey
        )
    }

    suspend fun processVtuOrder(
        service: String,
        provider: String,
        recipient: String,
        amount: Double,
        planId: String? = null,
        meterNumber: String? = null,
        smartcardNumber: String? = null,
        serviceIdOverride: String? = null,
        userToken: String? = null,
        userId: String? = null,
        userEmail: String? = null,
        userApiKey: String? = null
    ): GsubzOrderResult = executeVtuOrder(
        serviceType = service,
        provider = provider,
        recipient = recipient,
        amount = amount,
        planId = planId,
        meterNumber = meterNumber,
        smartcardNumber = smartcardNumber,
        serviceIdOverride = serviceIdOverride,
        userToken = userToken,
        userId = userId,
        userEmail = userEmail,
        userApiKey = userApiKey
    )

    fun mapToGsubzServiceId(serviceType: String, provider: String): String {
        val p = provider.uppercase()
        return when (serviceType) {
            "AIRTIME" -> {
                val net = NetworkProvider.detectFromTextOrPhone(provider) ?: NetworkProvider.MTN
                GsubzVtuService.airtimePrices.value[net]?.serviceId ?: net.id
            }
            "DATA" -> when {
                p.contains("MTN") -> "mtn_sme"
                p.contains("AIRTEL") -> "airtel_cg"
                p.contains("GLO") -> "glo_data"
                p.contains("9MOBILE") -> "etisalat_data"
                else -> "mtn_sme"
            }
            "CABLE_TV" -> when {
                p.contains("DSTV") -> "dstv"
                p.contains("GOTV") -> "gotv"
                p.contains("STARTIMES") -> "startimes"
                p.contains("SHOWMAX") -> "showmax"
                else -> "dstv"
            }
            "ELECTRICITY" -> when {
                p.contains("IKEDC") || p.contains("IKEJA") -> "ikeja-electric"
                p.contains("EKEDC") || p.contains("EKO") -> "eko-electric"
                p.contains("AEDC") || p.contains("ABUJA") -> "abuja-electric"
                p.contains("IBEDC") || p.contains("IBADAN") -> "ibadan-electric"
                p.contains("KEDCO") || p.contains("KANO") -> "kano-electric"
                p.contains("PHED") || p.contains("PORT HARCOURT") -> "portharcourt-electric"
                p.contains("JED") || p.contains("JOS") -> "jos-electric"
                p.contains("KAEDCO") || p.contains("KADUNA") -> "kaduna-electric"
                p.contains("EEDC") || p.contains("ENUGU") -> "enugu-electric"
                p.contains("BEDC") || p.contains("BENIN") -> "benin-electric"
                else -> "ikeja-electric"
            }
            "EDUCATION" -> when {
                p.contains("WAEC") -> "waec"
                p.contains("NECO") -> "neco"
                p.contains("NABTEB") -> "nabteb"
                p.contains("JAMB") -> "jamb"
                else -> "waec"
            }
            else -> serviceType.lowercase()
        }
    }
}
