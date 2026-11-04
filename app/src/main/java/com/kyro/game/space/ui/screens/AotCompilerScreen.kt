package com.kyro.game.space.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import com.kyro.game.space.data.model.GameItem
import com.kyro.game.space.data.repository.GameSpaceRepository
import com.kyro.game.space.ui.components.NoxInsufficientEnergyDialog
import com.kyro.game.space.ui.theme.KyroAccent
import com.kyro.game.space.ui.theme.KyroCyan
import com.kyro.game.space.ui.theme.KyroGreen
import com.kyro.game.space.ui.theme.KyroOrange
import com.kyro.game.space.ui.theme.KyroPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AotCompilerScreen(
    repository: GameSpaceRepository,
    homeGames: List<GameItem>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aotState by repository.aotCompilerState.collectAsState()
    val overview by repository.connectionState.collectAsState()

    var showInsufficientEnergyDialog by remember { mutableStateOf(false) }

    val isAllGamesSelected = aotState.selectedTargetPackage.isBlank()
    val requiredEnergy = when {
        aotState.selectedMode.isReset -> 0
        isAllGamesSelected -> NoxEnergyManager.COST_AOT_DEX_ALL
        else -> NoxEnergyManager.COST_AOT_DEX_SINGLE
    }

    val selectedGameTitle = remember(aotState.selectedTargetPackage, homeGames) {
        if (aotState.selectedTargetPackage.isBlank()) {
            "All Added Games (${homeGames.size} titles)"
        } else {
            homeGames.firstOrNull { it.packageName == aotState.selectedTargetPackage }?.appName
                ?: aotState.selectedTargetPackage
        }
    }

    val samplePkg = aotState.selectedTargetPackage.ifBlank {
        homeGames.firstOrNull()?.packageName ?: "com.game.package"
    }
    val previewCmd = aotState.selectedMode.buildCompileCommand(samplePkg)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("aot_compiler_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Navigation & Title Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("aot_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Tools"
                        )
                    }
                    Column {
                        Text(
                            text = "AOT GAME DEX COMPILER",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Ahead-Of-Time Native ARM64 Bytecode Compiler",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = KyroGreen.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, KyroGreen.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = if (requiredEnergy == 0) "FREE (0⚡)" else "COST: ${requiredEnergy}⚡",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = KyroGreen,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
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

                    Text(
                        text = "Standard Android games translate DEX code on-the-fly (JIT) during matches, causing sudden frame drops when new skills or effects appear. AOT Compilation pre-compiles game code into 100% native ARM64 machine instructions before you play.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB8C2D8),
                        lineHeight = 17.sp
                    )

                    if (aotState.lastCompiledCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = KyroGreen.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, KyroGreen.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = KyroGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Last Run: ${aotState.lastCompiledCount} game(s) compiled [${aotState.lastCompiledModeLabel}] in ${aotState.lastCompileDurationMs}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = KyroGreen,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 1: Target Game Selection (Single Game 15⚡ vs All Games 30⚡)
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
                                text = "1. SELECT TARGET GAME SCOPE",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.6.sp
                            )
                        }
                        Text(
                            text = "Single: 15⚡ • All: 30⚡",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = KyroCyan,
                            fontSize = 10.5.sp
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // All Added Games Chip (30⚡)
                        FilterChip(
                            selected = isAllGamesSelected,
                            onClick = { repository.selectAotTargetPackage("") },
                            label = {
                                Text(
                                    text = "All Added Games (${homeGames.size}) • 30⚡",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            },
                            leadingIcon = if (isAllGamesSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = KyroCyan.copy(alpha = 0.2f),
                                selectedLabelColor = KyroCyan
                            ),
                            modifier = Modifier.testTag("aot_target_all_chip")
                        )

                        // Individual Added Home Games (15⚡ each)
                        homeGames.forEach { game ->
                            val isSelected = aotState.selectedTargetPackage == game.packageName
                            FilterChip(
                                selected = isSelected,
                                onClick = { repository.selectAotTargetPackage(game.packageName) },
                                label = {
                                    Text(
                                        text = "${game.appName} • 15⚡",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = KyroGreen.copy(alpha = 0.2f),
                                    selectedLabelColor = KyroGreen
                                ),
                                modifier = Modifier.testTag("aot_target_game_${game.packageName}")
                            )
                        }
                    }

                    Text(
                        text = "Selected Target: $selectedGameTitle",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Section 2: Compilation Mode Selector
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                }
            }
        }

        // Section 3: Command Preview & Execute Action
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
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = KyroCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "3. EXECUTE NATIVE COMPILATION",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.6.sp
                        )
                    }

                    // Shell Command Preview Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0A0D16),
                        border = BorderStroke(1.dp, Color(0xFF1F263B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "SHELL COMMAND PREVIEW:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7B86A2),
                                fontSize = 9.5.sp
                            )
                            Text(
                                text = previewCmd,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = KyroGreen,
                                fontSize = 11.5.sp
                            )
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
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                        fontWeight = FontWeight.Bold
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

                    // Run Compilation Button
                    val executeCompilationNow: () -> Unit = {
                        scope.launch {
                            val res = repository.runAotGameCompile(
                                targetPackage = aotState.selectedTargetPackage,
                                mode = aotState.selectedMode
                            )
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
                            if (requiredEnergy > 0) {
                                if (!NoxEnergyManager.hasEnergy(context, requiredEnergy)) {
                                    showInsufficientEnergyDialog = true
                                } else {
                                    if (NoxEnergyManager.consumeEnergy(context, requiredEnergy)) {
                                        executeCompilationNow()
                                    }
                                }
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
                            else -> "Compile ${if (isAllGamesSelected) "All Games" else "Selected Game"} (${requiredEnergy}⚡)"
                        }
                        Text(
                            text = btnText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black
                        )
                    }

                    // Console Output Log
                    if (aotState.compiledLogLines.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        Text(
                            text = "COMPILER EXECUTION LOG:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8E99B3),
                            fontSize = 10.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF080A12),
                            border = BorderStroke(1.dp, Color(0xFF1E2538)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                aotState.compiledLogLines.forEach { line ->
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = when {
                                            line.contains("ERROR", ignoreCase = true) -> KyroAccent
                                            line.contains("OK", ignoreCase = true) || line.contains("Success", ignoreCase = true) -> KyroGreen
                                            else -> Color(0xFFD0D8E8)
                                        },
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInsufficientEnergyDialog) {
        val featureDesc = if (isAllGamesSelected) {
            "AOT Game DEX Compiler (All Games)"
        } else {
            "AOT Game DEX Compiler (Single Game)"
        }
        NoxInsufficientEnergyDialog(
            featureName = featureDesc,
            requiredCost = requiredEnergy,
            onDismiss = { showInsufficientEnergyDialog = false },
            onEnergyGranted = {
                showInsufficientEnergyDialog = false
                if (NoxEnergyManager.consumeEnergy(context, requiredEnergy)) {
                    scope.launch {
                        val res = repository.runAotGameCompile(
                            targetPackage = aotState.selectedTargetPackage,
                            mode = aotState.selectedMode
                        )
                        val msg = res.getOrElse { it.message ?: "AOT compilation failed" }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}
