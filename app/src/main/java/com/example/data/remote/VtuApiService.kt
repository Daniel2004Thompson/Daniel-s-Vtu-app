package com.example.data.remote

import android.util.Log
import com.example.auth.SupabaseProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

// ==========================================
// 1. GSUBZ VTU DATA MODELS (Moshi + Retrofit)
// ==========================================

@JsonClass(generateAdapter = false)
data class VtuRequest(
    @Json(name = "user_id") val userId: String,
    @Json(name = "email") val email: String,
    @Json(name = "service") val service: String,
    @Json(name = "service_type") val serviceType: String = service,
    @Json(name = "service_id") val serviceId: String,
    @Json(name = "serviceID") val gsubzServiceId: String = serviceId,
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "amount") val amount: Int? = null,
    @Json(name = "plan_id") val planId: String? = null,
    @Json(name = "plan") val plan: String? = planId,
    @Json(name = "customer_id") val customerId: String? = null,
    @Json(name = "customerID") val gsubzCustomerId: String? = customerId
)

@JsonClass(generateAdapter = false)
data class VtuResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "narration") val narration: String? = null,
    @Json(name = "gsubz_data") val gsubzData: Map<String, Any?>? = null
) {
    @Suppress("UNCHECKED_CAST")
    fun extractContentMap(): Map<String, Any?>? {
        return gsubzData?.get("content") as? Map<String, Any?>
    }

    fun extractCode(): String? {
        return (gsubzData?.get("code") ?: extractContentMap()?.get("code"))?.toString()?.takeIf { it.isNotBlank() }
    }

    fun extractStatus(): String? {
        return (gsubzData?.get("status") ?: extractContentMap()?.get("status"))?.toString()?.takeIf { it.isNotBlank() }
    }

    fun extractDescription(): String? {
        return (gsubzData?.get("description") ?: extractContentMap()?.get("description"))?.toString()?.takeIf { it.isNotBlank() }
    }

    fun extractApiResponse(): String? {
        return gsubzData?.get("api_response")?.toString()?.takeIf { it.isNotBlank() }
    }

    fun extractServiceName(): String? {
        return extractContentMap()?.get("serviceName")?.toString()?.takeIf { it.isNotBlank() }
    }

    fun extractServiceImageUrl(): String? {
        val raw = extractContentMap()?.get("image")?.toString()?.takeIf { it.isNotBlank() } ?: return null
        return if (raw.startsWith("//")) "https:$raw" else raw
    }

    fun extractTransactionId(): String? {
        val content = extractContentMap()
        return (content?.get("transactionID")?.toString()?.takeIf { it.isNotBlank() }
            ?: content?.get("requestID")?.toString()?.takeIf { it.isNotBlank() }
            ?: gsubzData?.get("transactionID")?.toString()?.takeIf { it.isNotBlank() }
            ?: gsubzData?.get("requestID")?.toString()?.takeIf { it.isNotBlank() })
    }

    fun extractTokenOrPin(): String? {
        val content = extractContentMap()
        return (content?.get("token")?.toString()?.takeIf { it.isNotBlank() }
            ?: content?.get("pin")?.toString()?.takeIf { it.isNotBlank() }
            ?: gsubzData?.get("token")?.toString()?.takeIf { it.isNotBlank() }
            ?: gsubzData?.get("pin")?.toString()?.takeIf { it.isNotBlank() })
    }
}

// --- API Key Request/response models ---

data class GenerateKeyRequest(
    val name: String,
    val email: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("userId") val altUserId: String? = null,
    val action: String = "generate",
    val regenerate: Boolean = true,
    @SerializedName("is_regenerate") val isRegenerate: Boolean = true,
    @SerializedName("regenerate_key") val regenerateKey: Boolean = true
)

data class RegenerateKeyRequest(
    val action: String = "regenerate",
    val regenerate: Boolean = true,
    @SerializedName("is_regenerate") val isRegenerate: Boolean = true,
    @SerializedName("regenerate_key") val regenerateKey: Boolean = true,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("userId") val altUserId: String? = null,
    val email: String? = null,
    val name: String? = null,
    @SerializedName("current_api_key") val currentApiKey: String? = null,
    @SerializedName("api_key") val apiKey: String? = null
)

