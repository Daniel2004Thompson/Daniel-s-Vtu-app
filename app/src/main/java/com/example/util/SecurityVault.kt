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
 * The only values allowed in the APK are the Supabase URL and the Supabase anon key.
 * No third-party or server-side secrets exist in BuildConfig, gradle, or the APK.
 */
object SecurityVault {

    private val MASK_KEY = intArrayOf(0x5A, 0x3C, 0x7F, 0x91, 0x2E, 0x6B, 0x84, 0x19)

    private val ENCODED_PROJECT_URL = intArrayOf(
        50, 72, 11, 225, 93, 81, 171, 54, 35, 86, 6, 252, 86, 15, 254, 125,
        50, 74, 29, 245, 68, 25, 229, 116, 54, 85, 15, 246, 0, 24, 241, 105,
        59, 94, 30, 226, 75, 69, 231, 118
    )

    private val ENCODED_ANON_KEY = intArrayOf(
        63, 69, 53, 249, 76, 44, 231, 112, 21, 85, 53, 216, 123, 17, 205, 40,
        20, 85, 54, 226, 103, 5, 214, 44, 57, 127, 54, 167, 103, 0, 244, 65,
        12, 127, 53, 168, 0, 14, 253, 83, 42, 95, 76, 220, 71, 36, 237, 83,
        32, 88, 39, 211, 70, 50, 233, 95, 32, 102, 44, 216, 93, 34, 234, 83,
        54, 102, 22, 216, 24, 34, 234, 117, 43, 89, 40, 160, 26, 49, 204, 105,
        49, 93, 55, 203, 71, 49, 195, 105, 35, 101, 40, 160, 93, 10, 220, 91,
        52, 117, 22, 230, 71, 8, 233, 32, 41, 102, 44, 216, 24, 34, 233, 95,
        47, 94, 77, 165, 71, 39, 199, 83, 42, 101, 39, 192, 71, 36, 238, 92,
        105, 115, 59, 246, 28, 38, 208, 114, 106, 113, 21, 200, 93, 34, 233, 79,
        110, 95, 60, 216, 24, 38, 238, 92, 45, 114, 59, 212, 27, 37, 208, 72,
        35, 114, 17, 161, 0, 5, 213, 72, 57, 79, 55, 197, 125, 8, 234, 65,
        14, 14, 39, 192, 86, 56, 211, 44, 107, 123, 19, 213, 122, 46, 233, 90,
        49, 74, 45, 169, 104, 38, 207, 94, 60, 123, 78, 246, 30, 61, 212, 76
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
     * Resolves the Supabase anon key.
     */
    fun supabaseAnonKey(): String {
        return decodeBytes(ENCODED_ANON_KEY)
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
     * migrates and wipes any sensitive tokens/PINs from legacy unencrypted SharedPreferences.
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
}
