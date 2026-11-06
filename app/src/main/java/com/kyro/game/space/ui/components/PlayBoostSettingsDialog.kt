package com.kyro.game.space.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.kyro.game.space.data.model.PlayBoostConfig
import com.kyro.game.space.data.model.PlayBoostTier
import com.kyro.game.space.ui.theme.KyroAccent
import com.kyro.game.space.ui.theme.KyroCyan
import com.kyro.game.space.ui.theme.KyroGreen
import com.kyro.game.space.ui.theme.KyroOrange
import com.kyro.game.space.ui.theme.KyroPrimary
import com.kyro.game.space.ui.theme.rememberResponsiveMetrics

@Composable
fun PlayBoostSettingsDialog(
    initialConfig: PlayBoostConfig,
    onSaveConfig: (PlayBoostConfig) -> Unit,
    onRestoreDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val responsive = rememberResponsiveMetrics()

    var deepRamPurge by remember(initialConfig) { mutableStateOf(initialConfig.deepRamPurge) }
    var forceStopBackgroundApps by remember(initialConfig) { mutableStateOf(initialConfig.forceStopBackgroundApps) }
    var surfaceFlingerZeroLag by remember(initialConfig) { mutableStateOf(initialConfig.surfaceFlingerZeroLag) }
    var ultraTouchResponse by remember(initialConfig) { mutableStateOf(initialConfig.ultraTouchResponse) }
    var blockHeadsUpNotifications by remember(initialConfig) { mutableStateOf(initialConfig.blockHeadsUpNotifications) }

    val activeTier = remember(
        deepRamPurge,
        forceStopBackgroundApps,
        surfaceFlingerZeroLag,
        ultraTouchResponse,
        blockHeadsUpNotifications
    ) {
        PlayBoostConfig.resolveMatchingTier(
            deepRamPurge = deepRamPurge,
            forceStopBackgroundApps = forceStopBackgroundApps,
            surfaceFlingerZeroLag = surfaceFlingerZeroLag,
            ultraTouchResponse = ultraTouchResponse,
            blockHeadsUpNotifications = blockHeadsUpNotifications
        )
    }

    val tierAccentColor = when (activeTier) {
        PlayBoostTier.ECO_PULSE -> KyroGreen
        PlayBoostTier.VELOCITY_CORE -> KyroCyan
        PlayBoostTier.NOX_OVERDRIVE -> KyroAccent
        PlayBoostTier.CUSTOM -> KyroOrange
    }

    val applyTierPreset: (PlayBoostTier) -> Unit = { tier ->
        val preset = PlayBoostConfig.forTier(tier)
        deepRamPurge = preset.deepRamPurge
        forceStopBackgroundApps = preset.forceStopBackgroundApps
        surfaceFlingerZeroLag = preset.surfaceFlingerZeroLag
        ultraTouchResponse = preset.ultraTouchResponse
        blockHeadsUpNotifications = preset.blockHeadsUpNotifications
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = Color(0xFF0D101A),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .widthIn(max = responsive.dialogMaxWidth)
            .testTag("play_boost_settings_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = tierAccentColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.5.dp, tierAccentColor.copy(alpha = 0.5f)),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = tierAccentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PLAY AUTO-BOOST SETTINGS",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.9.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Auto-executed when tapping Play on any game",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8E99B3),
                            fontSize = 10.5.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = tierAccentColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, tierAccentColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = activeTier.title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = tierAccentColor,
                        fontSize = 9.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Pinned Preset Tier Selector Row (Eco Pulse / Velocity Core / Nox Overdrive)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "AUTO-BOOST PRESET TIER",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8E99B3),
                        fontSize = 10.sp,
                        letterSpacing = 0.7.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BoostTierChipCard(
                            tier = PlayBoostTier.ECO_PULSE,
                            accentColor = KyroGreen,
                            selected = activeTier == PlayBoostTier.ECO_PULSE,
                            onClick = { applyTierPreset(PlayBoostTier.ECO_PULSE) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tier_eco_pulse_btn")
                        )
                        BoostTierChipCard(
                            tier = PlayBoostTier.VELOCITY_CORE,
                            accentColor = KyroCyan,
                            selected = activeTier == PlayBoostTier.VELOCITY_CORE,
                            onClick = { applyTierPreset(PlayBoostTier.VELOCITY_CORE) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tier_velocity_core_btn")
                        )
                        BoostTierChipCard(
                            tier = PlayBoostTier.NOX_OVERDRIVE,
                            accentColor = KyroAccent,
                            selected = activeTier == PlayBoostTier.NOX_OVERDRIVE,
                            onClick = { applyTierPreset(PlayBoostTier.NOX_OVERDRIVE) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tier_nox_overdrive_btn")
                        )
                    }

                    // Active Tier Summary Banner
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = tierAccentColor.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, tierAccentColor.copy(alpha = 0.30f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = activeTier.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD5DCEC),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF1F263B))

                // 2. Scrollable 5-Stage Thermal-Safe Execution Checklist
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 310.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "REAL PRE-LAUNCH EXECUTION CHECKLIST",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8E99B3),
                            fontSize = 10.sp,
                            letterSpacing = 0.7.sp
                        )
                        val count = listOf(
                            deepRamPurge,
                            forceStopBackgroundApps,
                            surfaceFlingerZeroLag,
                            ultraTouchResponse,
                            blockHeadsUpNotifications
                        ).count { it }
                        Text(
                            text = "$count/${PlayBoostConfig.TOTAL_STAGES} Active",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = tierAccentColor,
                            fontSize = 10.5.sp
                        )
                    }

                    BoostChecklistItemRow(
                        title = "Deep RAM & Cache Purge",
                        subtitle = "Purges inactive background memory and temporary app caches before launching the game.",
                        icon = Icons.Default.Memory,
                        accentColor = KyroGreen,
                        checked = deepRamPurge,
                        onCheckedChange = { deepRamPurge = it },
                        testTag = "chk_deep_ram"
                    )

                    BoostChecklistItemRow(
                        title = "Force-Stop Background Apps",
                        subtitle = "Stops non-essential third-party background apps so they do not consume CPU or RAM while gaming.",
                        icon = Icons.Default.Security,
                        accentColor = KyroAccent,
                        checked = forceStopBackgroundApps,
                        onCheckedChange = { forceStopBackgroundApps = it },
                        testTag = "chk_force_stop_bg"
                    )

                    BoostChecklistItemRow(
                        title = "SurfaceFlinger Zero-Lag Compositor",
                        subtitle = "Optimizes display frame buffer queuing to minimize screen rendering and VSync latency.",
                        icon = Icons.Default.Speed,
                        accentColor = KyroCyan,
                        checked = surfaceFlingerZeroLag,
                        onCheckedChange = { surfaceFlingerZeroLag = it },
                        testTag = "chk_sf_zero_lag"
                    )

                    BoostChecklistItemRow(
                        title = "Ultra Touch Polling & Sensitivity",
                        subtitle = "Maximizes touch pointer responsiveness and removes system touch filtering delay.",
                        icon = Icons.Default.Tune,
                        accentColor = KyroCyan,
                        checked = ultraTouchResponse,
                        onCheckedChange = { ultraTouchResponse = it },
                        testTag = "chk_ultra_touch"
                    )

                    BoostChecklistItemRow(
                        title = "Block Heads-Up Pop-Up Notifications",
                        subtitle = "Blocks floating pop-up notification banners from covering the top of the screen during gameplay.",
                        icon = Icons.Default.Notifications,
                        accentColor = KyroGreen,
                        checked = blockHeadsUpNotifications,
                        onCheckedChange = { blockHeadsUpNotifications = it },
                        testTag = "chk_block_headsup"
                    )

                    // Restore System Defaults Action
                    OutlinedButton(
                        onClick = onRestoreDefaults,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF2D354F)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_boost_defaults_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color(0xFFA2AEC6),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Restore Stock System Defaults (Display, Touch & Pop-Up)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA2AEC6),
                            fontSize = 10.5.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newCfg = PlayBoostConfig(
                        tier = activeTier,
                        deepRamPurge = deepRamPurge,
                        forceStopBackgroundApps = forceStopBackgroundApps,
                        surfaceFlingerZeroLag = surfaceFlingerZeroLag,
                        ultraTouchResponse = ultraTouchResponse,
                        blockHeadsUpNotifications = blockHeadsUpNotifications
                    )
                    onSaveConfig(newCfg)
                    Toast.makeText(
                        context,
                        "Play Boost saved: ${activeTier.title} (${newCfg.activeStagesCount}/${PlayBoostConfig.TOTAL_STAGES} stages active)",
                        Toast.LENGTH_SHORT
                    ).show()
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KyroPrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("save_play_boost_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Save Settings",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_play_boost_btn")
            ) {
                Text(
                    text = "Cancel",
                    color = Color(0xFF8E99B3),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
private fun BoostTierChipCard(
    tier: PlayBoostTier,
    accentColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accentColor.copy(alpha = 0.16f) else Color(0xFF141826),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accentColor else Color(0xFF232A40)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = tier.levelTag,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (selected) accentColor else Color(0xFF7B86A2),
                fontSize = 8.5.sp,
                maxLines = 1
            )
            Text(
                text = tier.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = if (selected) Color.White else Color(0xFFB5BFD6),
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BoostChecklistItemRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        color = if (checked) Color(0xFF151B2C) else Color(0xFF101420),
        border = BorderStroke(
            width = 1.dp,
            color = if (checked) accentColor.copy(alpha = 0.45f) else Color(0xFF1F263A)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = accentColor,
                    checkmarkColor = Color.Black,
                    uncheckedColor = Color(0xFF535E7A)
                ),
                modifier = Modifier.size(22.dp)
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) accentColor else Color(0xFF687494),
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (checked) Color.White else Color(0xFFAAB4CA),
                    fontSize = 12.sp
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF818DA8),
                    fontSize = 10.sp,
                    lineHeight = 13.5.sp
                )
            }
        }
    }
}
