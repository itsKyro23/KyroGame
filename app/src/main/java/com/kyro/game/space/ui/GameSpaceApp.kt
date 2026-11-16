package com.kyro.game.space.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyro.game.space.ads.AdMobManager
import com.kyro.game.space.data.repository.GameSpaceRepository
import com.kyro.game.space.ui.screens.AotCompilerScreen
import com.kyro.game.space.ui.screens.AppsScreen
import com.kyro.game.space.ui.screens.HomeScreen
import com.kyro.game.space.ui.screens.NetworkOptimizerScreen
import com.kyro.game.space.ui.screens.ResolutionChangerScreen
import com.kyro.game.space.ui.screens.SettingsScreen
import com.kyro.game.space.ui.screens.SplashScreen
import com.kyro.game.space.ui.screens.TextureTunerScreen
import com.kyro.game.space.ui.screens.ToolsScreen
import com.kyro.game.space.ui.theme.KyroAccent
import com.kyro.game.space.ui.theme.KyroPrimary
import com.kyro.game.space.ui.theme.LocalAppStrings
import com.kyro.game.space.ui.theme.appStrings
import com.kyro.game.space.ui.theme.rememberResponsiveMetrics
import com.kyro.game.space.ui.theme.stringsForLanguage
import androidx.compose.runtime.CompositionLocalProvider

enum class GameSpaceTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    TOOLS("Tools", Icons.Filled.Build, Icons.Outlined.Build),
    APPS("Apps", Icons.Filled.Apps, Icons.Filled.Apps),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

