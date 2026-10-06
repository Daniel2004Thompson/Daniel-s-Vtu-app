package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DataCategory
import com.example.data.model.DataPlan
import com.example.data.model.NetworkProvider
import com.example.data.model.VtuCatalog
import com.example.ui.theme.VtuCyan
import com.example.ui.theme.VtuGoldAccent
import com.example.ui.theme.VtuGreenPrimary
import com.example.ui.viewmodel.PendingTransaction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    walletBalance: Double,
    onBack: () -> Unit,
    onRequestPayment: (PendingTransaction) -> Unit
) {
    var selectedNetwork by remember { mutableStateOf(NetworkProvider.AIRTEL) }
    var selectedCategory by remember { mutableStateOf<DataCategory?>(null) }
    var phoneNumber by remember { mutableStateOf("") }
    var selectedPlan by remember { mutableStateOf<DataPlan?>(null) }
    var showNetworkPicker by remember { mutableStateOf(false) }

    val filteredPlans = VtuCatalog.dataPlans.filter {
        it.network == selectedNetwork && (selectedCategory == null || it.category == selectedCategory)
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

            // Plan Category Filter Chips
            item {
                Column {
                    Text(
                        text = "3. Select Package Type",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { selectedCategory = null },
                                label = { Text("All Plans") }
                            )
                        }
                        items(DataCategory.entries) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat.label) }
                            )
                        }
                    }
                }
            }

            // Data Bundles List
            items(filteredPlans) { plan ->
                val isSelected = selectedPlan?.id == plan.id
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
                        .testTag("data_plan_${plan.id}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) VtuGreenPrimary.copy(alpha = 0.08f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.example.ui.components.NetworkLogoIcon(
                                provider = plan.network,
                                size = 44.dp,
                                isCircular = true
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = plan.dataAmount,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = plan.category.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                Text(
                                    text = "Validity: ${plan.validity}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "₦%,.0f".format(plan.price),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = VtuGreenPrimary
                            )
                            if (plan.originalPrice != null) {
                                Text(
                                    text = "₦%,.0f".format(plan.originalPrice),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textDecoration = TextDecoration.LineThrough
                                )
                            }
                        }
                    }
                }
            }

            // Checkout Button
            item {
                Spacer(modifier = Modifier.height(8.dp))
                val isValid = phoneNumber.length >= 10 && selectedPlan != null
                Button(
                    onClick = {
                        val plan = selectedPlan ?: return@Button
                        val pending = PendingTransaction(
                            title = "${plan.network.displayName} Data ${plan.dataAmount}",
                            serviceType = "DATA",
                            provider = plan.network.displayName,
                            recipient = phoneNumber,
                            amount = plan.price,
                            planId = plan.id,
                            details = "${plan.dataAmount} • ${plan.validity} (${plan.category.label})"
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
                    Text(
                        text = if (selectedPlan != null) "Authorize & Pay ₦%,.0f".format(selectedPlan?.price ?: 0.0)
                        else "Select a Bundle",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}
