package com.kyro.game.space.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

@Composable
fun GameSpaceTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Strictly enforce OLED Dark Theme throughout the app
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
