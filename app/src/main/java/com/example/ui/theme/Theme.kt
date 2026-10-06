package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = VtuGreenSecondary,
    onPrimary = Color.White,
    primaryContainer = VtuGreenDark,
    onPrimaryContainer = VtuGreenLight,
    secondary = VtuCyan,
    onSecondary = Color.White,
    secondaryContainer = VtuNavyCard,
    onSecondaryContainer = Color.White,
    tertiary = VtuGoldAccent,
    background = VtuNavyPrimary,
    onBackground = Color(0xFFF1F5F9),
    surface = VtuNavySurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = VtuNavyCard,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF334E68)
)

private val LightColorScheme = lightColorScheme(
    primary = VtuGreenPrimary,
    onPrimary = Color.White,
    primaryContainer = VtuGreenLight,
    onPrimaryContainer = VtuGreenDark,
    secondary = VtuNavyPrimary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = VtuNavyPrimary,
    tertiary = VtuGoldAccent,
    background = NeutralLightBg,
    onBackground = NeutralTextPrimary,
    surface = NeutralCardBg,
    onSurface = NeutralTextPrimary,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = NeutralTextSecondary,
    outline = NeutralBorder
)

@Composable
fun DanielVtuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our brand colors for fintech identity
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
