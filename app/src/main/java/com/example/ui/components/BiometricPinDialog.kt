package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.example.data.model.VtuCatalog
import com.example.ui.theme.VtuGreenDark
import com.example.ui.theme.VtuGreenPrimary
import com.example.ui.theme.VtuNavyPrimary
import com.example.ui.viewmodel.PendingTransaction
import com.example.util.BiometricAuthHelper

@Composable
fun BiometricPinDialog(
    pendingTransaction: PendingTransaction?,
    isBiometricEnabled: Boolean,
    onPinVerify: (String) -> Boolean,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var usePinMode by remember { mutableStateOf(!isBiometricEnabled) }

    fun triggerBiometric() {
        val activity = context as? FragmentActivity
        if (activity != null) {
            val status = BiometricAuthHelper.checkBiometricStatus(context)
            if (status == BiometricAuthHelper.BiometricStatus.READY) {
                BiometricAuthHelper.authenticate(
                    activity = activity,
                    title = "Daniel VTU Security",
                    subtitle = pendingTransaction?.let { "Authorize ₦%,.2f for %s".format(it.amount, it.recipient) }
                        ?: "Authorize authentication",
                    negativeButtonText = "Use PIN",
                    onSuccess = onSuccess,
                    onError = { _, errString ->
                        // Switch to PIN on fallback
                        usePinMode = true
                    },
                    onFailed = {
                        errorMessage = "Biometric not recognized. Try again or use PIN."
                    }
                )
            } else {
                // Hardware or enrollment not ready (e.g. simulator)
                usePinMode = true
            }
        } else {
            usePinMode = true
        }
    }

    LaunchedEffect(Unit) {
        if (isBiometricEnabled) {
            triggerBiometric()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .testTag("auth_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(VtuGreenPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = VtuGreenPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Security Authorization",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel authorization"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Pending details if any
                if (pendingTransaction != null) {
                    val pendingExam = if (pendingTransaction.serviceType == "EDUCATION") {
                        VtuCatalog.findExamByProvider(pendingTransaction.provider)
                    } else null
                    val pendingCable = if (pendingTransaction.serviceType == "CABLE_TV") {
                        VtuCatalog.findCableByProvider(pendingTransaction.provider)
                    } else null
                    val pendingDisco = if (pendingTransaction.serviceType == "ELECTRICITY") {
                        VtuCatalog.findDiscoByProvider(pendingTransaction.provider)
                    } else null
                    val pendingLogoRes = pendingExam?.logoRes ?: pendingCable?.logoRes ?: pendingDisco?.logoRes
                    val pendingBrandColor = pendingExam?.brandColor ?: pendingCable?.brandColor ?: pendingDisco?.brandColor
                    val pendingLogoDesc = pendingExam?.shortName ?: pendingCable?.name ?: pendingDisco?.shortName
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (pendingLogoRes != null && pendingBrandColor != null) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White)
                                        .border(1.dp, pendingBrandColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = pendingLogoRes),
                                        contentDescription = pendingLogoDesc,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pendingTransaction.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "₦%,.2f".format(pendingTransaction.amount - pendingTransaction.discount),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = VtuGreenPrimary
                                    )
                                    Text(
                                        text = pendingTransaction.recipient,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (!usePinMode) {
                    // Biometric Prompt UI representation
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(VtuGreenPrimary.copy(alpha = 0.15f))
                                .clickable { triggerBiometric() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometric Fingerprint",
                                tint = VtuGreenPrimary,
                                modifier = Modifier.size(54.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Touch Fingerprint Sensor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Place your finger on sensor or verify biometric",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Sandbox / Test Simulator button
                        Button(
                            onClick = onSuccess,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("simulate_biometric_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VtuGreenPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Simulate Biometric Verification")
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { usePinMode = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Enter 4-Digit Security PIN")
                        }
                    }
                } else {
                    // PIN Entry Mode
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Enter 4-Digit Transaction PIN",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Default PIN: 1234",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // PIN indicator circles
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(4) { index ->
                                val isFilled = index < enteredPin.length
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isFilled) VtuGreenPrimary else Color.Transparent
                                        )
                                        .border(
                                            width = 2.dp,
                                            color = if (isFilled) VtuGreenPrimary else MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Numeric Keypad
                        val keypadNumbers = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf("BIO", "0", "DEL")
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            keypadNumbers.forEach { row ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    row.forEach { key ->
                                        Box(
                                            modifier = Modifier
                                                .size(62.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when (key) {
                                                        "BIO" -> VtuGreenPrimary.copy(alpha = 0.12f)
                                                        "DEL" -> MaterialTheme.colorScheme.surfaceVariant
                                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                    }
                                                )
                                                .clickable {
                                                    when (key) {
                                                        "DEL" -> {
                                                            if (enteredPin.isNotEmpty()) {
                                                                enteredPin = enteredPin.dropLast(1)
                                                                errorMessage = null
                                                            }
                                                        }
                                                        "BIO" -> {
                                                            usePinMode = false
                                                            triggerBiometric()
                                                        }
                                                        else -> {
                                                            if (enteredPin.length < 4) {
                                                                val nextPin = enteredPin + key
                                                                enteredPin = nextPin
                                                                if (nextPin.length == 4) {
                                                                    if (onPinVerify(nextPin)) {
                                                                        onSuccess()
                                                                    } else {
                                                                        errorMessage = "Incorrect PIN. Default is 1234."
                                                                        enteredPin = ""
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                .testTag("keypad_$key"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            when (key) {
                                                "DEL" -> Icon(
                                                    imageVector = Icons.Default.Backspace,
                                                    contentDescription = "Delete digit",
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                                "BIO" -> Icon(
                                                    imageVector = Icons.Default.Fingerprint,
                                                    contentDescription = "Switch to Biometric",
                                                    tint = VtuGreenPrimary
                                                )
                                                else -> Text(
                                                    text = key,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 22.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
