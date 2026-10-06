package com.example.util

import android.util.Base64
import android.util.Log
import org.json.JSONObject

object JwtUtils {

    /**
     * Checks if a JWT token is expired or will expire within [bufferSeconds].
     * Returns true if token is blank or expired.
     */
    fun isExpired(jwt: String?, bufferSeconds: Long = 60): Boolean {
        if (jwt.isNullOrBlank()) return true
        val parts = jwt.split(".")
        if (parts.size < 2) return false

        return try {
            val payloadBytes = Base64.decode(
                parts[1],
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            val json = JSONObject(String(payloadBytes, Charsets.UTF_8))
            val exp = json.optLong("exp", 0L)
            if (exp <= 0L) return false

            val nowSeconds = System.currentTimeMillis() / 1000
            val isExpired = nowSeconds >= (exp - bufferSeconds)
            if (isExpired) {
                Log.d("JwtUtils", "Token expired: exp=$exp, now=$nowSeconds")
            }
            isExpired
        } catch (e: Exception) {
            Log.w("JwtUtils", "Failed to parse JWT payload: ${e.message}")
            false
        }
    }

    /**
     * Returns expiration epoch seconds from JWT if present.
     */
    fun getExpiration(jwt: String?): Long? {
        if (jwt.isNullOrBlank()) return null
        val parts = jwt.split(".")
        if (parts.size < 2) return null
        return try {
            val payloadBytes = Base64.decode(
                parts[1],
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            val json = JSONObject(String(payloadBytes, Charsets.UTF_8))
            val exp = json.optLong("exp", 0L)
            if (exp > 0L) exp else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts the authenticated user's UUID ('sub' claim) from a Supabase JWT.
     */
    fun getUserIdFromJwt(jwt: String?): String? {
        if (jwt.isNullOrBlank()) return null
        val cleanJwt = jwt.removePrefix("Bearer ").removePrefix("bearer ").trim()
        val parts = cleanJwt.split(".")
        if (parts.size < 2) return null
        return try {
            val payloadBytes = Base64.decode(
                parts[1],
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            val json = JSONObject(String(payloadBytes, Charsets.UTF_8))
            val role = json.optString("role", "")
            if (role.equals("anon", ignoreCase = true)) return null
            val sub = json.optString("sub", "").trim()
            sub.takeIf { it.length == 36 && it.count { c -> c == '-' } == 4 }
        } catch (_: Exception) {
            null
        }
    }
}
