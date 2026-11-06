package com.kyro.game.space.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.kyro.game.space.data.energy.NoxEnergyManager
import com.kyro.game.space.data.model.AotCompileMode
import com.kyro.game.space.data.model.GameAotStatus
import com.kyro.game.space.data.model.GameItem
import com.kyro.game.space.data.repository.GameSpaceRepository
import com.kyro.game.space.ui.components.NoxInsufficientEnergyDialog
import com.kyro.game.space.ui.theme.KyroAccent
import com.kyro.game.space.ui.theme.KyroCyan
import com.kyro.game.space.ui.theme.KyroGreen
import com.kyro.game.space.ui.theme.KyroOrange
import com.kyro.game.space.util.AppIconCache
import kotlinx.coroutines.launch

@Composable
fun AotCompilerScreen(
    repository: GameSpaceRepository,
    homeGames: List<GameItem>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val responsive = com.kyro.game.space.ui.theme.rememberResponsiveMetrics()
    val scope = rememberCoroutineScope()
    val aotState by repository.aotCompilerState.collectAsState()
    val overview by repository.connectionState.collectAsState()

    var showTargetPickerDialog by remember { mutableStateOf(false) }
    var showInsufficientEnergyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(homeGames) {
        if (homeGames.isNotEmpty()) {
            AppIconCache.preloadIconsAsync(context, homeGames.map { it.packageName })
        }
    }

    val isAllGamesSelected = aotState.selectedTargetPackage.isBlank()
    val requiredEnergy = if (aotState.selectedMode.isReset) {
        0
    } else {
        NoxEnergyManager.COST_AOT_DEX_COMPILER // 15⚡
    }

    val selectedGameItem = remember(aotState.selectedTargetPackage, homeGames) {
        if (aotState.selectedTargetPackage.isBlank()) {
            null
        } else {
            homeGames.firstOrNull { it.packageName == aotState.selectedTargetPackage }
        }
    }

    val targetTitle = selectedGameItem?.appName
        ?: if (aotState.selectedTargetPackage.isNotBlank()) {
            aotState.selectedTargetPackage
        } else {
            "All Added Home Games (${homeGames.size})"
        }

    val selectedTargetAotStatus = if (aotState.selectedTargetPackage.isNotBlank()) {
        aotState.statusForPackage(aotState.selectedTargetPackage)
    } else {
        null
    }

    val targetSubtitle = if (selectedGameItem != null) {
        "${selectedGameItem.packageName} • ${selectedTargetAotStatus?.label ?: "Not Compiled"}"
    } else if (aotState.selectedTargetPackage.isNotBlank()) {
        "${aotState.selectedTargetPackage} • ${selectedTargetAotStatus?.label ?: "Not Compiled"}"
    } else {
        val compiledInHome = homeGames.count {
            val st = aotState.statusForPackage(it.packageName)
            st == GameAotStatus.SPEED_PROFILE || st == GameAotStatus.FULL_SPEED
        }
        "Compiled: $compiledInHome/${homeGames.size} Home games • Tap to select game"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("aot_compiler_screen"),
        contentPadding = PaddingValues(
            start = responsive.screenHorizontalPadding,
            end = responsive.screenHorizontalPadding,
            top = 8.dp,
            bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(responsive.sectionSpacing)
    ) {
        // Overview & Status Banner Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141826)),
                border = BorderStroke(1.dp, KyroGreen.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = KyroGreen.copy(alpha = 0.16f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = KyroGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Eliminate In-Game Skill & Map Micro-Stutter",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Engine: Android ART dex2oat • Active Backend: ${overview.activePermissionName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = KyroCyan,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = KyroGreen.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, KyroGreen.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = if (requiredEnergy == 0) "FREE (0⚡)" else "${requiredEnergy}⚡",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = KyroGreen,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Text(
                        text = "Standard Android games translate DEX code on-the-fly (JIT) during matches, causing sudden frame drops when new skills or effects appear. AOT Compilation pre-compiles game code into 100% native ARM64 machine instructions before you play.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB8C2D8),
                        lineHeight = 17.sp
                    )
                }
            }
        }

        // Section 1: Target Game Selector Card + Per-Game Live AOT Status List
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = KyroCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "1. SELECT TARGET GAME",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.6.sp
                            )
                        }
                        Text(
                            text = "Cost: 15⚡",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = KyroCyan,
                            fontSize = 11.sp
                        )
                    }

                    // Target Game Selector Row (Matches Lightweight Texture Selector Bar)
                    Surface(
                        onClick = { showTargetPickerDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0E0E15),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("aot_target_selector_bar")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val iconBmp = remember(aotState.selectedTargetPackage) {
                                AppIconCache.getCachedImageBitmap(aotState.selectedTargetPackage)
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = KyroCyan.copy(alpha = 0.16f),
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
                                            imageVector = if (aotState.selectedTargetPackage.isNotBlank()) {
                                                Icons.Default.SportsEsports
                                            } else {
                                                Icons.Default.Public
                                            },
                                            contentDescription = null,
                                            tint = KyroCyan,
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
                                color = KyroCyan.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, KyroCyan.copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        tint = KyroCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Select Game",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = KyroCyan,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    // Per-Game Live AOT Status List
                    if (homeGames.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                        Text(
                            text = "GAME AOT COMPILATION STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8E99B3),
                            fontSize = 10.sp,
                            letterSpacing = 0.6.sp
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            homeGames.forEach { game ->
                                val gameStatus = aotState.statusForPackage(game.packageName)
                                val isBeingCompiled = aotState.isCompiling && aotState.currentPackageBeingCompiled == game.packageName
                                val statusColor = when {
                                    isBeingCompiled -> KyroCyan
                                    gameStatus == GameAotStatus.SPEED_PROFILE -> KyroGreen
                                    gameStatus == GameAotStatus.FULL_SPEED -> KyroCyan
                                    gameStatus == GameAotStatus.FAILED -> KyroAccent
                                    gameStatus == GameAotStatus.RESET_DEFAULT -> KyroOrange
                                    else -> Color(0xFF8E99B3)
                                }

                                Surface(
                                    onClick = { repository.selectAotTargetPackage(game.packageName) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (aotState.selectedTargetPackage == game.packageName) {
                                        Color(0xFF151D2E)
                                    } else {
                                        Color(0xFF10131D)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (aotState.selectedTargetPackage == game.packageName) {
                                            KyroCyan.copy(alpha = 0.45f)
                                        } else {
                                            Color(0xFF1E2436)
                                        }
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = game.appName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp)
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = statusColor.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = if (isBeingCompiled) "Compiling..." else gameStatus.shortBadge,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = statusColor,
                                                fontSize = 9.5.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Compilation Mode Selector + Execute Action (Clean without CMD preview or compiler log)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = KyroGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "2. SELECT AOT COMPILATION MODE",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.6.sp
                        )
                    }

                    AotCompileMode.entries.forEach { mode ->
                        val isSelected = aotState.selectedMode == mode
                        val modeAccent = when (mode) {
                            AotCompileMode.SPEED_PROFILE -> KyroGreen
                            AotCompileMode.FULL_SPEED -> KyroCyan
                            AotCompileMode.RESET -> KyroOrange
                        }

                        Surface(
                            onClick = { repository.selectAotCompileMode(mode) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color(0xFF151D2E) else Color(0xFF121520),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) modeAccent else Color(0xFF23293E)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("aot_mode_${mode.name}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { repository.selectAotCompileMode(mode) },
                                    colors = RadioButtonDefaults.colors(selectedColor = modeAccent)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else Color(0xFFC5CEE0)
                                    )
                                    Text(
                                        text = mode.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF8B96B0),
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    if (aotState.isCompiling) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = KyroCyan
                                    )
                                    Text(
                                        text = aotState.lastStatusMessage,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = KyroCyan,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "${(aotState.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = KyroGreen
                                )
                            }
                            LinearProgressIndicator(
                                progress = { aotState.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = KyroGreen,
                                trackColor = Color(0xFF1B2234)
                            )
                        }
                    }

                    // Run Compilation Button (Only deducts 15⚡ when compilation succeeds!)
                    val executeCompilationNow: () -> Unit = {
                        scope.launch {
                            val res = repository.runAotGameCompile(
                                targetPackage = aotState.selectedTargetPackage,
                                mode = aotState.selectedMode
                            )
                            if (res.isSuccess && requiredEnergy > 0) {
                                NoxEnergyManager.consumeEnergy(context, requiredEnergy)
                            }
                            val msg = res.getOrElse { it.message ?: "AOT compilation failed" }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }

                    Button(
                        onClick = {
                            if (!overview.isAnyPermissionActive) {
                                Toast.makeText(
                                    context,
                                    "Connect Shizuku, Wireless ADB, or Root first!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            if (homeGames.isEmpty() && aotState.selectedTargetPackage.isBlank()) {
                                Toast.makeText(
                                    context,
                                    "Add at least 1 game to Home Hub first!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            if (requiredEnergy > 0 && !NoxEnergyManager.hasEnergy(context, requiredEnergy)) {
                                showInsufficientEnergyDialog = true
                            } else {
                                executeCompilationNow()
                            }
                        },
                        enabled = !aotState.isCompiling,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (aotState.selectedMode.isReset) KyroOrange else KyroGreen,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("run_aot_compile_btn")
                    ) {
                        Icon(
                            imageVector = if (aotState.selectedMode.isReset) Icons.Default.Refresh else Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val btnText = when {
                            aotState.isCompiling -> "Compiling DEX Bytecode..."
                            aotState.selectedMode.isReset -> "Reset Game Compilation (Free • 0⚡)"
                            else -> "Compile ${if (isAllGamesSelected) "All Games" else targetTitle} (${requiredEnergy}⚡)"
                        }
                        Text(
                            text = btnText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    // Target Game Picker Dialog (Shows live AOT Status badge for every game!)
    if (showTargetPickerDialog) {
        val gameCandidates = remember(homeGames) {
            homeGames.sortedBy { it.appName.lowercase() }
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
                        tint = KyroCyan
                    )
                    Column {
                        Text(
                            text = "Select Target Game",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Shows live AOT status for each game in your Home Hub",
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
                        val isGlobalSelected = aotState.selectedTargetPackage.isBlank()
                        val compiledCount = gameCandidates.count {
                            val st = aotState.statusForPackage(it.packageName)
                            st == GameAotStatus.SPEED_PROFILE || st == GameAotStatus.FULL_SPEED
                        }
                        Surface(
                            onClick = {
                                repository.selectAotTargetPackage("")
                                showTargetPickerDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isGlobalSelected) {
                                KyroCyan.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isGlobalSelected) KyroCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
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
                                    tint = KyroCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "All Added Home Games (${homeGames.size})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Compiled: $compiledCount/${homeGames.size} games • Cost: 15⚡",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isGlobalSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = KyroCyan,
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
                        val isSelected = aotState.selectedTargetPackage == game.packageName
                        val gameIconBitmap = remember(game.packageName) {
                            AppIconCache.getCachedImageBitmap(game.packageName)
                        }
                        val gameAotStatus = aotState.statusForPackage(game.packageName)
                        val badgeColor = when (gameAotStatus) {
                            GameAotStatus.SPEED_PROFILE -> KyroGreen
                            GameAotStatus.FULL_SPEED -> KyroCyan
                            GameAotStatus.FAILED -> KyroAccent
                            GameAotStatus.RESET_DEFAULT -> KyroOrange
                            GameAotStatus.NOT_COMPILED -> Color(0xFF94A0B8)
                        }

                        Surface(
                            onClick = {
                                repository.selectAotTargetPackage(game.packageName)
                                showTargetPickerDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) {
                                KyroCyan.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) KyroCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
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
                                    color = KyroCyan.copy(alpha = 0.14f),
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
                                                tint = KyroCyan,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
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
                                        Surface(
                                            shape = RoundedCornerShape(5.dp),
                                            color = badgeColor.copy(alpha = 0.16f),
                                            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = gameAotStatus.shortBadge,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = badgeColor,
                                                fontSize = 8.5.sp,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
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
                                        tint = KyroCyan,
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
        NoxInsufficientEnergyDialog(
            featureName = "AOT Game DEX Compiler ($targetTitle)",
            requiredCost = requiredEnergy,
            onDismiss = { showInsufficientEnergyDialog = false },
            onEnergyGranted = {
                showInsufficientEnergyDialog = false
                scope.launch {
                    val res = repository.runAotGameCompile(
                        targetPackage = aotState.selectedTargetPackage,
                        mode = aotState.selectedMode
                    )
                    if (res.isSuccess && requiredEnergy > 0) {
                        NoxEnergyManager.consumeEnergy(context, requiredEnergy)
                    }
                    val msg = res.getOrElse { it.message ?: "AOT compilation failed" }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
