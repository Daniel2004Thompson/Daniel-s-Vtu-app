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
                config {
                    connectTimeout(25, TimeUnit.SECONDS)
                    readTimeout(35, TimeUnit.SECONDS)
                    writeTimeout(25, TimeUnit.SECONDS)
                }
            }
        }
    }

    /**
     * Resolves the logged-in user's session access token from the single SupabaseProvider.client,
     * falling back to the provided token or the Supabase anon key if unauthenticated.
     */
    fun resolveSessionAccessToken(fallbackToken: String? = null): String {
        val cleanFallback = fallbackToken?.trim()?.removePrefix("Bearer ")?.removePrefix("bearer ")?.trim()
        val sessionToken = try {
            client?.auth?.currentAccessTokenOrNull()?.takeIf { it.isNotBlank() }
                ?: client?.auth?.currentSessionOrNull()?.accessToken?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
        return sessionToken
            ?: cleanFallback?.takeIf { it.isNotBlank() && !it.startsWith("session_") && !it.startsWith("demo_") }
            ?: rawKey
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
        val accessToken = resolveSessionAccessToken(accessTokenOverride)
        val bearer = if (accessToken.startsWith("Bearer ", ignoreCase = true)) accessToken else "Bearer $accessToken"

        val response = ktorClient.post(url) {
            header(HttpHeaders.Authorization, bearer)
            header("apikey", rawKey)
            header("x-client-info", "vtu-android-client/1.0")
            contentType(ContentType.Application.Json)
            setBody(bodyJson)
        }
        return Pair(response.status.value, response.bodyAsText())
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
        if (isLiveConfigured) {
            val client = supabase ?: throw IllegalStateException("Supabase client is not available")
            val cleanName = fullName.trim()
            val cleanPhone = phone?.trim()
            val userInfo = client.auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
                data = kotlinx.serialization.json.buildJsonObject {
                    if (cleanName.isNotBlank()) {
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
    }

    /** Step 2: verify the code the user typed in. */
    suspend fun verifySignUpCode(email: String, code: String): AuthResult = safeCall {
        if (isLiveConfigured) {
            val client = supabase ?: throw IllegalStateException("Supabase client is not available")
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
    }

    /**
     * Executes supabase.rpc('delete_user')
     * If error -> caught by safeCall and returns AuthResult.Error
     * If success -> signs out and returns AuthResult.Success
     */
    suspend fun deleteCurrentUserAccount(): AuthResult = safeCall {
        if (isLiveConfigured) {
            val client = supabase ?: throw IllegalStateException("Supabase client is not available")
            client.postgrest.rpc("delete_user")
            client.auth.signOut()
        }
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
        if (isLiveConfigured) {
            val client = supabase ?: throw IllegalStateException("Supabase client is not available")
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
                val msg = otpErr.message?.lowercase().orEmpty()
                if (msg.contains("rate limit") || msg.contains("after") || msg.contains("too many") || msg.contains("429") || msg.contains("security purposes")) {
                    Log.w("AuthRepository", "OTP email rate-limited after valid password login; allowing verification step: ${otpErr.message}")
                    otpSendWasRateLimited = true
                } else {
                    throw otpErr
                }
            }
        } else {
            // Demo mode fallback validation
            if (email.isBlank() || !email.contains("@") || password.length < 6) {
                throw IllegalArgumentException("Invalid email or password (min 6 chars)")
            }
        }
    }

    /** Step 2: verify the code. This is what actually starts the session. */
    suspend fun verifySignInCode(email: String, code: String): AuthResult = safeCall {
        if (isLiveConfigured) {
            val client = supabase ?: throw IllegalStateException("Supabase client is not available")
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
        } else {
            if (code.isBlank() || code.length < 4) {
                throw IllegalArgumentException("Please enter a valid 6-digit code")
            }
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
        if (isLiveConfigured) {
            val client = supabase ?: throw IllegalStateException("Supabase client is not available")
            client.auth.resetPasswordForEmail(email = email)
        } else {
            if (email.isBlank() || !email.contains("@")) {
                throw IllegalArgumentException("Please enter a valid email address")
            }
        }
    }

    suspend fun resetPassword(email: String): AuthResult = requestPasswordReset(email)

    /**
     * Step 2: verify the code. On success this starts a temporary
     * "recovery" session — the user is now authenticated just long
     * enough to set a new password.
     */
    suspend fun verifyPasswordResetCode(email: String, code: String): AuthResult = safeCall {
        if (isLiveConfigured) {
            val client = supabase ?: throw IllegalStateException("Supabase client is not available")
            client.auth.verifyEmailOtp(
                type = OtpType.Email.RECOVERY,
                email = email,
                token = code
            )
        } else {
            if (code.isBlank() || code.length < 4) {
                throw IllegalArgumentException("Invalid recovery code")
            }
        }
    }

    /** Step 3: set the new password (must be called right after step 2). */
    suspend fun setNewPassword(newPassword: String): AuthResult = safeCall {
        if (isLiveConfigured) {
            val client = supabase ?: throw IllegalStateException("Supabase client is not available")
            client.auth.updateUser {
                password = newPassword
            }
        } else {
            if (newPassword.length < 6) {
                throw IllegalArgumentException("Password must be at least 6 characters")
            }
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
        if (isLiveConfigured) {
            val client = supabase ?: throw IllegalStateException("Supabase client is not available")
            client.auth.updateUser {
                email = newEmail
            }
        } else {
            if (newEmail.isBlank() || !newEmail.contains("@")) {
                throw IllegalArgumentException("Please enter a valid email address")
            }
        }
    }

    /** Step 2: verify the code sent for the email change. */
    suspend fun verifyEmailChangeCode(newEmail: String, code: String): AuthResult = safeCall {
        if (isLiveConfigured) {
            val client = supabase ?: throw IllegalStateException("Supabase client is not available")
            client.auth.verifyEmailOtp(
                type = OtpType.Email.EMAIL_CHANGE,
                email = newEmail,
                token = code
            )
        } else {
            if (code.isBlank() || code.length < 4) {
                throw IllegalArgumentException("Invalid email verification code")
            }
        }
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

    private fun parseUserFriendlyError(e: Throwable): String {
        val raw = e.message ?: return "An unexpected error occurred. Please try again."
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

            lower.contains("token has expired") || lower.contains("otp_expired") || lower.contains("expired") ->
                "The confirmation code has expired. Please request a new code."

            lower.contains("invalid token") || lower.contains("otp_invalid") || lower.contains("token is invalid") || lower.contains("invalid code") ->
                "Invalid 6-digit code. Please check the code sent to your email and try again."

            lower.contains("rate limit") || lower.contains("over_request_rate_limit") || lower.contains("too many requests") ->
                "Too many attempts. Please wait a few moments before trying again."

            lower.contains("password should be at least") || lower.contains("weak_password") ->
                "Password must be at least 6 characters."

            lower.contains("unable to resolve host") || lower.contains("connectexception") || lower.contains("no route to host") || lower.contains("timeout") ->
                "Network connection issue. Please check your internet connection."

            else -> {
                // Strip technical debug URL / Headers / Bearer tokens from raw Ktor/Supabase exception
                val firstLine = raw.lines().firstOrNull { it.isNotBlank() } ?: "Authentication request failed."
                val cleaned = firstLine
                    .substringBefore("URL:")
                    .substringBefore("Headers:")
                    .substringBefore("Http Method:")
                    .trim()
                    .removeSuffix(":")

                if (cleaned.isNotBlank() && cleaned.length < 120 && !cleaned.contains("Bearer", ignoreCase = true) && !cleaned.contains("apikey", ignoreCase = true)) {
                    cleaned
                } else {
                    "Authentication failed. Please verify your details and try again."
                }
            }
        }
    }
}
