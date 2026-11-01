package com.kyro.game.space.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyro.game.space.data.repository.GameSpaceRepository
import com.kyro.game.space.ui.screens.AppsScreen
import com.kyro.game.space.ui.screens.HomeScreen
import com.kyro.game.space.ui.screens.SettingsScreen
import com.kyro.game.space.ui.theme.KyroAccent
import com.kyro.game.space.ui.theme.KyroPrimary

enum class GameSpaceTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    APPS("Apps", Icons.Filled.Apps, Icons.Outlined.Apps),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSpaceApp(
    repository: GameSpaceRepository,
    isUsageAccessGranted: Boolean,
    isOverlayGranted: Boolean,
    isNotificationGranted: Boolean,
    onRequestNotification: () -> Unit
) {
    var currentTab by rememberSaveable { mutableStateOf(GameSpaceTab.HOME) }

    val overview by repository.connectionState.collectAsState()
    val stats by repository.deviceStats.collectAsState()
    val homeGames by repository.homeGamesFlow.collectAsState(initial = emptyList())
    val allApps by repository.allGamesFlow.collectAsState(initial = emptyList())

    val refreshIntervalMs by repository.refreshIntervalMs.collectAsState()
    val floatingHudEnabled by repository.floatingHudEnabled.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentTab) {
                            GameSpaceTab.HOME -> "GAME SPACE"
                            GameSpaceTab.APPS -> "APP MANAGER"
                            GameSpaceTab.SETTINGS -> "PREFERENCES"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                GameSpaceTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = KyroAccent,
                            indicatorColor = KyroPrimary
                        ),
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tab_crossfade"
            ) { targetTab ->
                when (targetTab) {
                    GameSpaceTab.HOME -> HomeScreen(
                        repository = repository,
                        overview = overview,
                        stats = stats,
                        homeGames = homeGames,
                        isUsageAccessGranted = isUsageAccessGranted,
                        isOverlayGranted = isOverlayGranted,
                        isNotificationGranted = isNotificationGranted,
                        onRequestNotification = onRequestNotification,
                        onNavigateToApps = { currentTab = GameSpaceTab.APPS }
                    )
                    GameSpaceTab.APPS -> AppsScreen(
                        repository = repository,
                        allApps = allApps
                    )
                    GameSpaceTab.SETTINGS -> SettingsScreen(
                        repository = repository,
                        overview = overview,
                        refreshIntervalMs = refreshIntervalMs,
                        floatingHudEnabled = floatingHudEnabled,
                        isOverlayGranted = isOverlayGranted
                    )
                }
            }
        }
    }
}
