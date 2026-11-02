package com.kyro.game.space.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyro.game.space.data.model.GameItem
import com.kyro.game.space.data.model.TextureConfig
import com.kyro.game.space.data.model.TextureDetailLevel
import com.kyro.game.space.data.model.TextureQuickPreset
import com.kyro.game.space.data.repository.GameSpaceRepository
import com.kyro.game.space.ui.theme.KyroCyan
import com.kyro.game.space.ui.theme.KyroGreen
import com.kyro.game.space.ui.theme.KyroOrange
import com.kyro.game.space.util.AppIconCache
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

private val TextureSkyBlue = Color(0xFF56B4E9)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextureTunerScreen(
    repository: GameSpaceRepository,
    allApps: List<GameItem>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tunerState by repository.textureTunerState.collectAsState()
    val connectionOverview by repository.connectionState.collectAsState()

    val config = tunerState.currentConfig
    val compat = tunerState.compatibility

    var splitFraction by remember { mutableFloatStateOf(0.50f) }
    var showTargetPickerDialog by remember { mutableStateOf(false) }

    val holdBeforeInteraction = remember { MutableInteractionSource() }
    val isHoldingBefore by holdBeforeInteraction.collectIsPressedAsState()
    val effectiveSplit = if (isHoldingBefore) 1.0f else splitFraction

    val selectedGameItem = remember(tunerState.selectedTargetPackage, allApps) {
        allApps.firstOrNull { it.packageName == tunerState.selectedTargetPackage }
    }

    val targetTitle = selectedGameItem?.appName
        ?: if (tunerState.selectedTargetPackage.isNotBlank()) {
            tunerState.selectedTargetPackage
        } else {
            "Global GPU Profile (All 3D Games)"
        }

    val targetSubtitle = if (selectedGameItem != null) {
        "Active target for ${selectedGameItem.appName}"
    } else if (tunerState.selectedTargetPackage.isNotBlank()) {
        "Target package: ${tunerState.selectedTargetPackage}"
    } else {
        "Applies to all OpenGL ES & Vulkan games on device"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("texture_tuner_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Device & GPU Compatibility Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gpu_compatibility_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(
                    1.dp,
                    if (compat.isSupported) KyroGreen.copy(alpha = 0.45f) else KyroOrange.copy(alpha = 0.45f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = (if (compat.isSupported) KyroGreen else KyroOrange).copy(alpha = 0.16f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = if (compat.isSupported) KyroGreen else KyroOrange,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "DEVICE & GPU COMPATIBILITY",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.7.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = (if (compat.isSupported) KyroGreen else KyroOrange).copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = if (compat.isSupported) "SUPPORTED" else "LIMITED",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (compat.isSupported) KyroGreen else KyroOrange,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${compat.gpuVendor} • ${compat.gpuRenderer}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = KyroCyan,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val updated = repository.refreshTextureCompatibility()
                                    Toast.makeText(
                                        context,
                                        updated.compatibilityBadge,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("recheck_texture_compat_btn")
                        ) {
                            if (tunerState.isCheckingCompatibility) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 2.dp,
                                    color = KyroCyan
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Check Compatibility",
                                    modifier = Modifier.size(14.dp),
                                    tint = KyroCyan
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Check GPU",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompatibilityFeatureChip(
                            label = compat.glEsVersion,
                            enabled = true
                        )
                        CompatibilityFeatureChip(
                            label = if (compat.supportsVulkan) "Vulkan Hardware" else "OpenGL Driver",
                            enabled = true
                        )
                        CompatibilityFeatureChip(
                            label = "Mipmap LOD Bias",
                            enabled = compat.supportsLodBiasControl
                        )
                        CompatibilityFeatureChip(
                            label = "GL_NEAREST Sharp Pixel",
                            enabled = compat.supportsNearestNeighborFilter
                        )
                        CompatibilityFeatureChip(
                            label = "Engine: ${connectionOverview.method.name}",
                            enabled = true
                        )
                    }

                    Text(
                        text = compat.compatibilitySummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // 2. Interactive Before / After Texture Split Preview Card + Target Game Selector
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("texture_preview_card"),
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
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Split-Screen Interactive Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(225.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .borderWithRadius()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    if (size.width > 0) {
                                        splitFraction = (offset.x / size.width.toFloat()).coerceIn(0.08f, 0.92f)
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    if (size.width > 0) {
                                        splitFraction = (change.position.x / size.width.toFloat()).coerceIn(0.08f, 0.92f)
                                    }
                                }
                            }
                            .testTag("texture_before_after_canvas")
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val splitX = size.width * effectiveSplit

                            // Left Side: BEFORE (Original High-Detail Texture)
                            clipRect(left = 0f, top = 0f, right = splitX, bottom = size.height) {
                                drawBrickAndGroundScene(
                                    detailLevel = TextureDetailLevel.ORIGINAL,
                                    sharpPixels = false,
                                    hardDistanceCutoff = false
                                )
                            }

                            // Right Side: AFTER (Live Filtered / Blocky / Smooth / Pixelated Texture)
                            clipRect(left = splitX, top = 0f, right = size.width, bottom = size.height) {
                                drawBrickAndGroundScene(
                                    detailLevel = config.detailLevel,
                                    sharpPixels = config.sharpPixels,
                                    hardDistanceCutoff = config.hardDistanceCutoff
                                )
                            }

                            // Vertical Split Line
                            drawLine(
                                color = Color.White.copy(alpha = 0.92f),
                                start = Offset(splitX, 0f),
                                end = Offset(splitX, size.height),
                                strokeWidth = 2.5.dp.toPx()
                            )

                            // Center Split Handle Circle
                            val centerY = size.height * 0.52f
                            drawCircle(
                                color = Color.Black.copy(alpha = 0.45f),
                                radius = 18.dp.toPx(),
                                center = Offset(splitX, centerY)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 15.dp.toPx(),
                                center = Offset(splitX, centerY)
                            )
                        }

                        // Center Handle Icon overlay
                        Box(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Top-left "BEFORE" label
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "BEFORE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            // Top-right "AFTER" label
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = if (isHoldingBefore) "HOLDING: BEFORE" else "AFTER (${config.detailLevel.label.uppercase()})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = if (isHoldingBefore) KyroOrange else TextureSkyBlue,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            // Bottom-left active GLES filter mode tag
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "${config.filterModeLabel} • LOD +${config.detailLevel.lodBias}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            // Bottom-right "Hold: Before" button (matching "Tahan: Sebelum" in screenshot)
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isHoldingBefore) {
                                    TextureSkyBlue
                                } else {
                                    Color.Black.copy(alpha = 0.72f)
                                },
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp)
                                    .clickable(
                                        interactionSource = holdBeforeInteraction,
                                        indication = null,
                                        onClick = {}
                                    )
                                    .testTag("hold_before_preview_btn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TouchApp,
                                        contentDescription = "Hold for Before Preview",
                                        tint = if (isHoldingBefore) Color.Black else Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Hold: Before",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isHoldingBefore) Color.Black else Color.White,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // Target Game Selector Row (matches the "Free Fire MAX — Activate for Free Fire MAX" bar)
                    Surface(
                        onClick = { showTargetPickerDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0E0E15),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("texture_target_selector_bar")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                val iconBmp = remember(selectedGameItem?.packageName) {
                                    selectedGameItem?.packageName?.let { pkg ->
                                        AppIconCache.getIcon(context, pkg)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = TextureSkyBlue.copy(alpha = 0.16f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    if (iconBmp != null) {
                                        Image(
                                            bitmap = iconBmp.asImageBitmap(),
                                            contentDescription = targetTitle,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (selectedGameItem != null) {
                                                    Icons.Default.SportsEsports
                                                } else {
                                                    Icons.Default.Public
                                                },
                                                contentDescription = null,
                                                tint = TextureSkyBlue,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = targetTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = targetSubtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = TextureSkyBlue.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, TextureSkyBlue.copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        tint = TextureSkyBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Select Game",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextureSkyBlue,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Quick Presets + Texture Detail Slider + Sharp Pixels & Hard Distance Cutoff Toggles
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("texture_controls_card"),
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // QUICK PRESETS (Smooth / Retro / Total Blocky / Custom)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "QUICK PRESETS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0C0E14),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                TextureQuickPreset.entries.forEach { preset ->
                                    val isSelected = config.preset == preset
                                    Surface(
                                        onClick = {
                                            val updated = if (preset == TextureQuickPreset.CUSTOM) {
                                                config.copy(preset = TextureQuickPreset.CUSTOM)
                                            } else {
                                                TextureConfig(
                                                    preset = preset,
                                                    detailLevel = preset.defaultDetail,
                                                    sharpPixels = preset.defaultSharpPixels,
                                                    hardDistanceCutoff = preset.defaultHardCutoff
                                                )
                                            }
                                            repository.updateTextureConfigDraft(updated)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) TextureSkyBlue else Color.Transparent,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .testTag("texture_preset_${preset.name.lowercase()}")
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        ) {
                                            Text(
                                                text = preset.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) {
                                                    Color(0xFF061320)
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TEXTURE DETAIL SLIDER (5 Steps: Original, Light, Retro, Blocky, Extreme)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Texture Detail",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = config.detailLevel.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextureSkyBlue
                            )
                        }

                        Slider(
                            value = config.detailLevel.stepIndex.toFloat(),
                            onValueChange = { raw ->
                                val newLevel = TextureDetailLevel.fromStep(raw.roundToInt())
                                val matchedPreset = TextureQuickPreset.resolveMatchingPreset(
                                    detailLevel = newLevel,
                                    sharpPixels = config.sharpPixels,
                                    hardCutoff = config.hardDistanceCutoff
                                )
                                repository.updateTextureConfigDraft(
                                    config.copy(
                                        preset = matchedPreset,
                                        detailLevel = newLevel
                                    )
                                )
                            },
                            valueRange = 0f..4f,
                            steps = 3,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = TextureSkyBlue,
                                inactiveTrackColor = Color(0xFF232836),
                                activeTickColor = Color.White,
                                inactiveTickColor = Color(0xFF55607A)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("texture_detail_slider")
                        )

                        // Step labels row (Original, Light, Retro, Blocky, Extreme)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextureDetailLevel.entries.forEach { level ->
                                val active = config.detailLevel == level
                                Surface(
                                    onClick = {
                                        val matchedPreset = TextureQuickPreset.resolveMatchingPreset(
                                            detailLevel = level,
                                            sharpPixels = config.sharpPixels,
                                            hardCutoff = config.hardDistanceCutoff
                                        )
                                        repository.updateTextureConfigDraft(
                                            config.copy(
                                                preset = matchedPreset,
                                                detailLevel = level
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (active) {
                                        TextureSkyBlue.copy(alpha = 0.15f)
                                    } else {
                                        Color.Transparent
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = when (level) {
                                                TextureDetailLevel.ORIGINAL -> Icons.Default.GridOn
                                                TextureDetailLevel.LIGHT -> Icons.Default.BlurOn
                                                TextureDetailLevel.RETRO -> Icons.Default.AutoAwesomeMosaic
                                                TextureDetailLevel.BLOCKY -> Icons.Default.GridView
                                                TextureDetailLevel.EXTREME -> Icons.Default.Compare
                                            },
                                            contentDescription = null,
                                            tint = if (active) TextureSkyBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = level.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                            color = if (active) TextureSkyBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // TWO FILTER TOGGLE CARDS (Sharp Pixels & Hard Distance Cutoff)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Card 1: Sharp Pixels (Piksel Tajam)
                        FilterToggleCard(
                            title = "Sharp Pixels",
                            description = "Crisp blocky pixel texture edges. Turn off for smooth bilinear blur.",
                            checked = config.sharpPixels,
                            onCheckedChange = { enabled ->
                                val matchedPreset = TextureQuickPreset.resolveMatchingPreset(
                                    detailLevel = config.detailLevel,
                                    sharpPixels = enabled,
                                    hardCutoff = config.hardDistanceCutoff
                                )
                                repository.updateTextureConfigDraft(
                                    config.copy(
                                        preset = matchedPreset,
                                        sharpPixels = enabled
                                    )
                                )
                            },
                            testTag = "toggle_sharp_pixels",
                            modifier = Modifier.weight(1f)
                        )

                        // Card 2: Hard Distance Cutoff (Batas Jarak Tegas)
                        FilterToggleCard(
                            title = "Hard Distance Cutoff",
                            description = "Near-to-far mipmap detail transitions become sharply stepped.",
                            checked = config.hardDistanceCutoff,
                            onCheckedChange = { enabled ->
                                val matchedPreset = TextureQuickPreset.resolveMatchingPreset(
                                    detailLevel = config.detailLevel,
                                    sharpPixels = config.sharpPixels,
                                    hardCutoff = enabled
                                )
                                repository.updateTextureConfigDraft(
                                    config.copy(
                                        preset = matchedPreset,
                                        hardDistanceCutoff = enabled
                                    )
                                )
                            },
                            testTag = "toggle_hard_distance_cutoff",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Helpful In-Game Tips (matching the 2 tips in the screenshot)
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Changed while playing? Close the game and reopen it to reload GPU textures.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = KyroOrange,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Most noticeable when in-game graphics quality is set to Smooth / Low.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Live GPU Command Preview Box
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0A0C12),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = TextureSkyBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = config.buildShellCommand(tunerState.selectedTargetPackage),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Action Buttons Row (Reset + Save / Apply)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val res = repository.resetTextureConfigToOriginal()
                                    val msg = res.getOrElse { "Reset to original textures" }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("reset_texture_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Reset",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    val res = repository.saveAndApplyTextureConfig()
                                    val msg = res.getOrElse { "Texture settings saved & applied!" }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TextureSkyBlue,
                                contentColor = Color(0xFF061320)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("apply_texture_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (tunerState.isApplying) "Applying..." else "Save & Apply",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTargetPickerDialog) {
        val gameCandidates = remember(allApps) {
            allApps.sortedWith(compareByDescending<GameItem> { it.isAddedToHome }.thenByDescending { it.isGame }.thenBy { it.appName })
        }

        AlertDialog(
            onDismissRequest = { showTargetPickerDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(22.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = TextureSkyBlue
                    )
                    Column {
                        Text(
                            text = "Select Target Game",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Apply Lightweight Texture globally or per game",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        val isGlobalSelected = tunerState.selectedTargetPackage.isBlank()
                        Surface(
                            onClick = {
                                repository.selectTextureTargetPackage("")
                                showTargetPickerDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isGlobalSelected) {
                                TextureSkyBlue.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isGlobalSelected) TextureSkyBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = TextureSkyBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Global GPU Profile (All 3D Games)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "System-wide OpenGL ES / HWUI / EGL LOD bias",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isGlobalSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = TextureSkyBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    items(items = gameCandidates, key = { it.packageName }) { game ->
                        val isSelected = tunerState.selectedTargetPackage == game.packageName
                        val bmp = remember(game.packageName) {
                            AppIconCache.getIcon(context, game.packageName)
                        }
                        Surface(
                            onClick = {
                                repository.selectTextureTargetPackage(game.packageName)
                                showTargetPickerDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) {
                                TextureSkyBlue.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) TextureSkyBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.DarkGray,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = game.appName,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.SportsEsports,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = game.appName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = game.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = TextureSkyBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTargetPickerDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun CompatibilityFeatureChip(
    label: String,
    enabled: Boolean
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) {
            KyroGreen.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (enabled) KyroGreen.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (enabled) KyroGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun FilterToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0E1017),
        border = BorderStroke(
            width = 1.dp,
            color = if (checked) {
                TextureSkyBlue.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            }
        ),
        modifier = modifier.testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF061320),
                        checkedTrackColor = TextureSkyBlue
                    )
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                lineHeight = 13.sp
            )
        }
    }
}

private fun Modifier.borderWithRadius(): Modifier = this

/**
 * Procedurally renders the 3D Brick Wall + Receding Grassy Ground scene
 * matching the reference screenshot, with live Mipmap LOD Bias (`detailLevel`),
 * Nearest-Neighbor vs Bilinear (`sharpPixels`), and Hard Mipmap Distance Cutoff (`hardDistanceCutoff`).
 */
private fun DrawScope.drawBrickAndGroundScene(
    detailLevel: TextureDetailLevel,
    sharpPixels: Boolean,
    hardDistanceCutoff: Boolean
) {
    val w = size.width
    val h = size.height
    val horizonY = h * 0.44f

    // Pixel block size driven by TextureDetailLevel (1x, 2x, 4x, 8x, 16x)
    val baseCellPx = when (detailLevel) {
        TextureDetailLevel.ORIGINAL -> 3.5f
        TextureDetailLevel.LIGHT -> 7f
        TextureDetailLevel.RETRO -> 13f
        TextureDetailLevel.BLOCKY -> 24f
        TextureDetailLevel.EXTREME -> 42f
    }.coerceAtLeast(2f)

    // 1. Top Half: Brick Wall Texture
    val brickW = 44f
    val brickH = 20f
    val mortarThickness = if (detailLevel == TextureDetailLevel.EXTREME) 0f else 2.5f

    val cols = (w / baseCellPx).toInt().coerceAtLeast(1)
    val wallRows = (horizonY / baseCellPx).toInt().coerceAtLeast(1)

    for (ry in 0..wallRows) {
        val y = ry * baseCellPx
        if (y >= horizonY) break
        val cellH = (baseCellPx + 0.8f).coerceAtMost(horizonY - y)

        for (cx in 0..cols) {
            val x = cx * baseCellPx
            if (x >= w) break
            val cellW = (baseCellPx + 0.8f).coerceAtMost(w - x)

            val sampleX = x + baseCellPx * 0.5f
            val sampleY = y + baseCellPx * 0.5f

            val brickRow = floor(sampleY / brickH).toInt()
            val rowOffset = if (brickRow % 2 == 0) 0f else brickW * 0.5f
            val localX = (sampleX + rowOffset) % brickW
            val localY = sampleY % brickH
            val brickCol = floor((sampleX + rowOffset) / brickW).toInt()

            val isMortar = localX < mortarThickness || localY < mortarThickness
            val hash = ((brickRow * 37 + brickCol * 17) and 0xFF) / 255f

            // Fine surface grain for Original/Light
            val fineNoise = if (detailLevel == TextureDetailLevel.ORIGINAL) {
                (((cx * 13 + ry * 29) % 11) - 5) * 0.008f
            } else {
                0f
            }

            val rawColor = if (isMortar && detailLevel.stepIndex <= 2) {
                Color(0xFF3E352D)
            } else {
                val r = (0.56f + hash * 0.14f + fineNoise).coerceIn(0.25f, 0.82f)
                val g = (0.45f + hash * 0.11f + fineNoise).coerceIn(0.20f, 0.70f)
                val b = (0.35f + hash * 0.09f + fineNoise).coerceIn(0.15f, 0.60f)
                Color(r, g, b)
            }

            val finalWallColor = if (!sharpPixels && detailLevel != TextureDetailLevel.ORIGINAL) {
                // Smooth bilinear-filtered blend toward warm average stone color
                val avgStone = Color(0xFF8A725B)
                val blendFactor = (detailLevel.stepIndex * 0.16f).coerceIn(0.15f, 0.65f)
                lerpColor(rawColor, avgStone, blendFactor)
            } else {
                rawColor
            }

            drawRect(
                color = finalWallColor,
                topLeft = Offset(x, y),
                size = Size(cellW, cellH)
            )
        }
    }

    // Shadow line at wall base
    drawRect(
        color = Color(0xFF221D18).copy(alpha = 0.65f),
        topLeft = Offset(0f, (horizonY - 4f).coerceAtLeast(0f)),
        size = Size(w, 6f)
    )

    // 2. Bottom Half: Receding Grassy & Dirt Ground Plane
    val groundHeight = (h - horizonY).coerceAtLeast(1f)
    val groundRows = (groundHeight / baseCellPx).toInt().coerceAtLeast(1)

    for (ry in 0..groundRows) {
        val y = horizonY + ry * baseCellPx
        if (y >= h) break
        val cellH = (baseCellPx + 0.8f).coerceAtMost(h - y)

        // Normalized depth from 0.0 (far wall base) to 1.0 (near camera bottom)
        val rawDepth = ((y - horizonY) / groundHeight).coerceIn(0.01f, 1f)
        val depth = if (hardDistanceCutoff) {
            // Hard stepped distance bands (Mipmap LOD cutoff)
            (floor(rawDepth * 3.0f) / 3.0f).coerceIn(0.05f, 1f)
        } else {
            rawDepth
        }

        // Row cell width scales with distance cutoff or perspective
        val rowStepPx = if (hardDistanceCutoff && detailLevel.stepIndex >= 2) {
            baseCellPx * (if (rawDepth < 0.45f) 1.8f else 1.0f)
        } else {
            baseCellPx
        }
        val rowCols = (w / rowStepPx).toInt().coerceAtLeast(1)

        for (cx in 0..rowCols) {
            val x = cx * rowStepPx
            if (x >= w) break
            val cellW = (rowStepPx + 0.8f).coerceAtMost(w - x)

            // Perspective projection coordinates for dirt streaks & grass blades
            val centerOffset = (x + rowStepPx * 0.5f - w * 0.5f) / (depth * 0.85f + 0.35f)
            val worldX = (centerOffset / 18f).roundToInt()
            val worldZ = ((1f / (depth + 0.18f)) * 12f).roundToInt()

            val pattern = ((worldX * 19 + worldZ * 31) and 0x7F) / 127f
            val isGrassPatch = ((worldX + worldZ) % 3 == 0) || pattern > 0.56f

            val baseGround = if (isGrassPatch) {
                // Olive / Mossy Grass tone
                Color(
                    red = (0.32f + pattern * 0.10f).coerceIn(0.2f, 0.55f),
                    green = (0.48f + pattern * 0.16f).coerceIn(0.3f, 0.72f),
                    blue = (0.20f + pattern * 0.08f).coerceIn(0.1f, 0.38f)
                )
            } else {
                // Warm Earth / Dirt tone
                Color(
                    red = (0.46f + pattern * 0.12f).coerceIn(0.3f, 0.65f),
                    green = (0.37f + pattern * 0.09f).coerceIn(0.22f, 0.52f),
                    blue = (0.24f + pattern * 0.07f).coerceIn(0.14f, 0.38f)
                )
            }

            val shadedGround = lerpColor(
                baseGround,
                Color(0xFF2B261E),
                (1f - depth) * 0.35f
            )

            val finalGroundColor = if (!sharpPixels && detailLevel != TextureDetailLevel.ORIGINAL) {
                val smoothBlend = Color(0xFF615938)
                val factor = (detailLevel.stepIndex * 0.17f).coerceIn(0.18f, 0.68f)
                lerpColor(shadedGround, smoothBlend, factor)
            } else {
                shadedGround
            }

            drawRect(
                color = finalGroundColor,
                topLeft = Offset(x, y),
                size = Size(cellW, cellH)
            )
        }

        // Draw visible mipmap distance transition line when Hard Distance Cutoff is ON
        if (hardDistanceCutoff) {
            val cutoff1Y = horizonY + groundHeight * 0.34f
            val cutoff2Y = horizonY + groundHeight * 0.67f
            if (kotlin.math.abs(y - cutoff1Y) < baseCellPx * 0.5f ||
                kotlin.math.abs(y - cutoff2Y) < baseCellPx * 0.5f
            ) {
                drawLine(
                    color = Color.Black.copy(alpha = 0.28f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.5f
                )
            }
        }
    }

    // Subtle vignette gradient at top & bottom for depth
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.25f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.30f)
            )
        ),
        size = size
    )
}

private fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (stop.red - start.red) * t,
        green = start.green + (stop.green - start.green) * t,
        blue = start.blue + (stop.blue - start.blue) * t,
        alpha = 1f
    )
}
