package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.TransactionEntity
import com.example.data.model.VtuCatalog
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.VtuGreenPrimary
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AirtimeReceiptBreakdown(
    val airtimeValue: Double,
    val cashback: Double,
    val charged: Double
)

fun formatReceiptNaira(amount: Double): String {
    val bd = BigDecimal.valueOf(amount)
        .setScale(2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
    return "₦${bd.toPlainString()}"
}

fun parseAirtimeReceiptBreakdown(transaction: TransactionEntity): AirtimeReceiptBreakdown {
    val rawDetails = transaction.tokenOrDetails.orEmpty()
    val match = Regex("\\[SERVER_RECEIPT:airtime=([^,\\]]+),cashback=([^,\\]]+),charged=([^\\]]+)\\]")
        .find(rawDetails)
    if (match != null) {
        val airtime = match.groupValues[1].toDoubleOrNull() ?: transaction.amount
        val cashback = match.groupValues[2].toDoubleOrNull() ?: transaction.discountOrCashback
        val charged = match.groupValues[3].toDoubleOrNull()
            ?: BigDecimal.valueOf(airtime).subtract(BigDecimal.valueOf(cashback)).setScale(2, RoundingMode.HALF_UP).toDouble()
        return AirtimeReceiptBreakdown(
            airtimeValue = airtime,
            cashback = cashback,
            charged = charged
        )
    }
    val airtime = transaction.amount
    val cashback = transaction.discountOrCashback
    val charged = BigDecimal.valueOf(airtime)
        .subtract(BigDecimal.valueOf(cashback))
        .setScale(2, RoundingMode.HALF_UP)
        .toDouble()
    return AirtimeReceiptBreakdown(
        airtimeValue = airtime,
        cashback = cashback,
        charged = charged
    )
}

@Composable
fun TransactionReceiptDialog(
    transaction: TransactionEntity,
    onDismiss: () -> Unit,
    onDelete: ((TransactionEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    val formattedDate = remember(transaction.createdAt, transaction.timestamp) {
        com.example.util.TransactionDateFormatter.formatUtcToLagos(
            createdAt = transaction.createdAt,
            fallbackEpochMillis = transaction.timestamp
        ).trim()
    }

    val isFailedOrRefunded = remember(transaction.status, transaction.title, transaction.serviceType) {
        com.example.data.repository.VtuRepository.isFailedOrRefundedTransaction(
            status = transaction.status,
            title = transaction.title,
            service = transaction.serviceType
        )
    }
    val isTxSuccessful = !isFailedOrRefunded && (
        transaction.status.equals("SUCCESSFUL", ignoreCase = true) ||
            transaction.status.equals("SUCCESS", ignoreCase = true) ||
            transaction.status.equals("COMPLETED", ignoreCase = true)
        )
    val isTxPending = !isFailedOrRefunded && (
        transaction.status.equals("PENDING", ignoreCase = true) ||
            transaction.status.equals("PROCESSING", ignoreCase = true)
        )
    val isAirtime = transaction.serviceType.equals("AIRTIME", ignoreCase = true)
    val hasExplicitAirtimeBreakdown = remember(transaction) {
        isAirtime && (transaction.tokenOrDetails?.contains("[SERVER_RECEIPT:") == true || transaction.discountOrCashback > 0.0)
    }
    val airtimeBreakdown = remember(transaction, hasExplicitAirtimeBreakdown) {
        if (hasExplicitAirtimeBreakdown) parseAirtimeReceiptBreakdown(transaction) else null
    }

    val cleanService = remember(transaction.serviceType) {
        transaction.serviceType.replace('_', ' ').trim()
    }
    val cleanProvider = remember(transaction.provider) {
        transaction.provider.trim()
    }
    val cleanRecipient = remember(transaction.recipient) {
        transaction.recipient.trim()
    }
    val cleanCustomer = remember(transaction.customerName) {
        transaction.customerName?.trim().orEmpty()
    }
    val cleanStatus = remember(transaction.status, isFailedOrRefunded, isTxSuccessful, isTxPending) {
        when {
            isFailedOrRefunded -> "FAILED"
            isTxSuccessful -> "SUCCESSFUL"
            isTxPending -> "PENDING"
            else -> transaction.status.trim().ifEmpty { "FAILED" }
        }
    }
    val cleanReference = remember(transaction.reference) {
        transaction.reference.replace(Regex("GSUBZ-ERR-|GSUBZ-", RegexOption.IGNORE_CASE), "VTU-").trim()
    }
    val cleanDetails = remember(transaction.tokenOrDetails, isTxSuccessful, isTxPending) {
        val raw = transaction.tokenOrDetails
            ?.replace(Regex("\\s*\\[SERVER_RECEIPT:[^\\]]*\\]"), "")
            ?.trim()
            .orEmpty()
        if (raw.isBlank()) {
            null
        } else if (isTxPending) {
            "Purchase is being confirmed"
        } else if (isTxSuccessful) {
            val stripped = raw
                .replace(Regex("\\(\\s*Gsubz\\s*Live\\s*\\)", RegexOption.IGNORE_CASE), "")
                .replace(Regex("via\\s+Gsubz-VTU-Services", RegexOption.IGNORE_CASE), "")
                .replace(Regex("Gsubz-VTU-Services|Gsubz|Supabase|Edge\\s*Function", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+•\\s*$"), "")
                .trim()
            stripped.ifBlank { null }
        } else {
            null
        }
    }

    fun copyToClipboard(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun shareReceipt() {
        val text = buildString {
            append("=== DANIEL VTU RECEIPT ===\n")
            if (isTxPending) {
                append("Purchase is being confirmed\n")
            }
            if (cleanService.isNotEmpty()) {
                append("Service: $cleanService\n")
            }
            if (cleanProvider.isNotEmpty()) {
                append("Provider: $cleanProvider\n")
            }
            if (cleanRecipient.isNotEmpty()) {
                append("Recipient: $cleanRecipient\n")
            }
            if (cleanCustomer.isNotEmpty()) {
                append("Customer: $cleanCustomer\n")
            }
            if (isAirtime && airtimeBreakdown != null) {
                append("Airtime: ${formatReceiptNaira(airtimeBreakdown.airtimeValue)}\n")
                append("Cashback: ${formatReceiptNaira(airtimeBreakdown.cashback)}\n")
                append("You paid: ${formatReceiptNaira(airtimeBreakdown.charged)}\n")
            } else {
                append("Amount: ₦%,.2f\n".format(Locale.US, transaction.amount))
                if (transaction.discountOrCashback > 0) {
                    append("Discount/Cashback: ₦%,.2f\n".format(Locale.US, transaction.discountOrCashback))
                }
            }
            if (!cleanDetails.isNullOrBlank()) {
                append("Details: $cleanDetails\n")
            }
            if (cleanReference.isNotEmpty()) {
                append("Ref: $cleanReference\n")
            }
            if (formattedDate.isNotEmpty()) {
                append("Date: $formattedDate\n")
            }
            append("Payment Method: Daniel VTU Wallet\n")
            if (cleanStatus.isNotEmpty()) {
                append("Status: $cleanStatus\n")
            }
            append("==========================\n")
            append("Thank you for choosing Daniel VTU!")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Daniel VTU Transaction Receipt")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share Transaction Receipt"))
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Transaction?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to permanently delete this transaction (${transaction.serviceType} • ₦%,.2f) from your purchase history?".format(transaction.amount))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete?.invoke(transaction)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("receipt_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("transaction_receipt_dialog"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transaction Receipt",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onDelete != null) {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Delete transaction",
                                    tint = Color(0xFFE53935)
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close receipt")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val statusHeaderColor = when {
                    isTxSuccessful -> StatusSuccess
                    isTxPending -> Color(0xFFF59E0B)
                    else -> Color(0xFFE53935)
                }
                val statusHeaderIcon = when {
                    isTxSuccessful -> Icons.Default.CheckCircle
                    isTxPending -> Icons.Default.Schedule
                    else -> Icons.Default.Close
                }
                val statusHeaderText = when {
                    isTxPending -> "Purchase is being confirmed"
                    isTxSuccessful -> "Payment Successful"
                    else -> "Payment Failed"
                }

                // Status badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(statusHeaderColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = statusHeaderIcon,
                        contentDescription = null,
                        tint = statusHeaderColor,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = statusHeaderText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusHeaderColor,
                    modifier = Modifier.testTag("receipt_status_header")
                )

                if (isAirtime && airtimeBreakdown != null && (isTxSuccessful || isTxPending)) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(VtuGreenPrimary.copy(alpha = 0.08f))
                            .border(1.dp, VtuGreenPrimary.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                            .testTag("airtime_receipt_summary_box")
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Airtime: ${formatReceiptNaira(airtimeBreakdown.airtimeValue)}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("receipt_line_airtime")
                            )
                            Text(
                                text = "Cashback: ${formatReceiptNaira(airtimeBreakdown.cashback)}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = VtuGreenPrimary,
                                modifier = Modifier.testTag("receipt_line_cashback")
                            )
                            Text(
                                text = "You paid: ${formatReceiptNaira(airtimeBreakdown.charged)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("receipt_line_charged")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                } else {
                    Text(
                        text = "₦%,.2f".format(transaction.amount),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (transaction.discountOrCashback > 0) {
                        Text(
                            text = "Cashback/Discount: ₦%,.2f".format(transaction.discountOrCashback),
                            style = MaterialTheme.typography.bodySmall,
                            color = VtuGreenPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Token or PIN card if available (Electricity / Exam / Delivery details)
                if (!cleanDetails.isNullOrBlank() && !isAirtime) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isTxSuccessful || isTxPending) VtuGreenPrimary.copy(alpha = 0.08f) else Color(0xFFE53935).copy(alpha = 0.08f))
                            .border(1.dp, if (isTxSuccessful || isTxPending) VtuGreenPrimary.copy(alpha = 0.3f) else Color(0xFFE53935).copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = when {
                                    transaction.serviceType == "ELECTRICITY" && isTxSuccessful -> "PREPAID METER TOKEN"
                                    !isTxSuccessful && !isTxPending -> "STATUS / DETAILS"
                                    transaction.serviceType == "EDUCATION" && isTxSuccessful -> "EXAMINATION PIN"
                                    else -> "TRANSACTION DETAILS"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isTxSuccessful || isTxPending) VtuGreenPrimary else Color(0xFFE53935)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = cleanDetails,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                            if (isTxSuccessful && (transaction.serviceType == "ELECTRICITY" || transaction.serviceType == "EDUCATION")) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = { copyToClipboard(cleanDetails, "Token") },
                                    colors = ButtonDefaults.buttonColors(containerColor = VtuGreenPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy Token", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Receipt Breakdown Table
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val txProvider = com.example.data.model.NetworkProvider.detectFromTextOrPhone(
                        cleanProvider,
                        cleanRecipient
                    )
                    val txExam = if (transaction.serviceType.equals("EDUCATION", ignoreCase = true)) {
                        VtuCatalog.findExamByProvider(cleanProvider)
                    } else null
                    val txCable = if (transaction.serviceType.equals("CABLE_TV", ignoreCase = true) || transaction.serviceType.equals("CABLE", ignoreCase = true)) {
                        VtuCatalog.findCableByProvider(cleanProvider)
                    } else null
                    val txDisco = if (transaction.serviceType.equals("ELECTRICITY", ignoreCase = true)) {
                        VtuCatalog.findDiscoByProvider(cleanProvider)
                    } else null

                    if (cleanService.isNotEmpty()) {
                        ReceiptRow(label = "Service", value = cleanService)
                    }

                    if (cleanProvider.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Provider",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (txProvider != null && (transaction.serviceType.equals("AIRTIME", ignoreCase = true) || transaction.serviceType.equals("DATA", ignoreCase = true))) {
                                    NetworkLogoIcon(
                                        provider = txProvider,
                                        size = 18.dp,
                                        isCircular = true
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                } else if (txExam != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .border(0.8.dp, txExam.brandColor.copy(alpha = 0.4f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = txExam.logoRes),
                                            contentDescription = txExam.shortName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                } else if (txCable != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .border(0.8.dp, txCable.brandColor.copy(alpha = 0.4f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = txCable.logoRes),
                                            contentDescription = txCable.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                } else if (txDisco != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .border(0.8.dp, txDisco.brandColor.copy(alpha = 0.4f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = txDisco.logoRes),
                                            contentDescription = txDisco.shortName,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = cleanProvider,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (cleanRecipient.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recipient",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val recipientNet = com.example.data.model.NetworkProvider.detectFromPhone(cleanRecipient)
                                if (recipientNet != null) {
                                    NetworkLogoIcon(
                                        provider = recipientNet,
                                        size = 18.dp,
                                        isCircular = true
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = cleanRecipient,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (cleanCustomer.isNotEmpty()) {
                        ReceiptRow(label = "Customer", value = cleanCustomer)
                    }

                    if (isAirtime && airtimeBreakdown != null) {
                        ReceiptRow(label = "Airtime", value = formatReceiptNaira(airtimeBreakdown.airtimeValue))
                        ReceiptRow(label = "Cashback", value = formatReceiptNaira(airtimeBreakdown.cashback))
                        ReceiptRow(label = "You paid", value = formatReceiptNaira(airtimeBreakdown.charged), isHighlighted = true)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    if (cleanReference.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Reference",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = cleanReference,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { copyToClipboard(cleanReference, "Reference") },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Reference", modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    if (formattedDate.isNotEmpty()) {
                        ReceiptRow(label = "Date & Time", value = formattedDate)
                    }
                    ReceiptRow(label = "Payment Method", value = "Daniel VTU Wallet")
                    if (cleanStatus.isNotEmpty()) {
                        ReceiptRow(
                            label = "Status",
                            value = cleanStatus,
                            isHighlighted = true,
                            highlightColor = statusHeaderColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { shareReceipt() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share")
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = VtuGreenPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Done")
                    }
                }

                if (onDelete != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE53935))
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Transaction", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(
    label: String,
    value: String,
    isHighlighted: Boolean = false,
    highlightColor: Color = StatusSuccess
) {
    if (value.trim().isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isHighlighted) highlightColor else MaterialTheme.colorScheme.onSurface
        )
    }
}
