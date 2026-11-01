package com.kyro.game.space.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyro.game.space.data.model.ManualAdbInfo
import com.kyro.game.space.ui.theme.KyroCyan
import com.kyro.game.space.ui.theme.KyroGreen
import com.kyro.game.space.ui.theme.KyroOrange
import kotlinx.coroutines.launch

@Composable
fun ManualAdbPairingDialog(
    manualAdbInfo: ManualAdbInfo,
    onPair: suspend (host: String, pairingPort: Int, code: String) -> Result<String>,
    onConnect: suspend (host: String, connectPort: Int) -> Result<String>,
    onInjectDaemon: suspend () -> Result<String> = { Result.failure(Exception("Not configured")) },
    onStartAutoScan: (showNotification: Boolean) -> Unit = {},
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var host by remember { mutableStateOf(manualAdbInfo.host) }
    var pairingPortText by remember {
        mutableStateOf(if (manualAdbInfo.pairingPort > 0) manualAdbInfo.pairingPort.toString() else "37000")
    }
    var pairingCode by remember { mutableStateOf(manualAdbInfo.pairingCode) }
    var connectPortText by remember { mutableStateOf(manualAdbInfo.port.toString()) }

    var isProcessing by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Automatically start mDNS + libadb.so port scanner when this dialog opens
    DisposableEffect(Unit) {
        onStartAutoScan(false)
        onDispose { }
    }

    // Automatically fill IP Address, Pairing Port, and Connection Port when discovered
    LaunchedEffect(manualAdbInfo.host, manualAdbInfo.pairingPort, manualAdbInfo.port) {
        if (manualAdbInfo.host.isNotBlank()) {
            host = manualAdbInfo.host
        }
        if (manualAdbInfo.pairingPort > 0) {
            pairingPortText = manualAdbInfo.pairingPort.toString()
        }
        if (manualAdbInfo.port > 0) {
            connectPortText = manualAdbInfo.port.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Wireless ADB & Daemon Setup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = manualAdbInfo.libAdbDetails,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (manualAdbInfo.isLibAdbLoaded) KyroGreen else KyroOrange
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Persistent Daemon Status Banner
                val daemonColor = if (manualAdbInfo.isDaemonInjected) KyroGreen else KyroCyan
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = daemonColor.copy(alpha = 0.14f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, daemonColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(daemonColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (manualAdbInfo.isDaemonInjected) {
                                    "PERSISTENT DAEMON ACTIVE (Port ${manualAdbInfo.daemonPort})"
                                } else {
                                    "PERSISTENT DAEMON STANDBY"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = daemonColor
                            )
                            Text(
                                text = if (manualAdbInfo.isDaemonInjected) {
                                    "Connection stays active even after Wireless Debugging is turned OFF."
                                } else {
                                    "Once connected to Wireless ADB, the Persistent Daemon is automatically injected so communication stays alive when Wireless Debugging is turned OFF."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Auto-Scanner Live Status Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (manualAdbInfo.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = KyroGreen
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = null,
                                tint = KyroGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AUTO-SCAN & AUTO-CONNECT ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = KyroGreen
                            )
                            Text(
                                text = manualAdbInfo.scanStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 13.sp
                            )
                        }
                        IconButton(
                            onClick = { onStartAutoScan(true) },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("rescan_adb_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rescan Wireless ADB",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Shortcut to Developer Options + Notification Pairing Helper
                OutlinedButton(
                    onClick = {
                        // Start interactive notification pairing so user can type 6-digit code in notification shade
                        onStartAutoScan(true)
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Throwable) {
                            try {
                                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(fallback)
                            } catch (_: Throwable) {
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_dev_options_btn"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Open Wireless Debugging (Auto-Scan & Pair)",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("IP Address (Auto-Detected)") },
                    placeholder = { Text("127.0.0.1 or Wi-Fi IP") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adb_ip_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = pairingPortText,
                        onValueChange = { pairingPortText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Pairing Port") },
                        placeholder = { Text("Auto-scan") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("adb_pairing_port_input"),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = { if (it.length <= 6) pairingCode = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Pairing Code") },
                        placeholder = { Text("6 digits") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("adb_pairing_code_input"),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = connectPortText,
                    onValueChange = { connectPortText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Connection Port (Auto-Detected)") },
                    placeholder = { Text("e.g. 41235 or 5555") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adb_connect_port_input"),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Actions Row: 1. Pair & 2. Connect + Inject Daemon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                feedbackMessage = "Running libadb.so pair $host:$pairingPortText..."
                                val pPort = pairingPortText.toIntOrNull() ?: 0
                                val result = onPair(host.trim(), pPort, pairingCode.trim())
                                if (result.isSuccess) {
                                    isSuccess = true
                                    feedbackMessage = result.getOrNull()
                                } else {
                                    isSuccess = false
                                    feedbackMessage = result.exceptionOrNull()?.message ?: "Pairing failed"
                                }
                                isProcessing = false
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pair_button"),
                        enabled = !isProcessing && pairingCode.length == 6,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("1. Pair")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                feedbackMessage = "Connecting Wireless ADB & Injecting Daemon..."
                                val cPort = connectPortText.toIntOrNull() ?: 5555
                                val result = onConnect(host.trim(), cPort)
                                if (result.isSuccess) {
                                    isSuccess = true
                                    feedbackMessage = result.getOrNull()
                                } else {
                                    isSuccess = false
                                    feedbackMessage = result.exceptionOrNull()?.message ?: "Connection failed"
                                }
                                isProcessing = false
                            }
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("connect_button"),
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text("2. Connect & Inject")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 1-Click Direct Daemon Injection Button (via active ADB or Shizuku bridge)
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            isProcessing = true
                            feedbackMessage = "Injecting Persistent Daemon into /data/local/tmp..."
                            val res = onInjectDaemon()
                            if (res.isSuccess) {
                                isSuccess = true
                                feedbackMessage = res.getOrNull()
                            } else {
                                isSuccess = false
                                feedbackMessage = res.exceptionOrNull()?.message ?: "Failed to inject daemon"
                            }
                            isProcessing = false
                        }
                    },
                    enabled = !isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inject_daemon_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (manualAdbInfo.isDaemonInjected) Icons.Default.CheckCircle else Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (manualAdbInfo.isDaemonInjected) {
                            "Daemon Active (Tap to Re-Verify)"
                        } else {
                            "Inject Persistent Daemon Now"
                        }
                    )
                }

                if (manualAdbInfo.isConnected || manualAdbInfo.isDaemonInjected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onDisconnect()
                            feedbackMessage = "ADB session disconnected."
                            isSuccess = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("disconnect_adb_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Disconnect Active Session")
                    }
                }

                if (isProcessing) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Processing libadb.so & Daemon...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (feedbackMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSuccess) KyroGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = feedbackMessage!!,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (isSuccess) KyroGreen else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
