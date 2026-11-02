package com.kyro.game.space.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyro.game.space.data.model.ConnectionOverview
import com.kyro.game.space.data.model.ExecutionMethod
import com.kyro.game.space.data.repository.GameSpaceRepository
import com.kyro.game.space.service.FloatingHudService
import com.kyro.game.space.ui.components.AdbCommandDialog
import com.kyro.game.space.ui.components.ManualAdbPairingDialog
import com.kyro.game.space.ui.theme.KyroAccent
import com.kyro.game.space.ui.theme.KyroPrimary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    repository: GameSpaceRepository,
    overview: ConnectionOverview,
    refreshIntervalMs: Long,
    floatingHudEnabled: Boolean,
    isOverlayGranted: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showPairingDialog by remember { mutableStateOf(false) }
    var showTerminalDialog by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(overview.method) {
        repository.refreshActiveExecutionMethodStatus()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section A: Execution Method Selection
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("execution_method_card"),
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
                        .padding(18.dp)
                ) {
                    Text(
                        text = "EXECUTION METHOD",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose how elevated system optimization commands are dispatched (Shizuku, Wireless ADB Daemon, or Root).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Option 1: Shizuku
                    ExecutionOptionRow(
                        title = "Shizuku Engine (Recommended)",
                        subtitle = "Fastest IPC via official Shizuku service. API v${overview.shizuku.version}",
                        icon = Icons.Default.Cable,
                        selected = overview.method == ExecutionMethod.SHIZUKU,
                        onClick = {
                            repository.setExecutionMethod(ExecutionMethod.SHIZUKU)
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 2: Manual ADB (libadb.so)
                    ExecutionOptionRow(
                        title = "Manual ADB + Daemon (libadb.so)",
                        subtitle = "${overview.manualAdb.libAdbDetails}\n${overview.manualAdb.daemonStatus}",
                        icon = Icons.Default.Wifi,
                        selected = overview.method == ExecutionMethod.MANUAL_ADB,
                        onClick = {
                            repository.setExecutionMethod(ExecutionMethod.MANUAL_ADB)
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 3: Root Access (su / Magisk / KernelSU)
                    ExecutionOptionRow(
                        title = "Root Access (su / Magisk / KernelSU)",
                        subtitle = overview.root.statusMessage,
                        icon = Icons.Default.Security,
                        selected = overview.method == ExecutionMethod.ROOT,
                        onClick = {
                            repository.setExecutionMethod(ExecutionMethod.ROOT)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action buttons based on selected method
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (overview.method) {
                            ExecutionMethod.SHIZUKU -> {
                                Button(
                                    onClick = {
                                        val status = repository.requestShizukuPermission()
                                        when (status) {
                                            "ALREADY_GRANTED" -> Toast.makeText(context, "Shizuku permission is active and verified!", Toast.LENGTH_SHORT).show()
                                            "REQUESTED" -> Toast.makeText(context, "Requesting Shizuku permission...", Toast.LENGTH_SHORT).show()
                                            "OPENED_SHIZUKU" -> Toast.makeText(context, "Opening Shizuku app. Please ensure Shizuku service is Running.", Toast.LENGTH_LONG).show()
                                            "NOT_INSTALLED" -> Toast.makeText(context, "Shizuku app is not installed on this device.", Toast.LENGTH_LONG).show()
                                            else -> Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("request_shizuku_perm_btn"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Check / Grant Permission")
                                }
                            }
                            ExecutionMethod.MANUAL_ADB -> {
                                Button(
                                    onClick = { showPairingDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("open_pairing_dialog_btn"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(if (overview.manualAdb.isDaemonInjected) "Daemon Active (Setup)" else "Auto-Scan, Pair & Inject")
                                }
                            }
                            ExecutionMethod.ROOT -> {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val rootInfo = repository.requestRootPermission()
                                            Toast.makeText(
                                                context,
                                                rootInfo.statusMessage,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("request_root_perm_btn"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(if (overview.root.isRootGranted) "Root Active (Verify)" else "Check / Grant Root Access")
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showTerminalDialog = true },
                            modifier = Modifier.testTag("settings_terminal_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test")
                        }
                    }
                }
            }
        }

        // Section B: Floating In-Game HUD Overlay
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("floating_hud_card"),
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
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = KyroAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Floating Window (In-Game HUD)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Lightweight draggable overlay displaying FPS, Ping, and RAM while playing.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = floatingHudEnabled,
                            onCheckedChange = { isEnabled ->
                                if (isEnabled && !isOverlayGranted) {
                                    Toast.makeText(context, "Grant 'Display over other apps' first", Toast.LENGTH_LONG).show()
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    ).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } else {
                                    repository.setFloatingHudEnabled(isEnabled)
                                    if (isEnabled) {
                                        FloatingHudService.start(context)
                                        Toast.makeText(context, "Floating HUD activated", Toast.LENGTH_SHORT).show()
                                    } else {
                                        FloatingHudService.stop(context)
                                        Toast.makeText(context, "Floating HUD stopped", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = KyroPrimary
                            )
                        )
                    }
                }
            }
        }

        // Section C: General Settings
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("general_settings_card"),
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
                        .padding(18.dp)
                ) {
                    Text(
                        text = "GENERAL PREFERENCES",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dark Theme (Forced Pure Dark Gaming Mode)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Theme Style", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Permanent OLED Pure Dark",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "DARK ONLY",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Refresh interval
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Telemetry Rate", style = MaterialTheme.typography.bodyLarge)
                                Text("Update sensors and ping", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1000L to "1s", 2000L to "2s", 5000L to "5s").forEach { (interval, label) ->
                                FilterChip(
                                    selected = refreshIntervalMs == interval,
                                    onClick = { repository.setRefreshInterval(interval) },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Clear Cache / Re-scan button
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                repository.scanAndSyncInstalledApps()
                                Toast.makeText(context, "Rescanned installed games and synced usage stats", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Re-scan Apps & Sync Playtime")
                    }
                }
            }
        }

        // Section D: About Game Space
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("about_card"),
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
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kyro Game Space",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Version 1.0.0 (Build Native)\nMaterial Design 3 Gaming Hub with Shizuku & Wireless ADB command integration, real hardware monitoring, and in-game floating HUD overlay.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
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

    if (showTerminalDialog) {
        AdbCommandDialog(
            onDismiss = { showTerminalDialog = false },
            onExecute = { cmd -> repository.executeCommand(cmd) },
            executorName = overview.method.name
        )
    }
}

@Composable
private fun ExecutionOptionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerBg = if (selected) Color(0xFF2A1517) else Color(0xFF1E1E26)
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = containerBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
