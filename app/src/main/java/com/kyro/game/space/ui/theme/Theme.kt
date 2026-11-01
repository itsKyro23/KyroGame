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
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF775653),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2E6E5),
    onSecondaryContainer = Color(0xFF2C1513),
    tertiary = Color(0xFF006874),
    onTertiary = Color.White,
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFFD3D6DC)
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
