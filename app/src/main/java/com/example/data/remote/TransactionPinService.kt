package com.example.data.remote

import com.example.util.SecurityVault
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Empty JSON request body ({}) for Supabase RPC calls that take no parameters.
 */
class EmptyRpcRequest

/**
 * Request body for set_transaction_pin RPC: {"p_pin":"<4-digit-pin>"}.
 * Never logs or prints the PIN in toString().
 */
class SetTransactionPinRequest(
    @SerializedName("p_pin") val pPin: String
) {
    override fun toString(): String = "SetTransactionPinRequest(p_pin=****)"
}

/**
 * Request body for verify_transaction_pin RPC: {"p_pin":"<4-digit-pin>"}.
 * Never logs or prints the PIN in toString().
 */
class VerifyTransactionPinRequest(
    @SerializedName("p_pin") val pPin: String
) {
    override fun toString(): String = "VerifyTransactionPinRequest(p_pin=****)"
}

/**
 * Request body for change_transaction_pin RPC: {"p_old":"<4-digit-old>","p_new":"<4-digit-new>"}.
 * Never logs or prints the PINs in toString().
 */
class ChangeTransactionPinRequest(
    @SerializedName("p_old") val pOld: String,
    @SerializedName("p_new") val pNew: String
) {
    override fun toString(): String = "ChangeTransactionPinRequest(p_old=****, p_new=****)"
}

/**
 * Request body for get_my_transactions RPC: {"p_limit": 20}.
 */
data class GetMyTransactionsRequest(
    @SerializedName("p_limit") val pLimit: Int = 20
)

/**
 * Row returned by the Supabase RPC get_my_transactions for the authenticated user.
 */
data class RemoteTransactionDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title", alternate = ["narration", "description"]) val title: String? = null,
    @SerializedName("service", alternate = ["service_type", "type", "category"]) val service: String? = null,
    @SerializedName("provider", alternate = ["network"]) val provider: String? = null,
    @SerializedName("recipient", alternate = ["phone", "beneficiary", "account_number", "meter_number"]) val recipient: String? = null,
    @SerializedName("amount") val amount: Double? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("reference", alternate = ["tx_ref", "ref", "transaction_ref"]) val reference: String? = null,
    @SerializedName("created_at", alternate = ["timestamp", "date"]) val createdAt: String? = null,
    @SerializedName("details", alternate = ["token", "reason"]) val details: String? = null,
    @SerializedName("customer_name") val customerName: String? = null,
    @SerializedName("discount", alternate = ["cashback"]) val discount: Double? = null
)

sealed class PinVerifyOutcome {
    data object Verified : PinVerifyOutcome()
    data object WrongPin : PinVerifyOutcome()
    data object Locked : PinVerifyOutcome()
    data class Error(val message: String) : PinVerifyOutcome()
}

sealed class PinChangeOutcome {
    data object Changed : PinChangeOutcome()
    data object WrongOldPin : PinChangeOutcome()
    data object Locked : PinChangeOutcome()
    data class Error(val message: String) : PinChangeOutcome()
}

interface TransactionPinRetrofitService {

