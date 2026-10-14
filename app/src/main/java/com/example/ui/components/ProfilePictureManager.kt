package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.auth.SupabaseInstance
import com.example.ui.theme.VtuCyan
import com.example.ui.theme.VtuGreenPrimary
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

suspend fun uploadAvatar(context: Context, uri: Uri) {
    ProfilePictureStore.ensureSupabaseAuthHydrated(context)
    val supabase = SupabaseInstance.client
    val userId = try { supabase?.auth?.currentUserOrNull()?.id } catch (_: Throwable) { null }
        ?: ProfilePictureStore.resolveActiveUserId(context)
    val userEmail = ProfilePictureStore.resolveActiveUserEmail(context, null)

    // Shrink to max 512px and compress to JPEG (keeps it well under 2 MB)
    val bytes = withContext(Dispatchers.IO) {
        val rawBmp = try {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (_: Throwable) {
            null
        } ?: ProfilePictureStore.currentAvatar.value ?: return@withContext null
        val scale = 512f / maxOf(rawBmp.width.coerceAtLeast(1), rawBmp.height.coerceAtLeast(1))
        val small = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                rawBmp,
                (rawBmp.width * scale).toInt().coerceAtLeast(1),
                (rawBmp.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            rawBmp
        }
        val jpegBytes = ByteArrayOutputStream().also {
            small.compress(Bitmap.CompressFormat.JPEG, 85, it)
        }.toByteArray()
        ProfilePictureStore.persistLocalAvatarImmediately(context, small, jpegBytes, userEmail)
        jpegBytes
    } ?: return

    if (userId.isNullOrBlank()) return

    withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
        ProfilePictureStore.beginRemoteUpload()
        try {
            var sdkStorageUploaded = false
            if (supabase != null) {
                try {
                    supabase.storage.from("avatars").upload("$userId/avatar.jpg", bytes) { upsert = true }
                    sdkStorageUploaded = true
                    supabase.postgrest.rpc("set_my_avatar")      // saves the link in users.avatar_url
                } catch (_: Throwable) {}
            }
            // Always ensure Storage + RPC + public.users.avatar_url are synced with the valid user JWT
            ProfilePictureStore.uploadAvatarRestFallback(
                context = context,
                userId = userId,
                bytes = bytes,
                email = userEmail,
                skipStorageUpload = sdkStorageUploaded
            )
        } finally {
            ProfilePictureStore.endRemoteUpload()
        }
    }
}

suspend fun removeAvatar() {
    val supabase = SupabaseInstance.client
    val userId = try { supabase?.auth?.currentUserOrNull()?.id } catch (_: Throwable) { null }
        ?: ProfilePictureStore.resolveActiveUserId(null)
    ProfilePictureStore.markAvatarRemoved()
    if (userId.isNullOrBlank()) return
    withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
        try {
            if (supabase != null) {
                supabase.storage.from("avatars").delete("$userId/avatar.jpg")
                supabase.postgrest.rpc("remove_my_avatar")   // clears the link, app shows "DT"
            }
        } catch (_: Throwable) {}
        ProfilePictureStore.removeAvatarRestFallback(userId)
    }
}

object ProfilePictureStore {
    private const val PREFS_NAME = "daniel_vtu_prefs"
    private const val KEY_AVATAR_B64 = "key_profile_avatar_b64"
    private const val KEY_AVATAR_REMOVED = "key_profile_avatar_explicitly_removed"
    private const val KEY_PENDING_CAMERA_PATH = "key_pending_camera_file_path"

