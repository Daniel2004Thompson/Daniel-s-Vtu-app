package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import com.example.data.model.SupabaseUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.ui.theme.VtuCyan
import com.example.ui.theme.VtuGoldAccent
import com.example.ui.theme.VtuGreenDark
import com.example.ui.theme.VtuGreenPrimary
import com.example.ui.theme.VtuNavyPrimary
import com.example.ui.components.ContactSupportCard
import com.example.util.BiometricAuthHelper
import com.vtu.app.wallet.PermanentAccountSection
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSecurityScreen(
    isBiometricEnabled: Boolean,
    isAppLockEnabled: Boolean,
    isNotificationsEnabled: Boolean,
    currentUser: SupabaseUser? = null,
    onToggleBiometric: (Boolean) -> Unit,
    onToggleAppLock: (Boolean) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onUpdatePin: (String) -> Unit,
    onNavigateToChangeEmail: () -> Unit = {},
    onNavigateToResetPassword: () -> Unit = {},
    onNavigateToLogout: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    onNavigateToDevelopersForum: () -> Unit = {},
    userAuthToken: String = "",
    supabaseAnonKey: String = "",
    onPermanentAccountCreated: ((String, String) -> Unit)? = null,
    onPermanentAccountReset: (() -> Unit)? = null,
    onUserPhoneFetched: ((String, String?) -> Unit)? = null,
    onRefreshRemoteProfile: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var currentPinInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    var liveFetchedPhone by remember(currentUser?.id, currentUser?.phone) {
        mutableStateOf(com.example.data.repository.VtuRepository.sanitizeRealPhone(currentUser?.phone))
    }
    var liveFetchedName by remember(currentUser?.id, currentUser?.fullName) {
        mutableStateOf(com.example.data.repository.VtuRepository.sanitizeFullName(currentUser?.fullName))
    }

    androidx.compose.runtime.LaunchedEffect(currentUser?.id) {
        onRefreshRemoteProfile?.invoke()
        val supabase = com.example.auth.SupabaseInstance.client
        val authUser = try { supabase?.auth?.currentUserOrNull() } catch (_: Throwable) { null }
        val userId = authUser?.id
            ?: currentUser?.id?.takeIf { it.isNotBlank() && it != "usr_guest" && it != "usr_default" }
        val isValidUuid = !userId.isNullOrBlank() && userId.length == 36 && userId.count { it == '-' } == 4
        if (!isValidUuid || userId.isNullOrBlank()) {
            liveFetchedPhone = null
            liveFetchedName = ""
            return@LaunchedEffect
        }

        if (supabase != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val authMetaName = try {
                        authUser?.userMetadata?.get("full_name")?.let {
                            (it as? kotlinx.serialization.json.JsonPrimitive)?.content ?: it.toString().trim('"')
                        }?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    } catch (_: Throwable) { null }

                    val data = try {
                        supabase.postgrest.from("users").select {
                            filter { eq("id", userId) }
                            limit(1)
                        }.decodeSingleOrNull<kotlinx.serialization.json.JsonObject>()
                    } catch (_: Throwable) { null }

                    val dbPhone = com.example.data.repository.VtuRepository.sanitizeRealPhone(
                        data?.get("phone")?.let {
                            (it as? kotlinx.serialization.json.JsonPrimitive)?.content ?: it.toString().trim('"')
                        }
                    )
                    val dbFullName = data?.get("full_name")?.let {
                        (it as? kotlinx.serialization.json.JsonPrimitive)?.content ?: it.toString().trim('"')
                    }?.takeIf { !it.equals("null", ignoreCase = true) && it.isNotBlank() }

                    val resolvedPhone = dbPhone
                    val resolvedName = com.example.data.repository.VtuRepository.sanitizeFullName(
                        authMetaName ?: dbFullName ?: currentUser?.fullName,
                        currentUser?.email ?: authUser?.email
                    )
                    liveFetchedPhone = resolvedPhone
                    liveFetchedName = resolvedName
                    if (resolvedPhone != null || resolvedName.isNotBlank()) {
                        onUserPhoneFetched?.invoke(resolvedPhone ?: "", resolvedName)
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    val userName = com.example.data.repository.VtuRepository.sanitizeFullName(
        liveFetchedName.takeIf { it.isNotBlank() } ?: currentUser?.fullName,
        currentUser?.email
    )
    val userEmail = currentUser?.email ?: ""
    val userPhone = liveFetchedPhone ?: com.example.data.repository.VtuRepository.sanitizeRealPhone(currentUser?.phone)
    val detectedProvider = com.example.data.model.NetworkProvider.detectFromPhone(userPhone ?: "")
    val providerLabel = com.example.data.model.NetworkProvider.detectProviderName(userPhone)
    val initials = userName.split(" ")
        .filter { it.isNotBlank() }
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .ifEmpty { "U" }

    fun testBiometricNow() {
        val activity = context as? FragmentActivity
        if (activity != null) {
            BiometricAuthHelper.authenticate(
                activity = activity,
                title = "Biometric Test",
                subtitle = "Verifying biometric scanner configuration",
                negativeButtonText = "Cancel",
                onSuccess = {
                    Toast.makeText(context, "Biometric verified successfully!", Toast.LENGTH_SHORT).show()
                },
                onError = { code, msg ->
                    Toast.makeText(context, "Biometric info: $msg", Toast.LENGTH_SHORT).show()
                },
                onFailed = {
                    Toast.makeText(context, "Biometric not recognized. Try again.", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Security", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .testTag("profile_security_screen"),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Profile Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp)),
                colors = CardDefaults.cardColors(containerColor = VtuNavyPrimary)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    com.example.ui.components.EditableProfileAvatar(
                        initials = initials,
                        userEmail = userEmail,
                        size = 76.dp,
                        fontSize = 24.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = userName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Text(
                        text = userEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )

                    if (!userPhone.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (detectedProvider != null) {
                                com.example.ui.components.NetworkLogoIcon(
                                    provider = detectedProvider,
                                    size = 20.dp,
                                    isCircular = true
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = userPhone,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.testTag("profile_user_phone")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = (detectedProvider?.brandColor ?: Color.White.copy(alpha = 0.18f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    if (detectedProvider != null) {
                                        com.example.ui.components.NetworkLogoIcon(
                                            provider = detectedProvider,
                                            size = 14.dp,
                                            isCircular = true
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = providerLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = detectedProvider?.textColor ?: Color.White,
                                        modifier = Modifier.testTag("profile_user_provider")
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dedicated Virtual Account
                    Surface(
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                val rawVa = currentUser?.virtualAccountNumber?.trim()
                                val cleanVa = rawVa?.takeIf {
                                    !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.filter { c -> c.isDigit() }.length in 10..12
                                }
                                val isVaVerified = cleanVa != null
                                val rawBank = currentUser?.virtualBankName?.trim()?.takeIf {
                                    !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.isNotBlank()
                                }
                                val bankName = rawBank ?: ""
                                val acctName = com.example.data.repository.VtuRepository.resolveAccountHolderName(
                                    rawAccountName = currentUser?.virtualAccountName,
                                    fullName = userName,
                                    email = currentUser?.email
                                )
                                val displayVa = if (isVaVerified) {
                                    if (bankName.isNotBlank()) "$cleanVa ($bankName)" else cleanVa!!
                                } else {
                                    "No permanent account yet. Verify your NIN to create one"
                                }
                                Text(
                                    text = "Dedicated Virtual Account",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = displayVa,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isVaVerified) Color.White else Color(0xFFFFCC80)
                                )
                                if (isVaVerified && acctName.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Acct Name: $acctName",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VtuGreenPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = VtuGreenPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Permanent Account Section (NIN-verified Dedicated Account)
            Column {
                Text(
                    text = "Permanent Funding Account (NIN)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Verify your 11-digit National Identity Number (NIN) to create or manage your permanent dedicated funding account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                PermanentAccountSection(
                    currentUser = currentUser,
                    onAccountCreated = onPermanentAccountCreated,
                    onAccountReset = onPermanentAccountReset
                )
            }

            // Biometric & Security Settings Section
            Column {
                Text(
                    text = "Biometric & Security Controls",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Biometric Authorization toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(VtuGreenPrimary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = null,
                                        tint = VtuGreenPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Biometric Authorization",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Require Fingerprint for transactions",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isBiometricEnabled,
                                onCheckedChange = onToggleBiometric,
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VtuGreenPrimary),
                                modifier = Modifier.testTag("toggle_biometric_switch")
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )

                        // App Lock toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(VtuCyan.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = VtuCyan,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "App Lock on Launch",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Lock app when closed or minimized",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isAppLockEnabled,
                                onCheckedChange = onToggleAppLock,
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VtuCyan),
                                modifier = Modifier.testTag("toggle_app_lock_switch")
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )

                        // Transaction Notifications toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(VtuGoldAccent.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = VtuGoldAccent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Real-Time Notifications",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Instant alerts with tokens & receipts",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isNotificationsEnabled,
                                onCheckedChange = onToggleNotifications,
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VtuGreenPrimary),
                                modifier = Modifier.testTag("toggle_notifications_switch")
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )

                        // Change 4-digit PIN row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showChangePinDialog = true }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Change Security PIN",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Fallback 4-digit authorization code",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(Icons.Default.Edit, contentDescription = "Edit PIN", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Account & Credential Management Section
            Column {
                Text(
                    text = "Account & Credentials",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Change Email Option
                        Surface(
                            onClick = onNavigateToChangeEmail,
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_change_email_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(VtuGreenPrimary.copy(alpha = 0.12f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Email,
                                            contentDescription = null,
                                            tint = VtuGreenPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Change Email Address",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = userEmail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Change email",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Reset / Change Password Option
                        Surface(
                            onClick = onNavigateToResetPassword,
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_reset_password_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(VtuNavyPrimary.copy(alpha = 0.12f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LockReset,
                                            contentDescription = null,
                                            tint = VtuNavyPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Reset Account Password",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Request recovery link or enter reset OTP",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Reset password",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .graphicsLayer(rotationZ = 180f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Developer Portal & API Key Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, VtuGreenPrimary.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToDevelopersForum() }
                    .testTag("profile_developer_hub_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(VtuGreenPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Developer Hub & Forum",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            androidx.compose.material3.Surface(
                                color = VtuGoldAccent.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "API",
                                    color = VtuGoldAccent,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Manage your API key, webhooks & community discussions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Developer Forum",
                        tint = VtuGreenPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dedicated Contact Support Section
            ContactSupportCard(modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(16.dp))

            // Biometric Test Button
            OutlinedButton(
                onClick = { testBiometricNow() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("test_biometric_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = VtuGreenPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test Biometric Sensor Dialog", fontWeight = FontWeight.SemiBold)
            }

            // Supabase Account & Sign Out Button
            Button(
                onClick = onNavigateToLogout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("profile_logout_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Log out",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Session & Sign Out",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Delete Account Permanently Button
            com.vtu.app.wallet.DeleteAccountButton(
                userAuthToken = userAuthToken,
                supabaseAnonKey = supabaseAnonKey,
                userId = currentUser?.id,
                email = currentUser?.email,
                onAccountDeleted = onDeleteAccount,
                modifier = Modifier.fillMaxWidth()
            )

            // About & Version info
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Daniel VTU • Version 1.0.0",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Secured with Android Biometrics & Room Persistence",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }

    // Change PIN Dialog
    if (showChangePinDialog) {
        AlertDialog(
            onDismissRequest = {
                showChangePinDialog = false
                currentPinInput = ""
                newPinInput = ""
                pinError = null
            },
            title = { Text("Update Security PIN", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Default PIN is 1234. Enter a new 4-digit PIN for your wallet transactions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPinInput = it },
                        label = { Text("New 4-Digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError != null) {
                        Text(pinError ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPinInput.length == 4) {
                            onUpdatePin(newPinInput)
                            showChangePinDialog = false
                            newPinInput = ""
                            pinError = null
                            Toast.makeText(context, "PIN updated successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            pinError = "PIN must be exactly 4 digits"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VtuGreenPrimary)
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
