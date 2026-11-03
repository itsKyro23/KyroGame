package com.kyro.game.space.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyro.game.space.BuildConfig
import com.kyro.game.space.data.model.ConnectionOverview
import com.kyro.game.space.data.model.ExecutionMethod
import com.kyro.game.space.data.repository.GameSpaceRepository
import com.kyro.game.space.service.FloatingHudService
import com.kyro.game.space.ui.components.AdbCommandDialog
import com.kyro.game.space.ui.components.ManualAdbPairingDialog
import com.kyro.game.space.ui.components.PrivacyPolicyDialog
import com.kyro.game.space.ui.theme.KyroAccent
import com.kyro.game.space.ui.theme.KyroCyan
import com.kyro.game.space.ui.theme.KyroGreen
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
    var showPrivacyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(overview.method) {
        repository.refreshActiveExecutionMethodStatus()
    }

    val activeMethodDisplayName = when (overview.method) {
        ExecutionMethod.SHIZUKU -> "Shizuku IPC"
        ExecutionMethod.MANUAL_ADB -> "Wireless ADB Daemon"
        ExecutionMethod.ROOT -> "Root Access (SU)"
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
                border = BorderStroke(
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EXECUTION PERMISSION ENGINE",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = KyroPrimary.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = activeMethodDisplayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = KyroAccent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Select the privileged permission engine used to execute game optimizations, resolution scaling, texture tuning, and live HUD telemetry. Only the selected engine will be used.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Option 1: Shizuku
                    ExecutionOptionRow(
                        title = "Shizuku Engine (Recommended)",
                        subtitle = if (overview.shizuku.version > 0) {
                            "Low-latency binder IPC via Shizuku service (API v${overview.shizuku.version})"
                        } else {
                            "Low-latency binder IPC via official Shizuku service"
                        },
                        icon = Icons.Default.Cable,
                        selected = overview.method == ExecutionMethod.SHIZUKU,
                        onClick = {
                            repository.setExecutionMethod(ExecutionMethod.SHIZUKU)
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 2: Manual ADB (libadb.so)
                    ExecutionOptionRow(
                        title = "Wireless ADB Daemon (libadb.so)",
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
                        title = "Root Access (SU / Magisk / KernelSU)",
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
                                            "OPENED_SHIZUKU" -> Toast.makeText(context, "Opening Shizuku app. Please ensure Shizuku service is running.", Toast.LENGTH_LONG).show()
                                            "NOT_INSTALLED" -> Toast.makeText(context, "Shizuku app is not installed on this device.", Toast.LENGTH_LONG).show()
                                            else -> Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("request_shizuku_perm_btn"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Check / Grant Shizuku")
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
                                    Text(if (overview.manualAdb.isDaemonInjected) "Daemon Active (Configure)" else "Pair & Connect ADB")
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
                                    Text(if (overview.root.isRootGranted) "Root Active (Verify)" else "Check / Grant Root")
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showTerminalDialog = true },
                            modifier = Modifier.testTag("settings_terminal_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ADB Shell")
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
                border = BorderStroke(
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = KyroPrimary.copy(alpha = 0.16f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = KyroAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "In-Game Floating HUD",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Translucent black draggable overlay displaying live in-game FPS, real CPU load, and GPU usage while playing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                        Switch(
                            checked = floatingHudEnabled,
                            onCheckedChange = { isEnabled ->
                                if (isEnabled && !isOverlayGranted) {
                                    Toast.makeText(context, "Please grant 'Display over other apps' permission first", Toast.LENGTH_LONG).show()
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
                                        Toast.makeText(context, "In-Game Floating HUD enabled", Toast.LENGTH_SHORT).show()
                                    } else {
                                        FloatingHudService.stop(context)
                                        Toast.makeText(context, "In-Game Floating HUD disabled", Toast.LENGTH_SHORT).show()
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
                border = BorderStroke(
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Display Theme",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "OLED Pure Dark Gaming Interface",
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
                                text = "PURE DARK",
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Telemetry Polling Rate",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Hardware sensors & network ping update interval",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                                Toast.makeText(context, "Rescanned installed games and synced playtime", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Re-scan Installed Games & Sync Playtime")
                    }
                }
            }
        }

        // Section D: About Kyro Game Space (Comprehensive Application Info)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("about_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header with Icon, App Title, and Version Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = KyroPrimary.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, KyroPrimary.copy(alpha = 0.45f)),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SportsEsports,
                                    contentDescription = null,
                                    tint = KyroAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "NOX - Game Space",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = KyroGreen.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "v${BuildConfig.VERSION_NAME}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = KyroGreen,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Tactical Gaming Performance & Hardware Optimization Suite",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Description
                    Text(
                        text = "NOX - Game Space is a dedicated Android gaming performance hub engineered for competitive mobile gamers. It provides per-game hardware boosting, safe display resolution scaling with auto-revert protection, GPU mipmap/LOD lightweight texture tuning, multi-DNS stability benchmarking, and a live translucent in-game FPS, CPU, and GPU telemetry overlay.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    // Privacy Policy & Compliance Action Card
                    Surface(
                        onClick = { showPrivacyDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF161A28),
                        border = BorderStroke(1.dp, KyroCyan.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("privacy_policy_item_btn")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
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
                                    color = KyroCyan.copy(alpha = 0.16f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = "Privacy Policy",
                                            tint = KyroCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "Privacy Policy & Compliance",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Contact: kyrostudios23@gmail.com • Tap to view & copy",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF8E99B3),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = KyroCyan.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, KyroCyan.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = "View & Copy",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = KyroCyan,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    // Application Details Rows
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppInfoDetailRow(
                            label = "Application Version",
                            value = "${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
                        )
                        AppInfoDetailRow(
                            label = "Package Identifier",
                            value = BuildConfig.APPLICATION_ID
                        )
                        AppInfoDetailRow(
                            label = "Privacy Contact",
                            value = "kyrostudios23@gmail.com"
                        )
                        AppInfoDetailRow(
                            label = "Permission Engines",
                            value = "Shizuku IPC • Wireless ADB • Root (SU)"
                        )
                        AppInfoDetailRow(
                            label = "Tactical Modules",
                            value = "Resolution Changer • Lightweight Texture • Network Optimizer • Live HUD"
                        )
                        AppInfoDetailRow(
                            label = "Device Environment",
                            value = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE} • API ${Build.VERSION.SDK_INT})"
                        )
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

    if (showTerminalDialog) {
        AdbCommandDialog(
            onDismiss = { showTerminalDialog = false },
            onExecute = { cmd -> repository.executeCommand(cmd) },
            executorName = activeMethodDisplayName
        )
    }

    if (showPrivacyDialog) {
        PrivacyPolicyDialog(
            onDismiss = { showPrivacyDialog = false }
        )
    }
}

@Composable
private fun AppInfoDetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f)
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
        border = BorderStroke(1.dp, borderColor),
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
