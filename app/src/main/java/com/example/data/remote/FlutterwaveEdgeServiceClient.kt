package com.example.data.remote

import android.util.Log
import com.example.auth.SupabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class FlutterwaveInitResult(
    val isSuccess: Boolean,
    val paymentUrl: String?,
    val txRef: String,
    val message: String,
    val isLiveEdge: Boolean = false,
    val isInAppCheckout: Boolean = false
)

data class FlutterwaveVerifyResult(
    val isSuccess: Boolean,
    val status: String,
    val amount: Double,
    val txRef: String,
    val message: String,
    val isLiveEdge: Boolean = false
)

data class FlutterwaveVirtualAccount(
    val accountNumber: String,
    val bankName: String,
    val accountName: String,
    val flwRef: String?
)

class FlutterwaveEdgeServiceClient(
    val supabaseUrl: String = SupabaseProvider.safeUrl,
    val supabaseAnonKey: String = SupabaseProvider.rawKey
) {
    val isLiveConfigured: Boolean
        get() = SupabaseProvider.isConfigured

    /**
     * Initiates a payment strictly via the Supabase Edge Function /functions/v1/flutterwave-payment
     * using Ktor POST and the logged-in user's session access token from SupabaseProvider.client.
     */
    suspend fun initializePayment(
        amount: Double,
        email: String,
        name: String,
        phone: String? = null,
        userToken: String? = null
    ): FlutterwaveInitResult = withContext(Dispatchers.IO) {
        val txRef = "FLW-VTU-${System.currentTimeMillis()}-${(1000..9999).random()}"

        if (isLiveConfigured) {
            try {
                val jsonBody = JSONObject().apply {
                    put("action", "initialize")
                    put("amount", amount)
                    put("email", email)
                    put("name", name)
                    if (!phone.isNullOrBlank()) put("phone", phone)
                    put("tx_ref", txRef)
                    put("redirect_url", "https://daniel-vtu.web.app/payment-success")
                }

                val (statusCode, bodyString) = SupabaseProvider.postEdgeFunction(
                    functionName = "flutterwave-payment",
                    bodyJson = jsonBody.toString(),
                    accessTokenOverride = userToken
                )

                if (statusCode in 200..299 && bodyString.isNotBlank()) {
                    val json = JSONObject(bodyString)
                    val success = json.optBoolean("success", true)
                    val link = json.optString("payment_url", null)
                    val msg = json.optString("message", "Payment link created successfully")

                    if (success && !link.isNullOrBlank() && link.startsWith("https://")) {
                        return@withContext FlutterwaveInitResult(
                            isSuccess = true,
                            paymentUrl = link,
                            txRef = txRef,
                            message = msg,
                            isLiveEdge = true,
                            isInAppCheckout = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w("FlutterwaveEdge", "Supabase Edge call failed: ${e.message}")
            }
        }

        return@withContext FlutterwaveInitResult(
            isSuccess = false,
            paymentUrl = null,
            txRef = txRef,
            message = "Unable to initialize payment with server",
            isLiveEdge = false,
            isInAppCheckout = false
        )
    }

    /**
     * Verifies a payment strictly via the Supabase Edge Function /functions/v1/flutterwave-payment
     * using Ktor POST and the logged-in user's session access token from SupabaseProvider.client.
     */
    suspend fun verifyPayment(
        txRef: String,
        expectedAmount: Double,
        userToken: String? = null
    ): FlutterwaveVerifyResult = withContext(Dispatchers.IO) {
        if (isLiveConfigured) {
            try {
                val jsonBody = JSONObject().apply {
                    put("action", "verify")
                    put("tx_ref", txRef)
                }

                val (statusCode, bodyString) = SupabaseProvider.postEdgeFunction(
                    functionName = "flutterwave-payment",
                    bodyJson = jsonBody.toString(),
                    accessTokenOverride = userToken
                )

                if (statusCode in 200..299 && bodyString.isNotBlank()) {
                    val json = JSONObject(bodyString)
                    val success = json.optBoolean("success", false)
                    val status = json.optString("status", "")
                    val amount = json.optDouble("amount", expectedAmount)
                    val msg = json.optString("message", "Payment verification completed")

                    return@withContext FlutterwaveVerifyResult(
                        isSuccess = success && status.equals("successful", ignoreCase = true),
                        status = status,
                        amount = amount,
                        txRef = txRef,
                        message = msg,
                        isLiveEdge = true
                    )
                }
            } catch (e: Exception) {
                Log.w("FlutterwaveEdge", "Edge verify error: ${e.message}")
            }
        }

        FlutterwaveVerifyResult(
            isSuccess = false,
            status = "failed",
            amount = expectedAmount,
            txRef = txRef,
            message = "Unable to verify payment with server",
            isLiveEdge = false
        )
    }

    /**
     * Generates a permanent dedicated Virtual Account strictly via Supabase Edge Functions
     * using Ktor POST and the logged-in user's session access token from SupabaseProvider.client.
     */
    suspend fun generateVirtualAccount(
        name: String,
        email: String,
        phone: String?,
        nin: String? = null
    ): FlutterwaveVirtualAccount = withContext(Dispatchers.IO) {
        val cleanNin = nin?.filter { it.isDigit() }?.take(11) ?: ""
        val names = name.trim().split(" ").filter { it.isNotBlank() }
        val firstName = names.firstOrNull() ?: ""
        val lastName = if (names.size > 1) names.drop(1).joinToString(" ") else firstName

        if (isLiveConfigured) {
            val functionNames = listOf(
                "Create-Permanent-Account",
                "Create-Virtual-Account",
                "create-virtual-account",
                "flutterwave-payment"
            )

            for (fnName in functionNames) {
                try {
                    val jsonBody = JSONObject().apply {
                        put("action", "virtual_account")
                        put("firstName", firstName)
                        put("lastName", lastName)
                        put("name", name)
                        put("email", email)
                        if (cleanNin.isNotBlank()) put("nin", cleanNin)
                        if (!phone.isNullOrBlank()) put("phone", phone)
                    }

                    val (statusCode, bodyString) = SupabaseProvider.postEdgeFunction(
                        functionName = fnName,
                        bodyJson = jsonBody.toString()
                    )

                    if (statusCode in 200..299 && bodyString.isNotBlank()) {
                        val json = JSONObject(bodyString)
                        val data = json.optJSONObject("data")
                        val acctNo = json.optString("accountNumber").ifBlank { null }
                            ?: data?.optString("account_number")
                        val rawBank = json.optString("bankName").ifBlank { null }
                            ?: data?.optString("bank_name") ?: "Flutterwave MFB"
                        val bank = if (rawBank.contains("Wema", ignoreCase = true)) "Flutterwave MFB" else rawBank
                        val acctName = data?.optString("account_name")?.takeIf { it.isNotBlank() && it != "null" } ?: name
                        val flwRef = data?.optString("flw_ref")

                        if (!acctNo.isNullOrBlank()) {
                            return@withContext FlutterwaveVirtualAccount(
                                accountNumber = acctNo,
                                bankName = bank,
                                accountName = acctName,
                                flwRef = flwRef
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w("FlutterwaveEdge", "Edge virtual account error on $fnName: ${e.message}")
                }
            }
        }

        FlutterwaveVirtualAccount(
            accountNumber = "",
            bankName = "",
            accountName = "",
            flwRef = "KYC_REQUIRED"
        )
    }

    suspend fun getVirtualAccount(
        email: String,
        name: String
    ): FlutterwaveVirtualAccount {
        return generateVirtualAccount(
            name = name,
            email = email,
            phone = null
        )
    }
}