data class ApiKeyResponse(
    val status: String? = null,
    val success: Boolean? = null,
    val message: String? = null,
    @SerializedName("api_key") val apiKey: String? = null,
    @SerializedName("apiKey") val alternativeApiKey: String? = null,
    @SerializedName("key") val key: String? = null,
    @SerializedName("token") val token: String? = null,
    @SerializedName("vtu_api_key") val vtuApiKey: String? = null,
    @SerializedName("public_key") val publicKey: String? = null,
    @SerializedName("publicKey") val altPublicKey: String? = null,
    val data: ApiKeyData? = null,
    val error: String? = null
) {
    fun extractApiKey(): String? {
        return apiKey?.takeIf { it.isNotBlank() }
            ?: alternativeApiKey?.takeIf { it.isNotBlank() }
            ?: vtuApiKey?.takeIf { it.isNotBlank() }
            ?: key?.takeIf { it.isNotBlank() }
            ?: token?.takeIf { it.isNotBlank() }
            ?: data?.apiKey?.takeIf { it.isNotBlank() }
            ?: data?.alternativeApiKey?.takeIf { it.isNotBlank() }
            ?: data?.key?.takeIf { it.isNotBlank() }
            ?: data?.token?.takeIf { it.isNotBlank() }
    }

    fun extractPublicKey(): String? {
        return publicKey?.takeIf { it.isNotBlank() }
            ?: altPublicKey?.takeIf { it.isNotBlank() }
            ?: data?.publicKey?.takeIf { it.isNotBlank() }
    }
}

data class ApiKeyData(
    @SerializedName("api_key") val apiKey: String? = null,
    @SerializedName("apiKey") val alternativeApiKey: String? = null,
    @SerializedName("key") val key: String? = null,
    @SerializedName("token") val token: String? = null,
    @SerializedName("public_key") val publicKey: String? = null
)

// ==========================================
// 2. RETROFIT API INTERFACE
// ==========================================

interface VtuApiService {
    // Exact relative path matching your Supabase project ID
    @POST("functions/v1/Gsubz-VTU-Services")
    suspend fun processTransaction(
        @Header("Authorization") bearerToken: String,
        @Header("apikey") apikey: String = SupabaseProvider.rawKey,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: VtuRequest
    ): Response<VtuResponse>

    @POST
    suspend fun generateApiKey(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Header("apikey") apikey: String,
        @Body request: GenerateKeyRequest
    ): Response<ApiKeyResponse>

    @POST
    suspend fun regenerateApiKey(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Header("apikey") apikey: String,
        @Body request: RegenerateKeyRequest
    ): Response<ApiKeyResponse>
}

// ==========================================
// 3. NETWORK & SERVICE MANAGER (Gsubz-VTU-Services)
// ==========================================

