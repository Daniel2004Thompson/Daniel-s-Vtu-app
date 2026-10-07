package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.VtuCatalog
import com.example.ui.theme.VtuGreenPrimary
import com.example.ui.viewmodel.PendingTransaction

@Composable
fun BiometricPinDialog(
    pendingTransaction: PendingTransaction?,
    isVerifying: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit,
    onSubmitPin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    val isLocked = errorMessage?.contains("Too many wrong attempts", ignoreCase = true) == true ||
        errorMessage?.contains("locked", ignoreCase = true) == true

    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrBlank()) {
            enteredPin = ""
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isVerifying) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !isVerifying
                ) {
                    onDismiss()
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Consume clicks inside sheet */ }
                    .testTag("auth_dialog"),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header
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
                                    text = "Transaction PIN",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(
                                onClick = onDismiss,
                                enabled = !isVerifying,
                                modifier = Modifier.testTag("dismiss_pin_dialog_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel authorization"
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Pending transaction summary
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
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        Text(
                            text = "Enter 4-Digit Transaction PIN",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Verify your identity to authorize this transaction",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 4-Box Individual PIN Field Architecture (active cursor box + masked dots)
                        TransactionPinBoxesField(
                            value = enteredPin,
                            isFocused = !isVerifying && !isLocked,
                            onFocusRequest = { /* Already focused in dialog */ },
                            enabled = !isVerifying && !isLocked,
                            isError = !errorMessage.isNullOrBlank(),
                            testTag = "transaction_pin_input"
                        )

                        if (isVerifying) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = VtuGreenPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Verifying PIN...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("pin_dialog_error")
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                if (enteredPin.length == 4 && enteredPin.all { it.isDigit() }) {
                                    onSubmitPin(enteredPin)
                                }
                            },
                            enabled = enteredPin.length == 4 && !isVerifying && !isLocked,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("confirm_transaction_pin_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = VtuGreenPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (isVerifying) "Verifying..." else "Verify PIN",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Bottom-Anchored Secure Numeric Keypad (4-row rectangular tile grid with 2-col '0' and '⌫')
                    SecureNumericKeypad(
                        enabled = !isVerifying && !isLocked,
                        onDigitClick = { digit ->
                            if (digit.all { it.isDigit() } && enteredPin.length < 4) {
                                if (!errorMessage.isNullOrBlank()) onClearError()
                                val nextPin = enteredPin + digit
                                enteredPin = nextPin
                                if (nextPin.length == 4) {
                                    onSubmitPin(nextPin)
                                }
                            }
                        },
                        onBackspaceClick = {
                            if (enteredPin.isNotEmpty()) {
                                enteredPin = enteredPin.dropLast(1)
                            }
                            if (!errorMessage.isNullOrBlank()) onClearError()
                        },
                        onDoneClick = {
                            if (enteredPin.length == 4 && enteredPin.all { it.isDigit() }) {
                                onSubmitPin(enteredPin)
                            } else if (!isVerifying) {
                                onDismiss()
                            }
                        }
                    )
                }
            }
        }
    }
}
