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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object ProfilePictureStore {
    private const val PREFS_NAME = "daniel_vtu_prefs"
    private const val KEY_AVATAR_B64 = "key_profile_avatar_b64"
    private const val KEY_PENDING_CAMERA_PATH = "key_pending_camera_file_path"

    private val _currentAvatar = MutableStateFlow<Bitmap?>(null)
    val currentAvatar: StateFlow<Bitmap?> = _currentAvatar.asStateFlow()

    private var loadedEmailKey: String? = null

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
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (path.isNullOrBlank()) {
                prefs.edit().remove(KEY_PENDING_CAMERA_PATH).apply()
            } else {
                prefs.edit().putString(KEY_PENDING_CAMERA_PATH, path).apply()
            }
        } catch (_: Throwable) {}
    }

    fun getPendingCameraFilePath(context: Context): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_PENDING_CAMERA_PATH, null)?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun loadAvatar(context: Context, email: String?) = withContext(Dispatchers.IO) {
        val key = sanitizeEmailKey(email)
        if (_currentAvatar.value != null && loadedEmailKey == key) {
            return@withContext
        }
        if (loadedEmailKey != key) {
            _currentAvatar.value = null
        }
        loadedEmailKey = key

        // 1. Check user-specific local file storage
        val userFile = getAvatarFile(context, email)
        if (userFile.exists() && userFile.length() > 0) {
            try {
                val bytes = userFile.readBytes()
                val bmp = decodeSampledBitmap(bytes, 512)
                if (bmp != null) {
                    _currentAvatar.value = bmp
                    return@withContext
                }
            } catch (_: Throwable) {}
        }

        // 2. Check SharedPreferences Base64 backup for this user
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val b64 = prefs.getString("${KEY_AVATAR_B64}_$key", null)
        if (!b64.isNullOrBlank()) {
            val bmp = decodeBase64Bitmap(b64)
            if (bmp != null) {
                _currentAvatar.value = bmp
                saveBitmapToFile(bmp, userFile)
                return@withContext
            }
        }

        // 3. Check Supabase Auth user_metadata for cloud-backed avatar across app-data clears
        try {
            val supabase = SupabaseInstance.client
            val authUser = supabase?.auth?.currentUserOrNull()
            if (email.isNullOrBlank() || authUser?.email.equals(email, ignoreCase = true)) {
                val meta = authUser?.userMetadata
                val remoteB64 = meta?.get("avatar_base64")?.toString()?.trim('"')?.takeIf {
                    it.isNotBlank() && it != "null"
                }
                if (!remoteB64.isNullOrBlank()) {
                    val bmp = decodeBase64Bitmap(remoteB64)
                    if (bmp != null) {
                        _currentAvatar.value = bmp
                        saveBitmapToFile(bmp, userFile)
                        prefs.edit()
                            .putString("${KEY_AVATAR_B64}_$key", remoteB64)
                            .apply()
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    suspend fun saveAvatarFromFile(context: Context, file: File, email: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || file.length() <= 0L) return@withContext false
            val bytes = file.readBytes()
            val rawBitmap = decodeSampledBitmap(bytes, 1024) ?: return@withContext false
            saveAvatarFromBitmap(context, rawBitmap, email)
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun saveAvatarFromUri(context: Context, uri: Uri, email: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext false
            val rawBitmap = decodeSampledBitmap(bytes, 1024)
                ?: return@withContext false
            saveAvatarFromBitmap(context, rawBitmap, email)
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun saveAvatarFromBitmap(context: Context, rawBitmap: Bitmap, email: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val cropped = centerCropSquare(rawBitmap, 420)
            val key = sanitizeEmailKey(email)
            loadedEmailKey = key
            _currentAvatar.value = cropped

            // Save to user-specific file
            saveBitmapToFile(cropped, getAvatarFile(context, email))

            // Encode compact version for SharedPreferences and Supabase Auth metadata
            val compact = centerCropSquare(cropped, 200)
            val b64 = encodeBitmapBase64(compact, quality = 80)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("${KEY_AVATAR_B64}_$key", b64)
                .apply()

            // Sync to Supabase Auth metadata in background
            try {
                SupabaseInstance.client?.auth?.updateUser {
                    data = buildJsonObject {
                        put("avatar_base64", JsonPrimitive(b64))
                    }
                }
            } catch (_: Throwable) {}

            true
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun clearAvatar(context: Context, email: String?) = withContext(Dispatchers.IO) {
        val key = sanitizeEmailKey(email)
        _currentAvatar.value = null
        try {
            getAvatarFile(context, email).delete()
        } catch (_: Throwable) {}

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove("${KEY_AVATAR_B64}_$key")
            .remove(KEY_AVATAR_B64)
            .apply()

        try {
            SupabaseInstance.client?.auth?.updateUser {
                data = buildJsonObject {
                    put("avatar_base64", JsonPrimitive(""))
                }
            }
        } catch (_: Throwable) {}
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
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
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

    LaunchedEffect(userEmail) {
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

    LaunchedEffect(userEmail) {
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
