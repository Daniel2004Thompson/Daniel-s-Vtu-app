package com.example.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.Arrays

/**
 * Client configuration and anti-tamper / anti-Frida protection layer.
 *
 * All Supabase Auth, PostgREST, RPC, and Edge Function requests are routed through the
 * `swift-processor` Edge Function gateway so no Supabase API keys exist in the APK.
 */
object SecurityVault {

    const val GATEWAY_FUNCTION_NAME = "swift-processor"
    const val GATEWAY_MANAGED_KEY_PLACEHOLDER = "gateway-managed-key"

    private val MASK_KEY = intArrayOf(0x5A, 0x3C, 0x7F, 0x91, 0x2E, 0x6B, 0x84, 0x19)

    private val ENCODED_PROJECT_URL = intArrayOf(
        50, 72, 11, 225, 93, 81, 171, 54, 35, 86, 6, 252, 86, 15, 254, 125,
        50, 74, 29, 245, 68, 25, 229, 116, 54, 85, 15, 246, 0, 24, 241, 105,
        59, 94, 30, 226, 75, 69, 231, 118
    )

    private val SUSPICIOUS_HOOK_INDICATORS = arrayOf(
        "frida-agent",
        "frida-gadget",
        "frida-server",
        "gum-js-loop",
        "linjector",
        "xposedbridge",
        "libsubstrate",
        "edxposed",
        "lsposed"
    )

    private fun decodeBytes(encoded: IntArray): String {
        val buffer = ByteArray(encoded.size)
        return try {
            for (i in encoded.indices) {
                buffer[i] = (encoded[i] xor MASK_KEY[i % MASK_KEY.size]).toByte()
            }
            String(buffer, Charsets.UTF_8)
        } finally {
            Arrays.fill(buffer, 0.toByte())
        }
    }

    /**
     * Resolves the Supabase project URL.
     */
    fun supabaseUrl(): String {
        val raw = decodeBytes(ENCODED_PROJECT_URL)
        return if (raw.startsWith("http://") || raw.startsWith("https://")) {
            raw.trimEnd('/')
        } else {
            "https://${raw.trimEnd('/')}"
        }
    }

    /**
     * Resolves the `swift-processor` gateway Edge Function URL.
     */
    fun supabaseGatewayUrl(): String {
        return "${supabaseUrl()}/functions/v1/$GATEWAY_FUNCTION_NAME"
    }

    /**
     * Returns a non-secret client placeholder token; the real Supabase key is injected
     * server-side by the `swift-processor` Edge Function gateway.
     */
    fun supabaseAnonKey(): String {
        return GATEWAY_MANAGED_KEY_PLACEHOLDER
    }

    /**
     * OkHttp interceptor that transparently routes all Supabase Auth, PostgREST, RPC, and
     * Edge Function requests through `/functions/v1/swift-processor` via `x-target-path`,
     * stripping client-side placeholder keys so the gateway injects server-side credentials.
     */
    val gatewayInterceptor: okhttp3.Interceptor = okhttp3.Interceptor { chain ->
        val original = chain.request()
        val url = original.url
        val projectHost = supabaseUrl()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .trim()

        if (!url.host.equals(projectHost, ignoreCase = true)) {
            return@Interceptor chain.proceed(original)
        }

        val encodedPath = url.encodedPath
        val isWebSocket = original.header("Upgrade").equals("websocket", ignoreCase = true) ||
            encodedPath.startsWith("/realtime/")
        if (isWebSocket) {
            return@Interceptor chain.proceed(original)
        }

        val gatewayPath = "/functions/v1/$GATEWAY_FUNCTION_NAME"
        val builder = original.newBuilder()

        val effectiveTargetPath: String = if (!encodedPath.startsWith(gatewayPath)) {
            val encodedQuery = url.encodedQuery
            val targetPath = if (!encodedQuery.isNullOrBlank()) {
                "$encodedPath?$encodedQuery"
            } else {
                encodedPath
            }
            val gatewayHttpUrl = url.newBuilder()
                .encodedPath(gatewayPath)
                .encodedQuery(null)
                .build()
            builder.url(gatewayHttpUrl)
            builder.header("x-target-path", targetPath)
            targetPath
        } else {
            original.header("x-target-path")?.trim().orEmpty().ifBlank { encodedPath }
        }

        val existingApiKey = original.header("apikey")?.trim().orEmpty()
        if (existingApiKey.isEmpty() || existingApiKey.equals(GATEWAY_MANAGED_KEY_PLACEHOLDER, ignoreCase = true)) {
            builder.removeHeader("apikey")
        }

        val targetLower = effectiveTargetPath.lowercase()
        // Edge Functions that use server-side gateway anon key & body parameters (userId/email)
        // and enforce legacy JWT secret at the Edge Function gateway:
        val useGatewayManagedAuthOnly =
            targetLower.contains("/functions/v1/create-dynamic-account") ||
            targetLower.contains("/functions/v1/create-permanent-account") ||
            targetLower.contains("/functions/v1/create-virtual-account")

        val isPublicAuthEndpoint =
            targetLower.startsWith("/auth/v1/token") ||
            targetLower.startsWith("/auth/v1/otp") ||
            targetLower.startsWith("/auth/v1/verify") ||
            targetLower.startsWith("/auth/v1/signup") ||
            targetLower.startsWith("/auth/v1/recover")

        if (useGatewayManagedAuthOnly || isPublicAuthEndpoint) {
            builder.removeHeader("Authorization")
        } else {
            val existingAuth = original.header("Authorization")?.trim().orEmpty()
            val rawToken = existingAuth.removePrefix("Bearer ").removePrefix("bearer ").trim()
            val isRawValidJwt = rawToken.isNotBlank() &&
                !rawToken.equals(GATEWAY_MANAGED_KEY_PLACEHOLDER, ignoreCase = true) &&
                rawToken.startsWith("ey") &&
                !JwtUtils.isExpired(rawToken)

            if (isRawValidJwt) {
                com.example.auth.SupabaseProvider.activeUserAccessToken = rawToken
                builder.header("Authorization", "Bearer $rawToken")
            } else {
                val activeCandidate = com.example.auth.SupabaseProvider.activeUserAccessToken
                    ?.trim()
                    ?.removePrefix("Bearer ")
                    ?.removePrefix("bearer ")
                    ?.trim()
                    .orEmpty()
                val validActive = if (activeCandidate.startsWith("ey") && !JwtUtils.isExpired(activeCandidate)) {
                    activeCandidate
                } else {
                    com.example.auth.SupabaseProvider.blockingRefreshAccessTokenIfNeeded()
                }
                if (!validActive.isNullOrBlank() && validActive.startsWith("ey") && !JwtUtils.isExpired(validActive)) {
                    builder.header("Authorization", "Bearer $validActive")
                } else {
                    builder.removeHeader("Authorization")
                }
            }
        }

        chain.proceed(builder.build())
    }

