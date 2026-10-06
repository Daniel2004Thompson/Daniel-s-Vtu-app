package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.HeadsetMic
import com.example.ui.components.ContactSupportCard
import com.example.ui.components.ContactSupportDialog
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.TransactionEntity
import com.example.data.model.InAppNotification
import com.example.data.model.SupabaseUser
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.VtuCyan
import com.example.ui.theme.VtuGoldAccent
import com.example.ui.theme.VtuGreenDark
import com.example.ui.theme.VtuGreenPrimary
import com.example.ui.theme.VtuGreenSecondary
import com.example.ui.theme.VtuNavyCard
import com.example.ui.theme.VtuNavyPrimary
import com.example.ui.theme.VtuNavySurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vtu.app.wallet.WalletViewModel
import com.example.auth.SupabaseInstance
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest

@Composable
fun HomeScreen(
    walletBalance: Double,
    cashbackBalance: Double,
    isBiometricEnabled: Boolean,
    notifications: List<InAppNotification>,
    recentTransactions: List<TransactionEntity>,
    currentUser: SupabaseUser? = null,
    onNavigateToAirtime: () -> Unit,
    onNavigateToData: () -> Unit,
    onNavigateToBills: (String) -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToDynamicAccount: () -> Unit = {},
    onNavigateToDevelopersForum: () -> Unit = {},
    onOpenFundWallet: () -> Unit,
    onSelectTransactionReceipt: (TransactionEntity) -> Unit,
    walletViewModel: WalletViewModel = viewModel(),
    onUpdateWalletBalance: ((Double) -> Unit)? = null,
    onUserPhoneFetched: ((String, String?) -> Unit)? = null,
    onRefreshRemoteProfile: (() -> Unit)? = null
) {
    var isBalanceVisible by remember { mutableStateOf(true) }
    val unreadCount = notifications.count { !it.isRead }

    var showSupportDialog by remember { mutableStateOf(false) }
    var isRealtimeReloading by remember { mutableStateOf(false) }
    val reloadRotation by animateFloatAsState(
        targetValue = if (isRealtimeReloading) 360f else 0f,
        animationSpec = tween(durationMillis = 650),
        label = "reloadRotation"
    )
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val liveBalance by walletViewModel.walletBalance.collectAsState()

    var liveUserPhone by remember(currentUser?.id, currentUser?.phone) {
        mutableStateOf(
            com.example.data.repository.VtuRepository.sanitizeRealPhone(currentUser?.phone)
        )
    }
    var liveUserFullName by remember(currentUser?.id, currentUser?.fullName) {
        mutableStateOf(
            com.example.data.repository.VtuRepository.sanitizeFullName(currentUser?.fullName)
        )
    }

    LaunchedEffect(currentUser?.id) {
        onRefreshRemoteProfile?.invoke()
        val supabase = SupabaseInstance.client
        val authUser = try { supabase?.auth?.currentUserOrNull() } catch (_: Throwable) { null }
        val userId = authUser?.id
            ?: currentUser?.id?.takeIf { it.isNotBlank() && it != "usr_guest" && it != "usr_default" }
        val isValidUuid = !userId.isNullOrBlank() && userId.length == 36 && userId.count { it == '-' } == 4

        if (!userId.isNullOrBlank() && isValidUuid) {
            walletViewModel.loadInitialBalance(userId)
            walletViewModel.subscribeToWalletBalance(userId) { newBalance ->
                onUpdateWalletBalance?.invoke(newBalance)
            }
        } else {
            walletViewModel.resetState()
            liveUserPhone = null
            liveUserFullName = ""
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
                    liveUserPhone = resolvedPhone
                    liveUserFullName = resolvedName
                    if (resolvedPhone != null || resolvedName.isNotBlank()) {
                        onUserPhoneFetched?.invoke(resolvedPhone ?: "", resolvedName)
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    LaunchedEffect(liveBalance) {
        liveBalance?.let { bigDec ->
            onUpdateWalletBalance?.invoke(bigDec.toDouble())
        }
    }

    val displayBalance = liveBalance?.toDouble() ?: walletBalance
    val displayPhone = liveUserPhone ?: com.example.data.repository.VtuRepository.sanitizeRealPhone(currentUser?.phone)
    val detectedProvider = com.example.data.model.NetworkProvider.detectFromPhone(displayPhone ?: "")
    val detectedProviderName = com.example.data.model.NetworkProvider.detectProviderName(displayPhone)

    val displayFullName = com.example.data.repository.VtuRepository.sanitizeFullName(
        liveUserFullName.takeIf { it.isNotBlank() } ?: currentUser?.fullName,
        currentUser?.email
    )
    val initials = displayFullName.split(" ")
        .filter { it.isNotBlank() }
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .ifEmpty { "U" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Top Bar Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onNavigateToProfile() }
                ) {
                    com.example.ui.components.UserProfileAvatar(
                        initials = initials,
                        userEmail = currentUser?.email,
                        size = 46.dp,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (displayFullName.isNotBlank()) "Hello, $displayFullName" else "Hello",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (isBiometricEnabled) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Biometric Secured",
                                    tint = VtuGreenPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        if (!displayPhone.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (detectedProvider != null) {
                                    com.example.ui.components.NetworkLogoIcon(
                                        provider = detectedProvider,
                                        size = 18.dp,
                                        isCircular = true
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                }
                                Text(
                                    text = displayPhone,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("dashboard_user_phone")
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = detectedProvider?.brandColor ?: MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                                            text = detectedProviderName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = detectedProvider?.textColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.testTag("dashboard_user_provider")
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "VTU & Utility Services",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Realtime Reload Circular Button
                    IconButton(
                        onClick = {
                            if (!isRealtimeReloading) {
                                isRealtimeReloading = true
                                coroutineScope.launch {
                                    onRefreshRemoteProfile?.invoke()
                                    val uid = currentUser?.id ?: SupabaseInstance.client?.auth?.currentUserOrNull()?.id
                                    walletViewModel.refreshWalletRealtime(uid) { newBal ->
                                        Toast.makeText(context, "Wallet updated: ₦%,.2f".format(newBal), Toast.LENGTH_SHORT).show()
                                    }
                                    kotlinx.coroutines.delay(700)
                                    isRealtimeReloading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .testTag("realtime_reload_circular_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Realtime Reload",
                            tint = if (isRealtimeReloading) VtuGreenPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(reloadRotation)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = { showSupportDialog = true },
                        modifier = Modifier.testTag("support_icon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HeadsetMic,
                            contentDescription = "Contact Support",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onNavigateToNotifications,
                        modifier = Modifier.testTag("notifications_icon_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge(containerColor = VtuGoldAccent) {
                                        Text(unreadCount.toString(), color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }

        // Hero Wallet Balance Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .testTag("wallet_balance_card"),
                    colors = CardDefaults.cardColors(containerColor = VtuNavyPrimary),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Box {
                        // Background subtle gradient artwork
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            VtuNavyPrimary,
                                            VtuNavySurface,
                                            VtuGreenDark.copy(alpha = 0.6f)
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Wallet,
                                        contentDescription = null,
                                        tint = VtuGreenSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Available Balance",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    IconButton(
                                        onClick = { isBalanceVisible = !isBalanceVisible },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isBalanceVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Toggle Balance",
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (!isRealtimeReloading) {
                                                isRealtimeReloading = true
                                                coroutineScope.launch {
                                                    onRefreshRemoteProfile?.invoke()
                                                    val uid = currentUser?.id ?: SupabaseInstance.client?.auth?.currentUserOrNull()?.id
                                                    walletViewModel.refreshWalletRealtime(uid) { newBal ->
                                                        Toast.makeText(context, "Balance refreshed: ₦%,.2f".format(newBal), Toast.LENGTH_SHORT).show()
                                                    }
                                                    kotlinx.coroutines.delay(700)
                                                    isRealtimeReloading = false
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(24.dp).testTag("balance_card_reload_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Realtime Balance Reload",
                                            tint = if (isRealtimeReloading) VtuGreenSecondary else Color.White.copy(alpha = 0.75f),
                                            modifier = Modifier
                                                .size(16.dp)
                                                .rotate(reloadRotation)
                                        )
                                    }
                                }

                                // Cashback pill
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(VtuGoldAccent.copy(alpha = 0.2f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Bonus: ₦%,.2f".format(cashbackBalance),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VtuGoldAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = if (isBalanceVisible) "₦%,.2f".format(displayBalance) else "₦ • • • • • •",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = onOpenFundWallet,
                                        colors = ButtonDefaults.buttonColors(containerColor = VtuGreenPrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.testTag("fund_wallet_button")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("+ Fund Wallet", fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { showSupportDialog = true },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color.White
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier.testTag("support_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.HeadsetMic,
                                            contentDescription = "Support",
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Support",
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = VtuGreenSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Protected",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dedicated Flutterwave Virtual Account Card
        item {
            val context = LocalContext.current
            val rawVa = currentUser?.virtualAccountNumber?.trim()
            val cleanVa = rawVa?.takeIf {
                !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.filter { c -> c.isDigit() }.length in 10..12
            }
            val isVaVerified = cleanVa != null
            val vaNumber = if (isVaVerified) cleanVa!! else "No permanent account yet. Verify your NIN to create one"
            val rawBank = currentUser?.virtualBankName?.trim()?.takeIf {
                !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.isNotBlank()
            }
            val vaBank = if (isVaVerified) (rawBank ?: "") else "Tap to enter NIN & get permanent account"
            val vaAccountName = com.example.data.repository.VtuRepository.resolveAccountHolderName(
                rawAccountName = currentUser?.virtualAccountName,
                fullName = displayFullName,
                email = currentUser?.email
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isVaVerified) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) else Color(0xFFFFF3E0)
                ),
                shape = RoundedCornerShape(14.dp),
                border = if (!isVaVerified) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFB9129).copy(alpha = 0.6f)) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clickable { onOpenFundWallet() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isVaVerified) Color(0xFFFB9129) else Color(0xFFE65100)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isVaVerified) "FW" else "KYC",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isVaVerified) {
                                    Text(
                                        text = "Permanent Acct: ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = vaNumber,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isVaVerified) MaterialTheme.colorScheme.onSurface else Color(0xFFE65100)
                                )
                            }
                            if (vaBank.isNotBlank()) {
                                Text(
                                    text = vaBank,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isVaVerified) Color(0xFFFB9129) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (isVaVerified && vaAccountName.isNotBlank()) {
                                Text(
                                    text = "Name: $vaAccountName",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VtuGreenPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    if (isVaVerified) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Permanent Account", vaNumber)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Permanent account $vaNumber copied!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy account number",
                                tint = VtuGreenPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = { onOpenFundWallet() },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFB9129),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Activate", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Dynamic Virtual Account Action (Instant Dedicated Account)
            Spacer(modifier = Modifier.height(10.dp))
            val rawDyn = currentUser?.dynamicAccountNumber?.trim()
            val cleanDyn = rawDyn?.takeIf {
                !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.filter { c -> c.isDigit() }.length in 10..12
            }
            val hasDynamicAcc = cleanDyn != null
            val dynNumber = cleanDyn ?: ""
            val dynBank = currentUser?.dynamicBankName?.takeIf { !it.isNullOrBlank() && !it.equals("null", ignoreCase = true) } ?: ""

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF00B875).copy(alpha = 0.12f)
                ),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00B875).copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clickable { onNavigateToDynamicAccount() }
                    .testTag("home_dynamic_account_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00B875)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Dynamic Virtual Account",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (hasDynamicAcc) "Dynamic Acct: $dynNumber" else "Create Dynamic Account",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFF00B875),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (hasDynamicAcc) "ACTIVE" else "INSTANT",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (hasDynamicAcc) "$dynBank • Direct Bank transfer • Live Auto sync" else "Live Flutterwave edge account • Direct Bank transfer • Auto sync",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Go to Dynamic Account",
                        tint = Color(0xFF00B875),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Quick Services Grid Section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Quick Services",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ServiceCard(
                        title = "Airtime",
                        subtitle = "Instant 2% Off",
                        icon = Icons.Default.PhoneAndroid,
                        tint = VtuGreenPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("service_airtime"),
                        onClick = onNavigateToAirtime
                    )
                    ServiceCard(
                        title = "Data Bundles",
                        subtitle = "SME & Corporate",
                        icon = Icons.Default.Wifi,
                        tint = VtuCyan,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("service_data"),
                        onClick = onNavigateToData
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ServiceCard(
                        title = "Electricity",
                        subtitle = "Prepaid Token",
                        icon = Icons.Default.Bolt,
                        tint = VtuGoldAccent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("service_electricity"),
                        onClick = { onNavigateToBills("ELECTRICITY") }
                    )
                    ServiceCard(
                        title = "Cable TV",
                        subtitle = "DSTV, GOtv",
                        icon = Icons.Default.Tv,
                        tint = Color(0xFFE11D48),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("service_cable_tv"),
                        onClick = { onNavigateToBills("CABLE_TV") }
                    )
                    ServiceCard(
                        title = "Education",
                        subtitle = "WAEC & JAMB",
                        icon = Icons.Default.School,
                        tint = Color(0xFF7C3AED),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("service_education"),
                        onClick = { onNavigateToBills("EDUCATION") }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Developer Forum & API Key Banner Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = VtuNavyPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToDevelopersForum() }
                        .testTag("service_developer_api")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(VtuGreenPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = "Developer API",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Developer Hub & Forum",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = VtuGoldAccent,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "API KEYS",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Generate your secret API key • Webhooks & Forum",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open Developer Forum",
                            tint = VtuGreenPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Promotional Banner / Special Offer
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_vtu_banner),
                            contentDescription = "Promo Banner",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Zero Transaction Fees",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Enjoy instant biometric authorizations, real-time receipt generation & cashback.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Recent Transactions Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = onNavigateToTransactions) {
                    Text(
                        text = "See All",
                        color = VtuGreenPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = VtuGreenPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Recent Transactions List
        if (recentTransactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Wallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No transactions yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Recharge airtime or buy data to see records here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            items(recentTransactions) { tx ->
                TransactionRowItem(
                    transaction = tx,
                    onClick = { onSelectTransactionReceipt(tx) }
                )
            }
        }

        // Dedicated Contact Support Card
        item {
            ContactSupportCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            )
        }
    }

    if (showSupportDialog) {
        ContactSupportDialog(onDismiss = { showSupportDialog = false })
    }
}

@Composable
fun ServiceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TransactionRowItem(
    transaction: TransactionEntity,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        .format(Date(transaction.timestamp))

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("tx_item_${transaction.reference}"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val txNetwork = com.example.data.model.NetworkProvider.detectFromTextOrPhone(
                    transaction.provider,
                    transaction.recipient
                )
                val txExam = if (transaction.serviceType == "EDUCATION") {
                    com.example.data.model.VtuCatalog.findExamByProvider(transaction.provider)
                } else null
                val txCable = if (transaction.serviceType == "CABLE_TV") {
                    com.example.data.model.VtuCatalog.findCableByProvider(transaction.provider)
                } else null
                val txDisco = if (transaction.serviceType == "ELECTRICITY") {
                    com.example.data.model.VtuCatalog.findDiscoByProvider(transaction.provider)
                } else null
                if (txNetwork != null && (transaction.serviceType == "AIRTIME" || transaction.serviceType == "DATA")) {
                    com.example.ui.components.NetworkLogoIcon(
                        provider = txNetwork,
                        size = 44.dp,
                        isCircular = true
                    )
                } else if (txExam != null) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, txExam.brandColor.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = txExam.logoRes),
                            contentDescription = txExam.shortName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (txCable != null) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, txCable.brandColor.copy(alpha = 0.35f), CircleShape)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = txCable.logoRes),
                            contentDescription = txCable.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (txDisco != null) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, txDisco.brandColor.copy(alpha = 0.35f), CircleShape)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = txDisco.logoRes),
                            contentDescription = txDisco.shortName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                when (transaction.serviceType) {
                                    "AIRTIME" -> VtuGreenPrimary.copy(alpha = 0.12f)
                                    "DATA" -> VtuCyan.copy(alpha = 0.12f)
                                    "ELECTRICITY" -> VtuGoldAccent.copy(alpha = 0.12f)
                                    "CABLE_TV" -> Color(0xFFE11D48).copy(alpha = 0.12f)
                                    "EDUCATION" -> Color(0xFF7C3AED).copy(alpha = 0.12f)
                                    else -> VtuGreenPrimary.copy(alpha = 0.12f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (transaction.serviceType) {
                                "AIRTIME" -> Icons.Default.PhoneAndroid
                                "DATA" -> Icons.Default.Wifi
                                "ELECTRICITY" -> Icons.Default.Bolt
                                "CABLE_TV" -> Icons.Default.Tv
                                "EDUCATION" -> Icons.Default.School
                                else -> Icons.Default.Wallet
                            },
                            contentDescription = null,
                            tint = when (transaction.serviceType) {
                                "AIRTIME" -> VtuGreenPrimary
                                "DATA" -> VtuCyan
                                "ELECTRICITY" -> VtuGoldAccent
                                "CABLE_TV" -> Color(0xFFE11D48)
                                "EDUCATION" -> Color(0xFF7C3AED)
                                else -> VtuGreenPrimary
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "${transaction.provider} ${transaction.serviceType.replace('_', ' ')}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${transaction.recipient} • $dateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (transaction.serviceType == "WALLET_FUNDING") "+₦%,.2f".format(transaction.amount) else "-₦%,.2f".format(transaction.amount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (transaction.serviceType == "WALLET_FUNDING") StatusSuccess else MaterialTheme.colorScheme.onSurface
                    )
                    val isSuccess = transaction.status.equals("SUCCESSFUL", ignoreCase = true)
                    Text(
                        text = transaction.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSuccess) StatusSuccess else Color(0xFFE53935),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (onDelete != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete transaction",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
