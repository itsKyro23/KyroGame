package com.kyro.game.space.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyro.game.space.data.model.ConnectionOverview
import com.kyro.game.space.data.model.DeviceStats
import com.kyro.game.space.data.model.GameItem
import com.kyro.game.space.data.repository.GameSpaceRepository
import com.kyro.game.space.ui.components.AdbCommandDialog
import com.kyro.game.space.ui.components.ConnectionStatusCard
import com.kyro.game.space.ui.components.GameCard
import com.kyro.game.space.ui.components.GameCustomizeDialog
import com.kyro.game.space.ui.components.PerformanceCards
import com.kyro.game.space.ui.components.RequiredPermissionsCard
import kotlinx.coroutines.launch

import com.kyro.game.space.data.model.ExecutionMethod
import com.kyro.game.space.ui.components.ManualAdbPairingDialog

@Composable
fun HomeScreen(
    repository: GameSpaceRepository,
    overview: ConnectionOverview,
    stats: DeviceStats,
    homeGames: List<GameItem>,
    isUsageAccessGranted: Boolean,
    isOverlayGranted: Boolean,
    isNotificationGranted: Boolean,
    onRequestNotification: () -> Unit,
    onNavigateToApps: () -> Unit,
    onNavigateToNetworkOptimizer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showTerminalDialog by remember { mutableStateOf(false) }
    var showPairingDialog by remember { mutableStateOf(false) }
    var customizingGame by remember { mutableStateOf<GameItem?>(null) }

    androidx.compose.runtime.LaunchedEffect(overview.method) {
        repository.refreshActiveExecutionMethodStatus()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section A: Connection Status Card
        item {
            ConnectionStatusCard(
                overview = overview,
                onReconnect = {
                    scope.launch {
                        when (overview.method) {
                            ExecutionMethod.SHIZUKU -> {
                                repository.updateShizukuStatus()
                                val shizuku = repository.connectionState.value.shizuku
                                val msg = when {
                                    shizuku.isPermissionGranted -> "Shizuku connected & permission verified!"
                                    shizuku.isRunning -> "Shizuku is running. Grant permission to execute commands."
                                    else -> "Shizuku service is not running."
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                            ExecutionMethod.MANUAL_ADB -> {
                                val alive = repository.checkDaemonAndSyncState()
                                val msg = if (alive) {
                                    "Persistent Daemon is Active & Connected!"
                                } else {
                                    "Refreshed Wireless ADB / Daemon status"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                            ExecutionMethod.ROOT -> {
                                val rootInfo = repository.checkRootStatus(triggerPrompt = true)
                                Toast.makeText(context, rootInfo.statusMessage, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onRequestPermission = {
                    when (overview.method) {
                        ExecutionMethod.MANUAL_ADB -> {
                            showPairingDialog = true
                        }
                        ExecutionMethod.ROOT -> {
                            scope.launch {
                                val rootInfo = repository.requestRootPermission()
                                Toast.makeText(context, rootInfo.statusMessage, Toast.LENGTH_SHORT).show()
                            }
                        }
                        ExecutionMethod.SHIZUKU -> {
                            val status = repository.requestShizukuPermission()
                            when (status) {
                                "ALREADY_GRANTED" -> Toast.makeText(context, "Shizuku permission is active and verified!", Toast.LENGTH_SHORT).show()
                                "REQUESTED" -> Toast.makeText(context, "Requesting Shizuku permission...", Toast.LENGTH_SHORT).show()
                                "OPENED_SHIZUKU" -> Toast.makeText(context, "Opening Shizuku app. Please ensure Shizuku service is Running.", Toast.LENGTH_LONG).show()
                                "NOT_INSTALLED" -> Toast.makeText(context, "Shizuku app is not installed on this device.", Toast.LENGTH_LONG).show()
                                else -> Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onOpenTerminal = {
                    showTerminalDialog = true
                }
            )
        }

        // Section B: Device Performance Cards (Temperature, RAM, Ping)
        item {
            PerformanceCards(
                stats = stats,
                onOpenNetworkOptimizer = onNavigateToNetworkOptimizer
            )
        }

        // Section C: My Games
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "MY GAMES",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "${homeGames.size} Titles",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (homeGames.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("empty_games_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Gamepad,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No Games in Hub Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add games from the Apps manager to customize quick launching and track playtime.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToApps,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Browse Installed Apps")
                        }
                    }
                }
            }
        } else {
            items(items = homeGames, key = { it.packageName }) { game ->
                GameCard(
                    game = game,
                    onPlayClick = {
                        val launched = repository.launchGame(game.packageName)
                        if (!launched) {
                            Toast.makeText(context, "Could not launch ${game.appName}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCustomizeClick = {
                        customizingGame = game
                    },
                    onRemoveClick = {
                        scope.launch {
                            repository.setAddedToHome(game.packageName, false)
                            Toast.makeText(context, "Removed ${game.appName} from Home", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        // Section D: Required Permissions
        item {
            val isSelectedExecMethodGranted = when (overview.method) {
                ExecutionMethod.SHIZUKU -> overview.shizuku.isPermissionGranted
                ExecutionMethod.MANUAL_ADB -> overview.manualAdb.isDaemonInjected || overview.manualAdb.isConnected
                ExecutionMethod.ROOT -> overview.root.isRootGranted
            }
            RequiredPermissionsCard(
                isShizukuGranted = isSelectedExecMethodGranted,
                isUsageAccessGranted = isUsageAccessGranted,
                isOverlayGranted = isOverlayGranted,
                isNotificationGranted = isNotificationGranted,
                executionMethod = overview.method,
                onRequestShizuku = {
                    when (overview.method) {
                        ExecutionMethod.MANUAL_ADB -> {
                            showPairingDialog = true
                        }
                        ExecutionMethod.ROOT -> {
                            scope.launch {
                                val rootInfo = repository.requestRootPermission()
                                Toast.makeText(context, rootInfo.statusMessage, Toast.LENGTH_SHORT).show()
                            }
                        }
                        ExecutionMethod.SHIZUKU -> {
                            val status = repository.requestShizukuPermission()
                            when (status) {
                                "ALREADY_GRANTED" -> Toast.makeText(context, "Shizuku permission is active and verified!", Toast.LENGTH_SHORT).show()
                                "REQUESTED" -> Toast.makeText(context, "Requesting Shizuku permission...", Toast.LENGTH_SHORT).show()
                                "OPENED_SHIZUKU" -> Toast.makeText(context, "Opening Shizuku app. Please ensure Shizuku service is Running.", Toast.LENGTH_LONG).show()
                                "NOT_INSTALLED" -> Toast.makeText(context, "Shizuku app is not installed on this device.", Toast.LENGTH_LONG).show()
                                else -> Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onRequestNotification = onRequestNotification
            )
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

    if (showTerminalDialog) {
        AdbCommandDialog(
            onDismiss = { showTerminalDialog = false },
            onExecute = { cmd -> repository.executeCommand(cmd) },
            executorName = overview.method.name
        )
    }

    customizingGame?.let { targetGame ->
        // Keep up-to-date with latest config from homeGames if available
        val liveGame = homeGames.firstOrNull { it.packageName == targetGame.packageName } ?: targetGame
        GameCustomizeDialog(
            game = liveGame,
            onDismiss = { customizingGame = null },
            onSaveConfig = { newConfig ->
                customizingGame = null
                scope.launch {
                    val res = repository.saveAndApplyGameConfig(liveGame.packageName, newConfig)
                    val msg = res.getOrElse { "Saved profile for ${liveGame.appName}" }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
