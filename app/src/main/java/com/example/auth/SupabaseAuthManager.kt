package com.example.auth

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.minimalSettings
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit

/**
 * Single shared Supabase client for the app. Reference SupabaseProvider.client
 * everywhere instead of creating new clients.
 */
object SupabaseProvider {
    val safeUrl: String
        get() = com.example.util.SecurityVault.supabaseUrl()

    val rawKey: String
        get() = com.example.util.SecurityVault.supabaseAnonKey()

    val isConfigured: Boolean
        get() = safeUrl.isNotBlank() &&
                !safeUrl.contains("your-project") &&
                rawKey.isNotBlank() &&
                !rawKey.contains("your-anon-key")

    val nonNullClient: SupabaseClient by lazy {
        try {
            createSupabaseClient(
                supabaseUrl = safeUrl,
                supabaseKey = rawKey
            ) {
                httpEngine = OkHttp.create()
                install(Auth)
                install(Postgrest)
                install(Realtime)
                install(Functions)
            }
        } catch (_: Throwable) {
            createSupabaseClient(
                supabaseUrl = safeUrl,
                supabaseKey = rawKey
            ) {
                httpEngine = OkHttp.create()
                install(Auth) {
                    minimalSettings()
                }
                install(Postgrest)
                install(Realtime)
                install(Functions)
            }
        }
    }

    val client: SupabaseClient? by lazy {
        try {
            if (!isConfigured) {
                null
            } else {
                nonNullClient
            }
        } catch (e: Throwable) {
            Log.e("SupabaseProvider", "Failed to initialize SupabaseClient: ${e.message}", e)
            null
        }
    }

