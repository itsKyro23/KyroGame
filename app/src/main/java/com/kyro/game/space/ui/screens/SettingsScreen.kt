package com.kyro.game.space.ui.screens

import android.app.Activity
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
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.kyro.game.space.util.AppUpdateState
import com.kyro.game.space.util.GooglePlayManager
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
import com.kyro.game.space.ui.theme.AppLanguage
import com.kyro.game.space.ui.theme.KyroAccent
import com.kyro.game.space.ui.theme.KyroCyan
import com.kyro.game.space.ui.theme.KyroGreen
import com.kyro.game.space.ui.theme.KyroPrimary
import com.kyro.game.space.ui.theme.appStrings
import com.kyro.game.space.ui.theme.rememberResponsiveMetrics
import androidx.compose.ui.text.style.TextOverflow
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
    val responsive = rememberResponsiveMetrics()
    val strings = appStrings()
    val currentLanguage by repository.appLanguage.collectAsState()
    val scope = rememberCoroutineScope()

    var showPairingDialog by remember { mutableStateOf(false) }
    var showTerminalDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(overview.method) {
        repository.refreshActiveExecutionMethodStatus()
    }

    val activeMethodDisplayName = when (overview.method) {
        ExecutionMethod.SHIZUKU -> strings.engineShizukuBadge
        ExecutionMethod.MANUAL_ADB -> strings.engineAdbBadge
        ExecutionMethod.ROOT -> strings.engineRootBadge
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(
            start = responsive.screenHorizontalPadding,
            end = responsive.screenHorizontalPadding,
            top = 8.dp,
            bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(responsive.sectionSpacing)
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
                        .padding(responsive.cardInnerPadding)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.settingsExecEngineTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
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
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strings.settingsExecEngineDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Option 1: Shizuku
                    ExecutionOptionRow(
                        title = strings.settingsShizukuTitle,
                        subtitle = if (overview.shizuku.version > 0) {
                            "${strings.settingsShizukuSubtitle} (API v${overview.shizuku.version})"
                        } else {
                            strings.settingsShizukuSubtitle
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
                        title = strings.settingsWirelessAdbTitle,
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
                        title = strings.settingsRootTitle,
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
                                            "ALREADY_GRANTED" -> Toast.makeText(context, strings.toastShizukuActive, Toast.LENGTH_SHORT).show()
                                            "REQUESTED" -> Toast.makeText(context, strings.toastShizukuRequesting, Toast.LENGTH_SHORT).show()
                                            "OPENED_SHIZUKU" -> Toast.makeText(context, strings.toastShizukuOpenApp, Toast.LENGTH_LONG).show()
                                            "NOT_INSTALLED" -> Toast.makeText(context, strings.toastShizukuNotInstalled, Toast.LENGTH_LONG).show()
                                            else -> Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("request_shizuku_perm_btn"),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = if (overview.shizuku.isPermissionGranted) strings.btnShizukuActive else strings.btnCheckGrantShizuku,
                                        maxLines = 1
                                    )
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
                                    Text(
                                        text = if (overview.manualAdb.isDaemonInjected) strings.btnDaemonActiveConfig else strings.btnPairConnectAdb,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
                                    Text(
                                        text = if (overview.root.isRootGranted) strings.btnRootActiveVerify else strings.btnCheckGrantRoot,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
                            Text(strings.btnAdbShell, maxLines = 1)
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
                                text = strings.settingsFloatingHudTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.settingsFloatingHudDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                        Switch(
                            checked = floatingHudEnabled,
                            onCheckedChange = { isEnabled ->
                                if (isEnabled && !isOverlayGranted) {
                                    Toast.makeText(context, strings.toastOverlayFirst, Toast.LENGTH_LONG).show()
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
                                        Toast.makeText(context, strings.toastHudEnabled, Toast.LENGTH_SHORT).show()
                                    } else {
                                        FloatingHudService.stop(context)
                                        Toast.makeText(context, strings.toastHudDisabled, Toast.LENGTH_SHORT).show()
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

        // Section C: General Settings (Including App Language Selector)
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
                        text = strings.settingsGeneralTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // App Language Selector (English, Bahasa Indonesia, Malaysia, Philippines)
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
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = KyroCyan
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = strings.settingsLanguageTitle,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = strings.settingsLanguageSubtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = KyroCyan.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, KyroCyan.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = currentLanguage.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = KyroCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2x2 Responsive Grid of Language Options
                    val languagePairs = remember { AppLanguage.entries.chunked(2) }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        languagePairs.forEach { rowLanguages ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowLanguages.forEach { lang ->
                                    val isSelected = currentLanguage == lang
                                    Surface(
                                        onClick = {
                                            repository.setAppLanguage(lang)
                                            Toast.makeText(
                                                context,
                                                "${strings.toastLanguageChangedPrefix} ${lang.label}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) KyroCyan.copy(alpha = 0.16f) else Color(0xFF1E1E26),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) KyroCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("language_option_${lang.code}")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = lang.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) KyroCyan else Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = lang.subtitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                color = if (isSelected) KyroCyan.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
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
                                    text = strings.settingsDisplayThemeTitle,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = strings.settingsDisplayThemeSubtitle,
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
                                text = strings.settingsPureDarkBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Refresh interval
                    if (responsive.isCompactWidth) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.settingsPollingRateTitle,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = strings.settingsPollingRateSubtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(1000L to "1s", 2000L to "2s", 5000L to "5s").forEach { (interval, label) ->
                                    FilterChip(
                                        selected = refreshIntervalMs == interval,
                                        onClick = { repository.setRefreshInterval(interval) },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    } else {
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
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = strings.settingsPollingRateTitle,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = strings.settingsPollingRateSubtitle,
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
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Clear Cache / Re-scan button
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                repository.scanAndSyncInstalledApps()
                                Toast.makeText(context, strings.toastRescannedGames, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.btnRescanGames,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Section D: Google Play App Updates
        item {
            val updateState by GooglePlayManager.updateState.collectAsState()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("google_play_update_card"),
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
                        .padding(responsive.cardInnerPadding),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = KyroCyan.copy(alpha = 0.16f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "App Update",
                                        tint = KyroCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.settingsUpdateTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = strings.settingsUpdateSubtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        val (badgeText, badgeColor) = when (updateState) {
                            is AppUpdateState.Checking -> Pair(strings.updateBadgeChecking, KyroCyan)
                            is AppUpdateState.Available -> Pair(strings.updateBadgeAvailable, KyroAccent)
                            is AppUpdateState.Downloading -> Pair(strings.updateBadgeDownloading, KyroCyan)
                            is AppUpdateState.Downloaded -> Pair(strings.updateBadgeReady, KyroGreen)
                            is AppUpdateState.Error -> Pair(strings.updateBadgePlayStore, Color(0xFFE57373))
                            is AppUpdateState.UpToDate -> Pair(strings.updateBadgeLatest, KyroGreen)
                            AppUpdateState.Idle -> Pair("v${BuildConfig.VERSION_NAME}", KyroGreen)
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = badgeColor.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                fontSize = 10.sp,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    // Update state information
                    when (val state = updateState) {
                        is AppUpdateState.Checking -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = strings.updateCheckingText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = KyroCyan
                                )
                            }
                        }
                        is AppUpdateState.Available -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = KyroAccent.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, KyroAccent.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "NOX - Game Space (Build ${state.availableVersionCode})",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Button(
                                        onClick = {
                                            val act = context as? Activity
                                            if (act != null) {
                                                val started = GooglePlayManager.startUpdate(act, state.appUpdateInfo)
                                                if (!started) {
                                                    GooglePlayManager.openPlayStore(context)
                                                }
                                            } else {
                                                GooglePlayManager.openPlayStore(context)
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("start_inapp_update_btn"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = KyroAccent)
                                    ) {
                                        Text(strings.updateAvailableBtn, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        is AppUpdateState.Downloading -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val progress = if (state.totalBytes > 0) {
                                    state.bytesDownloaded.toFloat() / state.totalBytes.toFloat()
                                } else 0f
                                Text(
                                    text = "${state.bytesDownloaded / (1024 * 1024)}MB / ${state.totalBytes / (1024 * 1024)}MB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KyroCyan
                                )
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = KyroCyan
                                )
                            }
                        }
                        is AppUpdateState.Downloaded -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = KyroGreen.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, KyroGreen.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = strings.updateDownloadedText,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = KyroGreen
                                    )
                                    Button(
                                        onClick = { GooglePlayManager.completeUpdate() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("complete_update_btn"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = KyroGreen)
                                    ) {
                                        Text(strings.updateRestartBtn, color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        is AppUpdateState.Error -> {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE57373)
                            )
                        }
                        is AppUpdateState.UpToDate, AppUpdateState.Idle -> {
                            Text(
                                text = strings.formatCurrentVersionText(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                GooglePlayManager.checkForUpdate(context) { result ->
                                    if (result is AppUpdateState.UpToDate) {
                                        Toast.makeText(context, "${strings.toastAppUpToDatePrefix} (v${BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("check_update_btn"),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KyroPrimary)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = strings.btnCheckForUpdate,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                GooglePlayManager.openPlayStore(context)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_play_store_btn"),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = strings.btnOpenPlayStore,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Section E: Google Play Rating & Community Feedback
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("google_play_rating_card"),
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
                        .padding(responsive.cardInnerPadding),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFFB300).copy(alpha = 0.18f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Rating",
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.settingsRatingTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = strings.settingsRatingSubtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFB300).copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "5.0 ★ PLAY STORE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB300),
                                fontSize = 10.sp,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    // Rating Stars visual showcase
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF141724),
                        border = BorderStroke(1.dp, Color(0xFF262C40)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(5) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Star",
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Text(
                                text = strings.settingsRatingHeadline,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = strings.settingsRatingBody,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA6B0C3),
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val act = context as? Activity
                                if (act != null) {
                                    GooglePlayManager.launchInAppReview(act)
                                } else {
                                    GooglePlayManager.openPlayStore(context)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rate_app_inapp_btn"),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
                        ) {
                            Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = strings.btnRateInApp,
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                GooglePlayManager.openPlayStore(context)
                            },
                            modifier = Modifier
                                .weight(0.85f)
                                .testTag("write_review_btn"),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = strings.btnReviewOnPlay,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Section F: About NOX - Game Space (Comprehensive Application Info)
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
                                text = strings.aboutSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Description
                    Text(
                        text = strings.aboutDescription,
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
                                        text = strings.aboutPrivacyTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = strings.aboutPrivacySubtitle,
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
                                    text = strings.aboutViewCopyBtn,
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
                            label = strings.aboutLabelVersion,
                            value = "${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
                        )
                        AppInfoDetailRow(
                            label = strings.aboutLabelPackage,
                            value = BuildConfig.APPLICATION_ID
                        )
                        AppInfoDetailRow(
                            label = strings.aboutLabelContact,
                            value = "kyrostudios23@gmail.com"
                        )
                        AppInfoDetailRow(
                            label = strings.aboutLabelEngines,
                            value = "Shizuku • Wireless ADB • Root (SU)"
                        )
                        AppInfoDetailRow(
                            label = strings.aboutLabelModules,
                            value = "${strings.toolAotTitle} • ${strings.toolResolutionTitle} • ${strings.toolTextureTitle} • ${strings.toolNetworkTitle}"
                        )
                        AppInfoDetailRow(
                            label = strings.aboutLabelDevice,
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
