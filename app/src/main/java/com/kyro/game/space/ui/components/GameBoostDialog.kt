package com.kyro.game.space.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyro.game.space.data.model.GameItem
import com.kyro.game.space.data.model.PlayBoostTier
import com.kyro.game.space.data.repository.GameSpaceRepository
import com.kyro.game.space.ui.theme.KyroAccent
import com.kyro.game.space.ui.theme.KyroCyan
import com.kyro.game.space.ui.theme.KyroGreen
import com.kyro.game.space.ui.theme.KyroOrange
import com.kyro.game.space.ui.theme.KyroPrimary
import com.kyro.game.space.util.AppIconCache
import kotlinx.coroutines.launch

@Composable
fun GameBoostDialog(
    game: GameItem,
    repository: GameSpaceRepository,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val boostConfig by repository.playBoostConfig.collectAsState()

    var progress by remember { mutableFloatStateOf(0.05f) }
    var statusText by remember { mutableStateOf("Preparing game environment...") }
    var isCompleted by remember { mutableStateOf(false) }
    var isAppeared by remember { mutableStateOf(false) }

    val tierColor = when (boostConfig.tier) {
        PlayBoostTier.ECO_PULSE -> KyroGreen
        PlayBoostTier.VELOCITY_CORE -> KyroCyan
        PlayBoostTier.NOX_OVERDRIVE -> KyroAccent
        PlayBoostTier.CUSTOM -> KyroOrange
    }

    val enterScale by animateFloatAsState(
        targetValue = if (isAppeared) 1f else 0.88f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "boost_dialog_scale"
    )

    val enterAlpha by animateFloatAsState(
        targetValue = if (isAppeared) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "boost_dialog_alpha"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "boost_progress"
    )

    val gameIcon = remember(game.packageName) {
        AppIconCache.getIcon(context, game.packageName)
    }

    LaunchedEffect(Unit) {
        isAppeared = true
    }

    LaunchedEffect(game.packageName) {
        repository.boostAndLaunchGame(game.packageName) { stepProgress, stepMsg ->
            progress = stepProgress
            statusText = stepMsg
            if (stepProgress >= 1.0f) {
                isCompleted = true
            }
        }
        // Delay at 100% so user clearly sees completion before dismiss
        kotlinx.coroutines.delay(700)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = {
            if (isCompleted) onDismiss()
        },
        containerColor = Color(0xFF0F111A),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.testTag("game_boost_dialog"),
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(enterScale)
                    .alpha(enterAlpha)
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = tierColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, tierColor.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = tierColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "${boostConfig.tier.title.uppercase()} • PRE-LAUNCH BOOST",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = tierColor,
                            fontSize = 10.sp
                        )
                    }
                }

                // App Icon with pulsing circular glow
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(86.dp)) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(86.dp),
                        strokeWidth = 4.dp,
                        color = tierColor,
                        trackColor = Color(0xFF1E2235)
                    )

                    Surface(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        color = Color(0xFF181C29)
                    ) {
                        if (gameIcon != null) {
                            Image(
                                bitmap = gameIcon.asImageBitmap(),
                                contentDescription = game.appName,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = tierColor,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                // Game Name & Percentage
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = game.appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "${(animatedProgress * 100).toInt()}% OPTIMIZED",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompleted) KyroGreen else tierColor,
                        fontSize = 14.sp
                    )
                }

                // Linear Progress bar
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = tierColor,
                    trackColor = Color(0xFF1A1F30)
                )

                // Current Action / Status readout
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF141724),
                    border = BorderStroke(1.dp, Color(0xFF23283B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isCompleted) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = KyroGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = tierColor
                            )
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFC0CDDE),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Dynamic Optimization feature checklist based on PlayBoostConfig
                val activeSteps = remember(boostConfig) { boostConfig.activeStepLabels() }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    activeSteps.forEachIndexed { idx, stepLabel ->
                        val threshold = ((idx + 1).toFloat() / activeSteps.size.toFloat()) * 0.94f
                        BoostStepIndicator(
                            label = stepLabel,
                            isPassed = animatedProgress >= threshold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    repository.launchGame(game.packageName)
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KyroPrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("boost_instant_launch_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isCompleted) "Launching..." else "Instant Play",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF32374E)),
                modifier = Modifier.testTag("boost_cancel_btn")
            ) {
                Text(
                    text = "Cancel",
                    color = Color(0xFFA0A7BD)
                )
            }
        }
    )
}

@Composable
private fun BoostStepIndicator(
    label: String,
    isPassed: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (isPassed) Icons.Default.CheckCircle else Icons.Default.Speed,
            contentDescription = null,
            tint = if (isPassed) KyroGreen else Color(0xFF454C68),
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isPassed) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isPassed) Color.White else Color(0xFF6B7494),
            fontSize = 11.sp
        )
    }
}
