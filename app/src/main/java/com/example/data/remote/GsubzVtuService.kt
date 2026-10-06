package com.example.data.remote

import android.util.Log
import com.example.auth.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.json.JSONObject

@Serializable
data class GsubzVtuRequest(
    val serviceID: String,
    val plan: String = "",
    val amount: String = "",
    val phone: String,
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

    @Volatile
    var lastOutgoingRequestBodyString: String = ""

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
        val amountIntStr = if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()

        val supabase = SupabaseProvider.client
        val activeUserId = userId?.takeIf { it.isNotBlank() && it != "usr_guest" && it != "usr_default" }
            ?: try { supabase?.auth?.currentUserOrNull()?.id } catch (_: Throwable) { null }

        val activeToken = SupabaseProvider.resolveSessionAccessToken(userToken)

        val requestPayload = GsubzVtuRequest(
            serviceID = serviceID,
            plan = plan,
            amount = amountIntStr,
            phone = cleanPhone,
            customerID = customerID.ifBlank { cleanPhone }
        )

        val requestJsonString = try {
            jsonFormat.encodeToString(requestPayload)
        } catch (_: Throwable) {
            JSONObject().apply {
                put("serviceID", serviceID)
                put("plan", plan)
                put("amount", amountIntStr)
                put("phone", cleanPhone)
                put("customerID", customerID.ifBlank { cleanPhone })
            }.toString()
        }
        lastOutgoingRequestBodyString = requestJsonString

        // 1. Pre-flight balance check from public.users if user is authenticated
        var initialRemoteBalance: Double? = null
        if (!activeUserId.isNullOrBlank() && amount > 0) {
            initialRemoteBalance = fetchRemoteWalletBalance(activeUserId)
            if (initialRemoteBalance != null && initialRemoteBalance < amount) {
                return@withContext GsubzOrderResult(
                    isSuccess = false,
                    status = "insufficient_balance",
                    transactionId = "TX-INSUFFICIENT-${System.currentTimeMillis()}",
                    message = "Insufficient wallet balance in Supabase (₦%,.2f available, ₦%,.2f required).".format(
                        initialRemoteBalance,
                        amount
                    ),
                    amountPaid = 0.0,
                    isLiveEdge = true
                )
            }
        }

        // 2. Call /functions/v1/Gsubz-VTU-Services via Ktor POST with the user's session access token
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
                status = "network_error",
                transactionId = "TX-NETERR-${System.currentTimeMillis()}",
                message = "Network error reaching Gsubz-VTU-Services: ${netErr.localizedMessage ?: "Check connection"}",
                amountPaid = 0.0,
                isLiveEdge = false
            )
        }

        return@withContext parseAndFinalizeGsubzResponse(
            rawBody = rawBody,
            httpStatusCode = httpStatusCode,
            requestedAmount = amount,
            initialRemoteBalance = initialRemoteBalance,
            activeUserId = activeUserId
        )
    }

    private suspend fun parseAndFinalizeGsubzResponse(
        rawBody: String,
        httpStatusCode: Int,
        requestedAmount: Double,
        initialRemoteBalance: Double?,
        activeUserId: String?
    ): GsubzOrderResult {
        try {
            val json = JSONObject(rawBody)
            val dataObj = json.optJSONObject("data") ?: json
            val contentObj = dataObj.optJSONObject("content") ?: json.optJSONObject("content")

            val statusStr = dataObj.optString("status", json.optString("status", "")).trim()
            val codeVal = dataObj.optInt("code", json.optInt("code", httpStatusCode))
            val description = dataObj.optString(
                "description",
                json.optString(
                    "description",
                    dataObj.optString("message", json.optString("message", json.optString("error", "")))
                )
            )
            val contentStatus = contentObj?.optString("status", "") ?: ""

            val isSuccess = (httpStatusCode in 200..299) && (
                statusStr.equals("TRANSACTION_SUCCESSFUL", ignoreCase = true) ||
                    statusStr.equals("SUCCESSFUL", ignoreCase = true) ||
                    statusStr.equals("success", ignoreCase = true) ||
                    contentStatus.equals("TRANSACTION_SUCCESSFUL", ignoreCase = true) ||
                    contentStatus.equals("SUCCESSFUL", ignoreCase = true) ||
                    contentStatus.equals(" delivered", ignoreCase = true) ||
                    codeVal == 200 && !statusStr.contains("FAIL", ignoreCase = true) && !statusStr.contains("ERROR", ignoreCase = true) && !json.has("error")
                )

            val txId = contentObj?.optString("transactionID")?.takeIf { it.isNotBlank() }
                ?: dataObj.optString("transactionID").takeIf { it.isNotBlank() }
                ?: dataObj.optString("reference").takeIf { it.isNotBlank() }
                ?: "GSUBZ-${System.currentTimeMillis()}"

            val tokenVal = contentObj?.optString("token")?.takeIf { it.isNotBlank() && it != "null" }
                ?: contentObj?.optString("pin")?.takeIf { it.isNotBlank() && it != "null" }
                ?: dataObj.optString("token").takeIf { it.isNotBlank() && it != "null" }
                ?: dataObj.optString("pin").takeIf { it.isNotBlank() && it != "null" }

            val paidAmount = contentObj?.optDouble("amountPaid", requestedAmount)?.takeIf { !it.isNaN() && it > 0 }
                ?: dataObj.optDouble("amountPaid", requestedAmount).takeIf { !it.isNaN() && it > 0 }
                ?: requestedAmount

            if (isSuccess && !activeUserId.isNullOrBlank() && requestedAmount > 0) {
                ensureWalletDebitedAfterSuccess(
                    userId = activeUserId,
                    debitAmount = requestedAmount,
                    initialBalance = initialRemoteBalance
                )
            }

            return GsubzOrderResult(
                isSuccess = isSuccess,
                status = statusStr.ifBlank { if (isSuccess) "TRANSACTION_SUCCESSFUL" else "FAILED" },
                transactionId = txId,
                message = description.ifBlank {
                    if (isSuccess) "VTU Order Successful via Gsubz-VTU-Services"
                    else "VTU Order failed (HTTP $httpStatusCode)"
                },
                amountPaid = paidAmount,
                tokenOrPin = tokenVal,
                isLiveEdge = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gsubz-VTU-Services response: $rawBody", e)
            return GsubzOrderResult(
                isSuccess = false,
                status = "parse_error",
                transactionId = "TX-ERR-${System.currentTimeMillis()}",
                message = if (rawBody.isNotBlank()) rawBody.take(180) else "Unexpected response from Gsubz-VTU-Services (HTTP $httpStatusCode)",
                amountPaid = 0.0,
                isLiveEdge = true
            )
        }
    }

    private suspend fun fetchRemoteWalletBalance(userId: String): Double? {
        return try {
            val client = SupabaseProvider.client ?: return null
            val rows = client.from("users").select(columns = Columns.list("wallet_balance")) {
                filter { eq("id", userId) }
            }.decodeList<JsonObject>()
            rows.firstOrNull()?.get("wallet_balance")?.jsonPrimitive?.doubleOrNull
                ?: rows.firstOrNull()?.get("wallet_balance")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
        } catch (e: Throwable) {
            Log.w(TAG, "Balance check note: ${e.message}")
            null
        }
    }

    private suspend fun ensureWalletDebitedAfterSuccess(
        userId: String,
        debitAmount: Double,
        initialBalance: Double?
    ) {
        try {
            val currentRemote = fetchRemoteWalletBalance(userId)
            if (initialBalance != null && currentRemote != null && currentRemote <= (initialBalance - debitAmount + 0.01)) {
                com.vtu.app.wallet.SharedWalletObserver.updateBalance(currentRemote)
                return
            }

            val client = SupabaseProvider.client ?: return
            try {
                val params = buildJsonObject {
                    put("amount", JsonPrimitive(debitAmount))
                }
                client.postgrest.rpc("debit_wallet", params)
                val afterRpc = fetchRemoteWalletBalance(userId)
                if (afterRpc != null) {
                    com.vtu.app.wallet.SharedWalletObserver.updateBalance(afterRpc)
                }
            } catch (rpcErr: Throwable) {
                Log.w(TAG, "debit_wallet RPC note: ${rpcErr.message}")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error syncing wallet deduction after Gsubz purchase: ${e.message}")
        }
    }
}
