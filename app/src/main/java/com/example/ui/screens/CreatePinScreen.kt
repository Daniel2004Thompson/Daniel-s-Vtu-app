package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.SecureNumericKeypad
import com.example.ui.components.TransactionPinBoxesField
import com.example.ui.theme.VtuGreenPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePinScreen(
    isLoading: Boolean,
    onCreatePin: (pin: String, onError: (String) -> Unit) -> Unit,
    onBack: () -> Unit = {}
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // 0 = Enter PIN field, 1 = Confirm PIN field, -1 = keypad hidden
    var focusedField by remember { mutableIntStateOf(0) }

    val submitPinAction = {
        val cleanPin = pin.trim()
        val cleanConfirm = confirmPin.trim()
        when {
            cleanPin.length != 4 || !cleanPin.all { it.isDigit() } -> {
                errorMessage = "Please enter a 4-digit PIN"
            }
            cleanConfirm.length != 4 || !cleanConfirm.all { it.isDigit() } -> {
                errorMessage = "Please confirm your 4-digit PIN"
            }
            cleanPin != cleanConfirm -> {
                errorMessage = "PINs do not match"
            }
            else -> {
                errorMessage = null
                onCreatePin(cleanPin) { err ->
                    errorMessage = err
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("create_pin_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Create Transaction PIN",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("create_pin_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(VtuGreenPrimary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Create Transaction PIN",
                                tint = VtuGreenPrimary,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Create Transaction PIN",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Set a 4-digit transaction PIN to authorize all purchases and wallet withdrawals.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        TransactionPinBoxesField(
                            value = pin,
                            isFocused = focusedField == 0 && !isLoading,
                            onFocusRequest = {
                                if (!isLoading) focusedField = 0
                            },
                            label = "Enter 4-Digit PIN",
                            enabled = !isLoading,
                            isError = !errorMessage.isNullOrBlank() && pin.length < 4,
                            testTag = "create_pin_input"
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        TransactionPinBoxesField(
                            value = confirmPin,
                            isFocused = focusedField == 1 && !isLoading,
                            onFocusRequest = {
                                if (!isLoading) focusedField = 1
                            },
                            label = "Confirm 4-Digit PIN",
                            enabled = !isLoading,
                            isError = !errorMessage.isNullOrBlank() && (confirmPin.length < 4 || pin != confirmPin),
                            testTag = "create_pin_confirm_input"
                        )

                        if (!errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = errorMessage.orEmpty(),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .testTag("create_pin_error")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = submitPinAction,
                            enabled = !isLoading && pin.length == 4 && confirmPin.length == 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("create_pin_submit_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = VtuGreenPrimary),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isLoading) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Saving PIN...", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Text("Create PIN", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Bottom-Anchored Secure Numeric Keypad
            AnimatedVisibility(visible = focusedField in 0..1 && !isLoading) {
                SecureNumericKeypad(
                    enabled = !isLoading,
                    onDigitClick = { digit ->
                        if (!digit.all { it.isDigit() }) return@SecureNumericKeypad
                        errorMessage = null
                        when (focusedField) {
                            0 -> {
                                if (pin.length < 4) {
                                    val next = pin + digit
                                    pin = next
                                    if (next.length == 4 && confirmPin.length < 4) {
                                        focusedField = 1
                                    }
                                }
                            }
                            1 -> {
                                if (confirmPin.length < 4) {
                                    confirmPin += digit
                                }
                            }
                        }
                    },
                    onBackspaceClick = {
                        errorMessage = null
                        when (focusedField) {
                            0 -> {
                                if (pin.isNotEmpty()) {
                                    pin = pin.dropLast(1)
                                }
                            }
                            1 -> {
                                if (confirmPin.isNotEmpty()) {
                                    confirmPin = confirmPin.dropLast(1)
                                } else if (pin.isNotEmpty()) {
                                    focusedField = 0
                                    pin = pin.dropLast(1)
                                }
                            }
                        }
                    },
                    onDoneClick = {
                        if (pin.length == 4 && confirmPin.length == 4) {
                            submitPinAction()
                        } else {
                            focusedField = -1
                        }
                    }
                )
            }
        }
    }
}
