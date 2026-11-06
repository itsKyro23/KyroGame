package com.kyro.game.space.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.kyro.game.space.data.model.ResolutionPreset
import com.kyro.game.space.data.model.ResolutionState
import com.kyro.game.space.data.repository.GameSpaceRepository
import com.kyro.game.space.ui.theme.KyroCyan
import com.kyro.game.space.ui.theme.KyroGreen
import com.kyro.game.space.ui.theme.KyroOrange
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ResolutionChangerScreen(
    repository: GameSpaceRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val responsive = com.kyro.game.space.ui.theme.rememberResponsiveMetrics()
    val scope = rememberCoroutineScope()
    val resState by repository.resolutionState.collectAsState()

    var showInsufficientEnergyDialog by remember { mutableStateOf(false) }
    var pendingApplyWidth by remember { mutableStateOf<Int?>(null) }

    var sliderWidth by remember(resState.currentWidth, resState.maxWidth) {
        mutableIntStateOf(resState.currentWidth.coerceIn(resState.minWidth, resState.maxWidth))
    }

    val tryApplyResolution: (Int) -> Unit = { targetWidth ->
        val cost = com.kyro.game.space.data.energy.NoxEnergyManager.COST_RESOLUTION_CHANGER
        if (!com.kyro.game.space.data.energy.NoxEnergyManager.hasEnergy(context, cost)) {
            pendingApplyWidth = targetWidth
            showInsufficientEnergyDialog = true
        } else {
            com.kyro.game.space.data.energy.NoxEnergyManager.consumeEnergy(context, cost)
            scope.launch {
                repository.applyResolutionWithSafetyCountdown(targetWidth)
            }
        }
    }

    val previewHeight = remember(sliderWidth, resState.nativeWidth, resState.nativeHeight) {
        resState.computeHeightForWidth(sliderWidth)
    }
    val previewDpi = remember(sliderWidth, resState.nativeWidth, resState.nativeDensityDpi, resState.autoScaleDensity) {
        if (resState.autoScaleDensity) {
            resState.computeDensityForWidth(sliderWidth)
        } else {
            resState.nativeDensityDpi
        }
    }
    val scalePercent = remember(sliderWidth, resState.nativeWidth) {
        ((sliderWidth.toDouble() / resState.nativeWidth.toDouble()) * 100.0).roundToInt().coerceIn(25, 100)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("resolution_changer_screen"),
        contentPadding = PaddingValues(
            start = responsive.screenHorizontalPadding,
            end = responsive.screenHorizontalPadding,
            top = 8.dp,
            bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(responsive.sectionSpacing)
    ) {
        // 1. Main Resolution Changer Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("resolution_changer_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AspectRatio,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "RESOLUTION CHANGER",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Hardware Max: ${resState.nativeWidth}×${resState.nativeHeight} • Min: 360p",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (resState.isCustomModified) {
                                KyroOrange.copy(alpha = 0.15f)
                            } else {
                                KyroGreen.copy(alpha = 0.15f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (resState.isCustomModified) KyroOrange.copy(alpha = 0.4f) else KyroGreen.copy(alpha = 0.4f)
                            )
                        ) {
                            Text(
                                text = "Active: ${resState.currentWidth}×${resState.currentHeight}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (resState.isCustomModified) KyroOrange else KyroGreen,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // 10s Fail-Safe Protection Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0E0E14),
                        border = BorderStroke(1.dp, KyroCyan.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = KyroCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "10s Crash & Misclick Watchdog Active: Automatically reverts to original resolution if not saved within 10s or if app closes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Live Target Resolution Display Box
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "SELECTED TARGET RESOLUTION",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.6.sp
                                )
                                Text(
                                    text = "$sliderWidth × $previewHeight",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        text = "$scalePercent% Scale",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Density: $previewDpi DPI",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // Custom Resolution Slider (Min 360 .. Max Phone Supported Width)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Slider(
                            value = sliderWidth.toFloat(),
                            onValueChange = { raw ->
                                // Snap to even multiples of 8 pixels for clean GPU scaling
                                val stepped = ((raw.roundToInt() / 8) * 8).coerceIn(resState.minWidth, resState.maxWidth)
                                sliderWidth = stepped
                            },
                            valueRange = resState.minWidth.toFloat()..resState.maxWidth.toFloat().coerceAtLeast(362f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("resolution_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Min: 360×${resState.computeHeightForWidth(360)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "Max: ${resState.nativeWidth}×${resState.nativeHeight}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Auto-Scale Density Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = "Auto-Scale Screen Density (DPI)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Scales DPI proportionally ($previewDpi DPI) so buttons and UI never look oversized.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = resState.autoScaleDensity,
                            onCheckedChange = { repository.setAutoScaleDensity(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("switch_auto_scale_dpi")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    // Action Buttons: Apply Resolution (with 10s Countdown) + Reset Original
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                tryApplyResolution(sliderWidth)
                            },
                            enabled = !resState.isApplying && !resState.isPendingConfirmation,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(44.dp)
                                .testTag("apply_resolution_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Apply • 15⚡ ($sliderWidth×$previewHeight)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                sliderWidth = resState.nativeWidth
                                scope.launch {
                                    val res = repository.resetResolutionToNative()
                                    Toast.makeText(
                                        context,
                                        res.getOrElse { "Reset to native resolution" },
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            enabled = !resState.isApplying && !resState.isPendingConfirmation,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(0.85f)
                                .height(44.dp)
                                .testTag("reset_resolution_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = null,
                                tint = KyroCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Reset Native",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // Status Log
                    Text(
                        text = resState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // 2. Recommended Device-Specific Presets Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "RECOMMENDED PRESETS FOR THIS PHONE",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                }
            }
        }

        // 3. Preset Cards
        items(
            items = resState.presets,
            key = { it.id }
        ) { preset ->
            ResolutionPresetCard(
                preset = preset,
                isCurrentActive = resState.currentWidth == preset.width,
                isSliderSelected = abs(sliderWidth - preset.width) <= 8,
                onSelectPreset = {
                    sliderWidth = preset.width
                },
                onApplyPreset = {
                    sliderWidth = preset.width
                    tryApplyResolution(preset.width)
                }
            )
        }
    }

    if (showInsufficientEnergyDialog) {
        com.kyro.game.space.ui.components.NoxInsufficientEnergyDialog(
            featureName = "Resolution Changer",
            requiredCost = com.kyro.game.space.data.energy.NoxEnergyManager.COST_RESOLUTION_CHANGER,
            onDismiss = {
                showInsufficientEnergyDialog = false
                pendingApplyWidth = null
            },
            onEnergyGranted = {
                val target = pendingApplyWidth ?: sliderWidth
                tryApplyResolution(target)
            }
        )
    }

    // 10-Second Non-Cancelable Safety Confirmation Dialog
    if (resState.isPendingConfirmation) {
        ResolutionConfirmationDialog(
            resState = resState,
            onConfirmSave = {
                scope.launch {
                    val res = repository.confirmResolutionChange()
                    Toast.makeText(
                        context,
                        res.getOrElse { "Resolution saved!" },
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onRevertNow = {
                sliderWidth = resState.currentWidth
                scope.launch {
                    val res = repository.revertResolutionToOriginal("Canceled by user. Reverted to original resolution.")
                    Toast.makeText(
                        context,
                        res.getOrElse { "Reverted to original resolution" },
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}

@Composable
private fun ResolutionPresetCard(
    preset: ResolutionPreset,
    isCurrentActive: Boolean,
    isSliderSelected: Boolean,
    onSelectPreset: () -> Unit,
    onApplyPreset: () -> Unit
) {
    val badgeColor = if (preset.isRecommended) KyroGreen else MaterialTheme.colorScheme.primary

    Card(
        onClick = onSelectPreset,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("res_preset_${preset.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentActive || isSliderSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = BorderStroke(
            width = if (isCurrentActive || isSliderSelected) 1.5.dp else 1.dp,
            color = if (isCurrentActive) {
                KyroGreen
            } else if (isSliderSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = preset.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = preset.badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "${preset.resolutionLabel} (${preset.scalePercent}% • ${preset.densityDpi} DPI)",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
                Text(
                    text = preset.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = onApplyPreset,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCurrentActive) KyroGreen else MaterialTheme.colorScheme.primary,
                    contentColor = if (isCurrentActive) Color.Black else Color.White
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("apply_preset_${preset.id}")
            ) {
                if (isCurrentActive) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Active",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "Apply • 15⚡",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ResolutionConfirmationDialog(
    resState: ResolutionState,
    onConfirmSave: () -> Unit,
    onRevertNow: () -> Unit
) {
    val progress = (resState.countdownSeconds.toFloat() / ResolutionState.CONFIRMATION_TIMEOUT_SECONDS.toFloat())
        .coerceIn(0f, 1f)

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = KyroOrange.copy(alpha = 0.18f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${resState.countdownSeconds}s",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = KyroOrange
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Keep New Resolution?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${resState.pendingWidth} × ${resState.pendingHeight} (${resState.pendingDensity} DPI)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = KyroOrange,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Text(
                    text = "Please verify that the screen display looks clear and touch input works properly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Auto-Revert in ${resState.countdownSeconds} seconds: If you do not tap 'Save & Keep' (or if the app closes/crashes), resolution will automatically return to ${resState.currentWidth}×${resState.currentHeight}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmSave,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KyroGreen,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("confirm_save_resolution_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Save & Keep (${resState.countdownSeconds}s)",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onRevertNow,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.testTag("revert_resolution_now_button")
            ) {
                Text(
                    text = "Revert Now",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    )
}
