package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages encrypted storage for real developer API keys using EncryptedSharedPreferences.
 * Stores each user's key strictly under the per-user key name "api_key_<userId>",
 * never under a shared name, and removes any legacy shared entries.
 */
class SecureApiKeyStorage(private val context: Context) {

    companion object {
        private const val PREFS_FILE = "secure_vtu_developer_api_keys"
        private const val KEY_PREFIX = "api_key_"
        private const val PUB_KEY_PREFIX = "pub_key_"
        private const val CREATED_AT_PREFIX = "created_at_"

        private val INVALID_SHARED_USER_IDS = setOf(
            "",
            "usr_guest",
            "usr_default",
            "usr_developer",
            "usr_demo",
            "usr_test",
            "default",
            "shared",
            "guest",
            "null"
        )

        private val LEGACY_SHARED_KEYS = listOf(
            "api_key",
            "vtu_api_key",
            "developer_api_key",
            "secure_api_key",
            "secure_api_key_",
            "secure_api_key_usr_guest",
            "secure_api_key_usr_default",
            "secure_api_key_usr_developer",
            "api_key_usr_guest",
            "api_key_usr_default",
            "api_key_usr_developer",
            "api_key_default",
            "api_key_shared"
        )
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            Log.e("SecureApiKeyStorage", "EncryptedSharedPreferences init failed, falling back to private SharedPreferences", e)
            context.getSharedPreferences(PREFS_FILE + "_fallback", Context.MODE_PRIVATE)
        }
    }

    init {
        removeLegacySharedEntries()
    }

    fun removeLegacySharedEntries() {
        try {
            val editor = encryptedPrefs.edit()
            var changed = false
            for (legacyKey in LEGACY_SHARED_KEYS) {
                if (encryptedPrefs.contains(legacyKey)) {
                    editor.remove(legacyKey)
                    changed = true
                }
            }
            // Migrate any old per-user "secure_api_key_<validUserId>" to "api_key_<validUserId>" and remove old key
            val allKeys = encryptedPrefs.all.keys.toList()
            for (k in allKeys) {
                if (k.startsWith("secure_api_key_")) {
                    val suffix = k.removePrefix("secure_api_key_").trim()
                    val oldVal = encryptedPrefs.getString(k, null)
                    if (isValidUserId(suffix) && !oldVal.isNullOrBlank() && !isPlaceholderKey(oldVal)) {
                        if (!encryptedPrefs.contains(KEY_PREFIX + suffix)) {
                            editor.putString(KEY_PREFIX + suffix, oldVal)
                        }
                    }
                    editor.remove(k)
                    changed = true
                } else if (k.startsWith(KEY_PREFIX)) {
                    val suffix = k.removePrefix(KEY_PREFIX).trim()
                    if (!isValidUserId(suffix)) {
                        editor.remove(k)
                        changed = true
                    }
                }
            }
            if (changed) {
                editor.apply()
            }

            // Also clean any old shared key entries from standard app SharedPreferences
            val appPrefs = context.getSharedPreferences("vtu_app_prefs", Context.MODE_PRIVATE)
            val appEditor = appPrefs.edit()
            var appChanged = false
            for (legacyKey in LEGACY_SHARED_KEYS + listOf("key_api_key", "key_api_key_usr_guest", "key_api_key_usr_default", "key_api_key_usr_developer")) {
                if (appPrefs.contains(legacyKey)) {
                    appEditor.remove(legacyKey)
                    appChanged = true
                }
            }
            if (appChanged) {
                appEditor.apply()
            }
        } catch (_: Throwable) {}
    }

    private fun isValidUserId(userId: String?): Boolean {
        val clean = userId?.trim().orEmpty()
        return clean.isNotBlank() && clean.lowercase() !in INVALID_SHARED_USER_IDS
    }

    fun saveRealApiKey(userId: String, apiKey: String, publicKey: String? = null, createdAt: String? = null) {
        val cleanUserId = userId.trim()
        if (!isValidUserId(cleanUserId)) {
            Log.w("SecureApiKeyStorage", "Refusing to store API key for invalid/shared userId: $cleanUserId")
            return
        }
        if (apiKey.isBlank() || isPlaceholderKey(apiKey)) {
            Log.w("SecureApiKeyStorage", "Refusing to store blank or placeholder key in EncryptedSharedPreferences")
            return
        }
        removeLegacySharedEntries()
        val editor = encryptedPrefs.edit()
            .putString(KEY_PREFIX + cleanUserId, apiKey.trim())
        if (!publicKey.isNullOrBlank()) {
            editor.putString(PUB_KEY_PREFIX + cleanUserId, publicKey.trim())
        }
        if (!createdAt.isNullOrBlank()) {
            editor.putString(CREATED_AT_PREFIX + cleanUserId, createdAt.trim())
        }
        editor.apply()
    }

    fun getRealApiKey(userId: String): String? {
        val cleanUserId = userId.trim()
        if (!isValidUserId(cleanUserId)) return null
        removeLegacySharedEntries()
        val key = encryptedPrefs.getString(KEY_PREFIX + cleanUserId, null)
        return if (!key.isNullOrBlank() && !isPlaceholderKey(key)) key.trim() else null
    }

    fun getPublicKey(userId: String): String? {
        val cleanUserId = userId.trim()
        if (!isValidUserId(cleanUserId)) return null
        return encryptedPrefs.getString(PUB_KEY_PREFIX + cleanUserId, null)
    }

    fun getCreatedAt(userId: String): String? {
        val cleanUserId = userId.trim()
        if (!isValidUserId(cleanUserId)) return null
        return encryptedPrefs.getString(CREATED_AT_PREFIX + cleanUserId, null)
    }

    fun clearKey(userId: String) {
        val cleanUserId = userId.trim()
        if (cleanUserId.isBlank()) return
        encryptedPrefs.edit()
            .remove(KEY_PREFIX + cleanUserId)
            .remove(PUB_KEY_PREFIX + cleanUserId)
            .remove(CREATED_AT_PREFIX + cleanUserId)
            .remove("secure_api_key_$cleanUserId")
            .apply()
    }

    private fun isPlaceholderKey(key: String): Boolean {
        return key.startsWith("vtu_live_YOUR_") ||
               key.startsWith("vtu_live_secure_test_") ||
               key.equals("No API Key Generated Yet", ignoreCase = true) ||
               key.isBlank()
    }
}
