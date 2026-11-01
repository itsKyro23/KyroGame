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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyro.game.space.ui.theme.KyroGreen

data class PermissionRequirement(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val isGranted: Boolean,
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val permissions = listOf(
        PermissionRequirement(
            id = "shizuku",
            name = "Shizuku / Elevated ADB",
            description = "Allows non-root system command execution and background optimizations.",
            icon = Icons.Default.Cable,
            isGranted = isShizukuGranted,
            onGrant = { onRequestShizuku() }
        ),
        PermissionRequirement(
            id = "usage_stats",
            name = "Usage Access",
            description = "Required to track actual screen time and playtime for each game.",
            icon = Icons.Default.BarChart,
            isGranted = isUsageAccessGranted,
            onGrant = { ctx ->
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            }
        ),
        PermissionRequirement(
            id = "overlay",
            name = "Display Over Other Apps",
            description = "Powers the in-game Floating HUD performance window overlay.",
            icon = Icons.Default.Layers,
            isGranted = isOverlayGranted,
            onGrant = { ctx ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${ctx.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            }
        ),
        PermissionRequirement(
            id = "notifications",
            name = "Notifications",
            description = "Maintains the Floating Window HUD service active in foreground.",
            icon = Icons.Default.Notifications,
            isGranted = isNotificationGranted,
            onGrant = { onRequestNotification() }
        )
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("required_permissions_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = "REQUIRED PERMISSIONS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Grant specific permissions to unlock in-game overlay, playtime tracking, and elevated system controls.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                permissions.forEach { perm ->
                    PermissionRowItem(perm = perm, context = context)
                }
            }
        }
    }
}

@Composable
private fun PermissionRowItem(
    perm: PermissionRequirement,
    context: Context
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("permission_item_${perm.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                            text = "Granted",
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
                    Text(text = "Grant", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
