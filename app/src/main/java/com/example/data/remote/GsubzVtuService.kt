package com.example.data.remote

import android.util.Log
import com.example.auth.SupabaseProvider
import com.example.data.model.AirtimeNetworkPricing
import com.example.data.model.NetworkProvider
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
    val amount: JsonPrimitive,
    val phone: String,
    val plan: String = "",
    val customerID: String = ""
)

@Serializable
data class GsubzContentPayload(
    val transactionID: String? = null,
    val status: String? = null,
    val serviceName: String? = null,
    val amountPaid: Double? = null,
    val token: String? = null,
    val pin: String? = null
)

@Serializable
data class GsubzVtuResponse(
    val code: Int? = null,
    val status: String? = null,
    val description: String? = null,
    val message: String? = null,
    val error: String? = null,
    val pending: Boolean? = null,
    val airtimeValue: Double? = null,
    val cashback: Double? = null,
    val charged: Double? = null,
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

    @Volatile
    var lastOutgoingRequestBodyString: String = ""

    /**
     * Reads cashback_percent and service_id for each network from the Supabase vtu_prices table
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
     * Builds the JSON request body for Gsubz-VTU-Services:
     * - "amount" is ALWAYS a JSON NUMBER equal to the value chosen (e.g. 100), never discounted and never a string.
     * - "serviceID" is the exact service_id from vtu_prices.
     * - "phone" is the recipient phone number.
     * - "plan" is empty ("") for airtime.
     */
    fun buildRequestJson(
        serviceID: String,
        amount: Double,
        phone: String,
        plan: String = "",
        customerID: String = ""
    ): String {
        val cleanPhone = phone.trim()
        val amountNumberPrimitive: JsonPrimitive = if (amount % 1.0 == 0.0) {
            JsonPrimitive(amount.toLong())
        } else {
            JsonPrimitive(amount)
        }

        return buildJsonObject {
            put("serviceID", JsonPrimitive(serviceID))
            put("amount", amountNumberPrimitive)
            put("phone", JsonPrimitive(cleanPhone))
            if (plan.isNotBlank()) {
                put("plan", JsonPrimitive(plan))
                if (customerID.isNotBlank()) {
                    put("customerID", JsonPrimitive(customerID))
                }
            } else {
                put("plan", JsonPrimitive(""))
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
            customerID = customerID
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

    fun parseAndFinalizeGsubzResponse(
        rawBody: String,
        httpStatusCode: Int,
        requestedAmount: Double
    ): GsubzOrderResult {
        try {
            val json = JSONObject(rawBody)
            val dataObj = json.optJSONObject("data") ?: json
            val contentObj = dataObj.optJSONObject("content") ?: json.optJSONObject("content")

            val rawTxId = contentObj?.optString("transactionID")?.takeIf { it.isNotBlank() && it != "null" }
                ?: dataObj.optString("transactionID").takeIf { it.isNotBlank() && it != "null" }
                ?: json.optString("transactionID").takeIf { it.isNotBlank() && it != "null" }
                ?: dataObj.optString("reference").takeIf { it.isNotBlank() && it != "null" }
                ?: json.optString("reference").takeIf { it.isNotBlank() && it != "null" }
                ?: "VTU-${System.currentTimeMillis()}"
            val txId = rawTxId.replace(Regex("GSUBZ-ERR-|GSUBZ-", RegexOption.IGNORE_CASE), "VTU-")

            // Requirement 5: Show the "error" text from the response,
            // e.g. "Insufficient wallet balance" or "This service or plan is not available".
            val explicitError = json.optString("error", "").takeIf { it.isNotBlank() && it != "null" }
                ?: dataObj.optString("error", "").takeIf { it.isNotBlank() && it != "null" }

            val statusStr = dataObj.optString("status", json.optString("status", "")).trim()
            val contentStatus = contentObj?.optString("status", "") ?: ""
            val codeVal = dataObj.optInt("code", json.optInt("code", httpStatusCode))

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

            val tokenVal = contentObj?.optString("token")?.takeIf { it.isNotBlank() && it != "null" }
                ?: contentObj?.optString("pin")?.takeIf { it.isNotBlank() && it != "null" }
                ?: dataObj.optString("token").takeIf { it.isNotBlank() && it != "null" }
                ?: dataObj.optString("pin").takeIf { it.isNotBlank() && it != "null" }
                ?: json.optString("token").takeIf { it.isNotBlank() && it != "null" }
                ?: json.optString("pin").takeIf { it.isNotBlank() && it != "null" }

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
                    tokenOrPin = tokenVal,
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
                    isLiveEdge = true
                )
            }

            val isSuccess = (httpStatusCode in 200..299) && (
                json.optBoolean("success", false) ||
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

            val description = dataObj.optString(
                "description",
                json.optString(
                    "description",
                    dataObj.optString("message", json.optString("message", ""))
                )
            ).trim()

            if (!isSuccess) {
                val errorMsg = description.ifBlank { "Transaction failed" }
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
                    isLiveEdge = true
                )
            }

            val effectivePaid = serverCharged ?: requestedAmount
            val cleanMessage = description
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
                tokenOrPin = tokenVal,
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
