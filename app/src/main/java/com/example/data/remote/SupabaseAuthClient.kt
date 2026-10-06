package com.example.data.remote

import android.util.Log
import com.example.auth.SupabaseProvider
import com.example.data.model.AuthResult
import com.example.data.model.SupabaseSession
import com.example.data.model.SupabaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class SupabaseAuthClient(
    val supabaseUrl: String = GsubzEdgeServiceClient.defaultSupabaseUrl(),
    val supabaseAnonKey: String = GsubzEdgeServiceClient.defaultSupabaseAnonKey()
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    val isLiveConfigured: Boolean
        get() = supabaseUrl.isNotBlank() &&
                !supabaseUrl.contains("your-project") &&
                supabaseUrl.startsWith("http") &&
                supabaseAnonKey.isNotBlank() &&
                !supabaseAnonKey.contains("your-supabase-anon-key")

    suspend fun signUp(email: String, password: String, fullName: String, phone: String? = null): AuthResult =
        withContext(Dispatchers.IO) {
            if (!isLiveConfigured) {
                // Return demo simulated user when credentials are not yet configured
                val demoUser = SupabaseUser(
                    id = UUID.randomUUID().toString().take(12),
                    email = email.trim(),
                    fullName = fullName.trim(),
                    phone = phone?.trim()
                )
                val demoSession = SupabaseSession(
                    accessToken = "demo_token_" + UUID.randomUUID().toString(),
                    refreshToken = "demo_refresh_" + UUID.randomUUID().toString(),
                    user = demoUser
                )
                return@withContext AuthResult.Success(
                    user = demoUser,
                    session = demoSession,
                    message = "Account registered successfully (Demo Mode)"
                )
            }

            try {
                val cleanUrl = supabaseUrl.trimEnd('/')
                val endpoint = "$cleanUrl/auth/v1/signup"

                val jsonBody = JSONObject().apply {
                    put("email", email.trim())
                    put("password", password)
                    val metadata = JSONObject().apply {
                        put("full_name", fullName.trim())
                        if (!phone.isNullOrBlank()) {
                            put("phone", phone.trim())
                        }
                    }
                    put("data", metadata)
                }

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", supabaseAnonKey)
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val jsonObj = JSONObject(responseBody)
                    val userObj = jsonObj.optJSONObject("user") ?: jsonObj
                    val identities = userObj.optJSONArray("identities")
                    if (identities != null && identities.length() == 0) {
                        return@withContext AuthResult.Error("This email address has already been registered.")
                    }

                    val userId = userObj.optString("id", "")
                    val userEmail = userObj.optString("email", email)
                    val metadata = userObj.optJSONObject("user_metadata")
                    val parsedName = metadata?.optString("full_name", "")?.trim()?.takeIf {
                        it.isNotBlank() && !it.equals("null", ignoreCase = true)
                    } ?: ""
                    val parsedPhone = metadata?.optString("phone", "")?.trim()?.takeIf {
                        it.isNotBlank() && !it.equals("null", ignoreCase = true)
                    }

                    val accessToken = jsonObj.optString("access_token", "")
                    val refreshToken = jsonObj.optString("refresh_token", "")

                    val user = SupabaseUser(
                        id = userId,
                        email = userEmail,
                        fullName = parsedName,
                        phone = parsedPhone
                    )

                    if (accessToken.isNotBlank()) {
                        AuthResult.Success(
                            user = user,
                            session = SupabaseSession(accessToken, refreshToken, user),
                            message = "Account created and logged in!"
                        )
                    } else {
                        // Email confirmation may be enabled in Supabase project
                        AuthResult.RequiresEmailConfirmation(userEmail)
                    }
                } else {
                    val errMsg = parseError(responseBody, "Sign up failed (${response.code})")
                    AuthResult.Error(errMsg)
                }
            } catch (e: Exception) {
                Log.e("SupabaseAuth", "SignUp network error", e)
                AuthResult.Error(e.localizedMessage ?: "Network connection error. Check your internet.")
            }
        }

    suspend fun login(email: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (!isLiveConfigured) {
                return@withContext AuthResult.Error("Supabase is not configured.")
            }

            try {
                val cleanUrl = supabaseUrl.trimEnd('/')
                val endpoint = "$cleanUrl/auth/v1/token?grant_type=password"

                val jsonBody = JSONObject().apply {
                    put("email", email.trim())
                    put("password", password)
                }

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", supabaseAnonKey)
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val jsonObj = JSONObject(responseBody)
                    val accessToken = jsonObj.optString("access_token")
                    val refreshToken = jsonObj.optString("refresh_token")
                    val userObj = jsonObj.optJSONObject("user")

                    val userId = userObj?.optString("id") ?: ""
                    val userEmail = userObj?.optString("email") ?: email
                    val metadata = userObj?.optJSONObject("user_metadata")
                    val parsedName = metadata?.optString("full_name", "")?.trim()?.takeIf {
                        it.isNotBlank() && !it.equals("null", ignoreCase = true)
                    } ?: ""

                    val user = SupabaseUser(
                        id = userId,
                        email = userEmail,
                        fullName = parsedName,
                        phone = null
                    )
                    val session = SupabaseSession(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        user = user
                    )
                    AuthResult.Success(user = user, session = session)
                } else {
                    val errMsg = parseError(responseBody, "Invalid login credentials")
                    AuthResult.Error(errMsg)
                }
            } catch (e: Exception) {
                Log.e("SupabaseAuth", "Login network error", e)
                AuthResult.Error(e.localizedMessage ?: "Network connection error. Check your internet.")
            }
        }

    suspend fun sendPasswordResetEmail(email: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (email.isBlank() || !email.contains("@")) {
                return@withContext AuthResult.Error("Please enter a valid email address.")
            }
            if (!isLiveConfigured) {
                return@withContext AuthResult.Success(
                    user = SupabaseUser(
                        id = "usr_demo",
                        email = email.trim(),
                        fullName = email.substringBefore("@")
                    ),
                    message = "Password reset email sent to ${email.trim()} (Demo Mode)"
                )
            }

            try {
                val cleanUrl = supabaseUrl.trimEnd('/')
                val endpoint = "$cleanUrl/auth/v1/recover"

                val jsonBody = JSONObject().apply {
                    put("email", email.trim())
                }

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", supabaseAnonKey)
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    AuthResult.Success(
                        user = SupabaseUser(id = "", email = email.trim(), fullName = ""),
                        message = "Password reset instructions have been sent to $email."
                    )
                } else {
                    val errMsg = parseError(responseBody, "Failed to send reset email (${response.code})")
                    AuthResult.Error(errMsg)
                }
            } catch (e: Exception) {
                Log.e("SupabaseAuth", "sendPasswordResetEmail error", e)
                AuthResult.Error(e.localizedMessage ?: "Network connection error. Check your internet.")
            }
        }

    suspend fun resetPasswordWithOtp(email: String, otpToken: String, newPassword: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (newPassword.length < 6) {
                return@withContext AuthResult.Error("Password must be at least 6 characters.")
            }
            if (!isLiveConfigured) {
                val demoUser = SupabaseUser(
                    id = "usr_demo",
                    email = email.trim(),
                    fullName = email.substringBefore("@")
                )
                return@withContext AuthResult.Success(
                    user = demoUser,
                    session = SupabaseSession("demo_token_reset", null, demoUser),
                    message = "Password reset successfully! You can now log in."
                )
            }

            try {
                val cleanUrl = supabaseUrl.trimEnd('/')
                // Step 1: Verify OTP recovery token to exchange for a recovery session
                val verifyEndpoint = "$cleanUrl/auth/v1/verify"
                val verifyBody = JSONObject().apply {
                    put("type", "recovery")
                    put("email", email.trim())
                    put("token", otpToken.trim())
                }

                val verifyReq = Request.Builder()
                    .url(verifyEndpoint)
                    .addHeader("apikey", supabaseAnonKey)
                    .addHeader("Content-Type", "application/json")
                    .post(verifyBody.toString().toRequestBody(jsonMediaType))
                    .build()

                val verifyRes = client.newCall(verifyReq).execute()
                val verifyResponseBody = verifyRes.body?.string() ?: ""

                if (!verifyRes.isSuccessful) {
                    val err = parseError(verifyResponseBody, "Invalid or expired recovery code")
                    return@withContext AuthResult.Error(err)
                }

                val verifyJson = JSONObject(verifyResponseBody)
                val recoveryAccessToken = verifyJson.optString("access_token", "")
                if (recoveryAccessToken.isBlank()) {
                    return@withContext AuthResult.Error("Failed to obtain recovery session. Check the recovery code.")
                }

                // Step 2: Update password using recovery access token
                val updateEndpoint = "$cleanUrl/auth/v1/user"
                val updateBody = JSONObject().apply {
                    put("password", newPassword)
                }

                val updateReq = Request.Builder()
                    .url(updateEndpoint)
                    .addHeader("apikey", supabaseAnonKey)
                    .addHeader("Authorization", "Bearer $recoveryAccessToken")
                    .addHeader("Content-Type", "application/json")
                    .put(updateBody.toString().toRequestBody(jsonMediaType))
                    .build()

                val updateRes = client.newCall(updateReq).execute()
                val updateResponseBody = updateRes.body?.string() ?: ""

                if (updateRes.isSuccessful) {
                    val userObj = JSONObject(updateResponseBody)
                    val userId = userObj.optString("id", "")
                    val userEmail = userObj.optString("email", email)
                    val metadata = userObj.optJSONObject("user_metadata")
                    val parsedName = metadata?.optString("full_name", "")?.trim()?.takeIf {
                        it.isNotBlank() && !it.equals("null", ignoreCase = true)
                    } ?: ""
                    val updatedUser = SupabaseUser(id = userId, email = userEmail, fullName = parsedName)
                    AuthResult.Success(
                        user = updatedUser,
                        session = SupabaseSession(recoveryAccessToken, null, updatedUser),
                        message = "Your password has been successfully updated!"
                    )
                } else {
                    val errMsg = parseError(updateResponseBody, "Failed to update password")
                    AuthResult.Error(errMsg)
                }
            } catch (e: Exception) {
                Log.e("SupabaseAuth", "resetPasswordWithOtp error", e)
                AuthResult.Error(e.localizedMessage ?: "Network error during password reset.")
            }
        }

    suspend fun updateEmail(newEmail: String, accessToken: String?): AuthResult =
        withContext(Dispatchers.IO) {
            if (newEmail.isBlank() || !newEmail.contains("@")) {
                return@withContext AuthResult.Error("Please enter a valid new email address.")
            }
            if (!isLiveConfigured || accessToken.isNullOrBlank()) {
                return@withContext AuthResult.Error("Not authenticated.")
            }

            try {
                val cleanUrl = supabaseUrl.trimEnd('/')
                val endpoint = "$cleanUrl/auth/v1/user"

                val jsonBody = JSONObject().apply {
                    put("email", newEmail.trim())
                }

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", supabaseAnonKey)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Content-Type", "application/json")
                    .put(jsonBody.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val userObj = JSONObject(responseBody)
                    val userId = userObj.optString("id", "")
                    val confirmedEmail = userObj.optString("email", newEmail.trim())
                    val metadata = userObj.optJSONObject("user_metadata")
                    val parsedName = metadata?.optString("full_name", "")?.trim()?.takeIf {
                        it.isNotBlank() && !it.equals("null", ignoreCase = true)
                    } ?: ""
                    val updatedUser = SupabaseUser(
                        id = userId,
                        email = confirmedEmail,
                        fullName = parsedName
                    )
                    AuthResult.Success(
                        user = updatedUser,
                        message = "Email address updated successfully! A confirmation may have been sent to $newEmail."
                    )
                } else {
                    val errMsg = parseError(responseBody, "Failed to update email address")
                    AuthResult.Error(errMsg)
                }
            } catch (e: Exception) {
                Log.e("SupabaseAuth", "updateEmail error", e)
                AuthResult.Error(e.localizedMessage ?: "Network error while updating email.")
            }
        }

    suspend fun deleteUserAccount(
        userId: String?,
        accessToken: String?,
        email: String?
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isLiveConfigured) {
            return@withContext true
        }

        val cleanUrl = supabaseUrl.trimEnd('/')
        val bearerToken = if (!accessToken.isNullOrBlank()) {
            if (accessToken.startsWith("Bearer ", ignoreCase = true)) accessToken else "Bearer $accessToken"
        } else {
            "Bearer $supabaseAnonKey"
        }

        var deletedSuccessfully = false

        // 1. Try Supabase Edge Functions via Ktor POST to /functions/v1/<name> with session access token
        val edgeFunctionNames = listOf(
            "delete-user-account",
            "delete-account",
            "delete_user_account",
            "delete-user"
        )
        for (fnName in edgeFunctionNames) {
            try {
                val jsonBody = JSONObject().apply {
                    if (!userId.isNullOrBlank()) {
                        put("userId", userId)
                        put("user_id", userId)
                        put("id", userId)
                    }
                    if (!email.isNullOrBlank()) {
                        put("email", email.trim().lowercase())
                    }
                }

                val (statusCode, bodyStr) = SupabaseProvider.postEdgeFunction(
                    functionName = fnName,
                    bodyJson = jsonBody.toString(),
                    accessTokenOverride = accessToken
                )
                Log.d("SupabaseAuth", "Delete edge response ($fnName): code=$statusCode body=$bodyStr")
                if (statusCode in 200..299) {
                    deletedSuccessfully = true
                    break
                }
            } catch (e: Exception) {
                Log.w("SupabaseAuth", "Edge delete call error for $fnName: ${e.message}")
            }
        }

        // 2. Direct targeted DELETE on public.users table (deleting ONLY this user in the Supabase project)
        if (!userId.isNullOrBlank() || !email.isNullOrBlank()) {
            val userFilters = mutableListOf<String>()
            if (!userId.isNullOrBlank()) userFilters.add("id=eq.$userId")
            if (!email.isNullOrBlank()) userFilters.add("email=eq.${email.trim().lowercase()}")

            for (filter in userFilters) {
                try {
                    val req = Request.Builder()
                        .url("$cleanUrl/rest/v1/users?$filter")
                        .addHeader("apikey", supabaseAnonKey)
                        .addHeader("Authorization", bearerToken)
                        .addHeader("Prefer", "return=minimal")
                        .delete()
                        .build()
                    val response = client.newCall(req).execute()
                    Log.d("SupabaseAuth", "DELETE /rest/v1/users?$filter response: code=${response.code}")
                    if (response.isSuccessful || response.code in 200..204) {
                        deletedSuccessfully = true
                    }
                } catch (e: Exception) {
                    Log.w("SupabaseAuth", "DELETE /rest/v1/users error: ${e.message}")
                }
            }
        }

        // 3. Direct targeted DELETE on public.profiles table (deleting ONLY this user's profile)
        if (!userId.isNullOrBlank() || !email.isNullOrBlank()) {
            val profileFilters = mutableListOf<String>()
            if (!userId.isNullOrBlank()) profileFilters.add("id=eq.$userId")
            if (!email.isNullOrBlank()) profileFilters.add("email=eq.${email.trim().lowercase()}")

            for (filter in profileFilters) {
                try {
                    val req = Request.Builder()
                        .url("$cleanUrl/rest/v1/profiles?$filter")
                        .addHeader("apikey", supabaseAnonKey)
                        .addHeader("Authorization", bearerToken)
                        .addHeader("Prefer", "return=minimal")
                        .delete()
                        .build()
                    val response = client.newCall(req).execute()
                    Log.d("SupabaseAuth", "DELETE /rest/v1/profiles?$filter response: code=${response.code}")
                    if (response.isSuccessful || response.code in 200..204) {
                        deletedSuccessfully = true
                    }
                } catch (e: Exception) {
                    Log.w("SupabaseAuth", "DELETE /rest/v1/profiles error: ${e.message}")
                }
            }
        }

        // 4. Try Supabase PostgreSQL RPC functions (delete_user / delete_user_account / delete_current_user / delete_account)
        if (!accessToken.isNullOrBlank()) {
            val rpcEndpoints = listOf(
                "$cleanUrl/rest/v1/rpc/delete_user",
                "$cleanUrl/rest/v1/rpc/delete_user_account",
                "$cleanUrl/rest/v1/rpc/delete_current_user",
                "$cleanUrl/rest/v1/rpc/delete_account"
            )
            val rpcBody = JSONObject().apply {
                if (!userId.isNullOrBlank()) {
                    put("user_id", userId)
                    put("userId", userId)
                }
            }
            for (rpcEndpoint in rpcEndpoints) {
                try {
                    val request = Request.Builder()
                        .url(rpcEndpoint)
                        .addHeader("apikey", supabaseAnonKey)
                        .addHeader("Authorization", bearerToken)
                        .addHeader("Content-Type", "application/json")
                        .post(rpcBody.toString().toRequestBody(jsonMediaType))
                        .build()

                    val response = client.newCall(request).execute()
                    Log.d("SupabaseAuth", "Delete RPC response ($rpcEndpoint): code=${response.code}")
                    if (response.isSuccessful || response.code in 200..299) {
                        deletedSuccessfully = true
                        break
                    }
                } catch (e: Exception) {
                    Log.w("SupabaseAuth", "RPC delete call error for $rpcEndpoint: ${e.message}")
                }
            }
        }

        // 5. Try standard REST DELETE /auth/v1/user
        if (!accessToken.isNullOrBlank()) {
            try {
                val deleteReq = Request.Builder()
                    .url("$cleanUrl/auth/v1/user")
                    .addHeader("apikey", supabaseAnonKey)
                    .addHeader("Authorization", bearerToken)
                    .delete()
                    .build()
                val response = client.newCall(deleteReq).execute()
                Log.d("SupabaseAuth", "DELETE /auth/v1/user response: code=${response.code}")
                if (response.isSuccessful || response.code in 200..204) {
                    deletedSuccessfully = true
                }
            } catch (e: Exception) {
                Log.w("SupabaseAuth", "DELETE /auth/v1/user error: ${e.message}")
            }
        }

        // 6. Try Admin REST API if userId is present
        if (!userId.isNullOrBlank()) {
            try {
                val adminReq = Request.Builder()
                    .url("$cleanUrl/auth/v1/admin/users/$userId")
                    .addHeader("apikey", supabaseAnonKey)
                    .addHeader("Authorization", bearerToken)
                    .delete()
                    .build()
                val response = client.newCall(adminReq).execute()
                Log.d("SupabaseAuth", "DELETE /auth/v1/admin/users response: code=${response.code}")
                if (response.isSuccessful || response.code in 200..204) {
                    deletedSuccessfully = true
                }
            } catch (e: Exception) {
                Log.w("SupabaseAuth", "DELETE /auth/v1/admin/users error: ${e.message}")
            }
        }

        deletedSuccessfully
    }

    suspend fun logout(accessToken: String?): Boolean =
        withContext(Dispatchers.IO) {
            if (!isLiveConfigured || accessToken.isNullOrBlank() || accessToken.startsWith("demo_token_")) {
                return@withContext true
            }

            try {
                val cleanUrl = supabaseUrl.trimEnd('/')
                val endpoint = "$cleanUrl/auth/v1/logout"

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("apikey", supabaseAnonKey)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                Log.w("SupabaseAuth", "Remote logout failed, clearing local state", e)
                true
            }
        }

    private fun parseError(responseBody: String, fallback: String): String {
        return try {
            val json = JSONObject(responseBody)
            val raw = when {
                json.has("msg") -> json.getString("msg")
                json.has("error_description") -> json.getString("error_description")
                json.has("message") -> json.getString("message")
                else -> fallback
            }
            val lower = raw.lowercase()
            if (lower.contains("already registered") || lower.contains("already exists") || lower.contains("user_already_exists")) {
                "This email address has already been registered."
            } else {
                raw
            }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