enum class ToolSubScreen {
    NONE,
    RESOLUTION_CHANGER,
    TEXTURE_TUNER,
    NETWORK_OPTIMIZER,
    AOT_COMPILER
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
    val context = LocalContext.current
    val responsive = rememberResponsiveMetrics()
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var currentTab by rememberSaveable { mutableStateOf(GameSpaceTab.HOME) }
    var activeSubScreen by rememberSaveable { mutableStateOf(ToolSubScreen.NONE) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showEnergyDialog by remember { mutableStateOf(false) }

    val overview by repository.connectionState.collectAsState()
    val stats by repository.deviceStats.collectAsState()
    val homeGames by repository.homeGamesFlow.collectAsState(initial = emptyList())
    val allApps by repository.allGamesFlow.collectAsState(initial = emptyList())

    val refreshIntervalMs by repository.refreshIntervalMs.collectAsState()
    val floatingHudEnabled by repository.floatingHudEnabled.collectAsState()
    val appLanguage by repository.appLanguage.collectAsState()
    val currentStrings = remember(appLanguage) { stringsForLanguage(appLanguage) }

    if (showSplash) {
        SplashScreen(
            overview = overview,
            onSplashFinished = {
                (context as? Activity)?.let { activity ->
                    AdMobManager.showAppOpenAdIfAvailable(activity) {
                        showSplash = false
                    }
                } ?: run {
                    showSplash = false
                }
            }
        )
        return
    }

    BackHandler(enabled = activeSubScreen == ToolSubScreen.NONE) {
        showExitDialog = true
    }

    CompositionLocalProvider(LocalAppStrings provides currentStrings) {
        val strings = appStrings()

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        if (activeSubScreen != ToolSubScreen.NONE) {
                            IconButton(
                                onClick = { activeSubScreen = ToolSubScreen.NONE },
                                modifier = Modifier.testTag("network_optimizer_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    },
                    title = {
                        Text(
                            text = when (activeSubScreen) {
                                ToolSubScreen.RESOLUTION_CHANGER -> strings.topTitleResolution
                                ToolSubScreen.TEXTURE_TUNER -> strings.topTitleTexture
                                ToolSubScreen.NETWORK_OPTIMIZER -> strings.topTitleNetwork
                                ToolSubScreen.AOT_COMPILER -> strings.topTitleAot
                                ToolSubScreen.NONE -> when (currentTab) {
                                    GameSpaceTab.HOME -> strings.topTitleHome
                                    GameSpaceTab.TOOLS -> strings.topTitleTools
                                    GameSpaceTab.APPS -> strings.topTitleApps
                                    GameSpaceTab.SETTINGS -> strings.topTitleSettings
                                }
                            },
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = responsive.topBarTitleSize
                            ),
                            fontWeight = FontWeight.Black,
                            letterSpacing = if (responsive.isCompactWidth) 0.5.sp else 1.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    actions = {
                        com.kyro.game.space.ui.components.NoxEnergyBadge(
                            onClick = { showEnergyDialog = true },
                            modifier = Modifier.padding(end = if (responsive.isCompactWidth) 8.dp else 12.dp)
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
                    GameSpaceTab.entries.forEach { tab ->
                        val isSelected = currentTab == tab && activeSubScreen == ToolSubScreen.NONE
                        val tabLabel = when (tab) {
                            GameSpaceTab.HOME -> strings.tabHome
                            GameSpaceTab.TOOLS -> strings.tabTools
                            GameSpaceTab.APPS -> strings.tabApps
                            GameSpaceTab.SETTINGS -> strings.tabSettings
                        }
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                activeSubScreen = ToolSubScreen.NONE
                                currentTab = tab
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tabLabel
                                )
                            },
                            label = {
                                Text(
                                    text = tabLabel,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                AnimatedContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = responsive.maxContentWidth),
                    targetState = activeSubScreen,
                    contentAlignment = Alignment.TopCenter,
                    transitionSpec = {
                        val transform = if (targetState != ToolSubScreen.NONE) {
                            (slideInHorizontally(
                                animationSpec = tween(260, easing = FastOutSlowInEasing)
                            ) { width -> width / 4 } + fadeIn(animationSpec = tween(220)))
                                .togetherWith(
                                    slideOutHorizontally(
                                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                                    ) { width -> -width / 4 } + fadeOut(animationSpec = tween(180))
                                )
                        } else {
                            (slideInHorizontally(
                                animationSpec = tween(260, easing = FastOutSlowInEasing)
                            ) { width -> -width / 4 } + fadeIn(animationSpec = tween(220)))
                                .togetherWith(
                                    slideOutHorizontally(
                                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                                    ) { width -> width / 4 } + fadeOut(animationSpec = tween(180))
                                )
                        }
                        transform.using(SizeTransform(clip = true) { _, _ -> snap() })
                    },
                    label = "subscreen_animated_transition"
                ) { subScreen ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        when (subScreen) {
                            ToolSubScreen.RESOLUTION_CHANGER -> {
                                ResolutionChangerScreen(
                                    repository = repository,
                                    onBack = { activeSubScreen = ToolSubScreen.NONE }
                                )
                            }
                            ToolSubScreen.TEXTURE_TUNER -> {
                                TextureTunerScreen(
                                    repository = repository,
                                    allApps = allApps,
                                    onBack = { activeSubScreen = ToolSubScreen.NONE }
                                )
                            }
                            ToolSubScreen.NETWORK_OPTIMIZER -> {
                                NetworkOptimizerScreen(
                                    repository = repository,
                                    stats = stats,
                                    onBack = { activeSubScreen = ToolSubScreen.NONE }
                                )
                            }
                            ToolSubScreen.AOT_COMPILER -> {
                                AotCompilerScreen(
                                    repository = repository,
                                    homeGames = homeGames,
                                    allApps = allApps,
                                    onBack = { activeSubScreen = ToolSubScreen.NONE }
                                )
                            }
                            ToolSubScreen.NONE -> {
                                AnimatedContent(
                                    modifier = Modifier.fillMaxSize(),
                                    targetState = currentTab,
                                    contentAlignment = Alignment.TopCenter,
                                    transitionSpec = {
                                        val direction = targetState.ordinal.compareTo(initialState.ordinal)
                                        val tabTransform = if (direction >= 0) {
                                            (slideInHorizontally(
                                                animationSpec = tween(250, easing = FastOutSlowInEasing)
                                            ) { width -> width / 5 } + fadeIn(animationSpec = tween(220)))
                                                .togetherWith(
                                                    slideOutHorizontally(
                                                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                                                    ) { width -> -width / 5 } + fadeOut(animationSpec = tween(180))
                                                )
                                        } else {
                                            (slideInHorizontally(
                                                animationSpec = tween(250, easing = FastOutSlowInEasing)
                                            ) { width -> -width / 5 } + fadeIn(animationSpec = tween(220)))
                                                .togetherWith(
                                                    slideOutHorizontally(
                                                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                                                    ) { width -> width / 5 } + fadeOut(animationSpec = tween(180))
                                                )
                                        }
                                        tabTransform.using(SizeTransform(clip = true) { _, _ -> snap() })
                                    },
                                    label = "tab_smooth_slide"
                                ) { targetTab ->
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.TopCenter
                                    ) {
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
                                                onNavigateToApps = { currentTab = GameSpaceTab.APPS },
                                                onNavigateToNetworkOptimizer = {
                                                    activeSubScreen = ToolSubScreen.NETWORK_OPTIMIZER
                                                },
                                                onNavigateToAotCompiler = {
                                                    activeSubScreen = ToolSubScreen.AOT_COMPILER
                                                }
                                            )
                                            GameSpaceTab.TOOLS -> ToolsScreen(
                                                repository = repository,
                                                onOpenResolutionChanger = {
                                                    activeSubScreen = ToolSubScreen.RESOLUTION_CHANGER
                                                },
                                                onOpenNetworkOptimizer = {
                                                    activeSubScreen = ToolSubScreen.NETWORK_OPTIMIZER
                                                },
                                                onOpenTextureTuner = {
                                                    activeSubScreen = ToolSubScreen.TEXTURE_TUNER
                                                },
                                                onOpenAotCompiler = {
                                                    activeSubScreen = ToolSubScreen.AOT_COMPILER
                                                }
                                            )
                                            GameSpaceTab.APPS -> AppsScreen(
                                                repository = repository,
                                                allApps = allApps,
                                                overview = overview
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
                    }
                }
            }
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(22.dp),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            tint = KyroAccent
                        )
                        Text(
                            text = strings.exitDialogTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Text(
                        text = strings.exitDialogMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp
                    )
                },
                dismissButton = {
                    TextButton(
                        onClick = { showExitDialog = false },
                        modifier = Modifier.testTag("exit_dialog_cancel_btn")
                    ) {
                        Text(
                            text = strings.exitDialogCancel,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showExitDialog = false
                            repository.deactivateTextureSessionOnExit()
                            (context as? Activity)?.finish()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KyroPrimary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("exit_dialog_confirm_btn")
                    ) {
                        Text(
                            text = strings.exitDialogConfirm,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }

        if (showEnergyDialog) {
            com.kyro.game.space.ui.components.NoxEnergyTopUpDialog(
                onDismiss = { showEnergyDialog = false }
            )
        }
    }
}
