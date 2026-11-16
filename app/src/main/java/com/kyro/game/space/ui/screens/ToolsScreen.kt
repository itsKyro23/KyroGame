package com.kyro.game.space.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyro.game.space.data.repository.GameSpaceRepository
import com.kyro.game.space.ui.components.ManualAdbPairingDialog
import com.kyro.game.space.ui.components.PermissionRequiredBanner
import com.kyro.game.space.ui.theme.KyroCyan
import com.kyro.game.space.ui.theme.KyroGreen
import com.kyro.game.space.ui.theme.KyroOrange
import com.kyro.game.space.ui.theme.appStrings
import com.kyro.game.space.ui.theme.rememberResponsiveMetrics
import kotlinx.coroutines.launch

@Composable
fun ToolsScreen(
    repository: GameSpaceRepository,
    onOpenResolutionChanger: () -> Unit = {},
    onOpenNetworkOptimizer: () -> Unit = {},
    onOpenTextureTuner: () -> Unit = {},
    onOpenAotCompiler: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val responsive = rememberResponsiveMetrics()
    val strings = appStrings()
    val scope = rememberCoroutineScope()
    val overview by repository.connectionState.collectAsState()
    val resState by repository.resolutionState.collectAsState()
    val netState by repository.networkOptimizerState.collectAsState()
    val texState by repository.textureTunerState.collectAsState()
    val aotState by repository.aotCompilerState.collectAsState()
    val stats by repository.deviceStats.collectAsState()

    var showPairingDialog by remember { mutableStateOf(false) }

    val pingColor = when {
        stats.pingMs == null -> MaterialTheme.colorScheme.error
        stats.pingMs!! < 50 -> KyroGreen
        stats.pingMs!! < 120 -> KyroOrange
        else -> MaterialTheme.colorScheme.error
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("tools_screen"),
        contentPadding = PaddingValues(
            start = responsive.screenHorizontalPadding,
            end = responsive.screenHorizontalPadding,
            top = 8.dp,
            bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(responsive.sectionSpacing)
    ) {
        // Section Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = strings.toolsHeaderTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Permission Required Banner at the top when no execution permission is active
        if (!overview.isAnyPermissionActive) {
            item {
                PermissionRequiredBanner(
                    overview = overview,
                    onConnectShizuku = {
                        val status = repository.requestShizukuPermission()
                        if (status != "ALREADY_GRANTED") {
                            Toast.makeText(context, strings.toastOpeningShizuku, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onPairAdb = { showPairingDialog = true },
                    onRequestRoot = {
                        scope.launch {
                            val info = repository.requestRootPermission()
                            Toast.makeText(context, info.statusMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        // 1. AOT Game DEX Compiler Tool Card (Featured at Top)
        item {
            Card(
                onClick = onOpenAotCompiler,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tools_aot_compiler_card"),
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
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = KyroOrange.copy(alpha = 0.16f),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = KyroOrange,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.toolAotTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${strings.toolAotSubtitlePrefix} ${aotState.selectedMode.shortLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = KyroOrange.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, KyroOrange.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = KyroOrange,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "15⚡",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = KyroOrange,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Text(
                        text = strings.toolAotDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = KyroCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (aotState.lastCompiledCount > 0) {
                                    "${strings.toolAotCompiledFooterPrefix} ${aotState.lastCompiledCount} pkg • ${aotState.lastCompiledModeLabel}"
                                } else {
                                    strings.toolAotDefaultFooter
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = KyroCyan,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = onOpenAotCompiler,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("open_aot_compiler_btn")
                        ) {
                            Text(
                                text = strings.btnOpen,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Resolution Changer Tool Card
        item {
            Card(
                onClick = onOpenResolutionChanger,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tools_resolution_changer_card"),
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
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AspectRatio,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.toolResolutionTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${strings.toolResolutionSubtitlePrefix} ${resState.nativeWidth}p) & DPI",
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
                                text = "${resState.currentWidth}×${resState.currentHeight}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (resState.isCustomModified) KyroOrange else KyroGreen,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Text(
                        text = strings.toolResolutionDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = KyroCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "${strings.toolResolutionFooterPrefix} • ${resState.currentDensityDpi} DPI",
                                style = MaterialTheme.typography.labelSmall,
                                color = KyroCyan,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = onOpenResolutionChanger,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("open_resolution_changer_btn")
                        ) {
                            Text(
                                text = strings.btnOpen,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Lightweight Texture (GPU Texture & Mipmap LOD Tuner) Card
        item {
            val texSkyBlue = Color(0xFF56B4E9)
            Card(
                onClick = onOpenTextureTuner,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tools_texture_tuner_card"),
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
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = texSkyBlue.copy(alpha = 0.16f),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesomeMosaic,
                                        contentDescription = null,
                                        tint = texSkyBlue,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.toolTextureTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = strings.toolTextureSubtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = texSkyBlue.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, texSkyBlue.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = texState.currentConfig.detailLevel.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = texSkyBlue,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Text(
                        text = strings.toolTextureDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = KyroGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = texState.compatibility.compatibilityBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = KyroGreen,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = onOpenTextureTuner,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("open_texture_tuner_btn")
                        ) {
                            Text(
                                text = strings.btnOpen,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. Network Optimizer Tool Card
        item {
            Card(
                onClick = onOpenNetworkOptimizer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tools_network_optimizer_card"),
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
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = KyroGreen.copy(alpha = 0.16f),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.NetworkCheck,
                                        contentDescription = null,
                                        tint = KyroGreen,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.toolNetworkTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${netState.activeTransport.label} • ${netState.activeDnsName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = pingColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, pingColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = stats.pingMs?.let { "${it}ms" } ?: "Online",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = pingColor,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Text(
                        text = strings.toolNetworkDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = KyroGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "${strings.toolNetworkTargetPrefix} ${netState.activePingHost} (${netState.privateDnsHostname})",
                                style = MaterialTheme.typography.labelSmall,
                                color = KyroGreen,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = onOpenNetworkOptimizer,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("open_network_optimizer_btn")
                        ) {
                            Text(
                                text = strings.btnOpen,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPairingDialog) {
        ManualAdbPairingDialog(
            manualAdbInfo = overview.manualAdb,
            onPair = { h, p, c -> repository.pairManualAdb(h, p, c) },
            onConnect = { h, p -> repository.connectManualAdb(h, p) },
            onInjectDaemon = { repository.injectManualAdbDaemon() },
            onStartAutoScan = { notif -> repository.startWirelessAdbScan(notif) },
            onDisconnect = { repository.disconnectManualAdb() },
            onDismiss = { showPairingDialog = false }
        )
    }
}
