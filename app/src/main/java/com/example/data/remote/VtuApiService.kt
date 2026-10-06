package com.example.data.remote

import android.util.Log
import com.example.auth.SupabaseProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.github.jan.supabase.auth.auth
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
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

// --- Request/response models ---

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

// --- Retrofit interface ---

interface VtuApiService {
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

// --- Network client provider ---

object ApiNetworkClient {
    private val BASE_URL: String
        get() = "${com.example.util.SecurityVault.supabaseUrl()}/functions/v1/"

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
                        val msg = json?.optString("message") ?: json?.optString("error") ?: "No API key found in response"
                        lastError = IOException(msg)
                    }
                } else {
                    val errorMsg = json?.optString("error")
                        ?.ifBlank { json.optString("message") }
                        ?: "Edge function error ($statusCode)"
                    lastError = IOException(errorMsg)
                    if (statusCode != 404) {
                        return Result.failure(lastError ?: IOException("Request failed ($statusCode)"))
                    }
                }
            } catch (e: Exception) {
                Log.w("ApiKeyRepository", "Attempt to call $fnName failed: ${e.message}")
                lastError = e
            }
        }

        return Result.failure(lastError ?: IOException("Failed to connect to Supabase Edge Function"))
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