    fun applyGatewayInterceptor(builder: okhttp3.OkHttpClient.Builder): okhttp3.OkHttpClient.Builder {
        if (!builder.interceptors().contains(gatewayInterceptor)) {
            builder.addInterceptor(gatewayInterceptor)
        }
        return builder
    }

    fun createGatewayOkHttpClient(
        connectTimeoutSec: Long = 15,
        readTimeoutSec: Long = 15,
        writeTimeoutSec: Long = 15
    ): okhttp3.OkHttpClient {
        return applyGatewayInterceptor(
            okhttp3.OkHttpClient.Builder()
                .connectTimeout(connectTimeoutSec, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(readTimeoutSec, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(writeTimeoutSec, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
        ).build()
    }

    /**
     * Detects Frida, Xposed/LSPosed, and Substrate dynamic instrumentation in memory maps and call stacks.
     */
    fun isDynamicInstrumentationDetected(): Boolean {
        try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists() && mapsFile.canRead()) {
                BufferedReader(FileReader(mapsFile)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        val lower = line.lowercase()
                        for (indicator in SUSPICIOUS_HOOK_INDICATORS) {
                            if (lower.contains(indicator)) {
                                return true
                            }
                        }
                        line = reader.readLine()
                    }
                }
            }
        } catch (_: Throwable) {}

        try {
            val stack = Throwable().stackTrace
            for (element in stack) {
                val cls = element.className.lowercase()
                if (cls.contains("xposed") || cls.contains("substrate") || cls.contains("frida")) {
                    return true
                }
            }
        } catch (_: Throwable) {}

        return false
    }

    /**
     * Returns an Android Keystore AES-256-GCM EncryptedSharedPreferences instance and automatically
     * migrates and wipes any sensitive session tokens from legacy unencrypted SharedPreferences.
     */
    fun getEncryptedPreferences(context: Context, baseName: String): SharedPreferences {
        val encryptedFileName = "${baseName}_encrypted_v1"
        val legacyPrefs = context.getSharedPreferences(baseName, Context.MODE_PRIVATE)
        val encryptedPrefs = try {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context.applicationContext,
                encryptedFileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Throwable) {
            return legacyPrefs
        }

        try {
            val legacyAll = legacyPrefs.all
            if (legacyAll.isNotEmpty()) {
                val encEditor = encryptedPrefs.edit()
                for ((key, value) in legacyAll) {
                    // Never migrate any locally stored PIN keys
                    if (key.contains("pin", ignoreCase = true)) {
                        continue
                    }
                    if (!encryptedPrefs.contains(key)) {
                        when (value) {
                            is String -> encEditor.putString(key, value)
                            is Boolean -> encEditor.putBoolean(key, value)
                            is Int -> encEditor.putInt(key, value)
                            is Long -> encEditor.putLong(key, value)
                            is Float -> encEditor.putFloat(key, value)
                            is Set<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                (value as? Set<String>)?.let { encEditor.putStringSet(key, it) }
                            }
                        }
                    }
                }
                encEditor.apply()
                legacyPrefs.edit().clear().apply()
            }
        } catch (_: Throwable) {}

        return encryptedPrefs
    }

    /**
     * One-time cleanup that deletes any old locally stored PIN data left over on existing installs
     * across both EncryptedSharedPreferences and unencrypted SharedPreferences.
     */
    fun purgeLegacyLocalPinData(context: Context, activePrefs: SharedPreferences) {
        try {
            val legacyUnencrypted = context.getSharedPreferences("daniel_vtu_prefs", Context.MODE_PRIVATE)
            val legacyKeys = legacyUnencrypted.all.keys.filter { it.contains("pin", ignoreCase = true) }
            if (legacyKeys.isNotEmpty()) {
                val ed = legacyUnencrypted.edit()
                legacyKeys.forEach { ed.remove(it) }
                ed.apply()
            }
        } catch (_: Throwable) {}

        try {
            val encKeys = activePrefs.all.keys.filter {
                it.contains("security_pin", ignoreCase = true) ||
                    it.equals("pin", ignoreCase = true) ||
                    it.contains("transaction_pin", ignoreCase = true)
            }
            if (encKeys.isNotEmpty() || !activePrefs.getBoolean("key_legacy_pin_cleanup_done_v1", false)) {
                val ed = activePrefs.edit()
                encKeys.forEach { ed.remove(it) }
                ed.putBoolean("key_legacy_pin_cleanup_done_v1", true)
                ed.apply()
            }
        } catch (_: Throwable) {}
    }
}
