package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.SupabaseUser
import com.example.ui.theme.VtuGoldAccent
import com.example.ui.theme.VtuGreenDark
import com.example.ui.theme.VtuGreenPrimary
import com.example.ui.theme.VtuNavyPrimary
import com.vtu.app.wallet.DynamicAccountUiState
import com.vtu.app.wallet.DynamicAccountViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicAccountScreen(
    currentUser: SupabaseUser?,
    supabaseAnonKey: String,
    onBack: () -> Unit,
    onAccountDetailsUpdated: (accountNumber: String, bankName: String) -> Unit = { _, _ -> },
    onRefreshBalance: () -> Unit = {},
    viewModel: DynamicAccountViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Local state for account details (strictly isolated to Dynamic Account, never NIN/Permanent account)
    var accountNumber by remember(currentUser?.id) {
        mutableStateOf(
            currentUser?.dynamicAccountNumber?.trim()?.takeIf {
                !it.equals("null", ignoreCase = true) && !it.equals("nil", ignoreCase = true) && it.filter { c -> c.isDigit() }.length in 10..12
            } ?: ""
        )
    }
    var bankName by remember(currentUser?.id) {
        val raw = currentUser?.dynamicBankName?.trim()
        mutableStateOf(
            if (raw.isNullOrBlank() || raw.equals("null", ignoreCase = true)) "" else raw
        )
    }
    var accountName by remember(currentUser?.id) {
        mutableStateOf("Wallet Topup")
    }
    var selectedAmountText by remember(currentUser?.id) {
        val rawAmt = currentUser?.dynamicAccountAmount
        mutableStateOf(
            if (rawAmt != null && rawAmt > 0) String.format(java.util.Locale.US, "%.0f", rawAmt) else "100"
        )
    }

    LaunchedEffect(currentUser?.id) {
        viewModel.onUserChanged(currentUser?.id)
        if (currentUser?.id.isNullOrBlank()) {
            accountNumber = ""
            bankName = ""
            accountName = "Wallet Topup"
        }
    }

    // Keep state updated if currentUser.dynamicAccountNumber loads or changes
    LaunchedEffect(currentUser?.id, currentUser?.dynamicAccountNumber) {
        val dyn = currentUser?.dynamicAccountNumber?.trim()
        if (!dyn.isNullOrBlank() && dyn.filter { it.isDigit() }.length in 10..12) {
            accountNumber = dyn
            bankName = currentUser.dynamicBankName?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: ""
            accountName = "Wallet Topup"
            currentUser.dynamicAccountAmount?.let { amt ->
                selectedAmountText = String.format(java.util.Locale.US, "%.0f", amt)
            }
        } else {
            accountNumber = ""
            bankName = ""
            accountName = "Wallet Topup"
        }
    }

    // When dynamic account is successfully created via Edge Function:
    LaunchedEffect(uiState) {
        if (uiState is DynamicAccountUiState.Success) {
            val successState = uiState as DynamicAccountUiState.Success
            accountNumber = successState.accountNumber
            bankName = successState.bankName
            accountName = "Wallet Topup"
            if (successState.amount != null) {
                selectedAmountText = String.format(java.util.Locale.US, "%.0f", successState.amount)
            }
            onAccountDetailsUpdated(successState.accountNumber, successState.bankName)
            Toast.makeText(context, "Dynamic Account Created Successfully!", Toast.LENGTH_LONG).show()
        }
    }

    val userId = currentUser?.id ?: ""
    val userEmail = currentUser?.email ?: ""
    val hasAccount = accountNumber.isNotBlank() && !accountNumber.equals("null", ignoreCase = true) && accountNumber.filter { it.isDigit() }.length in 10..12

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Dynamic Virtual Account",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("dynamic_account_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onRefreshBalance()
                            Toast.makeText(context, "Checking real-time balance...", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Balance",
                            tint = VtuGreenPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Banner Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    VtuGreenDark,
                                    VtuNavyPrimary
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = VtuGoldAccent.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "⚡ INSTANT GENERATION",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = VtuGoldAccent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF00C853).copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00E676))
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Realtime Live",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Instant Flutterwave Dynamic Account",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Connected directly to your Supabase backend. When money is transferred from any Nigerian bank app, your dashboard balance updates instantly in real-time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // User Credentials Badge
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = VtuGreenPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Linked Profile",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Email:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = userEmail,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "User ID:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = userId.take(18) + (if (userId.length > 18) "..." else ""),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // IF ACCOUNT IS GENERATED -> DISPLAY ACCOUNT DETAILS
            if (hasAccount) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, VtuGreenPrimary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = VtuGreenPrimary.copy(alpha = 0.15f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = VtuGreenPrimary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "YOUR DYNAMIC DEDICATED ACCOUNT",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Account Number with 1-tap Copy
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Account Number",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = accountNumber,
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.5.sp
                                        ),
                                        maxLines = 1,
                                        softWrap = false,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Account Number", accountNumber)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Account number copied: $accountNumber", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = VtuGreenPrimary),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("copy_dynamic_account_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Copy",
                                        maxLines = 1,
                                        softWrap = false,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Bank Name & Account Name
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Bank Name",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = bankName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Account Name",
                                    maxLines = 1,
                                    softWrap = false,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Wallet Topup",
                                    maxLines = 1,
                                    softWrap = false,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        // Target Top-up Amount
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = VtuGreenPrimary.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Target Top-up Amount:",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "₦$selectedAmountText",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = VtuGreenDark
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Button: Copy Account Number
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Account Number", accountNumber)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Account number copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("copy_account_number_button")
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Copy Account Number",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    onClick = {
                        accountNumber = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Generate for a Different Amount", style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Realtime Sync Status Note
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE8F5E9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Realtime Listener Active: As soon as Flutterwave processes your transfer, your dashboard wallet balance reflects the funds automatically without needing to refresh.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF1B5E20),
                            lineHeight = 17.sp
                        )
                    }
                }
            } else {
                // IF ACCOUNT IS NOT YET GENERATED -> SHOW AMOUNT SELECTION & CREATE BUTTON
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Deposit / Top-up Amount",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Select or enter the amount you plan to deposit. The dynamic virtual account matches this amount to credit your wallet instantly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick Select Chips
                        val quickAmounts = listOf("500", "1000", "2000", "5000")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickAmounts.forEach { amt ->
                                val isSelected = selectedAmountText == amt
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedAmountText = amt },
                                    label = { Text("₦$amt", fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = VtuGreenPrimary,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = selectedAmountText,
                            onValueChange = { input ->
                                val clean = input.filter { it.isDigit() }
                                if (clean.length <= 7) {
                                    selectedAmountText = clean
                                }
                            },
                            label = { Text("Custom Amount (₦)") },
                            prefix = { Text("₦ ", fontWeight = FontWeight.Bold, color = VtuGreenPrimary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                val isLoading = uiState is DynamicAccountUiState.Loading

                Button(
                    onClick = {
                        val parsedAmount = selectedAmountText.toDoubleOrNull() ?: 1000.0
                        val effectiveAmount = if (parsedAmount < 100.0) 1000.0 else parsedAmount
                        viewModel.createVirtualAccount(
                            userId = userId,
                            email = userEmail,
                            anonKey = supabaseAnonKey,
                            amount = effectiveAmount,
                            firstName = currentUser?.fullName?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "",
                            lastName = currentUser?.fullName?.split(" ")?.drop(1)?.joinToString(" ")?.takeIf { it.isNotBlank() } ?: "",
                            onAccountCreated = { acc, bank ->
                                accountNumber = acc
                                bankName = bank
                                onAccountDetailsUpdated(acc, bank)
                            }
                        )
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = VtuGreenPrimary),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("create_dynamic_virtual_account_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Creating Dynamic Account...",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Create Dynamic Account (₦${selectedAmountText.ifBlank { "1000" }})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }

                if (uiState is DynamicAccountUiState.Error) {
                    val errorMsg = (uiState as DynamicAccountUiState.Error).message
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Feature Highlights
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "How Dynamic Accounts Work:",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FeaturePoint(
                            icon = Icons.Default.Bolt,
                            title = "Instant Generation",
                            description = "One-tap creation directly through Supabase Edge Functions with Flutterwave."
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FeaturePoint(
                            icon = Icons.Default.Security,
                            title = "Seamless Verification",
                            description = "Deposit directly without waiting for complex manual paperwork."
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FeaturePoint(
                            icon = Icons.Default.Sensors,
                            title = "Realtime Balance Update",
                            description = "Postgres changes listener updates your dashboard wallet balance automatically."
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturePoint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = VtuGreenPrimary.copy(alpha = 0.12f),
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = VtuGreenPrimary,
                modifier = Modifier
                    .padding(4.dp)
                    .size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}