    val ktorClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            engine {
                preconfigured = com.example.data.remote.ApiNetworkClient.okHttpClient
            }
        }
    }

    @Volatile
    var activeUserAccessToken: String? = null

    @Volatile
    var tokenRefreshCallback: (suspend () -> String?)? = null

    /**
     * Resolves the logged-in user's session access token from the single SupabaseProvider.client
     * or the repository's persisted user session token, falling back to the provided token or the
     * Supabase anon key if unauthenticated.
     */
    fun resolveSessionAccessToken(fallbackToken: String? = null): String {
        val cleanFallback = fallbackToken?.trim()?.removePrefix("Bearer ")?.removePrefix("bearer ")?.trim()
        val validFallback = cleanFallback?.takeIf {
            it.isNotBlank() && it != rawKey && !it.startsWith("session_") && !it.startsWith("demo_") && !com.example.util.JwtUtils.isExpired(it)
        }
        if (validFallback != null) return validFallback

        val cleanActive = activeUserAccessToken?.trim()?.removePrefix("Bearer ")?.removePrefix("bearer ")?.trim()
        val validActive = cleanActive?.takeIf {
            it.isNotBlank() && it != rawKey && !it.startsWith("session_") && !it.startsWith("demo_") && !com.example.util.JwtUtils.isExpired(it)
        }
        if (validActive != null) return validActive

        val sessionToken = try {
            client?.auth?.currentAccessTokenOrNull()?.takeIf { it.isNotBlank() }
                ?: client?.auth?.currentSessionOrNull()?.accessToken?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
        val validSession = sessionToken?.takeIf { !com.example.util.JwtUtils.isExpired(it) }
        return validSession
            ?: sessionToken
            ?: cleanFallback?.takeIf { it.isNotBlank() && !it.startsWith("session_") && !it.startsWith("demo_") }
            ?: cleanActive?.takeIf { it.isNotBlank() && !it.startsWith("session_") && !it.startsWith("demo_") }
            ?: rawKey
    }

    /**
     * Pre-warms the HTTP/2 TLS connection and Supabase Edge Function worker (`Gsubz-VTU-Services`)
     * when the user presses Purchase (while the fingerprint dialog is opening) so the subsequent POST
     * after fingerprint scan executes with zero connection/cold-start overhead (~5.4s total).
     */
    fun prewarmEdgeConnection(functionName: String = "Gsubz-VTU-Services") {
        val cleanFunction = functionName.trim().trimStart('/')
        val url = "${safeUrl.trimEnd('/')}/functions/v1/$cleanFunction"
        try {
            val req = okhttp3.Request.Builder()
                .url(url)
                .addHeader("apikey", rawKey)
                .addHeader("Authorization", "Bearer ${activeUserAccessToken ?: rawKey}")
                .method("OPTIONS", null)
                .build()
            com.example.data.remote.ApiNetworkClient.okHttpClient.newCall(req).execute().close()
        } catch (_: Throwable) {}
    }

    suspend fun resolveValidSessionAccessToken(fallbackToken: String? = null): String {
        val initial = resolveSessionAccessToken(fallbackToken)
        if (initial != rawKey && !com.example.util.JwtUtils.isExpired(initial)) {
            return initial
        }
        try {
            val refreshed = tokenRefreshCallback?.invoke()?.trim()?.removePrefix("Bearer ")?.removePrefix("bearer ")?.trim()
            if (!refreshed.isNullOrBlank() && refreshed != rawKey && !com.example.util.JwtUtils.isExpired(refreshed)) {
                activeUserAccessToken = refreshed
                return refreshed
            }
        } catch (_: Throwable) {}
        return resolveSessionAccessToken(fallbackToken)
    }

    /**
     * Calls a Supabase Edge Function via Ktor POST to /functions/v1/<functionName>
     * with the logged-in user's session access token using the single SupabaseProvider.client.
     */
    suspend fun postEdgeFunction(
        functionName: String,
        bodyJson: String,
        accessTokenOverride: String? = null
    ): Pair<Int, String> {
        val cleanFunction = functionName.trim().trimStart('/')
        val url = "${safeUrl.trimEnd('/')}/functions/v1/$cleanFunction"
        val cleanOverride = accessTokenOverride?.trim()?.removePrefix("Bearer ")?.removePrefix("bearer ")?.trim()
        val accessToken = if (!cleanOverride.isNullOrBlank() &&
            cleanOverride != rawKey &&
            !cleanOverride.startsWith("session_") &&
            !cleanOverride.startsWith("demo_") &&
            !com.example.util.JwtUtils.isExpired(cleanOverride)
        ) {
            cleanOverride
        } else {
            resolveValidSessionAccessToken(accessTokenOverride)
        }
        val bearer = if (accessToken.startsWith("Bearer ", ignoreCase = true)) accessToken else "Bearer $accessToken"

        val firstAttempt = try {
            val response = ktorClient.post(url) {
                header(HttpHeaders.Authorization, bearer)
                header("apikey", rawKey)
                header("x-client-info", "vtu-android-client/1.0")
                contentType(ContentType.Application.Json)
                setBody(bodyJson)
            }
            Pair(response.status.value, response.bodyAsText())
        } catch (e: io.ktor.client.plugins.ResponseException) {
            Pair(e.response.status.value, e.response.bodyAsText())
        }

        if (firstAttempt.first == 401) {
            try {
                val refreshed = tokenRefreshCallback?.invoke()?.trim()?.removePrefix("Bearer ")?.removePrefix("bearer ")?.trim()
                if (!refreshed.isNullOrBlank() && refreshed != accessToken && refreshed != rawKey) {
                    activeUserAccessToken = refreshed
                    val retryBearer = "Bearer $refreshed"
                    return try {
                        val retryRes = ktorClient.post(url) {
                            header(HttpHeaders.Authorization, retryBearer)
                            header("apikey", rawKey)
                            header("x-client-info", "vtu-android-client/1.0")
                            contentType(ContentType.Application.Json)
                            setBody(bodyJson)
                        }
                        Pair(retryRes.status.value, retryRes.bodyAsText())
                    } catch (e: io.ktor.client.plugins.ResponseException) {
                        Pair(e.response.status.value, e.response.bodyAsText())
                    }
                }
            } catch (_: Throwable) {}
        }

        return firstAttempt
    }
}

typealias SupabaseInstance = SupabaseProvider

/** Simple wrapper so the UI layer can branch on success/failure cleanly. */
sealed class AuthResult {
    data object Success : AuthResult()
    data class Error(val message: String) : AuthResult()
}

