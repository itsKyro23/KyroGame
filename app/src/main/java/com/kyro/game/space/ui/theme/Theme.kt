package com.kyro.game.space.ui.theme

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
    primary = KyroAccent,
    onPrimary = KyroOnPrimary,
    primaryContainer = KyroPrimary,
    onPrimaryContainer = Color.White,
    secondary = KyroPrimaryLight,
    onSecondary = Color.White,
    secondaryContainer = KyroAccentDim,
    onSecondaryContainer = Color.White,
    tertiary = KyroCyan,
    onTertiary = Color.Black,
    background = KyroBackground,
    onBackground = KyroOnBackground,
    surface = KyroSurface,
    onSurface = KyroOnSurface,
    surfaceVariant = KyroSurfaceVariant,
    onSurfaceVariant = KyroOnSurfaceVariant,
    outline = KyroOutline
)

private val LightColorScheme = lightColorScheme(
    primary = KyroPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEBEE),
    onPrimaryContainer = Color(0xFFB71C1C),
    secondary = Color(0xFF5D5E66),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F3F8),
    onSecondaryContainer = Color(0xFF1E2024),
    tertiary = Color(0xFF007A87),
    onTertiary = Color.White,
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF16181B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16181B),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF5A5E69),
    outline = Color(0xFFE2E4EB)
)

@Composable
fun GameSpaceTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
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
