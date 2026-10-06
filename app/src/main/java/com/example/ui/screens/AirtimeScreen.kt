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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BeneficiaryEntity
import com.example.data.model.NetworkProvider
import com.example.ui.theme.VtuGoldAccent
import com.example.ui.theme.VtuGreenPrimary
import com.example.ui.viewmodel.PendingTransaction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirtimeScreen(
    walletBalance: Double,
    beneficiaries: List<BeneficiaryEntity>,
    onBack: () -> Unit,
    onRequestPayment: (PendingTransaction) -> Unit
) {
    var selectedNetwork by remember { mutableStateOf(NetworkProvider.AIRTEL) }
    var phoneNumber by remember { mutableStateOf("") }
    var customAmount by remember { mutableStateOf("1000") }
    var showNetworkPicker by remember { mutableStateOf(false) }

    val quickAmounts = listOf(100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0)

    val enteredAmount = customAmount.toDoubleOrNull() ?: 0.0
    val discount = (enteredAmount * (selectedNetwork.airtimeDiscountPct / 100.0))
    val amountToPay = (enteredAmount - discount).coerceAtLeast(0.0)

    val airtimeBeneficiaries = beneficiaries.filter { it.serviceType == "AIRTIME" }

    if (showNetworkPicker) {
        com.example.ui.components.NetworkPickerDialog(
            selectedNetwork = selectedNetwork,
            onSelectNetwork = { selectedNetwork = it },
            onDismiss = { showNetworkPicker = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buy Airtime", fontWeight = FontWeight.Bold) },
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
                .testTag("airtime_screen"),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Select Network Provider
            Column {
                Text(
                    text = "Select Network",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NetworkProvider.entries.forEach { network ->
                        val isSelected = selectedNetwork == network
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) network.brandColor.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) network.brandColor else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedNetwork = network }
                                .padding(vertical = 12.dp)
                                .testTag("network_select_${network.name}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                com.example.ui.components.NetworkLogoIcon(
                                    provider = network,
                                    size = 44.dp,
                                    isCircular = false
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = network.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = "${network.airtimeDiscountPct}% Off",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VtuGreenPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Phone Number Input
            Column {
                Text(
                    text = "Phone Number",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { input ->
                        if (input.length <= 14) {
                            phoneNumber = input
                            // Auto detect network
                            val detected = NetworkProvider.detectFromPhone(input)
                            if (detected != null) {
                                selectedNetwork = detected
                            }
                        }
                    },
                    placeholder = { Text("Enter recipient phone number") },
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
                        .testTag("airtime_phone_input")
                )

                // Beneficiaries quick chips
                if (airtimeBeneficiaries.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Recent Contacts",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(airtimeBeneficiaries) { b ->
                            val contactNet = NetworkProvider.detectFromPhone(b.recipient)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        phoneNumber = b.recipient
                                        contactNet?.let {
                                            selectedNetwork = it
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (contactNet != null) {
                                        com.example.ui.components.NetworkLogoIcon(
                                            provider = contactNet,
                                            size = 18.dp,
                                            isCircular = true
                                        )
                                    } else {
                                        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(b.name, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            // Amount Selection
            Column {
                Text(
                    text = "Amount",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Quick Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickAmounts.take(3).forEach { amount ->
                        QuickAmountChip(
                            amount = amount,
                            isSelected = enteredAmount == amount,
                            modifier = Modifier.weight(1f),
                            onClick = { customAmount = amount.toInt().toString() }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickAmounts.drop(3).forEach { amount ->
                        QuickAmountChip(
                            amount = amount,
                            isSelected = enteredAmount == amount,
                            modifier = Modifier.weight(1f),
                            onClick = { customAmount = amount.toInt().toString() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = customAmount,
                    onValueChange = { if (it.all { char -> char.isDigit() }) customAmount = it },
                    label = { Text("Custom Amount (₦)") },
                    prefix = { Text("₦ ", fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("airtime_amount_input")
                )
            }

            // Summary Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Airtime Value", style = MaterialTheme.typography.bodyMedium)
                        Text("₦%,.2f".format(enteredAmount), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${selectedNetwork.displayName} Cashback (${selectedNetwork.airtimeDiscountPct}%)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VtuGreenPrimary
                        )
                        Text("-₦%,.2f".format(discount), fontWeight = FontWeight.Bold, color = VtuGreenPrimary)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("You Pay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "₦%,.2f".format(amountToPay),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Pay Button
            val isValid = phoneNumber.length >= 10 && enteredAmount >= 50.0
            Button(
                onClick = {
                    val pending = PendingTransaction(
                        title = "${selectedNetwork.displayName} Airtime",
                        serviceType = "AIRTIME",
                        provider = selectedNetwork.displayName,
                        recipient = phoneNumber,
                        amount = enteredAmount,
                        discount = discount
                    )
                    onRequestPayment(pending)
                },
                enabled = isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("pay_airtime_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VtuGreenPrimary)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Authorize & Pay ₦%,.2f".format(amountToPay),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuickAmountChip(
    amount: Double,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) VtuGreenPrimary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) VtuGreenPrimary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "₦%,.0f".format(amount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isSelected) VtuGreenPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}
