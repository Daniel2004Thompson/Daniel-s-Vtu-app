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
fun ChangePinScreen(
    isLoading: Boolean,
    onChangePin: (oldPin: String, newPin: String, onResult: (Boolean, String) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }
    // 0 = Old PIN, 1 = New PIN, 2 = Confirm New PIN, -1 = keypad hidden
    var focusedField by remember { mutableIntStateOf(0) }

    val submitChangePinAction = {
        val cleanOld = oldPin.trim()
        val cleanNew = newPin.trim()
        val cleanConfirm = confirmNewPin.trim()
        when {
            cleanOld.length != 4 || !cleanOld.all { it.isDigit() } -> {
                isSuccess = false
                feedbackMessage = "Please enter your 4-digit old PIN"
            }
            cleanNew.length != 4 || !cleanNew.all { it.isDigit() } -> {
                isSuccess = false
                feedbackMessage = "Please enter a 4-digit new PIN"
            }
            cleanNew != cleanConfirm -> {
                isSuccess = false
                feedbackMessage = "New PINs do not match"
            }
            else -> {
                feedbackMessage = null
                onChangePin(cleanOld, cleanNew) { success, msg ->
                    isSuccess = success
                    feedbackMessage = msg
                    if (success) {
                        oldPin = ""
                        newPin = ""
                        confirmNewPin = ""
                        focusedField = 0
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("change_pin_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Change Transaction PIN",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("change_pin_back_button")
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
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(VtuGreenPrimary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = VtuGreenPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Update Your 4-Digit PIN",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Enter your current PIN and choose a new 4-digit transaction PIN.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        TransactionPinBoxesField(
                            value = oldPin,
                            isFocused = focusedField == 0 && !isLoading,
                            onFocusRequest = {
                                if (!isLoading) focusedField = 0
                            },
                            label = "Old PIN (4 digits)",
                            enabled = !isLoading,
                            isError = !isSuccess && !feedbackMessage.isNullOrBlank() && oldPin.length < 4,
                            boxSize = 48.dp,
                            testTag = "change_pin_old_input"
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        TransactionPinBoxesField(
                            value = newPin,
                            isFocused = focusedField == 1 && !isLoading,
                            onFocusRequest = {
                                if (!isLoading) focusedField = 1
                            },
                            label = "New PIN (4 digits)",
                            enabled = !isLoading,
                            isError = !isSuccess && !feedbackMessage.isNullOrBlank() && newPin.length < 4,
                            boxSize = 48.dp,
                            testTag = "change_pin_new_input"
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        TransactionPinBoxesField(
                            value = confirmNewPin,
                            isFocused = focusedField == 2 && !isLoading,
                            onFocusRequest = {
                                if (!isLoading) focusedField = 2
                            },
                            label = "Confirm New PIN (4 digits)",
                            enabled = !isLoading,
                            isError = !isSuccess && !feedbackMessage.isNullOrBlank() && (confirmNewPin.length < 4 || newPin != confirmNewPin),
                            boxSize = 48.dp,
                            testTag = "change_pin_confirm_input"
                        )

                        if (!feedbackMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = if (isSuccess) {
                                    VtuGreenPrimary.copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.errorContainer
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = feedbackMessage.orEmpty(),
                                    color = if (isSuccess) {
                                        VtuGreenPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .testTag("change_pin_feedback_message")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = submitChangePinAction,
                            enabled = !isLoading && oldPin.length == 4 && newPin.length == 4 && confirmNewPin.length == 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("change_pin_submit_button"),
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
                                    Text("Updating PIN...", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Text("Change PIN", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Bottom-Anchored Secure Numeric Keypad
            AnimatedVisibility(visible = focusedField in 0..2 && !isLoading) {
                SecureNumericKeypad(
                    enabled = !isLoading,
                    onDigitClick = { digit ->
                        if (!digit.all { it.isDigit() }) return@SecureNumericKeypad
                        feedbackMessage = null
                        when (focusedField) {
                            0 -> {
                                if (oldPin.length < 4) {
                                    val next = oldPin + digit
                                    oldPin = next
                                    if (next.length == 4 && newPin.length < 4) {
                                        focusedField = 1
                                    }
                                }
                            }
                            1 -> {
                                if (newPin.length < 4) {
                                    val next = newPin + digit
                                    newPin = next
                                    if (next.length == 4 && confirmNewPin.length < 4) {
                                        focusedField = 2
                                    }
                                }
                            }
                            2 -> {
                                if (confirmNewPin.length < 4) {
                                    confirmNewPin += digit
                                }
                            }
                        }
                    },
                    onBackspaceClick = {
                        feedbackMessage = null
                        when (focusedField) {
                            0 -> {
                                if (oldPin.isNotEmpty()) {
                                    oldPin = oldPin.dropLast(1)
                                }
                            }
                            1 -> {
                                if (newPin.isNotEmpty()) {
                                    newPin = newPin.dropLast(1)
                                } else if (oldPin.isNotEmpty()) {
                                    focusedField = 0
                                    oldPin = oldPin.dropLast(1)
                                }
                            }
                            2 -> {
                                if (confirmNewPin.isNotEmpty()) {
                                    confirmNewPin = confirmNewPin.dropLast(1)
                                } else if (newPin.isNotEmpty()) {
                                    focusedField = 1
                                    newPin = newPin.dropLast(1)
                                }
                            }
                        }
                    },
                    onDoneClick = {
                        if (oldPin.length == 4 && newPin.length == 4 && confirmNewPin.length == 4) {
                            submitChangePinAction()
                        } else {
                            focusedField = -1
                        }
                    }
                )
            }
        }
    }
}
