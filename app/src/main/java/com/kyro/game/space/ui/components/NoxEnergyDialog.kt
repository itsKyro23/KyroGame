package com.kyro.game.space.ui.components

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.kyro.game.space.ads.AdMobManager
import com.kyro.game.space.data.energy.NoxEnergyManager
import com.kyro.game.space.ui.theme.KyroCyan
import com.kyro.game.space.ui.theme.KyroGreen
import com.kyro.game.space.ui.theme.KyroOrange
import com.kyro.game.space.ui.theme.rememberResponsiveMetrics
import kotlinx.coroutines.delay

/**
 * Clickable Energy Badge for TopAppBar.
 */
@Composable
fun NoxEnergyBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val energy by NoxEnergyManager.energyState
    val isTampered by NoxEnergyManager.isTampered

    val badgeColor = when {
        isTampered || energy < 0 -> Color(0xFFFF3366)
        energy < 20 -> KyroOrange
        energy < 60 -> KyroCyan
        else -> KyroGreen
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = badgeColor.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f)),
        modifier = modifier.testTag("nox_energy_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = if (isTampered || energy < 0) Icons.Default.Warning else Icons.Default.Bolt,
                contentDescription = "Energy",
                tint = badgeColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (isTampered || energy < 0) "PENALTY: $energy" else "$energy/${NoxEnergyManager.MAX_ENERGY}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = badgeColor,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * Polished Cyberpunk Energy Top Up & Status Dialog.
 */
@Composable
fun NoxEnergyTopUpDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val responsive = rememberResponsiveMetrics()
    val energy by NoxEnergyManager.energyState
    val isTampered by NoxEnergyManager.isTampered

    var isWaitingFallback by remember { mutableStateOf(false) }
    var fallbackSecondsLeft by remember { mutableIntStateOf(20) }

    LaunchedEffect(isWaitingFallback) {
        if (isWaitingFallback) {
            fallbackSecondsLeft = 20
            while (fallbackSecondsLeft > 0) {
                delay(1000L)
                fallbackSecondsLeft--
            }
            val updated = NoxEnergyManager.addEnergyFromAd(context)
            Toast.makeText(context, "+40 Energy added! Total: $updated⚡", Toast.LENGTH_SHORT).show()
            isWaitingFallback = false
            onDismiss()
        }
    }

    val progressFraction = (energy.coerceAtLeast(0).toFloat() / NoxEnergyManager.MAX_ENERGY.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "energy_anim_progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_halo")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    AlertDialog(
        onDismissRequest = {
            if (!isWaitingFallback) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .widthIn(max = responsive.dialogMaxWidth),
        containerColor = Color(0xFF0C0E17),
        shape = RoundedCornerShape(26.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = (if (isTampered || energy < 0) Color(0xFFFF3366) else KyroCyan).copy(alpha = 0.18f),
                    border = BorderStroke(
                        1.5.dp,
                        (if (isTampered || energy < 0) Color(0xFFFF3366) else KyroCyan).copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .size(44.dp)
                        .scale(if (!isTampered) pulseScale else 1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isTampered || energy < 0) Icons.Default.Warning else Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (isTampered || energy < 0) Color(0xFFFF3366) else KyroCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isTampered || energy < 0) "SECURITY INTEGRITY ALERT" else "NOX ENERGY CELL",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        color = Color.White
                    )
                    Text(
                        text = if (isTampered || energy < 0) "Tamper Violation Detected" else "Gaming Optimization Power Vault",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8E99B3),
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = responsive.dialogMaxHeight * 0.68f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isTampered || energy < 0) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0x2BFF3366),
                        border = BorderStroke(1.dp, Color(0xFFFF3366))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = Color(0xFFFF3366),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "UNFAIR TAMPER DETECTED",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFFF3366)
                                )
                            }
                            Text(
                                text = "Anti-hack integrity system identified an external database edit or memory manipulation. Energy is forcefully locked to -120 penalty.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE2E8F0),
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else {
                    // Futuristic Energy Gauge Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF131726),
                        border = BorderStroke(1.dp, KyroCyan.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = "CURRENT ENERGY",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF8E99B3),
                                        letterSpacing = 0.8.sp
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "$energy",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            color = KyroCyan
                                        )
                                        Text(
                                            text = " / ${NoxEnergyManager.MAX_ENERGY} ⚡",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF7683A3)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = KyroGreen.copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, KyroGreen.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "+40⚡ ON AD",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = KyroGreen,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // High-tech battery bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1E2438))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(animatedProgress)
                                        .height(12.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(KyroCyan, Color(0xFF00E676))
                                            )
                                        )
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "0⚡ Empty",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = Color(0xFF6B7280)
                                )
                                Text(
                                    text = "Initial: ${NoxEnergyManager.INITIAL_ENERGY}⚡",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = Color(0xFF8E99B3)
                                )
                                Text(
                                    text = "Max: ${NoxEnergyManager.MAX_ENERGY}⚡",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = KyroGreen
                                )
                            }
                        }
                    }

                    // Cyber Cost Breakdown Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF131726),
                        border = BorderStroke(1.dp, Color(0xFF232840)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "FEATURE ENERGY COSTS (DEDUCTED ON APPLY ONLY)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp,
                                color = Color(0xFF8E99B3),
                                letterSpacing = 0.6.sp
                            )

                            // 1. Lightweight Texture Tuner
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesomeMosaic,
                                        contentDescription = null,
                                        tint = Color(0xFF56B4E9),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Lightweight Texture",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Single: 20⚡ • All games: 40⚡",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF8E99B3),
                                            fontSize = 9.5.sp
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF56B4E9).copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "20 / 40⚡",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF56B4E9),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0xFF1E2438))

                            // 2. Resolution Changer
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AspectRatio,
                                        contentDescription = null,
                                        tint = KyroCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Resolution Changer",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Display downscaling & auto DPI sync",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF8E99B3),
                                            fontSize = 9.5.sp
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = KyroCyan.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "15⚡",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = KyroCyan,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0xFF1E2438))

                            // 3. Game Tune
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = KyroOrange,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Game Tune",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Per-game FPS limit & overlay mode",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF8E99B3),
                                            fontSize = 9.5.sp
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = KyroOrange.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "10⚡",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = KyroOrange,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0xFF1E2438))

                            // 4. AOT Game DEX Compiler
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = KyroGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "AOT Game DEX Compiler",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Native ARM64 ahead-of-time DEX compilation",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF8E99B3),
                                            fontSize = 9.5.sp
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = KyroGreen.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "15⚡",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = KyroGreen,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isWaitingFallback) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = KyroOrange.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, KyroOrange.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Ad currently unavailable • Please wait ${fallbackSecondsLeft}s to claim +40⚡...",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = KyroOrange
                                )
                                LinearProgressIndicator(
                                    progress = { (20 - fallbackSecondsLeft) / 20f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = KyroOrange,
                                    trackColor = Color(0xFF1E2438)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isTampered && energy >= 0) {
                Button(
                    onClick = {
                        if (isWaitingFallback) return@Button
                        val activity = context as? Activity
                        if (activity != null) {
                            AdMobManager.showRewardedAd(
                                activity = activity,
                                onReward = {
                                    val updated = NoxEnergyManager.addEnergyFromAd(context)
                                    Toast.makeText(context, "+40 Energy added! Total: $updated⚡", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                },
                                onAdUnavailable = {
                                    isWaitingFallback = true
                                }
                            )
                        } else {
                            isWaitingFallback = true
                        }
                    },
                    enabled = !isWaitingFallback,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KyroCyan,
                        contentColor = Color(0xFF07121D),
                        disabledContainerColor = KyroOrange.copy(alpha = 0.25f),
                        disabledContentColor = KyroOrange
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("watch_ad_energy_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isWaitingFallback) "Wait ${fallbackSecondsLeft}s (+40⚡)" else "Watch Ad (+40⚡)",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    isWaitingFallback = false
                    onDismiss()
                }
            ) {
                Text(
                    text = "Close",
                    color = Color(0xFFA0A7BD),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

/**
 * Polished Cyberpunk Insufficient Energy Dialog with 1-click Watch Ad action.
 */
@Composable
fun NoxInsufficientEnergyDialog(
    featureName: String,
    requiredCost: Int,
    onDismiss: () -> Unit,
    onEnergyGranted: () -> Unit
) {
    val context = LocalContext.current
    val responsive = rememberResponsiveMetrics()
    val energy by NoxEnergyManager.energyState
    val isTampered by NoxEnergyManager.isTampered

    var isWaitingFallback by remember { mutableStateOf(false) }
    var fallbackSecondsLeft by remember { mutableIntStateOf(20) }

    LaunchedEffect(isWaitingFallback) {
        if (isWaitingFallback) {
            fallbackSecondsLeft = 20
            while (fallbackSecondsLeft > 0) {
                delay(1000L)
                fallbackSecondsLeft--
            }
            val updated = NoxEnergyManager.addEnergyFromAd(context)
            Toast.makeText(context, "+40 Energy added! Total: $updated⚡", Toast.LENGTH_SHORT).show()
            isWaitingFallback = false
            if (updated >= requiredCost) {
                onEnergyGranted()
            }
            onDismiss()
        }
    }

    val deficit = (requiredCost - energy).coerceAtLeast(0)

    AlertDialog(
        onDismissRequest = {
            if (!isWaitingFallback) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .widthIn(max = responsive.dialogMaxWidth),
        containerColor = Color(0xFF0C0E17),
        shape = RoundedCornerShape(26.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = KyroOrange.copy(alpha = 0.18f),
                    border = BorderStroke(1.5.dp, KyroOrange.copy(alpha = 0.6f)),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = KyroOrange,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "INSUFFICIENT ENERGY",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.1.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Quick Recharge Available",
                        style = MaterialTheme.typography.labelSmall,
                        color = KyroOrange,
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = responsive.dialogMaxHeight * 0.68f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isTampered || energy < 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x2BFF3366),
                        border = BorderStroke(1.dp, Color(0xFFFF3366))
                    ) {
                        Text(
                            text = "⚠️ Security alert: Energy is penalized to -120 due to detected external tamper.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    // Deficit breakdown card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF131726),
                        border = BorderStroke(1.dp, KyroOrange.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Applying $featureName",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Required Energy:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF8E99B3)
                                )
                                Text(
                                    text = "$requiredCost⚡",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Current Energy:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF8E99B3)
                                )
                                Text(
                                    text = "$energy⚡",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = KyroOrange
                                )
                            }

                            HorizontalDivider(color = Color(0xFF1E2438))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Shortfall:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF8E99B3)
                                )
                                Text(
                                    text = "-$deficit⚡",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFFF4D4D)
                                )
                            }
                        }
                    }

                    // Boost Promo Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isWaitingFallback) KyroOrange.copy(alpha = 0.14f) else KyroCyan.copy(alpha = 0.12f),
                        border = BorderStroke(
                            1.dp,
                            if (isWaitingFallback) KyroOrange.copy(alpha = 0.5f) else KyroCyan.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isWaitingFallback) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Ad currently unavailable • Please wait ${fallbackSecondsLeft}s to claim +40⚡...",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = KyroOrange
                                )
                                LinearProgressIndicator(
                                    progress = { (20 - fallbackSecondsLeft) / 20f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = KyroOrange,
                                    trackColor = Color(0xFF1E2438)
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = KyroCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Watch a quick Rewarded Ad to instantly recharge +40⚡ energy and continue with your optimization!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFDEE8F5),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isTampered && energy >= 0) {
                Button(
                    onClick = {
                        if (isWaitingFallback) return@Button
                        val activity = context as? Activity
                        if (activity != null) {
                            AdMobManager.showRewardedAd(
                                activity = activity,
                                onReward = {
                                    val updated = NoxEnergyManager.addEnergyFromAd(context)
                                    Toast.makeText(context, "+40 Energy added! Total: $updated⚡", Toast.LENGTH_SHORT).show()
                                    if (updated >= requiredCost) {
                                        onEnergyGranted()
                                    }
                                    onDismiss()
                                },
                                onAdUnavailable = {
                                    isWaitingFallback = true
                                }
                            )
                        } else {
                            isWaitingFallback = true
                        }
                    },
                    enabled = !isWaitingFallback,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KyroCyan,
                        contentColor = Color(0xFF07121D),
                        disabledContainerColor = KyroOrange.copy(alpha = 0.25f),
                        disabledContentColor = KyroOrange
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isWaitingFallback) "Wait ${fallbackSecondsLeft}s (+40⚡)" else "Watch Ad (+40⚡)",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    isWaitingFallback = false
                    onDismiss()
                }
            ) {
                Text(
                    text = "Cancel",
                    color = Color(0xFFA0A7BD),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}
