package com.kyro.game.space.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyro.game.space.data.model.ManualAdbInfo
import com.kyro.game.space.ui.theme.KyroGreen
import kotlinx.coroutines.launch

@Composable
fun ManualAdbPairingDialog(
    manualAdbInfo: ManualAdbInfo,
    onPair: suspend (host: String, pairingPort: Int, code: String) -> Result<String>,
    onConnect: suspend (host: String, connectPort: Int) -> Result<String>,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    var host by remember { mutableStateOf(manualAdbInfo.host) }
    var pairingPortText by remember { mutableStateOf(if (manualAdbInfo.pairingPort > 0) manualAdbInfo.pairingPort.toString() else "37000") }
    var pairingCode by remember { mutableStateOf(manualAdbInfo.pairingCode) }
    var connectPortText by remember { mutableStateOf(manualAdbInfo.port.toString()) }

    var isProcessing by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

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
                    Text("Wireless ADB Pairing", style = MaterialTheme.typography.titleMedium)
                    Text(
                        manualAdbInfo.libAdbDetails,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                // Info Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Enable Wireless Debugging in Developer Options > 'Pair device with pairing code'. Note: Pairing port and Connection port are different.",
                            style = MaterialTheme.typography.labelSmall,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("IP Address") },
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
                        placeholder = { Text("e.g. 37000") },
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
                    label = { Text("Connection Port (Main Endpoint)") },
                    placeholder = { Text("e.g. 5555 or Wi-Fi port") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adb_connect_port_input"),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Actions Row
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                feedbackMessage = "Attempting pairing with $host:$pairingPortText..."
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

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                feedbackMessage = "Connecting to ADB daemon at $host:$connectPortText..."
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
                            .weight(1f)
                            .testTag("connect_button"),
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text("2. Connect")
                    }
                }

                if (manualAdbInfo.isConnected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onDisconnect()
                            feedbackMessage = "Disconnected from ADB daemon."
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
                        Text("Working...", style = MaterialTheme.typography.bodySmall)
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
