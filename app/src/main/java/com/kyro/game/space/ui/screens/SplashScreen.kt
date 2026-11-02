package com.kyro.game.space.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyro.game.space.data.model.ConnectionOverview
import com.kyro.game.space.ui.theme.KyroAccent
import com.kyro.game.space.ui.theme.KyroCyan
import com.kyro.game.space.ui.theme.KyroGreen
import com.kyro.game.space.ui.theme.KyroPrimary
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    overview: ConnectionOverview,
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var statusText by remember { mutableStateOf("Initializing Kyro Core Engine...") }
    var progressStep by remember { mutableStateOf(0.15f) }

    val infiniteTransition = rememberInfiniteTransition(label = "splash_glow")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    LaunchedEffect(Unit) {
        delay(600)
        statusText = "Initializing Kyro Core Engine & Hardware HAL..."
        progressStep = 0.25f
        delay(750)
        statusText = "Checking Shizuku • Wireless ADB • Root..."
        progressStep = 0.55f
        delay(750)
        statusText = "Probing GPU & ANGLE Vulkan subsystem..."
        progressStep = 0.80f
        delay(650)
        statusText = if (overview.isAnyPermissionActive) {
            "Permission Verified: ${overview.activePermissionName} Ready"
        } else {
            "Ready • Execution Backend Required"
        }
        progressStep = 1.0f
        delay(600)
        onSplashFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF090A10),
                        Color(0xFF0E101A),
                        Color(0xFF07080D)
                    )
                )
            )
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Decorative background glow
        Box(
            modifier = Modifier
                .size(280.dp)
                .scale(pulseScale)
                .alpha(glowAlpha * 0.25f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            KyroCyan,
                            KyroPrimary,
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Emblem
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(110.dp)
                    .scale(pulseScale)
            ) {
                // Outer ring
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color(0xFF131724))
                        .border(
                            2.dp,
                            Brush.sweepGradient(
                                listOf(
                                    KyroCyan,
                                    KyroPrimary,
                                    KyroAccent,
                                    KyroCyan
                                )
                            ),
                            CircleShape
                        )
                )

                // Icon
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = "Kyro Game Space",
                    tint = KyroCyan,
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // App Title
            Text(
                text = "KYRO GAME SPACE",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "TACTICAL PERFORMANCE & GAME BOOSTER",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = KyroCyan,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Progress bar
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF141724),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262C40)),
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressStep)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    KyroCyan,
                                    KyroPrimary,
                                    KyroGreen
                                )
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Status Ticker
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = KyroCyan
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFA5B2D0),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Tech feature pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SplashFeaturePill(text = "Shizuku", icon = Icons.Default.Bolt)
                SplashFeaturePill(text = "Wireless ADB", icon = Icons.Default.Memory)
                SplashFeaturePill(text = "Root (su)", icon = Icons.Default.Security)
            }
        }
    }
}

@Composable
private fun SplashFeaturePill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF141724),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF23283B))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = KyroCyan,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8E9CB8),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
