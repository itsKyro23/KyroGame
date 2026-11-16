package com.kyro.game.space.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyro.game.space.data.model.ExecutionMethod
import com.kyro.game.space.ui.theme.KyroGreen
import com.kyro.game.space.ui.theme.appStrings

data class PermissionRequirement(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val isGranted: Boolean,
    val isRestrictedTarget: Boolean = false,
    val onGrant: (Context) -> Unit
)

@Composable
fun RequiredPermissionsCard(
    isShizukuGranted: Boolean,
    isUsageAccessGranted: Boolean,
    isOverlayGranted: Boolean,
    isNotificationGranted: Boolean,
    onRequestShizuku: () -> Unit,
    onRequestNotification: () -> Unit,
    executionMethod: ExecutionMethod = ExecutionMethod.SHIZUKU,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = appStrings()
    var showRestrictedGuideDialog by remember { mutableStateOf(false) }
    var restrictedDialogTargetAction by remember { mutableStateOf("") }
    var restrictedDialogTitle by remember { mutableStateOf("") }

    val execPermTitle = when (executionMethod) {
        ExecutionMethod.SHIZUKU -> strings.permShizukuTitle
        ExecutionMethod.MANUAL_ADB -> strings.permAdbTitle
        ExecutionMethod.ROOT -> strings.permRootTitle
    }
    val execPermDesc = when (executionMethod) {
        ExecutionMethod.SHIZUKU -> strings.permShizukuDesc
        ExecutionMethod.MANUAL_ADB -> strings.permAdbDesc
        ExecutionMethod.ROOT -> strings.permRootDesc
    }

    val permissions = listOf(
        PermissionRequirement(
            id = "shizuku",
            name = execPermTitle,
            description = execPermDesc,
            icon = Icons.Default.Cable,
            isGranted = isShizukuGranted,
            onGrant = { onRequestShizuku() }
        ),
        PermissionRequirement(
            id = "usage_stats",
            name = strings.permUsageTitle,
            description = strings.permUsageDesc,
            icon = Icons.Default.BarChart,
            isGranted = isUsageAccessGranted,
            isRestrictedTarget = true,
            onGrant = { ctx ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isUsageAccessGranted) {
                    restrictedDialogTitle = strings.permUsageTitle
                    restrictedDialogTargetAction = Settings.ACTION_USAGE_ACCESS_SETTINGS
                    showRestrictedGuideDialog = true
                } else {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(intent)
                }
            }
        ),
        PermissionRequirement(
            id = "overlay",
            name = strings.permOverlayTitle,
            description = strings.permOverlayDesc,
            icon = Icons.Default.Layers,
            isGranted = isOverlayGranted,
            isRestrictedTarget = true,
            onGrant = { ctx ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isOverlayGranted) {
                    restrictedDialogTitle = strings.permOverlayTitle
                    restrictedDialogTargetAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                    showRestrictedGuideDialog = true
                } else {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${ctx.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(intent)
                }
            }
        ),
        PermissionRequirement(
            id = "notifications",
            name = strings.permNotifTitle,
            description = strings.permNotifDesc,
            icon = Icons.Default.Notifications,
            isGranted = isNotificationGranted,
            onGrant = { onRequestNotification() }
        )
    )

    val pendingPermissions = permissions.filter { !it.isGranted }

    // If all permissions have been granted, dismiss the card entirely
    if (pendingPermissions.isEmpty()) {
        return
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("required_permissions_card"),
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                ) {
                    Text(
                        text = strings.reqPermissionsTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${pendingPermissions.size} ${strings.pendingSuffix}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                // App Info shortcut button
                TextButton(
                    onClick = { openAppInfo(context) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = strings.permAppInfoBtn,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strings.reqPermissionsSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                pendingPermissions.forEach { perm ->
                    PermissionRowItem(
                        perm = perm,
                        context = context,
                        onOpenAppInfo = { openAppInfo(context) }
                    )
                }
            }
        }
    }

    if (showRestrictedGuideDialog) {
        AlertDialog(
            onDismissRequest = { showRestrictedGuideDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = strings.permRestrictedDialogTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = strings.permRestrictedDialogBody,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        openAppInfo(context)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.permRestrictedStep1Btn)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showRestrictedGuideDialog = false
                        val intent = Intent(restrictedDialogTargetAction).apply {
                            if (restrictedDialogTargetAction == Settings.ACTION_MANAGE_OVERLAY_PERMISSION) {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(strings.permRestrictedStep2Btn)
                }
            }
        )
    }
}

@Composable
private fun PermissionRowItem(
    perm: PermissionRequirement,
    context: Context,
    onOpenAppInfo: () -> Unit
) {
    val strings = appStrings()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("permission_item_${perm.id}"),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E1E26),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (perm.isGranted) KyroGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = perm.icon,
                            contentDescription = null,
                            tint = if (perm.isGranted) KyroGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = perm.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (perm.isGranted) KyroGreen else MaterialTheme.colorScheme.error)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = perm.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (perm.isGranted) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = KyroGreen.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Granted",
                                tint = KyroGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.permGranted,
                                style = MaterialTheme.typography.labelSmall,
                                color = KyroGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    FilledTonalButton(
                        onClick = { perm.onGrant(context) },
                        modifier = Modifier.testTag("grant_button_${perm.id}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text(text = strings.btnGrant, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // If not granted and subject to Android 13+ restricted setting, show quick unlock tip
            if (!perm.isGranted && perm.isRestrictedTarget && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.permRestrictedTip,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            lineHeight = 14.sp
                        )
                        TextButton(
                            onClick = onOpenAppInfo,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = strings.permOpenBtn,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun openAppInfo(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