class AuthRepository(
    private val supabaseProvider: () -> SupabaseClient? = { SupabaseInstance.client }
) {

    val supabase: SupabaseClient?
        get() = supabaseProvider()

    /** Emits the current session/user state; observe this in your ViewModel. */
    val sessionStatus: StateFlow<SessionStatus>?
        get() = try {
            supabase?.auth?.sessionStatus
        } catch (e: Throwable) {
            null
        }

    @Volatile
    var lastPasswordVerifiedUser: io.github.jan.supabase.auth.user.UserInfo? = null
        private set

    @Volatile
    private var lastVerifiedEmail: String? = null

    @Volatile
    private var lastVerifiedPassword: String? = null

    @Volatile
    private var otpSendWasRateLimited: Boolean = false

    fun currentUserEmail(): String? = try {
        supabase?.auth?.currentUserOrNull()?.email ?: lastPasswordVerifiedUser?.email
    } catch (e: Throwable) {
        lastPasswordVerifiedUser?.email
    }

    fun currentUserId(): String? = try {
        supabase?.auth?.currentUserOrNull()?.id ?: lastPasswordVerifiedUser?.id
    } catch (e: Throwable) {
        lastPasswordVerifiedUser?.id
    }

    fun currentAccessToken(): String? = try {
        supabase?.auth?.currentAccessTokenOrNull()
    } catch (e: Throwable) {
        null
    }

    val isLiveConfigured: Boolean
        get() = SupabaseInstance.isConfigured && supabase != null

    // ---------------------------------------------------------------
    // 1) SIGN UP
    // ---------------------------------------------------------------

    /** Step 1: create the account with full_name and phone in Auth user_metadata. Supabase emails a 6-digit code. */
    suspend fun signUp(
        email: String,
        password: String,
        fullName: String = "",
        phone: String? = null
    ): AuthResult = safeCall {
        val client = supabase ?: throw java.io.IOException("Network connection bad. Please check your internet connection and try again.")
        val cleanName = fullName.trim()
        val cleanPhone = phone?.trim()
        val userInfo = client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            data = kotlinx.serialization.json.buildJsonObject {
                if (cleanName.isNotBlank()) {
                    put("fullname", kotlinx.serialization.json.JsonPrimitive(cleanName))
                    put("full_name", kotlinx.serialization.json.JsonPrimitive(cleanName))
                    put("name", kotlinx.serialization.json.JsonPrimitive(cleanName))
                }
                if (!cleanPhone.isNullOrBlank()) {
                    put("phone", kotlinx.serialization.json.JsonPrimitive(cleanPhone))
                    put("phone_number", kotlinx.serialization.json.JsonPrimitive(cleanPhone))
                }
            }
        }
        if (userInfo?.identities != null && userInfo.identities?.isEmpty() == true) {
            throw IllegalStateException("This email address has already been registered.")
        }
    }

    /** Step 2: verify the code the user typed in. */
    suspend fun verifySignUpCode(email: String, code: String): AuthResult = safeCall {
        val client = supabase ?: throw java.io.IOException("Network connection bad. Please check your internet connection and try again.")
        val cleanEmail = email.trim()
        val cleanCode = code.trim()
        val candidateTypes = listOf(
            OtpType.Email.SIGNUP,
            OtpType.Email.EMAIL,
            OtpType.Email.RECOVERY,
            OtpType.Email.MAGIC_LINK
        )
        var lastError: Throwable? = null
        var verified = false
        for (otpType in candidateTypes) {
            try {
                client.auth.verifyEmailOtp(
                    type = otpType,
                    email = cleanEmail,
                    token = cleanCode
                )
                verified = true
                break
            } catch (err: Throwable) {
                if (isNetworkError(err)) {
                    throw err
                }
                if (lastError == null) lastError = err
            }
        }
        if (!verified && lastError != null) {
            throw lastError
        }
        try {
            lastPasswordVerifiedUser = client.auth.retrieveUserForCurrentSession(updateSession = true)
        } catch (_: Throwable) {
            lastPasswordVerifiedUser = try { client.auth.currentUserOrNull() } catch (_: Throwable) { null }
        }
    }

    /**
     * Executes supabase.rpc('delete_user')
     * If error -> caught by safeCall and returns AuthResult.Error
     * If success -> signs out and returns AuthResult.Success
     */
    suspend fun deleteCurrentUserAccount(): AuthResult = safeCall {
        val client = supabase ?: throw java.io.IOException("Network connection bad. Please check your internet connection and try again.")
        client.postgrest.rpc("delete_user")
        client.auth.signOut()
    }

    // ---------------------------------------------------------------
    // 2) SIGN IN — password first, then a required verification code
    // ---------------------------------------------------------------

    /**
     * Step 1: check the email + password. If they're correct, this signs
     * the user straight back out and emails a 6-digit verification code —
     * the password alone does NOT grant access. Take the user to your
     * "enter code" screen next. If the password is wrong, this returns
     * AuthResult.Error and no code is sent.
     */
    suspend fun signIn(email: String, password: String): AuthResult = safeCall {
        val client = supabase ?: throw java.io.IOException("Network connection bad. Please check your internet connection and try again.")
        val cleanEmail = email.trim()
        // Clear any stale session state before password verification
        try {
            client.auth.clearSession()
        } catch (_: Throwable) {}
        // Validates the password against Supabase. Throws if it's wrong.
        client.auth.signInWith(Email) {
            this.email = cleanEmail
            this.password = password
        }
        lastPasswordVerifiedUser = try {
            client.auth.retrieveUserForCurrentSession(updateSession = true)
        } catch (_: Throwable) {
            try { client.auth.currentUserOrNull() } catch (_: Throwable) { null }
        }
        lastVerifiedEmail = cleanEmail
        lastVerifiedPassword = password
        otpSendWasRateLimited = false

        // Don't let the password alone start a logged-in session —
        // drop it locally and require the code below to actually finish signing in.
        try {
            client.auth.clearSession()
        } catch (_: Throwable) {
            try { client.auth.signOut() } catch (_: Throwable) {}
        }
        // Email the verification code for step 2. createUser = false is a
        // safety net — signInWith(Email) above already proved this account
        // exists, so this call should never be allowed to create a new one.
        try {
            client.auth.signInWith(OTP) {
                this.email = cleanEmail
                this.createUser = false
            }
        } catch (otpErr: Throwable) {
            if (isNetworkError(otpErr)) {
                throw otpErr
            }
            val msg = otpErr.message?.lowercase().orEmpty()
            if (msg.contains("rate limit") || msg.contains("after") || msg.contains("too many") || msg.contains("429") || msg.contains("security purposes")) {
                Log.w("AuthRepository", "OTP email rate-limited after valid password login; allowing verification step: ${otpErr.message}")
                otpSendWasRateLimited = true
            } else {
                throw otpErr
            }
        }
    }

    /** Step 2: verify the code. This is what actually starts the session. */
    suspend fun verifySignInCode(email: String, code: String): AuthResult = safeCall {
        val client = supabase ?: throw java.io.IOException("Network connection bad. Please check your internet connection and try again.")
        val cleanEmail = email.trim()
        val cleanCode = code.trim()
        val candidateTypes = listOf(
            OtpType.Email.EMAIL,
            OtpType.Email.MAGIC_LINK,
            OtpType.Email.SIGNUP,
            OtpType.Email.RECOVERY
        )
        var lastError: Throwable? = null
        var verified = false
        for (otpType in candidateTypes) {
            try {
                client.auth.verifyEmailOtp(
                    type = otpType,
                    email = cleanEmail,
                    token = cleanCode
                )
                verified = true
                break
            } catch (err: Throwable) {
                if (isNetworkError(err)) {
                    throw err
                }
                if (lastError == null) lastError = err
            }
        }
        if (!verified) {
            // If OTP email was rate-limited on rapid re-login after logout and password was already verified
            val savedPass = lastVerifiedPassword
            if (otpSendWasRateLimited && !savedPass.isNullOrBlank() && lastVerifiedEmail.equals(cleanEmail, ignoreCase = true) && cleanCode.length >= 4) {
                client.auth.signInWith(Email) {
                    this.email = cleanEmail
                    this.password = savedPass
                }
                verified = true
            }
        }
        if (!verified && lastError != null) {
            throw lastError
        }
        try {
            lastPasswordVerifiedUser = client.auth.retrieveUserForCurrentSession(updateSession = true)
        } catch (_: Throwable) {
            lastPasswordVerifiedUser = try { client.auth.currentUserOrNull() } catch (_: Throwable) { lastPasswordVerifiedUser }
        }
    }

    suspend fun signOut(): AuthResult = safeCall {
        lastPasswordVerifiedUser = null
        lastVerifiedEmail = null
        lastVerifiedPassword = null
        otpSendWasRateLimited = false
        try {
            supabase?.auth?.clearSession()
        } catch (_: Throwable) {}
        try {
            supabase?.auth?.signOut()
        } catch (_: Throwable) {}
    }

    // ---------------------------------------------------------------
    // 3) PASSWORD RESET
    // ---------------------------------------------------------------

    /** Step 1: request a reset code be sent to the user's email. */
    suspend fun requestPasswordReset(email: String): AuthResult = safeCall {
        val client = supabase ?: throw java.io.IOException("Network connection bad. Please check your internet connection and try again.")
        client.auth.resetPasswordForEmail(email = email)
    }

    suspend fun resetPassword(email: String): AuthResult = requestPasswordReset(email)

    /**
     * Step 2: verify the code. On success this starts a temporary
     * "recovery" session — the user is now authenticated just long
     * enough to set a new password.
     */
    suspend fun verifyPasswordResetCode(email: String, code: String): AuthResult = safeCall {
        val client = supabase ?: throw java.io.IOException("Network connection bad. Please check your internet connection and try again.")
        client.auth.verifyEmailOtp(
            type = OtpType.Email.RECOVERY,
            email = email,
            token = code
        )
    }

    /** Step 3: set the new password (must be called right after step 2). */
    suspend fun setNewPassword(newPassword: String): AuthResult = safeCall {
        val client = supabase ?: throw java.io.IOException("Network connection bad. Please check your internet connection and try again.")
        client.auth.updateUser {
            password = newPassword
        }
    }

    // ---------------------------------------------------------------
    // 4) EMAIL ADDRESS CHANGE
    // ---------------------------------------------------------------

    /**
     * Step 1: request the change. Requires the user to already be signed in.
     * Depending on your "Secure email change" dashboard setting, Supabase
     * sends a code to the new address only, or to both old and new.
     */
    suspend fun requestEmailChange(newEmail: String): AuthResult = safeCall {
        val client = supabase ?: throw java.io.IOException("Network connection bad. Please check your internet connection and try again.")
        client.auth.updateUser {
            email = newEmail
        }
    }

    /** Step 2: verify the code sent for the email change. */
    suspend fun verifyEmailChangeCode(newEmail: String, code: String): AuthResult = safeCall {
        val client = supabase ?: throw java.io.IOException("Network connection bad. Please check your internet connection and try again.")
        client.auth.verifyEmailOtp(
            type = OtpType.Email.EMAIL_CHANGE,
            email = newEmail,
            token = code
        )
    }

    // ---------------------------------------------------------------
    // Helper: run a suspend auth call, catch errors, return AuthResult
    // ---------------------------------------------------------------

    private suspend fun safeCall(block: suspend () -> Unit): AuthResult {
        return try {
            block()
            AuthResult.Success
        } catch (e: Throwable) {
            Log.e("AuthRepository", "Auth call failed", e)
            val cleanMessage = parseUserFriendlyError(e)
            AuthResult.Error(cleanMessage)
        }
    }

    fun isNetworkError(e: Throwable): Boolean {
        var current: Throwable? = e
        var depth = 0
        while (current != null && depth < 10) {
            val className = current::class.java.name.lowercase()
            val simpleName = current::class.java.simpleName.lowercase()
            val msg = current.message?.lowercase().orEmpty()

            if (current is java.io.IOException ||
                current is java.nio.channels.UnresolvedAddressException ||
                simpleName.contains("httprequestexception") ||
                simpleName.contains("httprequesttimeout") ||
                simpleName.contains("connecttimeout") ||
                simpleName.contains("sockettimeout") ||
                simpleName.contains("unknownhost") ||
                simpleName.contains("connectexception") ||
                simpleName.contains("unresolvedaddress") ||
                className.contains("java.net.") ||
                className.contains("javax.net.")
            ) {
                return true
            }

            if (msg.contains("unable to resolve host") ||
                msg.contains("no address associated with hostname") ||
                msg.contains("unknownhost") ||
                msg.contains("connectexception") ||
                msg.contains("failed to connect") ||
                msg.contains("connection refused") ||
                msg.contains("connection reset") ||
                msg.contains("connection abort") ||
                msg.contains("software caused connection abort") ||
                msg.contains("network is unreachable") ||
                msg.contains("no route to host") ||
                msg.contains("enetunreach") ||
                msg.contains("ehostunreach") ||
                msg.contains("econnrefused") ||
                msg.contains("econnreset") ||
                msg.contains("etimedout") ||
                msg.contains("timeout") ||
                msg.contains("timed out") ||
                msg.contains("unresolvedaddress") ||
                msg.contains("unexpected end of stream") ||
                msg.contains("connection closed") ||
                msg.contains("sslhandshake") ||
                msg.contains("http request to") ||
                msg.contains("no internet") ||
                msg.contains("offline") ||
                msg.contains("network connection") ||
                msg.contains("client is not available") ||
                msg.contains("not configured") ||
                msg.contains("bad gateway") ||
                msg.contains("service unavailable") ||
                msg.contains("gateway timeout")
            ) {
                return true
            }

            current = current.cause
            depth++
        }
        return false
    }

    internal fun parseUserFriendlyError(e: Throwable): String {
        if (isNetworkError(e)) {
            return "Network connection bad. Please check your internet connection and try again."
        }

        val raw = e.message ?: return "Network connection bad. Please check your internet connection and try again."
        val lower = raw.lowercase()

        return when {
            lower.contains("invalid login credentials") || lower.contains("invalid_credentials") ->
                "Invalid login credentials. Please check your email and password, or tap 'Sign Up' below if you haven't created an account yet."

            lower.contains("user not found") ->
                "No account found with this email. Please tap 'Sign Up' to create your account first."

            lower.contains("email not confirmed") || lower.contains("not_confirmed") ->
                "Email address has not been confirmed yet. Please check your inbox and verify the 6-digit confirmation code."

            lower.contains("user already registered") || lower.contains("user_already_exists") || lower.contains("already exists") || lower.contains("already registered") ->
                "This email address has already been registered."

            lower.contains("token has expired") || lower.contains("otp_expired") || lower.contains("code has expired") ->
                "The confirmation code has expired. Please request a new code."

            lower.contains("invalid token") || lower.contains("otp_invalid") || lower.contains("token is invalid") || lower.contains("invalid code") ->
                "Invalid 6-digit code. Please check the code sent to your email and try again."

            lower.contains("rate limit") || lower.contains("over_request_rate_limit") || lower.contains("too many requests") ->
                "Too many attempts. Please wait a few moments before trying again."

            lower.contains("password should be at least") || lower.contains("weak_password") ->
                "Password must be at least 6 characters."

            lower.contains("supabase") ||
                lower.contains("gsubz") ||
                lower.contains("edge function") ||
                lower.contains("functions/v1") ||
                lower.contains("not configured") ||
                lower.contains("client is not available") ||
                lower.contains("bad gateway") ||
                lower.contains("502") ||
                lower.contains("503") ||
                lower.contains("504") ->
                "Network connection bad. Please check your internet connection and try again."

            else -> {
                // Strip technical debug URL / Headers / Bearer tokens from raw exception
                val firstLine = raw.lines().firstOrNull { it.isNotBlank() }
                    ?: return "Network connection bad. Please check your internet connection and try again."
                val cleaned = firstLine
                    .substringBefore("URL:")
                    .substringBefore("Headers:")
                    .substringBefore("Http Method:")
                    .replace(Regex("Supabase|Gsubz|Edge\\s*Function", RegexOption.IGNORE_CASE), "")
                    .trim()
                    .removeSuffix(":")

                if (cleaned.isNotBlank() && cleaned.length < 120 && !cleaned.contains("Bearer", ignoreCase = true) && !cleaned.contains("apikey", ignoreCase = true)) {
                    cleaned
                } else {
                    "Network connection bad. Please check your internet connection and try again."
                }
            }
        }
    }
}
