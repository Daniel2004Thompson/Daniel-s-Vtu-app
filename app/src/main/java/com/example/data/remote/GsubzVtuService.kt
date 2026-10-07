package com.example.data.remote

import android.util.Log
import com.example.auth.SupabaseProvider
import com.example.data.model.AirtimeNetworkPricing
import com.example.data.model.NetworkProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@Serializable
data class GsubzVtuRequest(
    val serviceID: String,
    @SerialName("service_id") val serviceId: String = "",
    val amount: JsonPrimitive,
    val phone: String,
    val plan: String = "",
    @SerialName("plan_id") val planId: String = "",
    val service: String = "airtime",
    val network: String = "",
    @SerialName("user_id") val userId: String = "",
    val email: String = "",
    val narration: String = "",
    val customerID: String = "",
    @SerialName("customer_id") val customerId: String = "",
    @SerialName("meter_number") val meterNumber: String = "",
    val iuc: String = ""
)

@Serializable
data class GsubzPlanItem(
    val displayName: String = "",
    val value: String = "",
    val price: String = "",
    val apiPrice: String = ""
)

@Serializable
data class GsubzContentPayload(
    val requestID: String? = null,
    val transactionID: String? = null,
    val serviceID: String? = null,
    val status: String? = null,
    val description: String? = null,
    val code: String? = null,
    val serviceName: String? = null,
    val image: String? = null,
    val amountPaid: Double? = null,
    val token: String? = null,
    val pin: String? = null,
    @SerialName("purchased_code") val purchasedCode: String? = null
)

@Serializable
data class GsubzDataEnvelope(
    val code: String? = null,
    val status: String? = null,
    val description: String? = null,
    @SerialName("api_response") val apiResponse: String? = null,
    val content: GsubzContentPayload? = null
)

@Serializable
data class GsubzVtuResponse(
    val success: Boolean? = null,
    @SerialName("user_id") val userId: String? = null,
    val email: String? = null,
    val narration: String? = null,
    val code: Int? = null,
    val status: String? = null,
    val description: String? = null,
    val message: String? = null,
    val error: String? = null,
    val pending: Boolean? = null,
    val airtimeValue: Double? = null,
    val cashback: Double? = null,
    val charged: Double? = null,
    @SerialName("gsubz_data") val gsubzData: GsubzDataEnvelope? = null,
    val content: GsubzContentPayload? = null,
    @SerialName("new_balance") val newBalance: Double? = null,
    @SerialName("wallet_balance") val walletBalance: Double? = null,
    val transactionID: String? = null,
    val token: String? = null,
    val pin: String? = null
)

object GsubzVtuService {
    private const val TAG = "GsubzVtuService"

    val SUPABASE_URL: String
        get() = SupabaseProvider.safeUrl
    const val FUNCTION_NAME = "Gsubz-VTU-Services"
    val FUNCTION_URL: String
        get() = "$SUPABASE_URL/functions/v1/$FUNCTION_NAME"

    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val _airtimePrices = MutableStateFlow<Map<NetworkProvider, AirtimeNetworkPricing>>(emptyMap())
    val airtimePrices: StateFlow<Map<NetworkProvider, AirtimeNetworkPricing>> = _airtimePrices.asStateFlow()

    private val _liveServicePlans = MutableStateFlow<Map<String, List<GsubzPlanItem>>>(emptyMap())
    val liveServicePlans: StateFlow<Map<String, List<GsubzPlanItem>>> = _liveServicePlans.asStateFlow()

    val vtuManager: VtuManager by lazy {
        VtuManager()
    }

    @Volatile
    var lastOutgoingRequestBodyString: String = ""