    private val backgroundScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
    )

    private val _currentAvatar = MutableStateFlow<Bitmap?>(null)
    val currentAvatar: StateFlow<Bitmap?> = _currentAvatar.asStateFlow()

    private val avatarVersion = java.util.concurrent.atomic.AtomicLong(0L)
    private val isUploadingAvatar = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    private var loadedEmailKey: String? = null

    private fun getSecurePrefs(context: Context): android.content.SharedPreferences {
        return try {
            com.example.util.SecurityVault.getEncryptedPreferences(context, PREFS_NAME)
        } catch (_: Throwable) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    internal fun beginRemoteUpload() {
        isUploadingAvatar.set(true)
        avatarVersion.incrementAndGet()
    }

    internal fun endRemoteUpload() {
        isUploadingAvatar.set(false)
    }

    internal fun markAvatarRemoved() {
        avatarVersion.incrementAndGet()
        isUploadingAvatar.set(false)
        _currentAvatar.value = null
    }

    internal fun updateCurrentAvatarBitmap(bitmap: Bitmap?) {
        avatarVersion.incrementAndGet()
        _currentAvatar.value = bitmap
    }

    internal fun persistLocalAvatarImmediately(
        context: Context,
        bitmap: Bitmap,
        jpegBytes: ByteArray? = null,
        email: String? = null
    ) {
        avatarVersion.incrementAndGet()
        _currentAvatar.value = bitmap
        val resolvedEmail = resolveActiveUserEmail(context, email)
        val key = sanitizeEmailKey(resolvedEmail)
        loadedEmailKey = key
        try {
            val file = getAvatarFile(context, resolvedEmail)
            if (jpegBytes != null && jpegBytes.isNotEmpty()) {
                file.writeBytes(jpegBytes)
            } else {
                saveBitmapToFile(bitmap, file)
            }
        } catch (_: Throwable) {}

        try {
            val b64 = if (jpegBytes != null && jpegBytes.isNotEmpty()) {
                Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
            } else {
                encodeBitmapBase64(bitmap, quality = 85)
            }
            val prefs = getSecurePrefs(context)
            prefs.edit()
                .putString("${KEY_AVATAR_B64}_$key", b64)
                .putString(KEY_AVATAR_B64, b64)
                .putBoolean("${KEY_AVATAR_REMOVED}_$key", false)
                .apply()
        } catch (_: Throwable) {}
    }

    internal fun resolveActiveUserEmail(context: Context?, fallbackEmail: String? = null): String? {
        val cleanFallback = fallbackEmail?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        if (cleanFallback != null) return cleanFallback

        val fromAuth = try {
            SupabaseInstance.client?.auth?.currentUserOrNull()?.email?.trim()?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
        if (fromAuth != null) return fromAuth

        val fromJwt = com.example.util.JwtUtils.getEmailFromJwt(SupabaseInstance.activeUserAccessToken)?.trim()
        if (!fromJwt.isNullOrBlank()) return fromJwt

        if (context != null) {
            try {
                val prefs = getSecurePrefs(context)
                val prefEmail = (prefs.getString("key_auth_user_email", null) ?: prefs.getString("key_user_email", null))?.trim()
                if (!prefEmail.isNullOrBlank() && !prefEmail.equals("null", ignoreCase = true)) {
                    return prefEmail
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    internal fun resolveActiveUserId(context: Context?): String? {
        val fromAuth = try {
            SupabaseInstance.client?.auth?.currentUserOrNull()?.id?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
        if (!fromAuth.isNullOrBlank()) return fromAuth

        val fromJwt = com.example.util.JwtUtils.getUserIdFromJwt(SupabaseInstance.activeUserAccessToken)
        if (!fromJwt.isNullOrBlank()) return fromJwt

        if (context != null) {
            try {
                val prefs = getSecurePrefs(context)
                val prefUid = (prefs.getString("key_auth_user_id", null) ?: prefs.getString("key_user_id", null))?.trim()
                if (!prefUid.isNullOrBlank() && prefUid != "usr_guest" && prefUid != "usr_default") {
                    return prefUid
                }
                val prefToken = prefs.getString("key_auth_access_token", null) ?: prefs.getString("key_access_token", null)
                val jwtUid = com.example.util.JwtUtils.getUserIdFromJwt(prefToken)
                if (!jwtUid.isNullOrBlank()) return jwtUid
            } catch (_: Throwable) {}
        }
        return null
    }

    internal fun onSessionCleared() {
        if (!isUploadingAvatar.get()) {
            _currentAvatar.value = null
            loadedEmailKey = null
        }
    }

    internal suspend fun ensureSupabaseAuthHydrated(context: Context?) {
        val supabase = SupabaseInstance.client ?: return
        val existingToken = try { supabase.auth.currentAccessTokenOrNull()?.trim() } catch (_: Throwable) { null }
        val existingUser = try { supabase.auth.currentUserOrNull() } catch (_: Throwable) { null }
        val hasValidSession = !existingToken.isNullOrBlank() &&
            existingToken != SupabaseInstance.rawKey &&
            !com.example.util.JwtUtils.isExpired(existingToken) &&
            existingUser != null
        if (hasValidSession) {
            SupabaseInstance.activeUserAccessToken = existingToken
            return
        }
        try {
            val prefs = context?.let { getSecurePrefs(it) }
            val storedAccess = prefs?.getString("key_auth_access_token", null) ?: prefs?.getString("key_access_token", null)
            var access = SupabaseInstance.resolveValidSessionAccessToken(storedAccess)
            var refresh = (prefs?.getString("key_auth_refresh_token", null) ?: prefs?.getString("key_refresh_token", null)).orEmpty()

            // If access token is expired and we have a refresh token, refresh via Supabase Auth REST endpoint directly
            if ((access.isBlank() || access == SupabaseInstance.rawKey || com.example.util.JwtUtils.isExpired(access)) && refresh.isNotBlank()) {
                val baseUrl = SupabaseInstance.safeUrl.trimEnd('/')
                val anonKey = SupabaseInstance.rawKey
                if (baseUrl.isNotBlank() && anonKey.isNotBlank()) {
                    try {
                        val bodyJson = org.json.JSONObject().put("refresh_token", refresh).toString()
                        val req = Request.Builder()
                            .url("$baseUrl/auth/v1/token?grant_type=refresh_token")
                            .addHeader("apikey", anonKey)
                            .addHeader("Content-Type", "application/json")
                            .post(bodyJson.toRequestBody("application/json".toMediaType()))
                            .build()
                        com.example.data.remote.ApiNetworkClient.okHttpClient.newCall(req).execute().use { res ->
                            if (res.isSuccessful) {
                                val json = org.json.JSONObject(res.body?.string().orEmpty().ifBlank { "{}" })
                                val newAccess = json.optString("access_token").trim()
                                val newRefresh = json.optString("refresh_token").trim()
                                if (newAccess.isNotBlank()) {
                                    access = newAccess
                                    if (newRefresh.isNotBlank()) refresh = newRefresh
                                    prefs?.edit()?.apply {
                                        putString("key_auth_access_token", newAccess)
                                        if (newRefresh.isNotBlank()) putString("key_auth_refresh_token", newRefresh)
                                        apply()
                                    }
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                }
            }

            if (access.isNotBlank() && access != SupabaseInstance.rawKey && !access.startsWith("session_")) {
                SupabaseInstance.activeUserAccessToken = access
                supabase.auth.importAuthToken(
                    accessToken = access,
                    refreshToken = refresh,
                    retrieveUser = false,
                    autoRefresh = refresh.isNotBlank()
                )
            }
        } catch (_: Throwable) {}
    }

    private fun buildCompactDataUri(bytes: ByteArray): String? {
        return try {
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: decodeSampledBitmap(bytes, 256) ?: return null
            val scale = 256f / maxOf(bmp.width.coerceAtLeast(1), bmp.height.coerceAtLeast(1))
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * scale).toInt().coerceAtLeast(1),
                    (bmp.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bmp
            }
            val compactBytes = ByteArrayOutputStream().also {
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, it)
            }.toByteArray()
            "data:image/jpeg;base64," + Base64.encodeToString(compactBytes, Base64.NO_WRAP)
        } catch (_: Throwable) {
            null
        }
    }

    internal suspend fun uploadAvatarRestFallback(
        context: Context?,
        userId: String,
        bytes: ByteArray,
        email: String? = null,
        skipStorageUpload: Boolean = false
    ) = withContext(Dispatchers.IO) {
        try {
            val prefs = context?.let { getSecurePrefs(it) }
            val storedAccess = prefs?.getString("key_auth_access_token", null) ?: prefs?.getString("key_access_token", null)
            val token = SupabaseInstance.resolveValidSessionAccessToken(storedAccess)
            val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
            val anonKey = SupabaseInstance.rawKey
            val baseUrl = SupabaseInstance.safeUrl.trimEnd('/')
            if (baseUrl.isBlank() || anonKey.isBlank()) return@withContext
            val http = com.example.data.remote.ApiNetworkClient.okHttpClient

            var storageUploadOk = skipStorageUpload
            if (!skipStorageUpload) {
                val uploadReq = Request.Builder()
                    .url("$baseUrl/storage/v1/object/avatars/$userId/avatar.jpg")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", bearer)
                    .addHeader("x-upsert", "true")
                    .addHeader("Content-Type", "image/jpeg")
                    .post(bytes.toRequestBody("image/jpeg".toMediaType()))
                    .build()
                storageUploadOk = try {
                    http.newCall(uploadReq).execute().use { it.isSuccessful }
                } catch (_: Throwable) { false }

                if (!storageUploadOk) {
                    val putReq = Request.Builder()
                        .url("$baseUrl/storage/v1/object/avatars/$userId/avatar.jpg")
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", bearer)
                        .addHeader("x-upsert", "true")
                        .addHeader("Content-Type", "image/jpeg")
                        .put(bytes.toRequestBody("image/jpeg".toMediaType()))
                        .build()
                    storageUploadOk = try {
                        http.newCall(putReq).execute().use { it.isSuccessful }
                    } catch (_: Throwable) { false }
                }
            }

            // Call set_my_avatar RPC so server function updates public.users.avatar_url
            val rpcReq = Request.Builder()
                .url("$baseUrl/rest/v1/rpc/set_my_avatar")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", bearer)
                .addHeader("Content-Type", "application/json")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            try { http.newCall(rpcReq).execute().close() } catch (_: Throwable) {}

            // Verify if Storage URL actually serves a valid decodable image
            val avatarPublicUrl = "$baseUrl/storage/v1/object/public/avatars/$userId/avatar.jpg"
            val storageDeliverable = if (storageUploadOk) {
                val verifyBmp = downloadAndDecodeUrl(avatarPublicUrl, bearer, anonKey)
                    ?: downloadAndDecodeUrl("$baseUrl/storage/v1/object/authenticated/avatars/$userId/avatar.jpg", bearer, anonKey)
                    ?: downloadAndDecodeUrl("$baseUrl/storage/v1/object/avatars/$userId/avatar.jpg", bearer, anonKey)
                verifyBmp != null
            } else {
                false
            }

            val compactDataUri = buildCompactDataUri(bytes)
            // Value to store in public.users.avatar_url:
            // If Storage object is downloadable, use the Storage public URL;
            // if Storage bucket is unavailable/blocked, store the self-contained data URI directly in public.users.avatar_url!
            val effectiveAvatarUrl = if (storageDeliverable) {
                avatarPublicUrl
            } else {
                compactDataUri ?: avatarPublicUrl
            }

            // Try updating public.users.avatar_url via Postgrest SDK first
            try {
                SupabaseInstance.client?.from("users")?.update(
                    buildJsonObject { put("avatar_url", JsonPrimitive(effectiveAvatarUrl)) }
                ) {
                    filter { eq("id", userId) }
                }
            } catch (_: Throwable) {}

            val patchBody = org.json.JSONObject().put("avatar_url", effectiveAvatarUrl).toString()
            var updatedRows = false
            val patchByIdReq = Request.Builder()
                .url("$baseUrl/rest/v1/users?id=eq.$userId")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", bearer)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .patch(patchBody.toRequestBody("application/json".toMediaType()))
                .build()
            try {
                http.newCall(patchByIdReq).execute().use { res ->
                    if (res.isSuccessful) {
                        val arr = JSONArray(res.body?.string().orEmpty().ifBlank { "[]" })
                        updatedRows = arr.length() > 0
                    }
                }
            } catch (_: Throwable) {}

            val resolvedEmail = resolveActiveUserEmail(context, email)
            if (!updatedRows && !resolvedEmail.isNullOrBlank()) {
                val encodedEmail = java.net.URLEncoder.encode(resolvedEmail, "UTF-8")
                val patchByEmailReq = Request.Builder()
                    .url("$baseUrl/rest/v1/users?email=eq.$encodedEmail")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", bearer)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .patch(patchBody.toRequestBody("application/json".toMediaType()))
                    .build()
                try {
                    http.newCall(patchByEmailReq).execute().close()
                } catch (_: Throwable) {}
            }

            // Also persist in Supabase Auth user_metadata so login session carries clean avatar_url,
            // while stripping any legacy bloated avatar_base64 or avatar_data_uri from the JWT payload
            try {
                val metaObj = org.json.JSONObject().apply {
                    if (effectiveAvatarUrl.startsWith("http://") || effectiveAvatarUrl.startsWith("https://")) {
                        put("avatar_url", effectiveAvatarUrl)
                    }
                    put("avatar_base64", org.json.JSONObject.NULL)
                    put("avatar_data_uri", org.json.JSONObject.NULL)
                }
                val authBody = org.json.JSONObject().put("data", metaObj).toString()
                val authReq = Request.Builder()
                    .url("$baseUrl/auth/v1/user")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", bearer)
                    .addHeader("Content-Type", "application/json")
                    .put(authBody.toRequestBody("application/json".toMediaType()))
                    .build()
                http.newCall(authReq).execute().close()
            } catch (_: Throwable) {}
        } catch (_: Throwable) {}
    }

    internal suspend fun removeAvatarRestFallback(userId: String) = withContext(Dispatchers.IO) {
        try {
            val token = SupabaseInstance.resolveValidSessionAccessToken()
            val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
            val anonKey = SupabaseInstance.rawKey
            val baseUrl = SupabaseInstance.safeUrl.trimEnd('/')
            if (baseUrl.isBlank() || anonKey.isBlank()) return@withContext
            val http = com.example.data.remote.ApiNetworkClient.okHttpClient

            val delReq = Request.Builder()
                .url("$baseUrl/storage/v1/object/avatars/$userId/avatar.jpg")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", bearer)
                .delete()
                .build()
            try { http.newCall(delReq).execute().close() } catch (_: Throwable) {}

            val rpcReq = Request.Builder()
                .url("$baseUrl/rest/v1/rpc/remove_my_avatar")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", bearer)
                .addHeader("Content-Type", "application/json")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            try { http.newCall(rpcReq).execute().close() } catch (_: Throwable) {}

            val patchBody = "{\"avatar_url\":null}"
            val patchReq = Request.Builder()
                .url("$baseUrl/rest/v1/users?id=eq.$userId")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", bearer)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .patch(patchBody.toRequestBody("application/json".toMediaType()))
                .build()
            try { http.newCall(patchReq).execute().close() } catch (_: Throwable) {}

            try {
                val metaObj = org.json.JSONObject().apply {
                    put("avatar_url", org.json.JSONObject.NULL)
                    put("avatar_data_uri", org.json.JSONObject.NULL)
                }
                val authBody = org.json.JSONObject().put("data", metaObj).toString()
                val authReq = Request.Builder()
                    .url("$baseUrl/auth/v1/user")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", bearer)
                    .addHeader("Content-Type", "application/json")
                    .put(authBody.toRequestBody("application/json".toMediaType()))
                    .build()
                http.newCall(authReq).execute().close()
            } catch (_: Throwable) {}
        } catch (_: Throwable) {}
    }

    private fun sanitizeEmailKey(email: String?): String {
        val clean = email?.trim()?.lowercase()?.replace(Regex("[^a-z0-9._-]"), "_")
        return if (clean.isNullOrBlank()) "default_user" else clean
    }

    private fun getAvatarFile(context: Context, email: String?): File {
        val key = sanitizeEmailKey(email)
        return File(context.filesDir, "profile_avatar_$key.jpg")
    }

    fun setPendingCameraFilePath(context: Context, path: String?) {
        try {
            val prefs = getSecurePrefs(context)
            if (path.isNullOrBlank()) {
                prefs.edit().remove(KEY_PENDING_CAMERA_PATH).apply()
            } else {
                prefs.edit().putString(KEY_PENDING_CAMERA_PATH, path).apply()
            }
        } catch (_: Throwable) {}
    }

    fun getPendingCameraFilePath(context: Context): String? {
        return try {
            val prefs = getSecurePrefs(context)
            prefs.getString(KEY_PENDING_CAMERA_PATH, null)?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeBytesToValidBitmap(bytes: ByteArray?): Bitmap? {
        if (bytes == null || bytes.size < 32) return null
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: decodeSampledBitmap(bytes, 512)
        } catch (_: Throwable) {
            null
        }
    }

    private fun downloadAndDecodeUrl(url: String, bearer: String, anonKey: String): Bitmap? {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return null
        if (cleanUrl.startsWith("data:image", ignoreCase = true) || (!cleanUrl.startsWith("http", ignoreCase = true) && cleanUrl.length > 200)) {
            return decodeBase64Bitmap(cleanUrl)
        }
        val http = com.example.data.remote.ApiNetworkClient.okHttpClient
        // 1. Try authenticated request with user JWT + apikey
        try {
            val reqAuth = Request.Builder()
                .url(cleanUrl)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", bearer)
                .addHeader("Cache-Control", "no-cache")
                .get()
                .build()
            http.newCall(reqAuth).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bmp = decodeBytesToValidBitmap(resp.body?.bytes())
                    if (bmp != null) return bmp
                }
            }
        } catch (_: Throwable) {}

        // 2. Try public request without Authorization header (in case signed/public URL rejects extra headers)
        try {
            val reqPub = Request.Builder()
                .url(cleanUrl)
                .addHeader("Cache-Control", "no-cache")
                .get()
                .build()
            http.newCall(reqPub).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bmp = decodeBytesToValidBitmap(resp.body?.bytes())
                    if (bmp != null) return bmp
                }
            }
        } catch (_: Throwable) {}
        return null
    }

    private suspend fun downloadRemoteAvatarBitmap(
        userId: String?,
        remoteAvatarUrl: String?,
        bearer: String,
        anonKey: String
    ): Bitmap? {
        val baseUrl = SupabaseInstance.safeUrl.trimEnd('/')
        val cleanUrl = remoteAvatarUrl?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

        // 1. If remoteAvatarUrl is a data URI or Base64 string, decode immediately
        if (!cleanUrl.isNullOrBlank() &&
            (cleanUrl.startsWith("data:image", ignoreCase = true) || (!cleanUrl.startsWith("http", ignoreCase = true) && !cleanUrl.contains("/") && cleanUrl.length > 128))
        ) {
            decodeBase64Bitmap(cleanUrl)?.let { return it }
        }

        // 2. If remoteAvatarUrl is a full URL or relative Storage path from public.users.avatar_url
        if (!cleanUrl.isNullOrBlank()) {
            val resolvedUrl = when {
                cleanUrl.startsWith("http", ignoreCase = true) -> cleanUrl
                cleanUrl.startsWith("/storage/", ignoreCase = true) -> "$baseUrl$cleanUrl"
                cleanUrl.startsWith("storage/", ignoreCase = true) -> "$baseUrl/$cleanUrl"
                cleanUrl.startsWith("avatars/", ignoreCase = true) -> "$baseUrl/storage/v1/object/public/$cleanUrl"
                else -> "$baseUrl/storage/v1/object/public/avatars/${cleanUrl.trimStart('/')}"
            }
            downloadAndDecodeUrl(resolvedUrl, bearer, anonKey)?.let { return it }
        }

        // 3. Try all Supabase Storage endpoints for "$userId/avatar.jpg" (validating each decoded Bitmap!)
        if (!userId.isNullOrBlank() && baseUrl.isNotBlank()) {
            val storageUrls = listOf(
                "$baseUrl/storage/v1/object/public/avatars/$userId/avatar.jpg",
                "$baseUrl/storage/v1/object/authenticated/avatars/$userId/avatar.jpg",
                "$baseUrl/storage/v1/object/avatars/$userId/avatar.jpg"
            )
            for (sUrl in storageUrls) {
                downloadAndDecodeUrl(sUrl, bearer, anonKey)?.let { return it }
            }

            val supabase = SupabaseInstance.client
            if (supabase != null) {
                val pubBytes = try {
                    supabase.storage.from("avatars").downloadPublic("$userId/avatar.jpg")
                } catch (_: Throwable) { null }
                decodeBytesToValidBitmap(pubBytes)?.let { return it }

                val authBytes = try {
                    supabase.storage.from("avatars").downloadAuthenticated("$userId/avatar.jpg")
                } catch (_: Throwable) { null }
                decodeBytesToValidBitmap(authBytes)?.let { return it }
            }
        }

        // 4. Check Supabase Auth user_metadata backup (avatar_data_uri / avatar_url)
        try {
            val meta = SupabaseInstance.client?.auth?.currentUserOrNull()?.userMetadata
            val metaDataUri = meta?.get("avatar_data_uri")?.jsonPrimitive?.contentOrNull
                ?: meta?.get("avatar_url")?.jsonPrimitive?.contentOrNull
            if (!metaDataUri.isNullOrBlank() && metaDataUri != "null") {
                downloadAndDecodeUrl(metaDataUri, bearer, anonKey)?.let { return it }
            }
        } catch (_: Throwable) {}

        return null
    }

    /**
     * Called whenever `public.users` is fetched during login or profile sync so `avatar_url`
     * from `public.users` is immediately downloaded and shown on the dashboard.
     */
    fun syncAvatarFromUsersRow(
        context: Context,
        userId: String?,
        email: String?,
        remoteAvatarUrl: String?
    ) {
        backgroundScope.launch {
            if (isUploadingAvatar.get()) return@launch
            val cleanUrl = remoteAvatarUrl?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            val resolvedEmail = resolveActiveUserEmail(context, email)
            val key = sanitizeEmailKey(resolvedEmail)
            val userFile = getAvatarFile(context, resolvedEmail)
            val prefs = getSecurePrefs(context)
            val wasExplicitlyRemoved = prefs.getBoolean("${KEY_AVATAR_REMOVED}_$key", false)
            if (wasExplicitlyRemoved && cleanUrl.isNullOrBlank()) return@launch

            // If we already have the avatar loaded in memory AND saved on disk for this user, no need to re-download
            if (_currentAvatar.value != null && userFile.exists() && userFile.length() > 0 && loadedEmailKey == key) {
                return@launch
            }

            if (!cleanUrl.isNullOrBlank()) {
                val storedAccess = prefs.getString("key_auth_access_token", null) ?: prefs.getString("key_access_token", null)
                val token = SupabaseInstance.resolveValidSessionAccessToken(storedAccess)
                val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
                val bmp = downloadRemoteAvatarBitmap(userId, cleanUrl, bearer, SupabaseInstance.rawKey)
                if (bmp != null && !isUploadingAvatar.get()) {
                    _currentAvatar.value = bmp
                    loadedEmailKey = key
                    saveBitmapToFile(bmp, userFile)
                    val b64 = encodeBitmapBase64(bmp, quality = 85)
                    prefs.edit()
                        .putString("${KEY_AVATAR_B64}_$key", b64)
                        .putString(KEY_AVATAR_B64, b64)
                        .putBoolean("${KEY_AVATAR_REMOVED}_$key", false)
                        .apply()
                    return@launch
                }
            }
            if (_currentAvatar.value == null) {
                loadAvatar(context, resolvedEmail)
            }
        }
    }

    suspend fun loadAvatar(context: Context, email: String?) = withContext(Dispatchers.IO) {
        if (isUploadingAvatar.get()) return@withContext
        val startVersion = avatarVersion.get()

        val resolvedEmail = resolveActiveUserEmail(context, email)
        val key = sanitizeEmailKey(resolvedEmail)
        val previousKey = loadedEmailKey
        if (previousKey != null &&
            previousKey != "default_user" &&
            key != "default_user" &&
            previousKey != key
        ) {
            _currentAvatar.value = null
        }
        if (key != "default_user" || previousKey == null) {
            loadedEmailKey = key
        }

        val userFile = getAvatarFile(context, resolvedEmail)
        val defaultFile = File(context.filesDir, "profile_avatar_default_user.jpg")
        val prefs = getSecurePrefs(context)
        val legacyPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wasExplicitlyRemoved = prefs.getBoolean("${KEY_AVATAR_REMOVED}_$key", false)

        // 1. Show cached local file or Base64 backup immediately
        if (!wasExplicitlyRemoved && _currentAvatar.value == null) {
            val candidateFile = when {
                userFile.exists() && userFile.length() > 0 -> userFile
                defaultFile.exists() && defaultFile.length() > 0 -> defaultFile
                else -> null
            }
            if (candidateFile != null) {
                try {
                    val bytes = candidateFile.readBytes()
                    val bmp = decodeBytesToValidBitmap(bytes)
                    if (bmp != null && !isUploadingAvatar.get() && avatarVersion.get() == startVersion) {
                        _currentAvatar.value = bmp
                    }
                } catch (_: Throwable) {}
            }
            if (_currentAvatar.value == null) {
                val b64 = prefs.getString("${KEY_AVATAR_B64}_$key", null)
                    ?: prefs.getString(KEY_AVATAR_B64, null)
                    ?: legacyPrefs.getString("${KEY_AVATAR_B64}_$key", null)
                    ?: legacyPrefs.getString(KEY_AVATAR_B64, null)
                if (!b64.isNullOrBlank()) {
                    val bmp = decodeBase64Bitmap(b64)
                    if (bmp != null && !isUploadingAvatar.get() && avatarVersion.get() == startVersion) {
                        _currentAvatar.value = bmp
                        saveBitmapToFile(bmp, userFile)
                    }
                }
            }
        }

        // 2. Query public.users.avatar_url and Supabase Storage "avatars" bucket
        ensureSupabaseAuthHydrated(context)
        if (isUploadingAvatar.get() || avatarVersion.get() != startVersion) return@withContext

        val supabase = SupabaseInstance.client
        val userId = resolveActiveUserId(context)
        val storedAccess = prefs.getString("key_auth_access_token", null) ?: prefs.getString("key_access_token", null)
        val token = SupabaseInstance.resolveValidSessionAccessToken(storedAccess)
        val bearer = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
        val anonKey = SupabaseInstance.rawKey
        val baseUrl = SupabaseInstance.safeUrl.trimEnd('/')

        if (supabase != null && (!userId.isNullOrBlank() || !resolvedEmail.isNullOrBlank())) {
            var remoteAvatarUrl: String? = null
            var checkedUsersTable = false

            if (!userId.isNullOrBlank()) {
                try {
                    val row = supabase.from("users").select(Columns.list("avatar_url")) {
                        filter { eq("id", userId) }
                        limit(1)
                    }.decodeSingleOrNull<JsonObject>()
                    if (row != null) {
                        checkedUsersTable = true
                        remoteAvatarUrl = row["avatar_url"]?.jsonPrimitive?.contentOrNull
                            ?.trim()
                            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    }
                } catch (_: Throwable) {}
            }

            if ((!checkedUsersTable || remoteAvatarUrl.isNullOrBlank()) && !userId.isNullOrBlank() && baseUrl.isNotBlank()) {
                try {
                    val req = Request.Builder()
                        .url("$baseUrl/rest/v1/users?select=avatar_url&id=eq.$userId&limit=1")
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", bearer)
                        .addHeader("Cache-Control", "no-cache")
                        .get()
                        .build()
                    com.example.data.remote.ApiNetworkClient.okHttpClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) {
                            val arr = JSONArray(res.body?.string().orEmpty().ifBlank { "[]" })
                            if (arr.length() > 0) {
                                checkedUsersTable = true
                                val obj = arr.getJSONObject(0)
                                if (obj.has("avatar_url") && !obj.isNull("avatar_url")) {
                                    remoteAvatarUrl = obj.optString("avatar_url", "")
                                        .trim()
                                        .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }

            if ((!checkedUsersTable || remoteAvatarUrl.isNullOrBlank()) && !resolvedEmail.isNullOrBlank() && baseUrl.isNotBlank()) {
                try {
                    val encodedEmail = java.net.URLEncoder.encode(resolvedEmail, "UTF-8")
                    val req = Request.Builder()
                        .url("$baseUrl/rest/v1/users?select=id,avatar_url&email=eq.$encodedEmail&limit=1")
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", bearer)
                        .addHeader("Cache-Control", "no-cache")
                        .get()
                        .build()
                    com.example.data.remote.ApiNetworkClient.okHttpClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) {
                            val arr = JSONArray(res.body?.string().orEmpty().ifBlank { "[]" })
                            if (arr.length() > 0) {
                                checkedUsersTable = true
                                val obj = arr.getJSONObject(0)
                                if (obj.has("avatar_url") && !obj.isNull("avatar_url")) {
                                    remoteAvatarUrl = obj.optString("avatar_url", "")
                                        .trim()
                                        .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }

            if (isUploadingAvatar.get() || avatarVersion.get() != startVersion) return@withContext

            if (!remoteAvatarUrl.isNullOrBlank() || (!wasExplicitlyRemoved && _currentAvatar.value == null)) {
                val bmp = downloadRemoteAvatarBitmap(
                    userId = userId,
                    remoteAvatarUrl = remoteAvatarUrl,
                    bearer = bearer,
                    anonKey = anonKey
                )
                if (bmp != null && !isUploadingAvatar.get() && avatarVersion.get() == startVersion) {
                    _currentAvatar.value = bmp
                    saveBitmapToFile(bmp, userFile)
                    val b64 = encodeBitmapBase64(bmp, quality = 85)
                    prefs.edit()
                        .putString("${KEY_AVATAR_B64}_$key", b64)
                        .putString(KEY_AVATAR_B64, b64)
                        .putBoolean("${KEY_AVATAR_REMOVED}_$key", false)
                        .apply()

                    // If public.users.avatar_url was blank when we recovered the avatar from Storage/Auth, sync it to public.users.avatar_url
                    if (remoteAvatarUrl.isNullOrBlank() && !userId.isNullOrBlank()) {
                        val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
                        uploadAvatarRestFallback(context, userId, bytes, resolvedEmail, skipStorageUpload = true)
                    }
                    return@withContext
                }
            }

            if (remoteAvatarUrl.isNullOrBlank() && _currentAvatar.value != null && !wasExplicitlyRemoved && !userId.isNullOrBlank()) {
                // Local avatar exists on device but public.users.avatar_url was null; push local avatar up to Supabase
                try {
                    val localBytes = if (userFile.exists() && userFile.length() > 0) {
                        userFile.readBytes()
                    } else {
                        val bmp = _currentAvatar.value
                        if (bmp != null) {
                            ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
                        } else null
                    }
                    if (localBytes != null && localBytes.isNotEmpty()) {
                        uploadAvatarRestFallback(context, userId, localBytes, resolvedEmail, skipStorageUpload = false)
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    suspend fun saveAvatarFromFile(context: Context, file: File, email: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || file.length() <= 0L) return@withContext false
            saveAvatarFromUri(context, Uri.fromFile(file), email)
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun saveAvatarFromUri(context: Context, uri: Uri, email: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolvedEmail = resolveActiveUserEmail(context, email)
            val key = sanitizeEmailKey(resolvedEmail)
            loadedEmailKey = key

            val rawBmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: _currentAvatar.value
                ?: return@withContext false

            val scale = 512f / maxOf(rawBmp.width.coerceAtLeast(1), rawBmp.height.coerceAtLeast(1))
            val small = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    rawBmp,
                    (rawBmp.width * scale).toInt().coerceAtLeast(1),
                    (rawBmp.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                rawBmp
            }
            val jpegBytes = ByteArrayOutputStream().also {
                small.compress(Bitmap.CompressFormat.JPEG, 85, it)
            }.toByteArray()

            // Save locally and update StateFlow immediately so it never flickers or disappears
            persistLocalAvatarImmediately(context, small, jpegBytes, resolvedEmail)

            // Execute the Supabase Storage upload + set_my_avatar RPC + public.users.avatar_url sync in backgroundScope
            // so navigating away from the Profile screen never cancels the upload
            val appContext = context.applicationContext ?: context
            backgroundScope.launch {
                ensureSupabaseAuthHydrated(appContext)
                val supabase = SupabaseInstance.client
                val userId = try { supabase?.auth?.currentUserOrNull()?.id } catch (_: Throwable) { null }
                    ?: resolveActiveUserId(appContext)
                if (!userId.isNullOrBlank()) {
                    beginRemoteUpload()
                    try {
                        var sdkUploaded = false
                        if (supabase != null) {
                            try {
                                supabase.storage.from("avatars").upload("$userId/avatar.jpg", jpegBytes) { upsert = true }
                                sdkUploaded = true
                                supabase.postgrest.rpc("set_my_avatar")
                            } catch (_: Throwable) {}
                        }
                        uploadAvatarRestFallback(
                            context = appContext,
                            userId = userId,
                            bytes = jpegBytes,
                            email = resolvedEmail,
                            skipStorageUpload = sdkUploaded
                        )
                    } finally {
                        endRemoteUpload()
                    }
                }
            }

            if (_currentAvatar.value == null) {
                _currentAvatar.value = small
            }

            true
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun saveAvatarFromBitmap(context: Context, rawBitmap: Bitmap, email: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempFile = File(context.cacheDir, "temp_avatar_upload_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { out ->
                rawBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                out.flush()
            }
            val result = saveAvatarFromUri(context, Uri.fromFile(tempFile), email)
            try { tempFile.delete() } catch (_: Throwable) {}
            result
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun clearAvatar(context: Context, email: String?) = withContext(Dispatchers.IO) {
        val resolvedEmail = resolveActiveUserEmail(context, email)
        val key = sanitizeEmailKey(resolvedEmail)
        markAvatarRemoved()
        try {
            getAvatarFile(context, resolvedEmail).delete()
            File(context.filesDir, "profile_avatar_default_user.jpg").delete()
        } catch (_: Throwable) {}

        val prefs = getSecurePrefs(context)
        prefs.edit()
            .remove("${KEY_AVATAR_B64}_$key")
            .remove(KEY_AVATAR_B64)
            .putBoolean("${KEY_AVATAR_REMOVED}_$key", true)
            .apply()

        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .remove("${KEY_AVATAR_B64}_$key")
                .remove(KEY_AVATAR_B64)
                .apply()
        } catch (_: Throwable) {}

        val appContext = context.applicationContext ?: context
        backgroundScope.launch {
            ensureSupabaseAuthHydrated(appContext)
            removeAvatar()
        }
    }

    private fun decodeSampledBitmap(bytes: ByteArray, maxDimension: Int): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
            var sampleSize = 1
            val w = boundsOptions.outWidth
            val h = boundsOptions.outHeight
            if (w > 0 && h > 0) {
                while (w / sampleSize > maxDimension * 2 || h / sampleSize > maxDimension * 2) {
                    sampleSize *= 2
                }
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        } catch (_: Throwable) {
            null
        }
    }

    private fun centerCropSquare(source: Bitmap, targetSize: Int): Bitmap {
        val width = source.width.coerceAtLeast(1)
        val height = source.height.coerceAtLeast(1)
        val size = min(width, height)
        val xOffset = (width - size) / 2
        val yOffset = (height - size) / 2
        val square = Bitmap.createBitmap(source, xOffset, yOffset, size, size)
        return if (size != targetSize) {
            Bitmap.createScaledBitmap(square, targetSize, targetSize, true)
        } else {
            square
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap, file: File) {
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.flush()
            }
        } catch (_: Throwable) {}
    }

    private fun encodeBitmapBase64(bitmap: Bitmap, quality: Int): String {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    private fun decodeBase64Bitmap(base64Str: String): Bitmap? {
        return try {
            val clean = base64Str.substringAfter("base64,", base64Str)
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            decodeSampledBitmap(bytes, 512)
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * Interactive profile picture component for Profile & Security screen.
 * Allows selecting an image from the gallery or snapping a picture using the device camera.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditableProfileAvatar(
    initials: String,
    userEmail: String?,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    fontSize: TextUnit = 26.sp
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val avatarBitmap by ProfilePictureStore.currentAvatar.collectAsState()
    var showPickerSheet by remember { mutableStateOf(false) }
    var pendingCameraUriString by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraFilePath by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(userEmail, SupabaseInstance.activeUserAccessToken) {
        ProfilePictureStore.loadAvatar(context, userEmail)
    }

    // 1. Android Photo Picker (Gallery)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = ProfilePictureStore.saveAvatarFromUri(context, uri, userEmail)
                if (ok) {
                    Toast.makeText(context, "Profile picture updated!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not load selected image.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Fallback gallery content picker
    val fallbackGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = ProfilePictureStore.saveAvatarFromUri(context, uri, userEmail)
                if (ok) {
                    Toast.makeText(context, "Profile picture updated!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not load selected image.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 2. Camera Bitmap fallback launcher
    val cameraPreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            scope.launch {
                val ok = ProfilePictureStore.saveAvatarFromBitmap(context, bitmap, userEmail)
                if (ok) {
                    Toast.makeText(context, "Profile picture snapped & saved!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to save captured photo.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 3. Full-resolution Camera launcher via FileProvider
    val cameraTakePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val resolvedPath = pendingCameraFilePath ?: ProfilePictureStore.getPendingCameraFilePath(context)
        val file = resolvedPath?.let { File(it) }
        val uri = pendingCameraUriString?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (success) {
            scope.launch {
                val ok = when {
                    file != null && file.exists() && file.length() > 0 ->
                        ProfilePictureStore.saveAvatarFromFile(context, file, userEmail)
                    uri != null ->
                        ProfilePictureStore.saveAvatarFromUri(context, uri, userEmail)
                    else -> false
                }
                ProfilePictureStore.setPendingCameraFilePath(context, null)
                if (ok) {
                    Toast.makeText(context, "Profile picture snapped & saved!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to process captured photo.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            ProfilePictureStore.setPendingCameraFilePath(context, null)
        }
    }

    fun launchGalleryPicker() {
        try {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (_: Throwable) {
            try {
                fallbackGalleryLauncher.launch("image/*")
            } catch (e: Throwable) {
                Toast.makeText(
                    context,
                    "Unable to open gallery: ${e.localizedMessage ?: "No gallery app available"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun launchCameraCapture() {
        try {
            val photoFile = File(context.cacheDir, "captured_profile_${System.currentTimeMillis()}.jpg")
            if (photoFile.exists()) photoFile.delete()
            photoFile.createNewFile()
            val authority = "${context.packageName}.fileprovider"
            val photoUri = FileProvider.getUriForFile(context, authority, photoFile)
            pendingCameraFilePath = photoFile.absolutePath
            pendingCameraUriString = photoUri.toString()
            ProfilePictureStore.setPendingCameraFilePath(context, photoFile.absolutePath)
            cameraTakePictureLauncher.launch(photoUri)
        } catch (_: Throwable) {
            try {
                cameraPreviewLauncher.launch(null)
            } catch (e: Throwable) {
                Toast.makeText(
                    context,
                    "Unable to open camera: ${e.localizedMessage ?: "No camera app available"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 4. Runtime CAMERA permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCameraCapture()
        } else {
            Toast.makeText(
                context,
                "Camera permission is required to snap a profile picture.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Box(
        modifier = modifier
            .size(size + 8.dp)
            .clickable { showPickerSheet = true }
            .testTag("profile_avatar_picker_button"),
        contentAlignment = Alignment.Center
    ) {
        // Main Circular Avatar
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(VtuGreenPrimary, VtuCyan)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            val currentBmp = avatarBitmap
            if (currentBmp != null) {
                Image(
                    bitmap = currentBmp.asImageBitmap(),
                    contentDescription = "User Profile Picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = initials,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = fontSize
                )
            }
        }

        // Camera Badge Overlay in bottom-right corner
        Surface(
            shape = CircleShape,
            color = VtuGreenPrimary,
            shadowElevation = 4.dp,
            modifier = Modifier
                .size(28.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-2).dp, y = (-2).dp)
                .border(2.dp, Color(0xFF0A192F), CircleShape)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Change Profile Picture",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }

    if (showPickerSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPickerSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Update Profile Picture",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Choose a picture from your phone gallery or snap a new photo with your camera.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Option 1: Choose from Gallery
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            showPickerSheet = false
                            launchGalleryPicker()
                        }
                        .testTag("pick_from_gallery_option"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(VtuGreenPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Gallery",
                                tint = VtuGreenPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Choose from Gallery",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Select an existing photo from your device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Option 2: Snap a Picture from Camera
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            showPickerSheet = false
                            try {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    launchCameraCapture()
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            } catch (_: Throwable) {
                                launchCameraCapture()
                            }
                        }
                        .testTag("snap_from_camera_option"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(VtuCyan.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "Camera",
                                tint = VtuGreenPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Snap a Picture",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Take a new photo using your camera",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Option 3: Remove current photo (only shown if custom photo exists)
                if (avatarBitmap != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                showPickerSheet = false
                                scope.launch {
                                    ProfilePictureStore.clearAvatar(context, userEmail)
                                    Toast.makeText(context, "Profile picture removed", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .testTag("remove_profile_photo_option"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Remove Photo",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Remove Photo",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Revert to default initials avatar ($initials)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                TextButton(
                    onClick = { showPickerSheet = false },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Read-only user avatar composable for HomeScreen and LogoutScreen that displays
 * the user's saved profile picture when set, or falls back to their initials.
 */
@Composable
fun UserProfileAvatar(
    initials: String,
    userEmail: String?,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    fontSize: TextUnit = 17.sp
) {
    val context = LocalContext.current
    val avatarBitmap by ProfilePictureStore.currentAvatar.collectAsState()

    LaunchedEffect(userEmail, SupabaseInstance.activeUserAccessToken) {
        ProfilePictureStore.loadAvatar(context, userEmail)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(VtuGreenPrimary, VtuCyan)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        val currentBmp = avatarBitmap
        if (currentBmp != null) {
            Image(
                bitmap = currentBmp.asImageBitmap(),
                contentDescription = "User Profile Picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = initials,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = fontSize
            )
        }
    }
}