class VtuManager(
    private val supabaseClient: SupabaseClient? = SupabaseProvider.client,
    private val supabaseAnonKey: String = SupabaseProvider.rawKey
) {

    companion object {
        // Your project's base URL
        const val BASE_URL = "https://yjymxdzdhvbdjramlipg.supabase.co/"
    }

    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val apiService: VtuApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(ApiNetworkClient.okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(VtuApiService::class.java)

    // ------------------------------------------
    // AUTHENTICATION METHODS
    // ------------------------------------------

    /**
     * Sign Up a new user with Email and Password
     */
    suspend fun signUp(emailInput: String, passwordInput: String): Result<Unit> {
        return try {
            val client = supabaseClient ?: SupabaseProvider.client
                ?: return Result.failure(IllegalStateException("Supabase client is not initialized."))
            client.auth.signUpWith(Email) {
                email = emailInput
                password = passwordInput
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Log In an existing user with Email and Password
     */
    suspend fun login(emailInput: String, passwordInput: String): Result<Unit> {
        return try {
            val client = supabaseClient ?: SupabaseProvider.client
                ?: return Result.failure(IllegalStateException("Supabase client is not initialized."))
            client.auth.signInWith(Email) {
                email = emailInput
                password = passwordInput
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign Out current user session
     */
    suspend fun logout() {
        val client = supabaseClient ?: SupabaseProvider.client
        client?.auth?.signOut()
    }

    // ------------------------------------------
    // VTU TRANSACTION METHODS
    // ------------------------------------------

    private fun resolveUserCredentials(
        userIdOverride: String? = null,
        emailOverride: String? = null
    ): Result<Pair<String, String>> {
        val explicitId = userIdOverride?.trim()?.takeIf { it.isNotBlank() }
        val explicitEmail = emailOverride?.trim()?.takeIf { it.isNotBlank() }
        if (explicitId != null && explicitEmail != null) {
            return Result.success(explicitId to explicitEmail)
        }

        val currentUser = try {
            (supabaseClient ?: SupabaseProvider.client)?.auth?.currentUserOrNull()
        } catch (_: Throwable) {
            null
        } ?: return Result.failure(IllegalStateException("No user currently logged in."))

        val userId = explicitId ?: currentUser.id
        val userEmail = explicitEmail ?: currentUser.email
            ?: return Result.failure(IllegalStateException("Logged in user has no associated email."))

        return Result.success(userId to userEmail)
    }

    /**
     * Buys Airtime using the currently logged-in user's credentials automatically
     */
    suspend fun buyAirtime(
        phone: String,
        network: String, // "airtel", "mtn", "glo", "9mobile"
        amount: Int,
        userIdOverride: String? = null,
        emailOverride: String? = null
    ): Result<VtuResponse> {
        val (userId, userEmail) = resolveUserCredentials(userIdOverride, emailOverride).getOrElse {
            return Result.failure(it)
        }

        val payload = VtuRequest(
            userId = userId,
            email = userEmail,
            service = "airtime",
            serviceId = network.trim().lowercase(),
            phone = phone.trim(),
            amount = amount
        )

        return executeTransaction(payload)
    }

    /**
     * Buys Data using the currently logged-in user's credentials automatically
     */
    suspend fun buyData(
        phone: String,
        network: String, // e.g. "mtn_sme", "mtn_cg", "airtel_cg", "glo_data", "etisalat_data", or "mtn", "airtel", "glo", "9mobile"
        planId: String,
        amount: Int? = null,
        userIdOverride: String? = null,
        emailOverride: String? = null
    ): Result<VtuResponse> {
        val (userId, userEmail) = resolveUserCredentials(userIdOverride, emailOverride).getOrElse {
            return Result.failure(it)
        }

        val payload = VtuRequest(
            userId = userId,
            email = userEmail,
            service = "data",
            serviceId = network.trim().lowercase(),
            phone = phone.trim(),
            amount = amount,
            planId = planId.trim()
        )

        return executeTransaction(payload)
    }

    /**
     * Buys Cable TV subscription (DStv, GOtv, StarTimes, Showmax) using Gsubz-VTU-Services
     */
    suspend fun buyCableTv(
        customerId: String, // Smartcard / IUC number
        provider: String,   // "dstv", "gotv", "startimes", "showmax"
        planId: String,     // e.g. "dstv-padi", "gotv-jolli", "nova", "full_3"
        phone: String? = null,
        amount: Int? = null,
        userIdOverride: String? = null,
        emailOverride: String? = null
    ): Result<VtuResponse> {
        val (userId, userEmail) = resolveUserCredentials(userIdOverride, emailOverride).getOrElse {
            return Result.failure(it)
        }

        val cleanCustomer = customerId.trim()
        val payload = VtuRequest(
            userId = userId,
            email = userEmail,
            service = "cable",
            serviceId = provider.trim().lowercase(),
            phone = phone?.trim()?.takeIf { it.isNotBlank() } ?: cleanCustomer,
            amount = amount,
            planId = planId.trim(),
            customerId = cleanCustomer
        )

        return executeTransaction(payload)
    }

    /**
     * Buys Electricity Meter Token / Postpaid Bill across all 10 DISCOs
     * ("ikeja-electric", "eko-electric", "abuja-electric", "ibadan-electric", "kano-electric",
     * "portharcourt-electric", "jos-electric", "kaduna-electric", "enugu-electric", "benin-electric")
     */
    suspend fun buyElectricity(
        customerId: String, // Meter number
        discoServiceId: String,
        amount: Int,
        meterTypePlanId: String = "prepaid", // "prepaid" or "postpaid"
        phone: String? = null,
        userIdOverride: String? = null,
        emailOverride: String? = null
    ): Result<VtuResponse> {
        val (userId, userEmail) = resolveUserCredentials(userIdOverride, emailOverride).getOrElse {
            return Result.failure(it)
        }

        val cleanMeter = customerId.trim()
        val payload = VtuRequest(
            userId = userId,
            email = userEmail,
            service = "electricity",
            serviceId = discoServiceId.trim().lowercase(),
            phone = phone?.trim()?.takeIf { it.isNotBlank() } ?: cleanMeter,
            amount = amount,
            planId = meterTypePlanId.trim().lowercase(),
            customerId = cleanMeter
        )

        return executeTransaction(payload)
    }

    /**
     * Buys Education Result Checker PINs (WAEC, NECO, NABTEB, JAMB) via Gsubz-VTU-Services
     */
    suspend fun buyEducationPin(
        phone: String,
        examServiceId: String, // "waec", "neco", "nabteb", "jamb"
        planId: String = examServiceId,
        amount: Int? = null,
        customerId: String? = phone,
        userIdOverride: String? = null,
        emailOverride: String? = null
    ): Result<VtuResponse> {
        val (userId, userEmail) = resolveUserCredentials(userIdOverride, emailOverride).getOrElse {
            return Result.failure(it)
        }

        val cleanExam = examServiceId.trim().lowercase()
        val cleanPhone = phone.trim()
        val payload = VtuRequest(
            userId = userId,
            email = userEmail,
            service = "education",
            serviceId = cleanExam,
            phone = cleanPhone,
            amount = amount,
            planId = planId.trim().ifBlank { cleanExam.uppercase() },
            customerId = customerId?.trim()?.ifBlank { cleanPhone } ?: cleanPhone
        )

        return executeTransaction(payload)
    }

    /**
     * Fetches live service plans from Gsubz API for the given serviceId
     */
    suspend fun fetchLivePlans(serviceId: String): Result<List<GsubzPlanItem>> {
        return try {
            val plans = GsubzVtuService.fetchLivePlansForService(serviceId)
            Result.success(plans)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun executeTransaction(payload: VtuRequest): Result<VtuResponse> = withContext(Dispatchers.IO) {
        return@withContext try {
            val activeToken = SupabaseProvider.resolveSessionAccessToken(supabaseAnonKey)
            val bearerHeader = if (activeToken.startsWith("Bearer ", ignoreCase = true)) {
                activeToken
            } else {
                "Bearer $activeToken"
            }
            val normalizedService = when (payload.service.trim().lowercase()) {
                "cable_tv", "tv", "cablesub" -> "cable"
                "power", "meter" -> "electricity"
                else -> payload.service.trim().lowercase()
            }
            val normalizedPayload = payload.copy(
                service = normalizedService,
                serviceType = normalizedService
            )
            var response = apiService.processTransaction(
                bearerToken = bearerHeader,
                apikey = supabaseAnonKey,
                request = normalizedPayload
            )
            if (response.code() == 400 && normalizedService == "education") {
                val fallbackPayload = normalizedPayload.copy(
                    service = "data",
                    serviceType = "data"
                )
                response = apiService.processTransaction(
                    bearerToken = bearerHeader,
                    apikey = supabaseAnonKey,
                    request = fallbackPayload
                )
            }
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// --- Network client provider ---

object ApiNetworkClient {
    private val BASE_URL: String
        get() = "${com.example.util.SecurityVault.supabaseUrl().trimEnd('/')}/"

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val service: VtuApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VtuApiService::class.java)
    }
}

// --- Repository ---

class ApiKeyRepository(private val service: VtuApiService = ApiNetworkClient.service) {

    companion object {
        val GENERATE_API_KEY_URL: String
            get() = "${com.example.util.SecurityVault.supabaseUrl()}/functions/v1/Generate-api-key"
        val GENERATE_API_KEY_URL_ALT: String
            get() = "${com.example.util.SecurityVault.supabaseUrl()}/functions/v1/generate-api-key"

        val DEFAULT_ANON_KEY: String
            get() = com.example.util.SecurityVault.supabaseAnonKey()
    }

    /**
     * Generates a new API Key for a specific user using per-user JSON body and Authorization Bearer header.
     */
    suspend fun generateApiKeyForUser(
        userId: String,
        email: String,
        name: String,
        userToken: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        dispatchKeyRequest(
            userId = userId,
            email = email,
            name = name,
            action = "generate",
            currentKey = "",
            userToken = userToken
        )
    }

    /**
     * Regenerates an API Key for a specific user, passing their current key in Authorization Bearer
     * and their user details in the JSON body.
     */
    suspend fun regenerateApiKeyForUser(
        userId: String,
        email: String,
        name: String,
        currentKey: String,
        userToken: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        dispatchKeyRequest(
            userId = userId,
            email = email,
            name = name,
            action = "regenerate",
            currentKey = currentKey,
            userToken = userToken
        )
    }

    /**
     * Backwards-compatible overload for generateApiKey(name, email).
     */
    suspend fun generateApiKey(name: String, email: String): Result<String> =
        generateApiKeyForUser(
            userId = "usr_" + UUID.randomUUID().toString().replace("-", "").take(8),
            email = email,
            name = name
        )

    /**
     * Backwards-compatible overload for regenerateApiKey(currentKey).
     */
    suspend fun regenerateApiKey(currentKey: String): Result<String> =
        regenerateApiKeyForUser(
            userId = "usr_developer",
            email = "developer@vtu.com",
            name = "VTU Developer",
            currentKey = currentKey
        )

    /**
     * Calls the Generate-api-key Edge Function via Ktor POST to /functions/v1/<name>
     * sending the logged-in user's session access token via SupabaseProvider.client.
     */
    private suspend fun dispatchKeyRequest(
        userId: String,
        email: String,
        name: String,
        action: String,
        currentKey: String,
        userToken: String?
    ): Result<String> {
        val cleanEmail = email.trim().lowercase()
        val isRegenerate = action.equals("regenerate", ignoreCase = true)

        val jsonPayload = JSONObject().apply {
            put("user_id", userId.trim())
            put("userId", userId.trim())
            put("email", cleanEmail)
            put("name", name.trim())
            put("fullName", name.trim())
            put("action", action)
            put("regenerate", isRegenerate)
            put("is_regenerate", isRegenerate)
            put("isRegenerate", isRegenerate)
            put("regenerate_key", isRegenerate)
            if (currentKey.isNotBlank()) {
                put("current_api_key", currentKey.trim())
            }
        }.toString()

        val functionsToTry = listOf(
            "Generate-api-key",
            "generate-api-key"
        )
        var lastError: Exception? = null

        for (fnName in functionsToTry) {
            try {
                val (statusCode, responseText) = SupabaseProvider.postEdgeFunction(
                    functionName = fnName,
                    bodyJson = jsonPayload,
                    accessTokenOverride = userToken
                )
                Log.d("ApiKeyRepository", "Supabase Edge ($fnName) status: $statusCode, body: $responseText")

                val json = try {
                    JSONObject(responseText)
                } catch (_: Exception) {
                    null
                }

                if (statusCode in 200..299) {
                    val extractedKey = json?.let { parseKeyFromJson(it) }
                    if (!extractedKey.isNullOrBlank()) {
                        return Result.success(extractedKey)
                    } else {
                        lastError = IOException("Network connection bad. Please check your internet connection and try again.")
                    }
                } else {
                    lastError = IOException("Network connection bad. Please check your internet connection and try again.")
                    if (statusCode != 404) {
                        return Result.failure(lastError)
                    }
                }
            } catch (e: Exception) {
                Log.w("ApiKeyRepository", "Attempt to call $fnName failed: ${e.message}")
                lastError = IOException("Network connection bad. Please check your internet connection and try again.")
            }
        }

        return Result.failure(IOException("Network connection bad. Please check your internet connection and try again."))
    }

    private fun parseKeyFromJson(json: JSONObject): String? {
        val candidateFields = listOf(
            "api_key",
            "apiKey",
            "key",
            "vtu_api_key",
            "secret_key",
            "raw_key",
            "plain_key",
            "full_key",
            "token"
        )
        for (field in candidateFields) {
            val v = json.optString(field).trim()
            if (v.isNotBlank() && !v.equals("null", ignoreCase = true)) {
                return v
            }
        }

        val nestedContainers = listOf("data", "result", "api_client", "client")
        for (container in nestedContainers) {
            val nestedObj = json.optJSONObject(container) ?: continue
            for (field in candidateFields) {
                val v = nestedObj.optString(field).trim()
                if (v.isNotBlank() && !v.equals("null", ignoreCase = true)) {
                    return v
                }
            }
        }

        return null
    }
}