    /**
     * Fetches plans for a specific VTU service strictly from the user's Supabase backend
     * (`vtu_prices` table and `Gsubz-VTU-Services` edge function at
     * `https://yjymxdzdhvbdjramlipg.supabase.co/functions/v1/Gsubz-VTU-Services`),
     * with no external Gsubz API URLs.
     */
    suspend fun fetchLivePlansForService(serviceId: String): List<GsubzPlanItem> = withContext(Dispatchers.IO) {
        val cleanId = serviceId.trim().lowercase()
        if (cleanId.isBlank()) return@withContext emptyList()

        val baseUrl = SUPABASE_URL.trimEnd('/')
        val anonKey = SupabaseProvider.rawKey
        val activeToken = SupabaseProvider.resolveSessionAccessToken(null)
        val bearer = if (activeToken.startsWith("Bearer ", ignoreCase = true)) activeToken else "Bearer $activeToken"

        // 1. Query Supabase vtu_prices table for active plans belonging to this service_id
        try {
            val url = "$baseUrl/rest/v1/vtu_prices?select=service_id,plan,price,cashback_percent,active&service_id=eq.$cleanId"
            val req = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", bearer)
                .get()
                .build()
            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string().orEmpty()
                    val arr = JSONArray(body)
                    val items = mutableListOf<GsubzPlanItem>()
                    for (i in 0 until arr.length()) {
                        val row = arr.optJSONObject(i) ?: continue
                        if (row.has("active") && !row.isNull("active") && !row.optBoolean("active", true)) continue
                        val planCode = row.optString("plan", "").trim()
                        if (planCode.isBlank()) continue
                        val priceVal = row.optDouble("price", Double.NaN)
                        val priceStr = if (!priceVal.isNaN() && priceVal > 0.0) {
                            if (priceVal % 1.0 == 0.0) priceVal.toLong().toString() else priceVal.toString()
                        } else {
                            row.optString("price", "").trim()
                        }
                        if (priceStr.isNotBlank()) {
                            items.add(
                                GsubzPlanItem(
                                    displayName = planCode,
                                    value = planCode,
                                    price = priceStr
                                )
                            )
                        }
                    }
                    if (items.isNotEmpty()) {
                        _liveServicePlans.value = _liveServicePlans.value + (cleanId to items)
                        return@withContext items
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error querying Supabase vtu_prices for $cleanId: ${e.message}")
        }

        // 2. Fallback to catalog plans configured for this Gsubz service ID
        val catalogItems = com.example.data.model.VtuCatalog.dataPlans
            .filter { it.gsubzServiceId.equals(cleanId, ignoreCase = true) }
            .map { plan ->
                val priceStr = if (plan.price % 1.0 == 0.0) plan.price.toLong().toString() else plan.price.toString()
                GsubzPlanItem(
                    displayName = "${plan.dataAmount} - ${plan.validity}",
                    value = plan.planCode,
                    price = priceStr
                )
            }
            .ifEmpty {
                com.example.data.model.VtuCatalog.cableProviders
                    .firstOrNull { it.gsubzServiceId.equals(cleanId, ignoreCase = true) }
                    ?.bouquets
                    ?.map { b ->
                        val priceStr = if (b.price % 1.0 == 0.0) b.price.toLong().toString() else b.price.toString()
                        GsubzPlanItem(
                            displayName = b.name,
                            value = b.planCode,
                            price = priceStr
                        )
                    }
                    .orEmpty()
            }
            .ifEmpty {
                com.example.data.model.VtuCatalog.educationExams
                    .filter { it.gsubzServiceId.equals(cleanId, ignoreCase = true) }
                    .map { exam ->
                        val priceStr = if (exam.price % 1.0 == 0.0) exam.price.toLong().toString() else exam.price.toString()
                        GsubzPlanItem(
                            displayName = exam.name,
                            value = exam.planCode,
                            price = priceStr
                        )
                    }
            }

        if (catalogItems.isNotEmpty()) {
            _liveServicePlans.value = _liveServicePlans.value + (cleanId to catalogItems)
            return@withContext catalogItems
        }

        return@withContext _liveServicePlans.value[cleanId].orEmpty()
    }

    /**
     * Parses Gsubz plans response JSON, supporting both `"plans"` (used by Data & Education services)
     * and `"list"` (used by Cable TV services like DStv, GOtv, StarTimes), as well as both
     * `"displayName"` and `"display_name"`.
     */
    fun parseGsubzPlansJson(rawJson: String): List<GsubzPlanItem> {
        if (rawJson.isBlank()) return emptyList()
        val list = mutableListOf<GsubzPlanItem>()
        try {
            val root = JSONObject(rawJson)
            val plansArr = root.optJSONArray("plans")
                ?: root.optJSONArray("list")
                ?: return emptyList()
            for (i in 0 until plansArr.length()) {
                val item = plansArr.optJSONObject(i) ?: continue
                val displayName = item.optString("displayName", "")
                    .ifBlank { item.optString("display_name", "") }
                    .trim()
                val value = item.optString("value", "").trim()
                val price = item.optString("price", "").trim()
                val apiPrice = item.optString("api_price", "").trim()
                if (value.isNotBlank()) {
                    list.add(
                        GsubzPlanItem(
                            displayName = displayName.ifBlank { value },
                            value = value,
                            price = price,
                            apiPrice = apiPrice
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Gsubz plans JSON: ${e.message}")
        }
        return list
    }

    /**
     * Reads cashback_percent and service_id for each network strictly from the Supabase vtu_prices table
     * (rows where plan = '').
     */
    suspend fun fetchAirtimePricesFromVtuPrices(
        accessToken: String? = null,
        supabaseAnonKey: String = SupabaseProvider.rawKey
    ): Map<NetworkProvider, AirtimeNetworkPricing> = withContext(Dispatchers.IO) {
        val baseUrl = SUPABASE_URL.trimEnd('/')
        val anonKey = supabaseAnonKey.ifBlank { SupabaseProvider.rawKey }
        val activeToken = SupabaseProvider.resolveSessionAccessToken(accessToken)
        val bearer = if (activeToken.startsWith("Bearer ", ignoreCase = true)) activeToken else "Bearer $activeToken"

        val candidateUrls = listOf(
            "$baseUrl/rest/v1/vtu_prices?select=service_id,plan,cashback_percent,active&plan=eq.",
            "$baseUrl/rest/v1/vtu_prices?select=service_id,plan,cashback_percent&plan=eq.",
            "$baseUrl/rest/v1/vtu_prices?select=service_id,plan,cashback_percent"
        )

        for (url in candidateUrls) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", bearer)
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string().orEmpty()
                        val parsed = parseAirtimePricesJson(body)
                        if (parsed.isNotEmpty()) {
                            _airtimePrices.value = _airtimePrices.value + parsed
                            return@withContext _airtimePrices.value
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Error querying vtu_prices via HTTP: ${e.message}")
            }
        }

        // Fallback via SupabaseProvider.client Postgrest
        try {
            val client = SupabaseProvider.client
            if (client != null) {
                val rows = client.from("vtu_prices")
                    .select(Columns.list("service_id", "plan", "cashback_percent")) {
                        filter { eq("plan", "") }
                    }
                    .decodeList<JsonObject>()
                val arr = JSONArray()
                for (row in rows) {
                    val obj = JSONObject()
                    obj.put("service_id", row["service_id"]?.jsonPrimitive?.contentOrNull ?: "")
                    obj.put("plan", row["plan"]?.jsonPrimitive?.contentOrNull ?: "")
                    val cb = row["cashback_percent"]?.jsonPrimitive?.doubleOrNull
                        ?: row["cashback_percent"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                        ?: 0.0
                    obj.put("cashback_percent", cb)
                    arr.put(obj)
                }
                val parsed = parseAirtimePricesJson(arr.toString())
                if (parsed.isNotEmpty()) {
                    _airtimePrices.value = _airtimePrices.value + parsed
                    return@withContext _airtimePrices.value
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error querying vtu_prices via Postgrest: ${e.message}")
        }

        return@withContext _airtimePrices.value
    }

    /**
     * Parses a JSON array from `vtu_prices` (filtering rows where `plan == ""`),
     * mapping each network to its exact `service_id` and `cashback_percent` from the table.
     */
    fun parseAirtimePricesJson(rawJson: String): Map<NetworkProvider, AirtimeNetworkPricing> {
        if (rawJson.isBlank()) return emptyMap()
        val result = mutableMapOf<NetworkProvider, AirtimeNetworkPricing>()
        val unmatchedAirtimeRows = mutableListOf<Pair<String, Double>>()

        try {
            val array = JSONArray(rawJson)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val plan = if (obj.isNull("plan")) "" else obj.optString("plan", "")
                if (plan.isNotEmpty()) continue

                if (obj.has("active") && !obj.isNull("active") && !obj.optBoolean("active", true)) {
                    continue
                }

                val serviceId = obj.optString("service_id", "").trim()
                if (serviceId.isBlank()) continue

                val rawCashback = obj.optDouble("cashback_percent", Double.NaN)
                val cashbackPercent = if (!rawCashback.isNaN()) {
                    rawCashback
                } else {
                    obj.optString("cashback_percent", "0").toDoubleOrNull() ?: 0.0
                }

                val lowerId = serviceId.lowercase()
                val matchedNetwork = when {
                    lowerId == "mtn" || lowerId.startsWith("mtn") -> NetworkProvider.MTN
                    lowerId == "airtel" || lowerId.startsWith("airtel") -> NetworkProvider.AIRTEL
                    lowerId == "glo" || lowerId.startsWith("glo") -> NetworkProvider.GLO
                    lowerId.contains("9mobile") ||
                        lowerId.contains("etisalat") ||
                        lowerId.contains("9mob") ||
                        lowerId.contains("emts") ||
                        lowerId.contains("t2") -> NetworkProvider.NINEMOBILE
                    else -> null
                }

                if (matchedNetwork != null) {
                    result[matchedNetwork] = AirtimeNetworkPricing(
                        network = matchedNetwork,
                        serviceId = serviceId,
                        cashbackPercent = cashbackPercent
                    )
                } else {
                    unmatchedAirtimeRows.add(serviceId to cashbackPercent)
                }
            }

            if (!result.containsKey(NetworkProvider.NINEMOBILE) && unmatchedAirtimeRows.isNotEmpty()) {
                val (fallbackId, fallbackCb) = unmatchedAirtimeRows.first()
                result[NetworkProvider.NINEMOBILE] = AirtimeNetworkPricing(
                    network = NetworkProvider.NINEMOBILE,
                    serviceId = fallbackId,
                    cashbackPercent = fallbackCb
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse vtu_prices JSON: ${e.message}")
        }
        return result
    }

    fun updateCachedAirtimePrices(prices: Map<NetworkProvider, AirtimeNetworkPricing>) {
        _airtimePrices.value = prices
    }

    /**
     * Resolves the exact serviceID from vtu_prices for the given network provider.
     */
    suspend fun resolveAirtimeServiceId(
        providerOrNetwork: String,
        accessToken: String? = null
    ): String {
        val network = NetworkProvider.detectFromTextOrPhone(providerOrNetwork) ?: when {
            providerOrNetwork.contains("mtn", ignoreCase = true) -> NetworkProvider.MTN
            providerOrNetwork.contains("airtel", ignoreCase = true) -> NetworkProvider.AIRTEL
            providerOrNetwork.contains("glo", ignoreCase = true) -> NetworkProvider.GLO
            providerOrNetwork.contains("9mobile", ignoreCase = true) ||
                providerOrNetwork.contains("etisalat", ignoreCase = true) -> NetworkProvider.NINEMOBILE
            else -> null
        }

        if (network != null) {
            val cached = _airtimePrices.value[network]?.serviceId?.takeIf { it.isNotBlank() }
            if (cached != null) return cached

            val fetched = fetchAirtimePricesFromVtuPrices(accessToken)
            val fromTable = fetched[network]?.serviceId?.takeIf { it.isNotBlank() }
            if (fromTable != null) return fromTable

            return network.id
        }

        return providerOrNetwork.trim().lowercase()
    }

    /**
     * Infers the `service` category ("airtime", "data", "cable", "electricity") from serviceID and plan
     * when not explicitly provided.
     */
    fun inferEdgeServiceCategory(serviceID: String, plan: String = ""): String {
        val lower = serviceID.trim().lowercase()
        return when {
            lower.contains("electric") -> "electricity"
            lower in setOf("waec", "neco", "nabteb", "jamb") -> "education"
            lower in setOf("dstv", "gotv", "startimes", "showmax") -> "cable"
            lower.contains("_sme") || lower.contains("_cg") || lower.contains("_data") || lower.contains("_gifting") -> "data"
            plan.isNotBlank() -> "data"
            else -> "airtime"
        }
    }

    /**
     * Builds the JSON request body for Gsubz-VTU-Services:
     * - "amount" is ALWAYS a JSON NUMBER equal to the value chosen (e.g. 100), never discounted and never a string.
     * - "service_type" and "service" are the edge function service type ("airtime", "data", "cable", "electricity", "education").
     * - "serviceID" and "service_id" are the exact service_id from vtu_prices / catalog.
     * - "network" is the target network/provider identifier.
     * - "user_id" and "email" are the required identity fields for the updated Gsubz-VTU-Services function.
     * - "phone" is the recipient phone number.
     * - "plan" is empty ("") for airtime, and "plan", "plan_id", and "variation_code" are populated for plan-based services.
     */
    fun buildRequestJson(
        serviceID: String,
        amount: Double,
        phone: String,
        plan: String = "",
        customerID: String = "",
        service: String = "",
        network: String = "",
        userId: String? = null,
        userEmail: String? = null,
        narration: String? = null,
        meterNumber: String = "",
        smartcardNumber: String = "",
        userToken: String? = null
    ): String {
        val cleanPhone = phone.trim()
        val amountNumberPrimitive: JsonPrimitive = if (amount % 1.0 == 0.0) {
            JsonPrimitive(amount.toLong())
        } else {
            JsonPrimitive(amount)
        }

        val resolvedService = service.trim().lowercase().ifBlank {
            inferEdgeServiceCategory(serviceID, plan)
        }
        val resolvedNetwork = network.trim().lowercase().ifBlank {
            serviceID.trim().lowercase()
        }

        val sessionUser = try {
            SupabaseProvider.client?.auth?.currentUserOrNull()
        } catch (_: Throwable) {
            null
        }
        val resolvedUserId = userId?.trim()?.takeIf { it.isNotBlank() }
            ?: sessionUser?.id?.takeIf { it.isNotBlank() }
            ?: com.example.util.JwtUtils.getUserIdFromJwt(userToken)
            ?: "00000000-0000-0000-0000-000000000000"
        val resolvedEmail = userEmail?.trim()?.takeIf { it.isNotBlank() }
            ?: sessionUser?.email?.takeIf { it.isNotBlank() }
            ?: com.example.util.JwtUtils.getEmailFromJwt(userToken)
            ?: "user@danielvtu.app"

        val resolvedServiceId = serviceID.trim().lowercase().ifBlank { resolvedNetwork }
        val effectivePlan = if (resolvedService == "electricity" && plan.isBlank()) "prepaid" else plan

        return buildJsonObject {
            put("user_id", JsonPrimitive(resolvedUserId))
            put("email", JsonPrimitive(resolvedEmail))
            put("service_type", JsonPrimitive(resolvedService))
            put("service", JsonPrimitive(resolvedService))
            put("service_id", JsonPrimitive(resolvedServiceId))
            put("network", JsonPrimitive(resolvedNetwork))
            put("serviceID", JsonPrimitive(serviceID))
            put("amount", amountNumberPrimitive)
            put("phone", JsonPrimitive(cleanPhone))
            if (!narration.isNullOrBlank()) {
                put("narration", JsonPrimitive(narration.trim()))
            }
            if (effectivePlan.isNotBlank()) {
                put("plan", JsonPrimitive(effectivePlan))
                put("plan_id", JsonPrimitive(effectivePlan))
                put("variation_code", JsonPrimitive(effectivePlan))
            } else {
                put("plan", JsonPrimitive(""))
            }
            if (resolvedService == "electricity") {
                put("meter_type", JsonPrimitive(effectivePlan.ifBlank { "prepaid" }))
            }
            val effectiveCustomer = customerID.trim().ifBlank {
                meterNumber.trim().ifBlank { smartcardNumber.trim() }
            }
            if (effectiveCustomer.isNotBlank()) {
                put("customer_id", JsonPrimitive(effectiveCustomer))
                put("customerID", JsonPrimitive(effectiveCustomer))
            }
            if (meterNumber.isNotBlank()) {
                put("meter_number", JsonPrimitive(meterNumber.trim()))
            }
            if (smartcardNumber.isNotBlank()) {
                put("iuc", JsonPrimitive(smartcardNumber.trim()))
                put("smartcard_number", JsonPrimitive(smartcardNumber.trim()))
            }
        }.toString()
    }

    /**
     * Calls the Supabase Edge Function `/functions/v1/Gsubz-VTU-Services` via Ktor POST
     * with the logged-in user's session access token using the single SupabaseProvider.client.
     */
    suspend fun purchaseVtuService(
        serviceID: String,
        plan: String = "",
        amount: Double,
        phone: String,
        customerID: String = "",
        service: String = "",
        network: String = "",
        meterNumber: String = "",
        smartcardNumber: String = "",
        narration: String? = null,
        userId: String? = null,
        userEmail: String? = null,
        userToken: String? = null,
        supabaseAnonKey: String = SupabaseProvider.rawKey
    ): GsubzOrderResult = withContext(Dispatchers.IO) {
        val cleanPhone = phone.trim()
        val activeToken = SupabaseProvider.resolveSessionAccessToken(userToken)

        val requestJsonString = buildRequestJson(
            serviceID = serviceID,
            amount = amount,
            phone = cleanPhone,
            plan = plan,
            customerID = customerID,
            service = service,
            network = network,
            userId = userId,
            userEmail = userEmail,
            narration = narration,
            meterNumber = meterNumber,
            smartcardNumber = smartcardNumber,
            userToken = activeToken
        )
        lastOutgoingRequestBodyString = requestJsonString

        val (httpStatusCode, rawBody) = try {
            SupabaseProvider.postEdgeFunction(
                functionName = FUNCTION_NAME,
                bodyJson = requestJsonString,
                accessTokenOverride = activeToken
            )
        } catch (netErr: Exception) {
            Log.e(TAG, "Network error calling $FUNCTION_URL: ${netErr.message}", netErr)
            return@withContext GsubzOrderResult(
                isSuccess = false,
                isPending = false,
                status = "network_error",
                transactionId = "VTU-${System.currentTimeMillis()}",
                message = "Network connection bad. Please check your internet connection and try again.",
                amountPaid = 0.0,
                isLiveEdge = false
            )
        }

        return@withContext parseAndFinalizeGsubzResponse(
            rawBody = rawBody,
            httpStatusCode = httpStatusCode,
            requestedAmount = amount
        )
    }

    private fun optDoubleOrNull(obj: JSONObject?, key: String): Double? {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null
        val d = obj.optDouble(key, Double.NaN)
        if (!d.isNaN()) return d
        return obj.optString(key, "").trim().toDoubleOrNull()
    }

    private fun formatGsubzDescription(rawDescription: String, apiResponse: String = ""): String {
        val cleanDesc = rawDescription.trim()
        val cleanApi = apiResponse.trim()
        return when {
            cleanDesc.equals("INSUFFICIENT_BALANCE", ignoreCase = true) -> "Insufficient wallet balance"
            cleanDesc.equals("INVALID_PLAN", ignoreCase = true) -> {
                if (cleanApi.isNotBlank()) cleanApi else "This service or plan is not available"
            }
            cleanDesc.equals("SERVICE_CANNOT_BE_FOUND", ignoreCase = true) -> "This service or plan is not available"
            cleanDesc.equals("AMOUNT_BELOW_MIN", ignoreCase = true) -> "Entered amount is below the minimum allowed for this service"
            cleanDesc.isNotBlank() -> cleanDesc
            cleanApi.isNotBlank() -> cleanApi
            else -> ""
        }
    }

    fun parseAndFinalizeGsubzResponse(
        rawBody: String,
        httpStatusCode: Int,
        requestedAmount: Double
    ): GsubzOrderResult {
        try {
            val json = JSONObject(rawBody)
            val gsubzDataObj = json.optJSONObject("gsubz_data")
            val dataObj = gsubzDataObj ?: json.optJSONObject("data") ?: json
            val contentObj = gsubzDataObj?.optJSONObject("content")
                ?: dataObj.optJSONObject("content")
                ?: json.optJSONObject("content")

            val requestId = contentObj?.optString("requestID")?.takeIf { it.isNotBlank() && it != "null" }
                ?: dataObj.optString("requestID").takeIf { it.isNotBlank() && it != "null" }

            val rawTxId = contentObj?.optString("transactionID")?.takeIf { it.isNotBlank() && it != "null" }
                ?: requestId
                ?: dataObj.optString("transactionID").takeIf { it.isNotBlank() && it != "null" }
                ?: json.optString("transactionID").takeIf { it.isNotBlank() && it != "null" }
                ?: dataObj.optString("reference").takeIf { it.isNotBlank() && it != "null" }
                ?: json.optString("reference").takeIf { it.isNotBlank() && it != "null" }
                ?: "VTU-${System.currentTimeMillis()}"
            val txId = rawTxId.replace(Regex("GSUBZ-ERR-|GSUBZ-", RegexOption.IGNORE_CASE), "VTU-")

            val serviceName = contentObj?.optString("serviceName")?.takeIf { it.isNotBlank() && it != "null" }
            val rawImage = contentObj?.optString("image")?.takeIf { it.isNotBlank() && it != "null" }
            val serviceImageUrl = when {
                rawImage == null -> null
                rawImage.startsWith("//") -> "https:$rawImage"
                else -> rawImage
            }
            val narration = json.optString("narration", "").takeIf { it.isNotBlank() && it != "null" }
            val apiResponse = gsubzDataObj?.optString("api_response", "")?.takeIf { it.isNotBlank() && it != "null" }

            // Requirement 5: Show the "error" text from the response,
            // e.g. "Insufficient wallet balance" or "This service or plan is not available".
            val explicitError = json.optString("error", "").takeIf { it.isNotBlank() && it != "null" }
                ?: dataObj.optString("error", "").takeIf { it.isNotBlank() && it != "null" }

            val statusStr = dataObj.optString("status", json.optString("status", "")).trim()
            val contentStatus = contentObj?.optString("status", "")?.trim() ?: ""
            val codeVal = dataObj.optString("code", "").trim().toIntOrNull()
                ?: contentObj?.optString("code", "")?.trim()?.toIntOrNull()
                ?: dataObj.optInt("code", json.optInt("code", httpStatusCode))

            val isPending = (httpStatusCode == 202) ||
                json.optBoolean("pending", false) ||
                dataObj.optBoolean("pending", false) ||
                statusStr.equals("pending", ignoreCase = true) ||
                statusStr.equals("PROCESSING", ignoreCase = true) ||
                contentStatus.equals("pending", ignoreCase = true)

            // Requirement 3: Extract receipt fields directly from the function response
            val serverAirtimeValue = optDoubleOrNull(json, "airtimeValue")
                ?: optDoubleOrNull(dataObj, "airtimeValue")
                ?: optDoubleOrNull(contentObj, "airtimeValue")
                ?: optDoubleOrNull(json, "airtime_value")
                ?: optDoubleOrNull(dataObj, "airtime_value")
                ?: optDoubleOrNull(json, "amount")
                ?: optDoubleOrNull(dataObj, "amount")

            val serverCashback = optDoubleOrNull(json, "cashback")
                ?: optDoubleOrNull(dataObj, "cashback")
                ?: optDoubleOrNull(contentObj, "cashback")

            val serverCharged = optDoubleOrNull(json, "charged")
                ?: optDoubleOrNull(dataObj, "charged")
                ?: optDoubleOrNull(contentObj, "charged")
                ?: optDoubleOrNull(contentObj, "amountPaid")
                ?: optDoubleOrNull(dataObj, "amountPaid")

            val serverNewBalance = optDoubleOrNull(json, "new_balance")
                ?: optDoubleOrNull(json, "wallet_balance")
                ?: optDoubleOrNull(dataObj, "new_balance")
                ?: optDoubleOrNull(dataObj, "wallet_balance")

            val tokenVal = contentObj?.optString("token")?.takeIf { it.isNotBlank() && it != "null" }
                ?: contentObj?.optString("pin")?.takeIf { it.isNotBlank() && it != "null" }
                ?: contentObj?.optString("purchased_code")?.takeIf { it.isNotBlank() && it != "null" }
                ?: contentObj?.optString("mainToken")?.takeIf { it.isNotBlank() && it != "null" }
                ?: dataObj.optString("token").takeIf { it.isNotBlank() && it != "null" }
                ?: dataObj.optString("pin").takeIf { it.isNotBlank() && it != "null" }
                ?: dataObj.optString("purchased_code").takeIf { it.isNotBlank() && it != "null" }
                ?: json.optString("token").takeIf { it.isNotBlank() && it != "null" }
                ?: json.optString("pin").takeIf { it.isNotBlank() && it != "null" }
                ?: json.optString("purchased_code").takeIf { it.isNotBlank() && it != "null" }

            if (isPending && explicitError == null) {
                val effectivePaid = serverCharged ?: requestedAmount
                return GsubzOrderResult(
                    isSuccess = false,
                    isPending = true,
                    status = "PENDING",
                    transactionId = txId,
                    message = "Purchase is being confirmed",
                    amountPaid = effectivePaid,
                    airtimeValue = serverAirtimeValue ?: requestedAmount,
                    cashback = serverCashback ?: 0.0,
                    charged = serverCharged ?: effectivePaid,
                    newBalance = serverNewBalance,
                    tokenOrPin = tokenVal,
                    serviceName = serviceName,
                    serviceImageUrl = serviceImageUrl,
                    narration = narration,
                    apiResponse = apiResponse,
                    requestId = requestId,
                    isLiveEdge = true
                )
            }

            if (explicitError != null) {
                return GsubzOrderResult(
                    isSuccess = false,
                    isPending = false,
                    status = "FAILED",
                    transactionId = txId,
                    message = explicitError,
                    amountPaid = 0.0,
                    airtimeValue = serverAirtimeValue,
                    cashback = serverCashback,
                    charged = serverCharged,
                    newBalance = serverNewBalance,
                    serviceName = serviceName,
                    serviceImageUrl = serviceImageUrl,
                    narration = narration,
                    apiResponse = apiResponse,
                    requestId = requestId,
                    isLiveEdge = true
                )
            }

            val hasExplicitSuccessFlag = json.has("success") && !json.isNull("success")
            val explicitSuccessFlag = if (hasExplicitSuccessFlag) json.optBoolean("success", false) else null

            val isExplicitlyFailed = (explicitSuccessFlag == false) ||
                statusStr.contains("FAIL", ignoreCase = true) ||
                statusStr.contains("ERROR", ignoreCase = true) ||
                contentStatus.contains("FAIL", ignoreCase = true) ||
                contentStatus.contains("ERROR", ignoreCase = true) ||
                codeVal >= 400

            val isSuccess = (httpStatusCode in 200..299) && !isExplicitlyFailed && (
                explicitSuccessFlag == true ||
                    dataObj.optBoolean("success", false) ||
                    statusStr.equals("TRANSACTION_SUCCESSFUL", ignoreCase = true) ||
                    statusStr.equals("SUCCESSFUL", ignoreCase = true) ||
                    statusStr.equals("success", ignoreCase = true) ||
                    contentStatus.equals("TRANSACTION_SUCCESSFUL", ignoreCase = true) ||
                    contentStatus.equals("SUCCESSFUL", ignoreCase = true) ||
                    contentStatus.trim().equals("delivered", ignoreCase = true) ||
                    serverCharged != null ||
                    serverAirtimeValue != null ||
                    (codeVal in 200..299 && !statusStr.contains("FAIL", ignoreCase = true) && !statusStr.contains("ERROR", ignoreCase = true))
                )

            val rawDescription = dataObj.optString(
                "description",
                contentObj?.optString("description", "")?.takeIf { it.isNotBlank() }
                    ?: json.optString(
                        "description",
                        dataObj.optString("message", json.optString("message", ""))
                    )
            ).trim()

            val formattedDescription = formatGsubzDescription(rawDescription, apiResponse.orEmpty())

            if (!isSuccess) {
                val errorMsg = formattedDescription.ifBlank { "Transaction failed" }
                return GsubzOrderResult(
                    isSuccess = false,
                    isPending = false,
                    status = statusStr.ifBlank { "FAILED" },
                    transactionId = txId,
                    message = errorMsg,
                    amountPaid = 0.0,
                    airtimeValue = serverAirtimeValue,
                    cashback = serverCashback,
                    charged = serverCharged,
                    newBalance = serverNewBalance,
                    serviceName = serviceName,
                    serviceImageUrl = serviceImageUrl,
                    narration = narration,
                    apiResponse = apiResponse,
                    requestId = requestId,
                    isLiveEdge = true
                )
            }

            val effectivePaid = serverCharged ?: requestedAmount
            val cleanMessage = formattedDescription
                .replace(Regex("via\\s+Gsubz-VTU-Services", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\(?Gsubz\\s*Live\\)?", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifBlank { "Transaction Successful" }

            return GsubzOrderResult(
                isSuccess = true,
                isPending = false,
                status = "SUCCESSFUL",
                transactionId = txId,
                message = cleanMessage,
                amountPaid = effectivePaid,
                airtimeValue = serverAirtimeValue ?: requestedAmount,
                cashback = serverCashback ?: 0.0,
                charged = serverCharged ?: effectivePaid,
                newBalance = serverNewBalance,
                tokenOrPin = tokenVal,
                serviceName = serviceName,
                serviceImageUrl = serviceImageUrl,
                narration = narration,
                apiResponse = apiResponse,
                requestId = requestId,
                isLiveEdge = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gsubz-VTU-Services response: $rawBody", e)
            val fallbackError = rawBody.trim().takeIf { it.isNotBlank() && !it.startsWith("<") }
                ?: "Network connection bad. Please check your internet connection and try again."
            return GsubzOrderResult(
                isSuccess = false,
                isPending = false,
                status = "parse_error",
                transactionId = "VTU-${System.currentTimeMillis()}",
                message = fallbackError,
                amountPaid = 0.0,
                isLiveEdge = true
            )
        }
    }
}
