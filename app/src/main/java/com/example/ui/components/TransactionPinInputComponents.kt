package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.VtuGreenPrimary

/**
 * Individual rounded-square PIN boxes row matching the architecture in the reference image:
 * - 4 distinct rounded-square boxes arranged horizontally
 * - Active slot displays a highlighted border and vertical cursor bar `|`
 * - Entered digits are always masked with a solid dot
 * - Tapping the boxes focuses this PIN field and activates the custom bottom numeric keypad
 */
@Composable
fun TransactionPinBoxesField(
    value: String,
    isFocused: Boolean,
    onFocusRequest: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    pinLength: Int = 4,
    enabled: Boolean = true,
    isError: Boolean = false,
    boxSize: Dp = 54.dp,
    testTag: String = "transaction_pin_input"
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pin_cursor_blink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .semantics {
                contentDescription = label ?: "4-Digit Transaction PIN"
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                onFocusRequest()
            }
    ) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isFocused) VtuGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Container surrounding the 4 individual PIN boxes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val activeCursorIndex = value.length.coerceAtMost(pinLength - 1)

            repeat(pinLength) { index ->
                val isFilled = index < value.length
                val isCurrentCursorSlot = isFocused && enabled && (
                    index == value.length || (value.length == pinLength && index == pinLength - 1)
                )

                val borderColor = when {
                    isError -> MaterialTheme.colorScheme.error
                    isCurrentCursorSlot -> VtuGreenPrimary
                    isFilled && isFocused -> VtuGreenPrimary.copy(alpha = 0.55f)
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
                }

                val borderWidth = if (isCurrentCursorSlot || isError) 1.8.dp else 1.dp

                val boxBackground = when {
                    isCurrentCursorSlot -> MaterialTheme.colorScheme.surface
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                }

                Box(
                    modifier = Modifier
                        .size(boxSize)
                        .clip(RoundedCornerShape(10.dp))
                        .background(boxBackground)
                        .border(
                            width = borderWidth,
                            color = borderColor,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = enabled
                        ) {
                            onFocusRequest()
                        }
                        .testTag("${testTag}_box_$index"),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isFilled -> {
                            // Masked PIN dot
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface)
                            )
                        }
                        isFocused && enabled && index == activeCursorIndex && value.length < pinLength -> {
                            // Vertical cursor line '|' inside the active box
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(22.dp)
                                    .alpha(cursorAlpha)
                                    .background(VtuGreenPrimary, RoundedCornerShape(1.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bottom-anchored Secure Numeric Keypad matching the architecture in the reference image:
 * - Top bar with centered security shield + label and right-aligned "Done" action
 * - 4 rows of rectangular rounded key tiles:
 *   Row 1: [ 1 ] [ 2 ] [ 3 ]
 *   Row 2: [ 4 ] [ 5 ] [ 6 ]
 *   Row 3: [ 7 ] [ 8 ] [ 9 ]
 *   Row 4: [     0     ] [ ⌫ ] (where 0 spans 2 columns and backspace is in column 3)
 */
@Composable
fun SecureNumericKeypad(
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    includeNavigationBarsPadding: Boolean = true
) {
    val trayBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    val keySurfaceColor = MaterialTheme.colorScheme.surface

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("secure_numeric_keypad"),
        color = trayBackground,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (includeNavigationBarsPadding) {
                        Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    } else {
                        Modifier
                    }
                )
                .padding(bottom = 8.dp)
        ) {
            // Keypad Header Bar: centered shield + title, right-aligned "Done"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = VtuGreenPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Secure Numeric Keypad",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = "Done",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = VtuGreenPrimary,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = enabled) { onDoneClick() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("pin_keypad_done")
                )
            }

            // 4-Row Rectangular Tile Grid
            val gap = 8.dp
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                val cellWidth = (maxWidth - gap * 2) / 3
                val zeroWidth = cellWidth * 2 + gap

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    val numberRows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9")
                    )

                    numberRows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            row.forEach { digit ->
                                NumericKeyTile(
                                    modifier = Modifier
                                        .width(cellWidth)
                                        .height(52.dp)
                                        .testTag("pin_key_$digit"),
                                    backgroundColor = keySurfaceColor,
                                    enabled = enabled,
                                    onClick = { onDigitClick(digit) }
                                ) {
                                    Text(
                                        text = digit,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Row: [      0      ] (2 columns wide) + [ ⌫ ] (1 column wide)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NumericKeyTile(
                            modifier = Modifier
                                .width(zeroWidth)
                                .height(52.dp)
                                .testTag("pin_key_0"),
                            backgroundColor = keySurfaceColor,
                            enabled = enabled,
                            onClick = { onDigitClick("0") }
                        ) {
                            Text(
                                text = "0",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }

                        NumericKeyTile(
                            modifier = Modifier
                                .width(cellWidth)
                                .height(52.dp)
                                .testTag("pin_key_DEL"),
                            backgroundColor = keySurfaceColor,
                            enabled = enabled,
                            onClick = onBackspaceClick
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Backspace",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumericKeyTile(
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        shadowElevation = 0.5.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}
