package com.kyro.game.space.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyro.game.space.data.model.ConnectionOverview
import com.kyro.game.space.data.model.ConnectionStatus
import com.kyro.game.space.data.model.ExecutionMethod
import com.kyro.game.space.ui.theme.KyroCyan
import com.kyro.game.space.ui.theme.KyroGreen
import com.kyro.game.space.ui.theme.KyroOrange
import com.kyro.game.space.ui.theme.appStrings
import com.kyro.game.space.ui.theme.rememberResponsiveMetrics

@Composable
fun ConnectionStatusCard(
    overview: ConnectionOverview,
    onReconnect: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenTerminal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val responsive = rememberResponsiveMetrics()
    val strings = appStrings()
    val statusColor = when (overview.status) {
        ConnectionStatus.CONNECTED -> KyroGreen
        ConnectionStatus.CONNECTING -> KyroCyan
        ConnectionStatus.PERMISSION_REQUIRED -> KyroOrange
        ConnectionStatus.DISCONNECTED,
        ConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
    }

    val statusIcon = when (overview.status) {
        ConnectionStatus.CONNECTED -> Icons.Default.CheckCircle
        ConnectionStatus.PERMISSION_REQUIRED -> Icons.Default.Warning
        else -> Icons.Default.ErrorOutline
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("connection_status_card")
            .animateContentSize(),
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
                .padding(responsive.cardInnerPadding)
        ) {
            // Header: Method & Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Cable,
                                contentDescription = "Connection Engine",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (overview.method) {
                                ExecutionMethod.SHIZUKU -> "Shizuku Engine"
                                ExecutionMethod.MANUAL_ADB -> "Manual ADB (libadb.so)"
                                ExecutionMethod.ROOT -> "Root Access (su)"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = overview.androidVersion,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = if (responsive.isCompactWidth) 8.dp else 10.dp,
                            vertical = 5.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (overview.status) {
                                ConnectionStatus.CONNECTED -> strings.statusConnected
                                ConnectionStatus.CONNECTING -> strings.statusConnecting
                                ConnectionStatus.PERMISSION_REQUIRED -> strings.statusPermission
                                ConnectionStatus.DISCONNECTED -> strings.statusDisconnected
                                ConnectionStatus.ERROR -> strings.statusError
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            letterSpacing = 0.5.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Details Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1E1E26),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = overview.statusDetails,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    when (overview.method) {
                        ExecutionMethod.SHIZUKU -> {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Service: ${if (overview.shizuku.isRunning) "Running (API v${overview.shizuku.version})" else "Stopped"} • Permission: ${if (overview.shizuku.isPermissionGranted) "Granted" else "Not Granted"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (!overview.shizuku.isRunning && overview.shizuku.isInstalled) {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = {
                                        val launchIntent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                        if (launchIntent != null) {
                                            context.startActivity(launchIntent)
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(strings.btnOpenShizukuApp)
                                }
                            }
                        }
                        ExecutionMethod.MANUAL_ADB -> {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${overview.manualAdb.libAdbDetails}\n${overview.manualAdb.daemonStatus}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (overview.manualAdb.isDaemonInjected) KyroGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ExecutionMethod.ROOT -> {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Binary: ${if (overview.root.isSuBinaryPresent) "su (${overview.root.suVersion})" else "Not Found"} • Access: ${if (overview.root.isRootGranted) "Granted (uid=0)" else "Not Granted"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (overview.root.isRootGranted) KyroGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ElevatedButton(
                    onClick = onReconnect,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reconnect_button"),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.btnReconnect,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (overview.status == ConnectionStatus.PERMISSION_REQUIRED && overview.method == ExecutionMethod.SHIZUKU) {
                    FilledTonalButton(
                        onClick = onRequestPermission,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("grant_shizuku_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = strings.btnGrant,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (overview.method == ExecutionMethod.MANUAL_ADB && !overview.manualAdb.isDaemonInjected) {
                    FilledTonalButton(
                        onClick = onRequestPermission,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("setup_adb_daemon_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = strings.btnSetupInject,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (overview.method == ExecutionMethod.ROOT && !overview.root.isRootGranted) {
                    FilledTonalButton(
                        onClick = onRequestPermission,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("grant_root_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = strings.btnGrantRoot,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onOpenTerminal,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("terminal_test_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.btnTerminal,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
