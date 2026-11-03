package com.kyro.game.space.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyro.game.space.data.model.AngleDriverMode
import com.kyro.game.space.data.model.GameItem
import com.kyro.game.space.data.model.TextureDetailLevel
import com.kyro.game.space.data.model.TextureQuickPreset
import com.kyro.game.space.data.repository.GameSpaceRepository
import com.kyro.game.space.ui.theme.KyroCyan
import com.kyro.game.space.ui.theme.KyroGreen
import com.kyro.game.space.ui.theme.KyroOrange
import com.kyro.game.space.util.AppIconCache
import kotlinx.coroutines.launch
import java.util.Locale
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
    var isHoldingBefore by remember { mutableStateOf(false) }
    var showTargetPickerDialog by remember { mutableStateOf(false) }
    var showInsufficientEnergyDialog by remember { mutableStateOf(false) }

    val effectiveSplit = if (isHoldingBefore) 1.0f else splitFraction

    // Pre-computed lightweight bitmaps so Canvas drawing is 2 GPU texture blits per frame (zero lag)
    val beforePreviewBitmap = remember {
        buildScenePreviewBitmap(
            detailLevel = TextureDetailLevel.ORIGINAL,
            sharpPixels = true,
            hardDistanceCutoff = false
        )
    }

    val afterPreviewBitmap = remember(
        config.detailLevel,
        config.sharpPixels,
        config.hardDistanceCutoff
    ) {
        buildScenePreviewBitmap(
            detailLevel = config.detailLevel,
            sharpPixels = config.sharpPixels,
            hardDistanceCutoff = config.hardDistanceCutoff
        )
    }

    val afterFilterQuality = if (config.sharpPixels || config.detailLevel == TextureDetailLevel.ORIGINAL) {
        FilterQuality.None
    } else {
        FilterQuality.Medium
    }

    val selectedGameItem = remember(tunerState.selectedTargetPackage, allApps) {
        if (tunerState.selectedTargetPackage.isBlank()) {
            null
        } else {
            allApps.firstOrNull { it.packageName == tunerState.selectedTargetPackage }
        }
    }

    val targetTitle = selectedGameItem?.appName
        ?: if (tunerState.selectedTargetPackage.isNotBlank()) {
            tunerState.selectedTargetPackage
        } else {
            "Global GPU Profile (All 3D Games)"
        }

    val targetSubtitle = if (selectedGameItem != null) {
        "${selectedGameItem.packageName} • ANGLE Driver"
    } else if (tunerState.selectedTargetPackage.isNotBlank()) {
        "${tunerState.selectedTargetPackage} • ANGLE Driver"
    } else {
        "Applies to all added Game Space Home games via ANGLE Driver"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("texture_tuner_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Device, GPU & ANGLE Driver Compatibility Card
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
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

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "GPU & ANGLE DRIVER",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.6.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${compat.gpuVendor} • ${compat.gpuRenderer}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = KyroCyan,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 15.sp
                            )
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
                            label = compat.angleDriverStatus,
                            enabled = compat.supportsAngleDriver
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
                            label = "Mode: ${connectionOverview.method.name}",
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
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp))
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
                            val canvasW = size.width.roundToInt().coerceAtLeast(1)
                            val canvasH = size.height.roundToInt().coerceAtLeast(1)
                            val dstSize = IntSize(canvasW, canvasH)
                            val splitX = size.width * effectiveSplit

                            // Left Side: BEFORE (Original High-Detail Texture)
                            clipRect(left = 0f, top = 0f, right = splitX, bottom = size.height) {
                                drawImage(
                                    image = beforePreviewBitmap,
                                    dstOffset = IntOffset.Zero,
                                    dstSize = dstSize,
                                    filterQuality = FilterQuality.None
                                )
                            }

                            // Right Side: AFTER (Live Filtered / Blocky / Smooth / Pixelated Texture)
                            if (splitX < size.width) {
                                clipRect(left = splitX, top = 0f, right = size.width, bottom = size.height) {
                                    drawImage(
                                        image = afterPreviewBitmap,
                                        dstOffset = IntOffset.Zero,
                                        dstSize = dstSize,
                                        filterQuality = afterFilterQuality
                                    )
                                }
                            }

                            // Vertical Split Line & Handle
                            if (!isHoldingBefore) {
                                drawLine(
                                    color = Color.White.copy(alpha = 0.92f),
                                    start = Offset(splitX, 0f),
                                    end = Offset(splitX, size.height),
                                    strokeWidth = 2.5.dp.toPx()
                                )

                                val centerY = size.height * 0.52f
                                drawCircle(
                                    color = Color.Black.copy(alpha = 0.45f),
                                    radius = 17.dp.toPx(),
                                    center = Offset(splitX, centerY)
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 14.dp.toPx(),
                                    center = Offset(splitX, centerY)
                                )
                            }
                        }

                        // Overlay Badges & Hold: Before Button
                        Box(modifier = Modifier.fillMaxSize()) {
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
                            val afterBadgeLabel = if (isHoldingBefore) {
                                "HOLDING: BEFORE"
                            } else if (!config.isActive) {
                                "AFTER (ORIGINAL)"
                            } else {
                                "AFTER (${config.preset.shortLabel.uppercase()} • ${config.detailLevel.label.uppercase()})"
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = afterBadgeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = if (isHoldingBefore) KyroOrange else TextureSkyBlue,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            // Bottom-left Live Filter & LOD Telemetry Pill
                            val lodFormatted = String.format(Locale.US, "+%.1f", config.detailLevel.lodBias)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.68f),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "${config.filterModeLabel} • LOD $lodFormatted • ANGLE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }

                            // Bottom-right "Hold: Before" button
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
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
                                            isHoldingBefore = true
                                            waitForUpOrCancellation()
                                            isHoldingBefore = false
                                        }
                                    }
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

                    // Target Game Selector Row
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val iconBmp = remember(tunerState.selectedTargetPackage) {
                                tunerState.selectedTargetPackage
                                    .takeIf { it.isNotBlank() }
                                    ?.let { pkg -> AppIconCache.getIcon(context, pkg)?.asImageBitmap() }
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = TextureSkyBlue.copy(alpha = 0.16f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                if (iconBmp != null) {
                                    Image(
                                        bitmap = iconBmp,
                                        contentDescription = targetTitle,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (tunerState.selectedTargetPackage.isNotBlank()) {
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

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
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
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 14.sp
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = TextureSkyBlue.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, TextureSkyBlue.copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }


        // 4. Quick Presets + Texture Detail Slider + Sharp Pixels & Hard Distance Cutoff Toggles
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "QUICK PRESETS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = config.summaryBadge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextureSkyBlue,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

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
                                    val isSelected = config.isActive && config.preset == preset
                                    Surface(
                                        onClick = {
                                            val updated = if (preset == TextureQuickPreset.CUSTOM) {
                                                val nextDetail = if (config.detailLevel == TextureDetailLevel.ORIGINAL) {
                                                    TextureDetailLevel.RETRO
                                                } else {
                                                    config.detailLevel
                                                }
                                                config.copy(
                                                    preset = TextureQuickPreset.CUSTOM,
                                                    detailLevel = nextDetail
                                                )
                                            } else {
                                                config.copy(
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
                                text = "${config.detailLevel.label} (${config.detailLevel.filterTag})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextureSkyBlue
                            )
                        }

                        Slider(
                            value = config.detailLevel.stepIndex.toFloat(),
                            onValueChange = { raw ->
                                val newLevel = TextureDetailLevel.fromStep(raw.roundToInt())
                                if (newLevel != config.detailLevel) {
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
                                }
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
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                                        TextureSkyBlue.copy(alpha = 0.18f)
                                    } else {
                                        Color.Transparent
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = level.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                            color = if (active) TextureSkyBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // TWO FILTER TOGGLE CARDS (Stacked full-width for responsive layout on all screen sizes)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterToggleCard(
                            title = "Sharp Pixels",
                            description = "Crisp blocky pixel texture edges (GL_NEAREST). Turn off for smooth clay blur (GL_LINEAR).",
                            checked = config.sharpPixels,
                            onCheckedChange = { enabled ->
                                val nextDetail = if (config.detailLevel == TextureDetailLevel.ORIGINAL) {
                                    TextureDetailLevel.RETRO
                                } else {
                                    config.detailLevel
                                }
                                val matchedPreset = TextureQuickPreset.resolveMatchingPreset(
                                    detailLevel = nextDetail,
                                    sharpPixels = enabled,
                                    hardCutoff = config.hardDistanceCutoff
                                )
                                repository.updateTextureConfigDraft(
                                    config.copy(
                                        preset = matchedPreset,
                                        detailLevel = nextDetail,
                                        sharpPixels = enabled
                                    )
                                )
                            },
                            testTag = "toggle_sharp_pixels",
                            modifier = Modifier.fillMaxWidth()
                        )

                        FilterToggleCard(
                            title = "Hard Distance Cutoff",
                            description = "Near-to-far mipmap detail transitions become sharply stepped.",
                            checked = config.hardDistanceCutoff,
                            onCheckedChange = { enabled ->
                                val nextDetail = if (config.detailLevel == TextureDetailLevel.ORIGINAL) {
                                    TextureDetailLevel.BLOCKY
                                } else {
                                    config.detailLevel
                                }
                                val matchedPreset = TextureQuickPreset.resolveMatchingPreset(
                                    detailLevel = nextDetail,
                                    sharpPixels = config.sharpPixels,
                                    hardCutoff = enabled
                                )
                                repository.updateTextureConfigDraft(
                                    config.copy(
                                        preset = matchedPreset,
                                        detailLevel = nextDetail,
                                        hardDistanceCutoff = enabled
                                    )
                                )
                            },
                            testTag = "toggle_hard_distance_cutoff",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Live Status Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (tunerState.isAppliedLive) {
                            KyroGreen.copy(alpha = 0.12f)
                        } else {
                            TextureSkyBlue.copy(alpha = 0.10f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (tunerState.isAppliedLive) {
                                KyroGreen.copy(alpha = 0.35f)
                            } else {
                                TextureSkyBlue.copy(alpha = 0.30f)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (tunerState.isAppliedLive) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (tunerState.isAppliedLive) KyroGreen else TextureSkyBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = tunerState.statusMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Helpful In-Game Tips
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
                                text = "Save & Apply automatically force-stops the target game so ANGLE & GPU textures reload cleanly.",
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
                                text = "Most noticeable when in-game graphics quality is set to Smooth / Low with ANGLE enabled.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
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

                        val isAllGames = tunerState.selectedTargetPackage.isBlank()
                        val requiredTextureEnergy = if (isAllGames) {
                            com.kyro.game.space.data.energy.NoxEnergyManager.COST_LIGHTWEIGHT_TEXTURE_ALL
                        } else {
                            com.kyro.game.space.data.energy.NoxEnergyManager.COST_LIGHTWEIGHT_TEXTURE_SINGLE
                        }

                        Button(
                            onClick = {
                                if (!com.kyro.game.space.data.energy.NoxEnergyManager.hasEnergy(context, requiredTextureEnergy)) {
                                    showInsufficientEnergyDialog = true
                                } else {
                                    com.kyro.game.space.data.energy.NoxEnergyManager.consumeEnergy(context, requiredTextureEnergy)
                                    scope.launch {
                                        val res = repository.saveAndApplyTextureConfig()
                                        val msg = res.getOrElse { "Texture & ANGLE settings saved & applied!" }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
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
                                text = if (tunerState.isApplying) "Applying..." else "Save & Apply • ${requiredTextureEnergy}⚡",
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
            allApps
                .filter { it.isAddedToHome }
                .sortedBy { it.appName.lowercase() }
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
                            text = "Showing only games added to your Game Space Home",
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
                                        text = "Global GPU Profile (All Home Games)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Applies ANGLE Driver & OpenGL ES LOD bias to all added Home games",
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

                    if (gameCandidates.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SportsEsports,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "No Games Added Yet",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Add your games from the Apps tab first so they appear here.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(items = gameCandidates, key = { it.packageName }) { game ->
                        val isSelected = tunerState.selectedTargetPackage == game.packageName
                        val gameIconBitmap = remember(game.packageName) {
                            AppIconCache.getIcon(context, game.packageName)?.asImageBitmap()
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
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = TextureSkyBlue.copy(alpha = 0.14f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    if (gameIconBitmap != null) {
                                        Image(
                                            bitmap = gameIconBitmap,
                                            contentDescription = game.appName,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.SportsEsports,
                                                contentDescription = null,
                                                tint = TextureSkyBlue,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = game.appName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (game.isGame || game.isAddedToHome) {
                                            Surface(
                                                shape = RoundedCornerShape(5.dp),
                                                color = TextureSkyBlue.copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = "GAME",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextureSkyBlue,
                                                    fontSize = 8.sp,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
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

    if (showInsufficientEnergyDialog) {
        val isAllGames = tunerState.selectedTargetPackage.isBlank()
        val requiredTextureEnergy = if (isAllGames) {
            com.kyro.game.space.data.energy.NoxEnergyManager.COST_LIGHTWEIGHT_TEXTURE_ALL
        } else {
            com.kyro.game.space.data.energy.NoxEnergyManager.COST_LIGHTWEIGHT_TEXTURE_SINGLE
        }
        com.kyro.game.space.ui.components.NoxInsufficientEnergyDialog(
            featureName = if (isAllGames) "Lightweight Texture (All Games)" else "Lightweight Texture ($targetTitle)",
            requiredCost = requiredTextureEnergy,
            onDismiss = { showInsufficientEnergyDialog = false },
            onEnergyGranted = {
                if (com.kyro.game.space.data.energy.NoxEnergyManager.consumeEnergy(context, requiredTextureEnergy)) {
                    scope.launch {
                        val res = repository.saveAndApplyTextureConfig()
                        val msg = res.getOrElse { "Texture & ANGLE settings saved & applied!" }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF061320),
                    checkedTrackColor = TextureSkyBlue
                )
            )
        }
    }
}

/**
 * Generates a pre-cached 120x76 ImageBitmap of the 3D Brick Wall + Grassy Ground scene.
 * When `sharpPixels` is false (Smooth mode), it downsamples the mipmap buffer and applies real
 * bilinear filtering (`createScaledBitmap(..., filter = true)`) so Smooth vs Retro vs Blocky
 * produces an immediate, realistic visual difference with zero frame lag.
 */
private fun buildScenePreviewBitmap(
    detailLevel: TextureDetailLevel,
    sharpPixels: Boolean,
    hardDistanceCutoff: Boolean
): ImageBitmap {
    val width = 120
    val height = 76
    val horizonY = 34
    val pixels = IntArray(width * height)

    val stepBlock = when (detailLevel) {
        TextureDetailLevel.ORIGINAL -> 1
        TextureDetailLevel.LIGHT -> 2
        TextureDetailLevel.RETRO -> 4
        TextureDetailLevel.BLOCKY -> 8
        TextureDetailLevel.EXTREME -> 15
    }

    val brickW = 16
    val brickH = 8

    for (y in 0 until height) {
        val qy = (y / stepBlock) * stepBlock
        if (y < horizonY) {
            val brickRow = qy / brickH
            val rowOffset = if (brickRow % 2 == 0) 0 else brickW / 2
            val localY = qy % brickH

            for (x in 0 until width) {
                val qx = (x / stepBlock) * stepBlock
                val shiftedX = qx + rowOffset
                val localX = shiftedX % brickW
                val brickCol = shiftedX / brickW

                val isMortar = (detailLevel.stepIndex <= 2) && (localX == 0 || localY == 0)
                val hash = ((brickRow * 37 + brickCol * 17) and 0xFF) / 255f
                val grain = if (detailLevel == TextureDetailLevel.ORIGINAL) {
                    (((x * 13 + y * 29) % 9) - 4) * 0.008f
                } else 0f

                var r: Float
                var g: Float
                var b: Float

                if (isMortar) {
                    r = 0.24f
                    g = 0.21f
                    b = 0.18f
                } else {
                    r = (0.56f + hash * 0.15f + grain).coerceIn(0.25f, 0.82f)
                    g = (0.45f + hash * 0.12f + grain).coerceIn(0.20f, 0.70f)
                    b = (0.35f + hash * 0.09f + grain).coerceIn(0.15f, 0.60f)
                }

                pixels[y * width + x] = packRgb(r, g, b)
            }
        } else if (y == horizonY) {
            val wallShadow = packRgb(0.14f, 0.12f, 0.10f)
            for (x in 0 until width) {
                pixels[y * width + x] = wallShadow
            }
        } else {
            val groundSpan = (height - horizonY).coerceAtLeast(1).toFloat()
            val rawDepth = ((qy - horizonY).coerceAtLeast(1)) / groundSpan
            val depth = if (hardDistanceCutoff) {
                (floor(rawDepth * 3.0f) / 3.0f).coerceIn(0.08f, 1f)
            } else {
                rawDepth.coerceIn(0.05f, 1f)
            }

            val rowStep = if (hardDistanceCutoff && detailLevel.stepIndex >= 2 && rawDepth < 0.48f) {
                (stepBlock * 2).coerceAtMost(24)
            } else {
                stepBlock
            }

            val isCutoffLine = hardDistanceCutoff &&
                (y == horizonY + (groundSpan * 0.34f).toInt() || y == horizonY + (groundSpan * 0.67f).toInt())

            for (x in 0 until width) {
                if (isCutoffLine) {
                    pixels[y * width + x] = packRgb(0.16f, 0.15f, 0.12f)
                    continue
                }
                val qx = (x / rowStep) * rowStep
                val centerOffset = (qx - width * 0.5f) / (depth * 0.85f + 0.35f)
                val worldX = (centerOffset / 6f).roundToInt()
                val worldZ = ((1f / (depth + 0.18f)) * 9f).roundToInt()
                val pattern = ((worldX * 19 + worldZ * 31) and 0x7F) / 127f
                val isGrass = ((worldX + worldZ) % 3 == 0) || pattern > 0.55f

                var r: Float
                var g: Float
                var b: Float
                if (isGrass) {
                    r = (0.31f + pattern * 0.10f).coerceIn(0.20f, 0.54f)
                    g = (0.48f + pattern * 0.16f).coerceIn(0.30f, 0.72f)
                    b = (0.20f + pattern * 0.08f).coerceIn(0.10f, 0.38f)
                } else {
                    r = (0.46f + pattern * 0.12f).coerceIn(0.30f, 0.64f)
                    g = (0.37f + pattern * 0.09f).coerceIn(0.22f, 0.52f)
                    b = (0.24f + pattern * 0.07f).coerceIn(0.14f, 0.38f)
                }

                val shade = (1f - depth) * 0.32f
                r += (0.17f - r) * shade
                g += (0.15f - g) * shade
                b += (0.12f - b) * shade

                pixels[y * width + x] = packRgb(r, g, b)
            }
        }
    }

    val baseBmp = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    if (!sharpPixels && detailLevel != TextureDetailLevel.ORIGINAL) {
        val downW = (width / stepBlock).coerceAtLeast(6)
        val downH = (height / stepBlock).coerceAtLeast(4)
        val mipBmp = Bitmap.createScaledBitmap(baseBmp, downW, downH, true)
        val smoothBmp = Bitmap.createScaledBitmap(mipBmp, width, height, true)
        return smoothBmp.asImageBitmap()
    }
    return baseBmp.asImageBitmap()
}

private fun packRgb(r: Float, g: Float, b: Float): Int {
    val ri = (r.coerceIn(0f, 1f) * 255f).toInt() and 0xFF
    val gi = (g.coerceIn(0f, 1f) * 255f).toInt() and 0xFF
    val bi = (b.coerceIn(0f, 1f) * 255f).toInt() and 0xFF
    return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
}
