package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CableBouquet
import com.example.data.model.CableProvider
import com.example.data.model.EducationExam
import com.example.data.model.ElectricityProvider
import com.example.data.model.VtuCatalog
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.VtuCyan
import com.example.ui.theme.VtuGoldAccent
import com.example.ui.theme.VtuGreenPrimary
import com.example.ui.viewmodel.PendingTransaction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(
    initialCategory: String = "ELECTRICITY",
    walletBalance: Double,
    onBack: () -> Unit,
    onRequestPayment: (PendingTransaction) -> Unit
) {
    var selectedTab by remember {
        mutableIntStateOf(
            when (initialCategory) {
                "CABLE_TV" -> 1
                "EDUCATION" -> 2
                else -> 0
            }
        )
    }

    val tabs = listOf("Electricity", "Cable TV", "Education PIN")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bill Payments", fontWeight = FontWeight.Bold) },
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
                .testTag("bills_screen")
        ) {
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.SemiBold) },
                        icon = {
                            Icon(
                                imageVector = when (index) {
                                    0 -> Icons.Default.Bolt
                                    1 -> Icons.Default.Tv
                                    else -> Icons.Default.School
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                when (selectedTab) {
                    0 -> ElectricityTabContent(onRequestPayment = onRequestPayment)
                    1 -> CableTvTabContent(onRequestPayment = onRequestPayment)
                    2 -> EducationTabContent(onRequestPayment = onRequestPayment)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectricityTabContent(
    onRequestPayment: (PendingTransaction) -> Unit
) {
    var selectedDisco by remember { mutableStateOf(VtuCatalog.electricityDiscos.first()) }
    var discoDropdownExpanded by remember { mutableStateOf(false) }
    var isPrepaid by remember { mutableStateOf(true) }
    var meterNumber by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("2000") }
    var verifiedCustomerName by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick-Select Electricity DisCo Logo Strip (IKEDC, EKEDC, AEDC, IBEDC, PHED, EEDC, KEDCO)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Distribution Company (DisCo)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VtuCatalog.electricityDiscos.forEach { disco ->
                    val isSelected = selectedDisco.id == disco.id
                    Card(
                        modifier = Modifier
                            .width(86.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) disco.brandColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                selectedDisco = disco
                                if (meterNumber.length >= 10) {
                                    verifiedCustomerName = "Verified Meter (${disco.shortName})"
                                }
                            }
                            .testTag("disco_chip_${disco.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) disco.brandColor.copy(alpha = 0.10f)
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .border(
                                        width = 1.dp,
                                        color = disco.brandColor.copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = disco.logoRes),
                                    contentDescription = "${disco.shortName} Logo",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(9.dp))
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = disco.shortName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSelected) disco.brandColor else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = disco.brandColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Disco selector dropdown with leading logo and menu item logos
            ExposedDropdownMenuBox(
                expanded = discoDropdownExpanded,
                onExpandedChange = { discoDropdownExpanded = !discoDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = "${selectedDisco.name} • ${selectedDisco.stateCoverage}",
                    onValueChange = {},
                    readOnly = true,
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(1.dp, selectedDisco.brandColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = selectedDisco.logoRes),
                                contentDescription = selectedDisco.shortName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(6.dp))
                            )
                        }
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = discoDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = discoDropdownExpanded,
                    onDismissRequest = { discoDropdownExpanded = false }
                ) {
                    VtuCatalog.electricityDiscos.forEach { disco ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White)
                                            .border(0.8.dp, disco.brandColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = disco.logoRes),
                                            contentDescription = disco.shortName,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(6.dp))
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(disco.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            disco.stateCoverage,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            onClick = {
                                selectedDisco = disco
                                if (meterNumber.length >= 10) {
                                    verifiedCustomerName = "Verified Meter (${disco.shortName})"
                                }
                                discoDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Meter Type (Prepaid vs Postpaid)
        Column {
            Text("Meter Type", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = isPrepaid,
                    onClick = { isPrepaid = true },
                    label = { Text("Prepaid (Token Generated)") },
                    leadingIcon = { if (isPrepaid) Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = !isPrepaid,
                    onClick = { isPrepaid = false },
                    label = { Text("Postpaid") },
                    leadingIcon = { if (!isPrepaid) Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
        }

        // Meter Number
        Column {
            Text("Meter Number", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = meterNumber,
                onValueChange = { input ->
                    if (input.all { it.isDigit() } && input.length <= 13) {
                        meterNumber = input
                        if (input.length >= 10) {
                            verifiedCustomerName = "Verified Meter (${selectedDisco.shortName})"
                        } else {
                            verifiedCustomerName = null
                        }
                    }
                },
                placeholder = { Text("e.g. 45019283741") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("meter_number_input")
            )

            if (verifiedCustomerName != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Verified: $verifiedCustomerName",
                        style = MaterialTheme.typography.labelMedium,
                        color = StatusSuccess,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Amount Input
        Column {
            Text("Amount (₦)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { if (it.all { char -> char.isDigit() }) amount = it },
                prefix = { Text("₦ ", fontWeight = FontWeight.Bold) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("meter_amount_input")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        val parsedAmount = amount.toDoubleOrNull() ?: 0.0
        val canPay = meterNumber.length >= 10 && parsedAmount >= 500.0

        Button(
            onClick = {
                val pending = PendingTransaction(
                    title = "${selectedDisco.shortName} Electricity (${if (isPrepaid) "Prepaid" else "Postpaid"})",
                    serviceType = "ELECTRICITY",
                    provider = selectedDisco.name,
                    recipient = meterNumber,
                    amount = parsedAmount,
                    customerName = verifiedCustomerName ?: "",
                    meterNumber = meterNumber,
                    details = if (isPrepaid) "Prepaid Meter Token Generator" else "Postpaid Bill Payment"
                )
                onRequestPayment(pending)
            },
            enabled = canPay,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("pay_electricity_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VtuGreenPrimary)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = selectedDisco.logoRes),
                    contentDescription = selectedDisco.shortName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Authorize & Generate Token (₦%,.0f)".format(parsedAmount),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
        Spacer(modifier = Modifier.height(30.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CableTvTabContent(
    onRequestPayment: (PendingTransaction) -> Unit
) {
    var selectedProvider by remember { mutableStateOf(VtuCatalog.cableProviders.first()) }
    var selectedBouquet by remember { mutableStateOf(selectedProvider.bouquets.first()) }
    var smartcardNumber by remember { mutableStateOf("") }
    var verifiedCustomer by remember { mutableStateOf<String?>(null) }
    var bouquetDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Provider Selection with Logos (DStv, GOtv, StarTimes)
        Column {
            Text("Select Cable Provider", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VtuCatalog.cableProviders.forEach { provider ->
                    val isSelected = selectedProvider.id == provider.id
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) provider.brandColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                selectedProvider = provider
                                selectedBouquet = provider.bouquets.first()
                            }
                            .testTag("cable_provider_${provider.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) provider.brandColor.copy(alpha = 0.10f)
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .border(
                                        width = 1.dp,
                                        color = provider.brandColor.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = provider.logoRes),
                                    contentDescription = "${provider.name} Logo",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(9.dp))
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = provider.name,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) provider.brandColor else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = provider.brandColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            if (provider.tagline.isNotBlank()) {
                                Text(
                                    text = provider.tagline,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Smartcard / IUC Number
        Column {
            Text("Smartcard / IUC Number", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = smartcardNumber,
                onValueChange = { input ->
                    if (input.all { it.isDigit() } && input.length <= 11) {
                        smartcardNumber = input
                        if (input.length >= 10) {
                            verifiedCustomer = "Verified Smartcard ($input)"
                        } else {
                            verifiedCustomer = null
                        }
                    }
                },
                placeholder = { Text("Enter 10-digit Smartcard number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("smartcard_number_input")
            )

            if (verifiedCustomer != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Customer: $verifiedCustomer",
                        style = MaterialTheme.typography.labelMedium,
                        color = StatusSuccess,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Bouquet selection dropdown & interactive package cards with logo
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Select ${selectedProvider.name} Package / Bouquet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            ExposedDropdownMenuBox(
                expanded = bouquetDropdownExpanded,
                onExpandedChange = { bouquetDropdownExpanded = !bouquetDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = "${selectedBouquet.name} - ₦%,.0f".format(selectedBouquet.price),
                    onValueChange = {},
                    readOnly = true,
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(1.dp, selectedProvider.brandColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = selectedProvider.logoRes),
                                contentDescription = selectedProvider.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bouquetDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = bouquetDropdownExpanded,
                    onDismissRequest = { bouquetDropdownExpanded = false }
                ) {
                    selectedProvider.bouquets.forEach { bouquet ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White)
                                                .border(0.8.dp, selectedProvider.brandColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                                .padding(2.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                painter = painterResource(id = selectedProvider.logoRes),
                                                contentDescription = selectedProvider.name,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(bouquet.name, fontWeight = FontWeight.Bold)
                                            Text("${bouquet.channelsCount}+ Channels", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Text("₦%,.0f".format(bouquet.price), color = VtuGreenPrimary, fontWeight = FontWeight.Bold)
                                }
                            },
                            onClick = {
                                selectedBouquet = bouquet
                                bouquetDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Bouquet Price Cards with Provider Logo
            selectedProvider.bouquets.forEach { bouquet ->
                val isBouquetSelected = selectedBouquet.id == bouquet.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = if (isBouquetSelected) 2.dp else 1.dp,
                            color = if (isBouquetSelected) selectedProvider.brandColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { selectedBouquet = bouquet }
                        .testTag("cable_bouquet_${bouquet.id}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isBouquetSelected) selectedProvider.brandColor.copy(alpha = 0.08f)
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White)
                                    .border(1.dp, selectedProvider.brandColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = selectedProvider.logoRes),
                                    contentDescription = selectedProvider.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = bouquet.name,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (isBouquetSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = selectedProvider.brandColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${bouquet.channelsCount}+ Channels • Monthly Subscription",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "₦%,.0f".format(bouquet.price),
                            fontWeight = FontWeight.ExtraBold,
                            color = selectedProvider.brandColor,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val canPay = smartcardNumber.length >= 10
        Button(
            onClick = {
                val pending = PendingTransaction(
                    title = "${selectedProvider.name} ${selectedBouquet.name}",
                    serviceType = "CABLE_TV",
                    provider = selectedProvider.name,
                    recipient = smartcardNumber,
                    amount = selectedBouquet.price,
                    customerName = verifiedCustomer ?: "",
                    planId = selectedBouquet.id,
                    smartcardNumber = smartcardNumber,
                    details = "${selectedBouquet.name} (1 Month)"
                )
                onRequestPayment(pending)
            },
            enabled = canPay,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("pay_cable_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VtuGreenPrimary)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = selectedProvider.logoRes),
                    contentDescription = selectedProvider.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Authorize & Renew ₦%,.0f".format(selectedBouquet.price),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun EducationTabContent(
    onRequestPayment: (PendingTransaction) -> Unit
) {
    var selectedExam by remember { mutableStateOf(VtuCatalog.educationExams.first()) }
    var candidatePhone by remember { mutableStateOf("") }
    var candidateName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Select Exam Institute & PIN Token",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        // Quick-select Exam Institute Logo Row (WAEC, JAMB, NECO, NABTEB)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VtuCatalog.educationExams.forEach { exam ->
                val isSelected = selectedExam.id == exam.id
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) exam.brandColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { selectedExam = exam }
                        .testTag("exam_chip_${exam.shortName.lowercase()}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) exam.brandColor.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.dp, exam.brandColor.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = exam.logoRes),
                                contentDescription = exam.shortName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = exam.shortName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) exam.brandColor else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Exam Institute Price Token Cards with Corresponding Logos
        VtuCatalog.educationExams.forEach { exam ->
            val isSelected = selectedExam.id == exam.id
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) exam.brandColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { selectedExam = exam }
                    .testTag("exam_card_${exam.id}"),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) exam.brandColor.copy(alpha = 0.09f)
                    else MaterialTheme.colorScheme.surface
                )
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
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(
                                    width = 1.dp,
                                    color = exam.brandColor.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = exam.logoRes),
                                contentDescription = "${exam.shortName} Logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(exam.brandColor.copy(alpha = 0.14f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = exam.shortName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = exam.brandColor,
                                        fontSize = 10.sp
                                    )
                                }
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = exam.brandColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = exam.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = exam.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "₦%,.0f".format(exam.price),
                            fontWeight = FontWeight.ExtraBold,
                            color = exam.brandColor,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Instant PIN",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // Phone for SMS delivery
        Column {
            Text("Candidate / Delivery Phone", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = candidatePhone,
                onValueChange = { candidatePhone = it },
                placeholder = { Text("Phone number to receive PIN via SMS") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("education_phone_input")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        val canPay = candidatePhone.length >= 10
        Button(
            onClick = {
                val pending = PendingTransaction(
                    title = selectedExam.name,
                    serviceType = "EDUCATION",
                    provider = selectedExam.shortName,
                    recipient = candidatePhone,
                    amount = selectedExam.price,
                    customerName = candidateName,
                    details = "e-PIN & Serial for ${selectedExam.name}"
                )
                onRequestPayment(pending)
            },
            enabled = canPay,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("pay_education_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VtuGreenPrimary)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = selectedExam.logoRes),
                    contentDescription = selectedExam.shortName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Buy ${selectedExam.shortName} PIN for ₦%,.0f".format(selectedExam.price),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        Spacer(modifier = Modifier.height(30.dp))
    }
}
