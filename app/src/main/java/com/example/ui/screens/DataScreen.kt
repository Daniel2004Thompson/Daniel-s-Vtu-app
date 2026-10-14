package com.example.ui.screens

import android.util.Log
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DataCategory
import com.example.data.model.NetworkProvider
import com.example.data.model.VtuPriceRow
import com.example.data.remote.GsubzVtuService
import com.example.ui.theme.VtuGreenPrimary
import com.example.ui.viewmodel.PendingTransaction

private const val TAG = "DataScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    walletBalance: Double,
    onBack: () -> Unit,
    onRequestPayment: (PendingTransaction) -> Unit
) {
    var selectedNetwork by remember { mutableStateOf(NetworkProvider.AIRTEL) }
    var selectedCategory by remember { mutableStateOf(DataCategory.SME) }
    var phoneNumber by remember { mutableStateOf("") }
    var selectedPlan by remember { mutableStateOf<VtuPriceRow?>(null) }
    var showNetworkPicker by remember { mutableStateOf(false) }
    var isLoadingPrices by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var plansForCurrentTab by remember { mutableStateOf<List<VtuPriceRow>>(emptyList()) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(selectedNetwork, selectedCategory, refreshTrigger) {
        isLoadingPrices = true
        loadError = null
        selectedPlan = null
        try {
            val results = GsubzVtuService.fetchPlansForNetworkAndTab(
                network = selectedNetwork,
                category = selectedCategory
            )
            plansForCurrentTab = results
            loadError = null
        } catch (e: Throwable) {
            val errMsg = e.message?.ifBlank { e.javaClass.simpleName }
                ?: "Unable to load live ${selectedNetwork.displayName} data prices right now"
            Log.e(TAG, "Unable to load live ${selectedNetwork.displayName} data prices right now: $errMsg", e)
            loadError = errMsg
            plansForCurrentTab = emptyList()
        } finally {
            isLoadingPrices = false
        }
    }

    if (showNetworkPicker) {
        com.example.ui.components.NetworkPickerDialog(
            selectedNetwork = selectedNetwork,
            onSelectNetwork = {
                selectedNetwork = it
                selectedPlan = null
            },
            onDismiss = { showNetworkPicker = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buy Data Bundle", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .testTag("data_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Network Selector
            item {
                Column {
                    Text(
                        text = "1. Select Network",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NetworkProvider.entries.forEach { network ->
                            val isSelected = selectedNetwork == network
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) network.brandColor.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) network.brandColor else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        selectedNetwork = network
                                        selectedPlan = null
                                    }
                                    .padding(vertical = 10.dp)
                                    .testTag("data_network_${network.name}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    com.example.ui.components.NetworkLogoIcon(
                                        provider = network,
                                        size = 42.dp,
                                        isCircular = false
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = network.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Phone Number Input
            item {
                Column {
                    Text(
                        text = "2. Recipient Phone",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { input ->
                            if (input.length <= 14) {
                                phoneNumber = input
                                NetworkProvider.detectFromPhone(input)?.let {
                                    selectedNetwork = it
                                    selectedPlan = null
                                }
                            }
                        },
                        placeholder = { Text("Enter destination phone number") },
                        leadingIcon = {
                            com.example.ui.components.PhoneInputNetworkPrefix(
                                selectedNetwork = selectedNetwork,
                                onClick = { showNetworkPicker = true }
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("data_phone_input")
                    )
                }
            }

            // Tabs: SME Data, Corporate, Gifting, Monthly, Daily/Weekly
            item {
                Column {
                    Text(
                        text = "3. Select Package Type",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ScrollableTabRow(
                        selectedTabIndex = DataCategory.entries.indexOf(selectedCategory).coerceAtLeast(0),
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        contentColor = VtuGreenPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("data_category_tabs")
                    ) {
                        DataCategory.entries.forEach { cat ->
                            val isSelected = selectedCategory == cat
                            Tab(
                                selected = isSelected,
                                onClick = {
                                    if (selectedCategory != cat) {
                                        selectedCategory = cat
                                        selectedPlan = null
                                    }
                                },
                                text = {
                                    Text(
                                        text = cat.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                },
                                modifier = Modifier
                                    .testTag("tab_${cat.categoryValue}")
                                    .testTag("data_category_${cat.categoryValue}")
                            )
                        }
                    }
                }
            }

            // Data Bundles List / Empty / Error States
            if (isLoadingPrices) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .testTag("data_loading_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = VtuGreenPrimary,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Fetching live ${selectedNetwork.displayName} data prices...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (loadError != null) {
                // On failure, log the real exception message with Log.e and show it in the error card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .testTag("data_error_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = loadError ?: "Unable to load live ${selectedNetwork.displayName} data prices right now.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { refreshTrigger++ },
                                colors = ButtonDefaults.buttonColors(containerColor = VtuGreenPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("refresh_prices_button")
                            ) {
                                Text("Refresh Live Prices", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else if (plansForCurrentTab.isEmpty()) {
                // If a tab has 0 plans (e.g. 9mobile Daily/Weekly), show "No plans available here" instead of the error.
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .testTag("data_empty_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No plans available here",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(plansForCurrentTab) { plan ->
                    val isSelected = selectedPlan?.plan == plan.plan && selectedPlan?.service_id == plan.service_id
                    val formattedPrice = if (plan.price % 1.0 == 0.0) {
                        "₦%,.0f".format(plan.price)
                    } else {
                        "₦%,.2f".format(plan.price)
                    }
                    val planName = plan.label?.takeIf { it.isNotBlank() } ?: "${plan.plan} Data"
                    val validityText = if (plan.validity_days != null) {
                        if (plan.validity_days == 1) "Validity: 1 Day" else "Validity: ${plan.validity_days} Days"
                    } else {
                        ""
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) VtuGreenPrimary else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { selectedPlan = plan }
                            .testTag("data_plan_${plan.service_id}_${plan.plan}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) VtuGreenPrimary.copy(alpha = 0.08f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                com.example.ui.components.NetworkLogoIcon(
                                    provider = selectedNetwork,
                                    size = 42.dp,
                                    isCircular = true
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = planName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontSize = if (planName.length > 15) 14.sp else 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = selectedCategory.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                    if (validityText.isNotBlank()) {
                                        Text(
                                            text = validityText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formattedPrice,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = VtuGreenPrimary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // Checkout Button
            item {
                Spacer(modifier = Modifier.height(8.dp))
                val activePlan = selectedPlan
                val isValid = phoneNumber.length >= 10 && activePlan != null && activePlan.price > 0.0
                Button(
                    onClick = {
                        val plan = selectedPlan ?: return@Button
                        if (plan.price <= 0.0) return@Button
                        val planDisplayName = plan.label?.takeIf { it.isNotBlank() } ?: "${plan.plan} Data"
                        val pending = PendingTransaction(
                            title = "${selectedNetwork.displayName} Data $planDisplayName",
                            serviceType = "DATA",
                            provider = selectedNetwork.displayName,
                            recipient = phoneNumber.trim(),
                            amount = plan.price,
                            planId = plan.plan, // THAT ROW's plan code (as plan_id)
                            serviceId = plan.service_id, // THAT ROW's service_id
                            details = "$planDisplayName (${selectedCategory.label})"
                        )
                        onRequestPayment(pending)
                    },
                    enabled = isValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("pay_data_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VtuGreenPrimary)
                ) {
                    val payLabel = if (activePlan != null && activePlan.price > 0.0) {
                        val priceText = if (activePlan.price % 1.0 == 0.0) {
                            "₦%,.0f".format(activePlan.price)
                        } else {
                            "₦%,.2f".format(activePlan.price)
                        }
                        "Authorize & Pay $priceText"
                    } else {
                        "Select a Bundle"
                    }
                    Text(
                        text = payLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}