    @POST("rest/v1/rpc/has_transaction_pin")
    suspend fun hasTransactionPin(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: EmptyRpcRequest = EmptyRpcRequest()
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/set_transaction_pin")
    suspend fun setTransactionPin(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: SetTransactionPinRequest
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/verify_transaction_pin")
    suspend fun verifyTransactionPin(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: VerifyTransactionPinRequest
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/change_transaction_pin")
    suspend fun changeTransactionPin(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: ChangeTransactionPinRequest
    ): Response<ResponseBody>

    @POST("rest/v1/rpc/get_my_transactions")
    suspend fun getMyTransactions(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: GetMyTransactionsRequest = GetMyTransactionsRequest(20)
    ): Response<List<RemoteTransactionDto>>
}

class TransactionPinClient(
    private val supabaseUrlProvider: () -> String = { SecurityVault.supabaseUrl() },
    private val supabaseAnonKeyProvider: () -> String = { SecurityVault.supabaseAnonKey() }
) {
    // Dedicated OkHttpClient with NO logging interceptor so PIN payloads are never logged or printed.
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val service: TransactionPinRetrofitService by lazy {
        val baseUrl = "${supabaseUrlProvider().trimEnd('/')}/"
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TransactionPinRetrofitService::class.java)
    }

    private fun buildBearerHeader(accessToken: String): String {
        val clean = accessToken.trim()
        return if (clean.startsWith("Bearer ", ignoreCase = true)) {
            clean
        } else {
            "Bearer $clean"
        }
    }

    suspend fun hasTransactionPin(accessToken: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Missing user access token"))
        }
        try {
            val response = service.hasTransactionPin(
                apiKey = supabaseAnonKeyProvider(),
                authorization = buildBearerHeader(accessToken),
                body = EmptyRpcRequest()
            )
            if (response.isSuccessful) {
                val raw = response.body()?.string()?.trim().orEmpty()
                val parsed = parseBooleanResponse(raw, fnName = "has_transaction_pin", defaultOnEmpty = false)
                Result.success(parsed)
            } else {
                val errRaw = response.errorBody()?.string()?.trim().orEmpty()
                val errMsg = extractErrorMessage(errRaw, response.code())
                Result.failure(IllegalStateException(errMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setTransactionPin(pin: String, accessToken: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanPin = pin.trim()
        if (cleanPin.length != 4 || !cleanPin.all { it.isDigit() }) {
            return@withContext Result.failure(IllegalArgumentException("PIN must be exactly 4 digits"))
        }
        if (accessToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Missing user access token"))
        }
        try {
            val response = service.setTransactionPin(
                apiKey = supabaseAnonKeyProvider(),
                authorization = buildBearerHeader(accessToken),
                body = SetTransactionPinRequest(pPin = cleanPin)
            )
            if (response.isSuccessful) {
                val raw = response.body()?.string()?.trim().orEmpty()
                val parsed = parseBooleanResponse(raw, fnName = "set_transaction_pin", defaultOnEmpty = true)
                if (parsed) {
                    Result.success(true)
                } else {
                    Result.failure(IllegalStateException("Could not set transaction PIN"))
                }
            } else {
                val errRaw = response.errorBody()?.string()?.trim().orEmpty()
                val errMsg = extractErrorMessage(errRaw, response.code())
                Result.failure(IllegalStateException(errMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyTransactionPin(pin: String, accessToken: String): PinVerifyOutcome = withContext(Dispatchers.IO) {
        val cleanPin = pin.trim()
        if (cleanPin.length != 4 || !cleanPin.all { it.isDigit() }) {
            return@withContext PinVerifyOutcome.WrongPin
        }
        if (accessToken.isBlank()) {
            return@withContext PinVerifyOutcome.Error("Session expired. Please sign in again.")
        }
        try {
            val response = service.verifyTransactionPin(
                apiKey = supabaseAnonKeyProvider(),
                authorization = buildBearerHeader(accessToken),
                body = VerifyTransactionPinRequest(pPin = cleanPin)
            )
            if (response.isSuccessful) {
                val raw = response.body()?.string()?.trim().orEmpty()
                if (raw.contains("locked", ignoreCase = true)) {
                    return@withContext PinVerifyOutcome.Locked
                }
                val isValid = parseBooleanResponse(raw, fnName = "verify_transaction_pin", defaultOnEmpty = false)
                if (isValid) {
                    PinVerifyOutcome.Verified
                } else {
                    PinVerifyOutcome.WrongPin
                }
            } else {
                val errRaw = response.errorBody()?.string()?.trim().orEmpty()
                val errMsg = extractErrorMessage(errRaw, response.code())
                if (errRaw.contains("locked", ignoreCase = true) || errMsg.contains("locked", ignoreCase = true)) {
                    PinVerifyOutcome.Locked
                } else if (errMsg.contains("wrong pin", ignoreCase = true) ||
                    errMsg.contains("invalid pin", ignoreCase = true) ||
                    errMsg.contains("incorrect pin", ignoreCase = true)
                ) {
                    PinVerifyOutcome.WrongPin
                } else {
                    PinVerifyOutcome.Error(errMsg)
                }
            }
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("locked", ignoreCase = true)) {
                PinVerifyOutcome.Locked
            } else {
                PinVerifyOutcome.Error("Network connection bad. Please check your internet connection and try again.")
            }
        }
    }

    suspend fun changeTransactionPin(
        oldPin: String,
        newPin: String,
        accessToken: String
    ): PinChangeOutcome = withContext(Dispatchers.IO) {
        val cleanOld = oldPin.trim()
        val cleanNew = newPin.trim()
        if (cleanOld.length != 4 || !cleanOld.all { it.isDigit() }) {
            return@withContext PinChangeOutcome.WrongOldPin
        }
        if (cleanNew.length != 4 || !cleanNew.all { it.isDigit() }) {
            return@withContext PinChangeOutcome.Error("New PIN must be 4 digits")
        }
        if (accessToken.isBlank()) {
            return@withContext PinChangeOutcome.Error("Session expired. Please sign in again.")
        }
        try {
            val response = service.changeTransactionPin(
                apiKey = supabaseAnonKeyProvider(),
                authorization = buildBearerHeader(accessToken),
                body = ChangeTransactionPinRequest(pOld = cleanOld, pNew = cleanNew)
            )
            if (response.isSuccessful) {
                val raw = response.body()?.string()?.trim().orEmpty()
                if (raw.contains("locked", ignoreCase = true)) {
                    return@withContext PinChangeOutcome.Locked
                }
                val isChanged = parseBooleanResponse(raw, fnName = "change_transaction_pin", defaultOnEmpty = false)
                if (isChanged) {
                    PinChangeOutcome.Changed
                } else {
                    PinChangeOutcome.WrongOldPin
                }
            } else {
                val errRaw = response.errorBody()?.string()?.trim().orEmpty()
                val errMsg = extractErrorMessage(errRaw, response.code())
                if (errRaw.contains("locked", ignoreCase = true) || errMsg.contains("locked", ignoreCase = true)) {
                    PinChangeOutcome.Locked
                } else if (errMsg.contains("wrong", ignoreCase = true) ||
                    errMsg.contains("incorrect", ignoreCase = true) ||
                    errMsg.contains("invalid", ignoreCase = true)
                ) {
                    PinChangeOutcome.WrongOldPin
                } else {
                    PinChangeOutcome.Error(errMsg)
                }
            }
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("locked", ignoreCase = true)) {
                PinChangeOutcome.Locked
            } else {
                PinChangeOutcome.Error("Network connection bad. Please check your internet connection and try again.")
            }
        }
    }

    suspend fun getMyTransactions(
        accessToken: String,
        limit: Int = 20
    ): Result<List<RemoteTransactionDto>> = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Missing user access token"))
        }
        try {
            val response = service.getMyTransactions(
                apiKey = supabaseAnonKeyProvider(),
                authorization = buildBearerHeader(accessToken),
                body = GetMyTransactionsRequest(pLimit = limit)
            )
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                val errRaw = response.errorBody()?.string()?.trim().orEmpty()
                val errMsg = extractErrorMessage(errRaw, response.code())
                Result.failure(IllegalStateException(errMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun parseBooleanResponse(raw: String, fnName: String, defaultOnEmpty: Boolean): Boolean {
        val clean = raw.trim().trim('"')
        if (clean.isEmpty() || clean.equals("null", ignoreCase = true)) {
            return defaultOnEmpty
        }
        if (clean.equals("true", ignoreCase = true) || clean == "1" || clean.equals("t", ignoreCase = true)) {
            return true
        }
        if (clean.equals("false", ignoreCase = true) || clean == "0" || clean.equals("f", ignoreCase = true)) {
            return false
        }
        try {
            if (raw.startsWith("{")) {
                val obj = JSONObject(raw)
                val keysToCheck = listOf(fnName, "result", "success", "valid", "has_pin", "changed")
                for (key in keysToCheck) {
                    if (obj.has(key) && !obj.isNull(key)) {
                        return obj.optBoolean(key, defaultOnEmpty)
                    }
                }
                val firstKey = obj.keys().asSequence().firstOrNull()
                if (firstKey != null) {
                    return obj.optBoolean(firstKey, defaultOnEmpty)
                }
            } else if (raw.startsWith("[")) {
                val arr = JSONArray(raw)
                if (arr.length() > 0) {
                    val first = arr.get(0)
                    if (first is Boolean) return first
                    if (first is JSONObject) {
                        val firstKey = first.keys().asSequence().firstOrNull()
                        if (firstKey != null) return first.optBoolean(firstKey, defaultOnEmpty)
                    }
                }
            }
        } catch (_: Throwable) {}
        return defaultOnEmpty
    }

    internal fun extractErrorMessage(errRaw: String, statusCode: Int): String {
        if (errRaw.isBlank()) {
            return "Request failed ($statusCode)"
        }
        return try {
            val json = JSONObject(errRaw)
            val msg = json.optString("message").takeIf { it.isNotBlank() }
                ?: json.optString("error_description").takeIf { it.isNotBlank() }
                ?: json.optString("details").takeIf { it.isNotBlank() }
                ?: json.optString("error").takeIf { it.isNotBlank() }
                ?: errRaw
            msg
        } catch (_: Throwable) {
            errRaw
        }
    }
}
