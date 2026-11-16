package com.kyro.game.space.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import com.kyro.game.space.data.model.ConnectionOverview
import com.kyro.game.space.data.model.GameItem
import com.kyro.game.space.data.repository.GameSpaceRepository
import com.kyro.game.space.ui.components.ManualAdbPairingDialog
import com.kyro.game.space.ui.components.PermissionRequiredBanner
import com.kyro.game.space.ui.theme.KyroAccent
import com.kyro.game.space.ui.theme.KyroCyan
import com.kyro.game.space.ui.theme.KyroOrange
import com.kyro.game.space.ui.theme.KyroPrimary
import com.kyro.game.space.ui.theme.appStrings
import com.kyro.game.space.ui.theme.rememberResponsiveMetrics
import kotlinx.coroutines.launch

enum class AppFilter {
    ALL,
    GAMES,
    APPS,
    ADDED
}

@Composable
fun AppsScreen(
    repository: GameSpaceRepository,
    allApps: List<GameItem>,
    overview: ConnectionOverview = repository.connectionState.collectAsState().value,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val responsive = rememberResponsiveMetrics()
    val strings = appStrings()
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(AppFilter.ALL) }
    var showPermissionBlockedDialog by remember { mutableStateOf(false) }
    var showPairingDialog by remember { mutableStateOf(false) }

    val filteredApps by remember(allApps, searchQuery, selectedFilter) {
        derivedStateOf {
            allApps.filter { item ->
                val matchesQuery = searchQuery.isBlank() ||
                        item.appName.contains(searchQuery, ignoreCase = true) ||
                        item.packageName.contains(searchQuery, ignoreCase = true)

                val matchesCategory = when (selectedFilter) {
                    AppFilter.ALL -> true
                    AppFilter.GAMES -> item.isGame
                    AppFilter.APPS -> !item.isGame
                    AppFilter.ADDED -> item.isAddedToHome
                }

                matchesQuery && matchesCategory
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("apps_screen")
    ) {
        // Search & Filter Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = responsive.screenHorizontalPadding, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(strings.searchAppsPlaceholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_search_field")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppFilter.values().forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = {
                            val count = when (filter) {
                                AppFilter.ALL -> allApps.size
                                AppFilter.GAMES -> allApps.count { it.isGame }
                                AppFilter.APPS -> allApps.count { !it.isGame }
                                AppFilter.ADDED -> allApps.count { it.isAddedToHome }
                            }
                            val filterLabel = when (filter) {
                                AppFilter.ALL -> strings.filterAll
                                AppFilter.GAMES -> strings.filterGames
                                AppFilter.APPS -> strings.filterApps
                                AppFilter.ADDED -> strings.filterAdded
                            }
                            Text("$filterLabel ($count)")
                        },
                        modifier = Modifier.testTag("filter_chip_${filter.name}")
                    )
                }
            }
        }

        // App List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = responsive.screenHorizontalPadding,
                end = responsive.screenHorizontalPadding,
                top = 4.dp,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!overview.isAnyPermissionActive) {
                item {
                    PermissionRequiredBanner(
                        overview = overview,
                        onConnectShizuku = {
                            val status = repository.requestShizukuPermission()
                            if (status != "ALREADY_GRANTED") {
                                Toast.makeText(context, strings.toastOpeningShizuku, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onPairAdb = { showPairingDialog = true },
                        onRequestRoot = {
                            scope.launch {
                                val rootInfo = repository.requestRootPermission()
                                Toast.makeText(context, rootInfo.statusMessage, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            items(items = filteredApps, key = { it.packageName }) { app ->
                AppListItem(
                    app = app,
                    isPermissionActive = overview.isAnyPermissionActive,
                    onToggleHome = { newState ->
                        if (!overview.isAnyPermissionActive) {
                            showPermissionBlockedDialog = true
                        } else {
                            scope.launch {
                                repository.setAddedToHome(app.packageName, newState)
                            }
                        }
                    }
                )
            }
        }
    }

    if (showPermissionBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionBlockedDialog = false },
            containerColor = Color(0xFF141724),
            shape = RoundedCornerShape(22.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = KyroOrange
                    )
                    Text(
                        text = strings.cannotAddGamesTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Text(
                    text = strings.cannotAddGamesDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFC0CDDE),
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionBlockedDialog = false
                        val status = repository.requestShizukuPermission()
                        if (status != "ALREADY_GRANTED") {
                            Toast.makeText(context, strings.toastOpeningShizuku, Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KyroCyan,
                        contentColor = Color(0xFF061424)
                    )
                ) {
                    Text(strings.btnConnectShizuku, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionBlockedDialog = false }) {
                    Text(strings.btnClose, color = Color(0xFFA0A7BD))
                }
            }
        )
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
}

@Composable
private fun AppListItem(
    app: GameItem,
    isPermissionActive: Boolean,
    onToggleHome: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val strings = appStrings()
    val appIconBitmap = remember(app.packageName) {
        com.kyro.game.space.util.AppIconCache.getIcon(context, app.packageName)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_item_${app.packageName}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp)),
                color = Color.DarkGray
            ) {
                if (appIconBitmap != null) {
                    Image(
                        bitmap = appIconBitmap.asImageBitmap(),
                        contentDescription = app.appName,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.VideogameAsset,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Game / App Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (app.isGame) KyroAccent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (app.isGame) strings.badgeGame else strings.badgeApp,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (app.isGame) KyroAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Add to Home Switch
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (app.isAddedToHome) strings.switchHome else strings.switchAdd,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (app.isAddedToHome) KyroAccent else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = app.isAddedToHome,
                    onCheckedChange = onToggleHome,
                    modifier = Modifier.testTag("switch_home_${app.packageName}"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = KyroPrimary
                    )
                )
            }
        }
    }
}
