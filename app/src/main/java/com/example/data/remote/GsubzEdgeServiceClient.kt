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
    val newBalance: Double? = null,
    val tokenOrPin: String? = null,
    val serviceName: String? = null,
    val serviceImageUrl: String? = null,
    val narration: String? = null,
    val apiResponse: String? = null,
    val requestId: String? = null,
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
        narration: String? = null,
        userToken: String? = null,
        userId: String? = null,
        userEmail: String? = null,
        userApiKey: String? = null
    ): GsubzOrderResult = withContext(Dispatchers.IO) {
        val effectiveToken = userToken ?: resolvedAuthToken
        val rawMappedServiceId = if (serviceType.equals("AIRTIME", ignoreCase = true)) {
            serviceIdOverride?.takeIf { it.isNotBlank() }
                ?: GsubzVtuService.resolveAirtimeServiceId(provider, effectiveToken)
        } else {
            serviceIdOverride?.takeIf { it.isNotBlank() }
                ?: mapToGsubzServiceId(serviceType, provider)
        }
        val mappedServiceId = if (serviceType.equals("DATA", ignoreCase = true)) {
            normalizeActiveDataServiceId(rawMappedServiceId, provider)
        } else {
            rawMappedServiceId
        }

        val edgeServiceCategory = mapToEdgeServiceCategory(serviceType)
        val edgeNetwork = mapToEdgeNetwork(serviceType, provider, mappedServiceId)

        val effectivePlan = when {
            serviceType.equals("AIRTIME", ignoreCase = true) -> ""
            serviceType.equals("ELECTRICITY", ignoreCase = true) -> {
                planId?.takeIf { it.isNotBlank() } ?: "prepaid"
            }
            serviceType.equals("EDUCATION", ignoreCase = true) -> {
                planId?.takeIf { it.isNotBlank() } ?: edgeNetwork.uppercase()
            }
            else -> planId ?: ""
        }

        val effectiveCustomerId = when (serviceType.uppercase()) {
            "ELECTRICITY" -> meterNumber?.takeIf { it.isNotBlank() } ?: recipient
            "CABLE_TV", "CABLE" -> smartcardNumber?.takeIf { it.isNotBlank() } ?: recipient
            else -> recipient
        }

        return@withContext GsubzVtuService.purchaseVtuService(
            serviceID = mappedServiceId,
            service = edgeServiceCategory,
            network = edgeNetwork,
            plan = effectivePlan,
            amount = amount,
            phone = recipient,
            customerID = effectiveCustomerId,
            meterNumber = meterNumber ?: (if (serviceType.equals("ELECTRICITY", ignoreCase = true)) recipient else ""),
            smartcardNumber = smartcardNumber ?: (if (serviceType.equals("CABLE_TV", ignoreCase = true) || serviceType.equals("CABLE", ignoreCase = true)) recipient else ""),
            narration = narration,
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
        narration: String? = null,
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
        narration = narration,
        userToken = userToken,
        userId = userId,
        userEmail = userEmail,
        userApiKey = userApiKey
    )

    private fun normalizeActiveDataServiceId(serviceId: String, provider: String = ""): String {
        return when (serviceId.trim().lowercase()) {
            "mtn_cg", "mtn_cg_lite", "mtn_cglite", "mtn_corporate", "mtn_data", "mtn_sme" -> "mtn_sme"
            "mtn_gifting", "mtn_coupon", "mtn_awoof" -> "mtn_gifting"
            "airtel_cg", "airtel_sme", "airtel_corporate" -> "airtel_sme"
            "airtel_gifting", "airtel_data", "airtel_awoof" -> "airtel_gifting"
            "glo_cg", "glo_sme", "glo_corporate" -> "glo_sme"
            "glo_data", "glo_gifting" -> "glo_data"
            "etisalat_data", "9mobile_data", "9mobile_sme", "etisalat_sme" -> "etisalat_data"
            else -> serviceId.trim().lowercase().ifBlank { mapToGsubzServiceId("DATA", provider) }
        }
    }

    /**
     * Maps the app's internal serviceType to the `service_type` / `service` parameter accepted by
     * the updated `Gsubz-VTU-Services` edge function ("airtime", "data", "cable", "electricity", "education").
     */
    fun mapToEdgeServiceCategory(serviceType: String): String {
        return when (serviceType.trim().uppercase()) {
            "AIRTIME" -> "airtime"
            "DATA" -> "data"
            "CABLE_TV", "CABLE", "TV" -> "cable"
            "ELECTRICITY", "POWER", "METER" -> "electricity"
            "EDUCATION", "EDU", "EXAM", "RESULT_CHECKER" -> "education"
            else -> serviceType.trim().lowercase()
        }
    }

    /**
     * Resolves the `network` identifier expected by the updated `Gsubz-VTU-Services` edge function.
     */
    fun mapToEdgeNetwork(serviceType: String, provider: String, mappedServiceId: String = ""): String {
        val p = provider.trim().uppercase()
        val sId = mappedServiceId.trim().lowercase()
        return when (serviceType.trim().uppercase()) {
            "AIRTIME" -> when {
                sId.isNotBlank() -> sId
                p.contains("MTN") -> "mtn"
                p.contains("AIRTEL") -> "airtel"
                p.contains("GLO") -> "glo"
                p.contains("9MOBILE") || p.contains("ETISALAT") -> "etisalat"
                else -> (NetworkProvider.detectFromTextOrPhone(provider) ?: NetworkProvider.MTN).id
            }
            "DATA" -> when {
                sId.isNotBlank() && !sId.equals("data", ignoreCase = true) -> normalizeActiveDataServiceId(sId, provider)
                p.contains("MTN") && p.contains("GIFTING") -> "mtn_gifting"
                p.contains("MTN") -> "mtn_sme"
                p.contains("AIRTEL") && p.contains("SME") -> "airtel_sme"
                p.contains("AIRTEL") -> "airtel_gifting"
                p.contains("GLO") && p.contains("SME") -> "glo_sme"
                p.contains("GLO") -> "glo_data"
                p.contains("9MOBILE") || p.contains("ETISALAT") -> "etisalat_data"
                else -> "mtn_sme"
            }
            "CABLE_TV", "CABLE" -> when {
                p.contains("DSTV") || sId == "dstv" -> "dstv"
                p.contains("GOTV") || sId == "gotv" -> "gotv"
                p.contains("STARTIMES") || sId == "startimes" -> "startimes"
                p.contains("SHOWMAX") || sId == "showmax" -> "showmax"
                sId.isNotBlank() -> sId
                else -> "dstv"
            }
            "ELECTRICITY" -> mapToGsubzServiceId("ELECTRICITY", provider.ifBlank { sId })
            "EDUCATION" -> when {
                p.contains("WAEC") || sId == "waec" -> "waec"
                p.contains("NECO") || sId == "neco" -> "neco"
                p.contains("NABTEB") || sId == "nabteb" -> "nabteb"
                p.contains("JAMB") || sId == "jamb" -> "jamb"
                sId.isNotBlank() -> sId
                else -> "waec"
            }
            else -> sId.ifBlank { provider.trim().lowercase() }
        }
    }

    fun mapToGsubzServiceId(serviceType: String, provider: String): String {
        val p = provider.uppercase()
        return when (serviceType.uppercase()) {
            "AIRTIME" -> {
                val net = NetworkProvider.detectFromTextOrPhone(provider) ?: NetworkProvider.MTN
                GsubzVtuService.airtimePrices.value[net]?.serviceId ?: net.id
            }
            "DATA" -> when {
                p.contains("MTN") && p.contains("GIFTING") -> "mtn_gifting"
                p.contains("MTN") -> "mtn_sme"
                p.contains("AIRTEL") && p.contains("SME") -> "airtel_sme"
                p.contains("AIRTEL") -> "airtel_gifting"
                p.contains("GLO") && p.contains("SME") -> "glo_sme"
                p.contains("GLO") -> "glo_data"
                p.contains("9MOBILE") || p.contains("ETISALAT") -> "etisalat_data"
                else -> "mtn_sme"
            }
            "CABLE_TV", "CABLE" -> when {
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
                p.contains("PHED") || p.contains("PORT HARCOURT") || p.contains("PORTHARCOURT") -> "portharcourt-electric"
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
