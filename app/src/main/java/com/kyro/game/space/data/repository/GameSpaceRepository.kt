package com.kyro.game.space.data.repository

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.Display
import com.kyro.game.space.data.db.GameDao
import com.kyro.game.space.data.db.GameEntity
import com.kyro.game.space.data.db.GameSpaceDatabase
import com.kyro.game.space.data.executor.CommandResult
import com.kyro.game.space.data.executor.ExecutorFactory
import com.kyro.game.space.data.model.ConnectionOverview
import com.kyro.game.space.data.model.ConnectionStatus
import com.kyro.game.space.data.model.DeviceStats
import com.kyro.game.space.data.model.DnsBenchmarkResult
import com.kyro.game.space.data.model.DnsPreset
import com.kyro.game.space.data.model.ExecutionMethod
import com.kyro.game.space.data.model.GameApiTarget
import com.kyro.game.space.data.model.GameCustomConfig
import com.kyro.game.space.data.model.GameItem
import com.kyro.game.space.data.model.GameOptimizationMode
import com.kyro.game.space.data.model.DeviceTextureCompatibility
import com.kyro.game.space.data.model.GpuTextureCompatibilityProbe
import com.kyro.game.space.data.model.ManualAdbInfo
import com.kyro.game.space.data.model.NetworkOptimizerState
import com.kyro.game.space.data.model.NetworkTransportType
import com.kyro.game.space.data.model.ResolutionPreset
import com.kyro.game.space.data.model.ResolutionState
import com.kyro.game.space.data.model.RootInfo
import com.kyro.game.space.data.model.ShizukuInfo
import com.kyro.game.space.data.model.AngleDriverMode
import com.kyro.game.space.data.model.AotCompileMode
import com.kyro.game.space.data.model.AotCompilerState
import com.kyro.game.space.data.model.PlayBoostConfig
import com.kyro.game.space.data.model.PlayBoostTier
import com.kyro.game.space.data.model.TextureConfig
import com.kyro.game.space.data.model.TextureDetailLevel
import com.kyro.game.space.data.model.TextureQuickPreset
import com.kyro.game.space.data.model.TextureTunerState
import com.kyro.game.space.data.model.formatDownscaleClean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Calendar
import java.util.Locale

class GameSpaceRepository(
    private val context: Context,
    val executorFactory: ExecutorFactory = ExecutorFactory()
) {
    private val db = GameSpaceDatabase.getDatabase(context)
    private val dao: GameDao = db.gameDao()
    private val prefs = context.getSharedPreferences("kyro_game_space_prefs", Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Device telemetry state
    private val _deviceStats = MutableStateFlow(DeviceStats())
    val deviceStats: StateFlow<DeviceStats> = _deviceStats.asStateFlow()

    // Connection overview state
    private val _connectionState = MutableStateFlow(ConnectionOverview())
    val connectionState: StateFlow<ConnectionOverview> = _connectionState.asStateFlow()

    // Preferences state
    val executionMethod = MutableStateFlow(
        ExecutionMethod.valueOf(
            prefs.getString("execution_method", ExecutionMethod.SHIZUKU.name) ?: ExecutionMethod.SHIZUKU.name
        )
    )

    val refreshIntervalMs = MutableStateFlow(prefs.getLong("refresh_interval_ms", 2000L))
    val floatingHudEnabled = MutableStateFlow(prefs.getBoolean("floating_hud_enabled", false))

    private val _gameConfigs = MutableStateFlow<Map<String, GameCustomConfig>>(loadAllSavedGameConfigs())

    private val _networkOptimizerState = MutableStateFlow(loadInitialNetworkOptimizerState())
    val networkOptimizerState: StateFlow<NetworkOptimizerState> = _networkOptimizerState.asStateFlow()

    private val _resolutionState = MutableStateFlow(loadInitialResolutionState())
    val resolutionState: StateFlow<ResolutionState> = _resolutionState.asStateFlow()
    private var resolutionCountdownJob: Job? = null

    private val _textureTunerState = MutableStateFlow(loadInitialTextureTunerState())
    val textureTunerState: StateFlow<TextureTunerState> = _textureTunerState.asStateFlow()
    val eglTextureSession = com.kyro.game.space.data.model.EglTextureSessionController()

    private val _playBoostConfig = MutableStateFlow(loadInitialPlayBoostConfig())
    val playBoostConfig: StateFlow<PlayBoostConfig> = _playBoostConfig.asStateFlow()

    private val _aotCompilerState = MutableStateFlow(loadInitialAotCompilerState())
    val aotCompilerState: StateFlow<AotCompilerState> = _aotCompilerState.asStateFlow()

    @Volatile
    private var isProbingDaemon: Boolean = true

    val adbScanner = com.kyro.game.space.data.executor.AdbWirelessScanner(
        context = context,
        onEndpointDiscovered = { ip, connectPort, _ ->
            updateConnectionOverview()
            if (connectPort > 0 && !executorFactory.libAdbExecutor.isDaemonInjected) {
                val res = executorFactory.libAdbExecutor.connect(ip, connectPort)
                if (res.isSuccess) {
                    saveDaemonPrefs(ip, connectPort, executorFactory.libAdbExecutor.isDaemonInjected)
                }
                updateConnectionOverview()
            }
        }
    )

    // Shizuku listeners
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        updateShizukuStatus()
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        updateShizukuStatus()
    }
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        updateShizukuStatus()
    }

    // Capture startup pending confirmation state synchronously in constructor
    // so asynchronous startup checks never race with newly started resolution tests
    private val hadUnconfirmedResolutionOnStartup: Boolean = run {
        val pending = prefs.getBoolean("res_pending_confirmation", false)
        if (pending) {
            prefs.edit().putBoolean("res_pending_confirmation", false).commit()
        }
        pending
    }

    private val adbStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == com.kyro.game.space.service.AdbPairingReceiver.ACTION_ADB_STATE_CHANGED) {
                scope.launch {
                    try {
                        val host = prefs.getString("last_adb_host", "127.0.0.1") ?: "127.0.0.1"
                        val port = prefs.getInt("last_adb_port", 5555)
                        val wasInjected = prefs.getBoolean("daemon_was_injected", false)
                        executorFactory.libAdbExecutor.restoreSavedEndpoint(host, port, wasInjected)
                        checkDaemonAndSyncState()
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }

    init {
        try {
            val filter = IntentFilter(com.kyro.game.space.service.AdbPairingReceiver.ACTION_ADB_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(adbStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(adbStateReceiver, filter)
            }
        } catch (_: Throwable) {
        }
        try {
            val savedHost = prefs.getString("last_adb_host", "127.0.0.1") ?: "127.0.0.1"
            val savedPort = prefs.getInt("last_adb_port", 5555)
            val wasInjected = prefs.getBoolean("daemon_was_injected", false)
            executorFactory.libAdbExecutor.restoreSavedEndpoint(savedHost, savedPort, wasInjected)
            if (executionMethod.value == ExecutionMethod.MANUAL_ADB) {
                com.kyro.game.space.data.executor.AdbNativeBridge.initialize(context)
            }
        } catch (_: Throwable) {
        }
        try {
            initShizukuListeners()
        } catch (_: Throwable) {
        }
        try {
            if (executionMethod.value == ExecutionMethod.SHIZUKU) {
                updateShizukuStatus()
            } else {
                updateConnectionOverview()
            }
        } catch (_: Throwable) {
        }
        try {
            if (_textureTunerState.value.isAppliedLive && _textureTunerState.value.currentConfig.isActive) {
                eglTextureSession.activateSession(_textureTunerState.value.currentConfig)
            }
        } catch (_: Throwable) {
        }
        try {
            startPeriodicMonitoring()
        } catch (_: Throwable) {
        }
        scope.launch {
            try {
                refreshActiveExecutionMethodStatus()
            } catch (_: Throwable) {
            }
            if (hadUnconfirmedResolutionOnStartup) {
                try {
                    recoverUnconfirmedResolutionOnStartup()
                } catch (_: Throwable) {
                }
            }
            try {
                scanAndSyncInstalledApps()
            } catch (_: Throwable) {
            }
        }
        scope.launch {
            adbScanner.state.collect {
                if (executionMethod.value == ExecutionMethod.MANUAL_ADB) {
                    updateConnectionOverview()
                }
            }
        }
    }

    private fun initShizukuListeners() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (_: Throwable) {
            // Shizuku service not installed or binder unavailable
        }
    }

    fun deactivateTextureSessionOnExit() {
        try {
            eglTextureSession.destroy()
            val wasLive = prefs.getBoolean("tex_session_live", false)
            if (wasLive || _textureTunerState.value.isAppliedLive) {
                prefs.edit().putBoolean("tex_session_live", false).apply()
                Thread {
                    try {
                        kotlinx.coroutines.runBlocking {
                            executorFactory.getExecutor(executionMethod.value)
                                .execute(TextureConfig.buildSessionExitCleanupCommand(), timeoutMs = 1500L)
                        }
                    } catch (_: Throwable) {
                    }
                }.start()
            }
        } catch (_: Throwable) {
        }
    }

    fun cleanUp() {
        try {
            eglTextureSession.destroy()
            resolutionCountdownJob?.cancel()
            adbScanner.close()
            scope.cancel()
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
            try {
                context.unregisterReceiver(adbStateReceiver)
            } catch (_: Throwable) {
            }
        } catch (_: Throwable) {
        }
    }

    val allGamesFlow: Flow<List<GameItem>> = combine(
        dao.getAllGamesFlow(),
        _gameConfigs
    ) { entities, configs ->
        entities.map { entity ->
            entity.toGameItem(configs[entity.packageName] ?: getGameConfig(entity.packageName))
        }
    }

    val homeGamesFlow: Flow<List<GameItem>> = combine(
        dao.getHomeGamesFlow(),
        _gameConfigs
    ) { entities, configs ->
        entities.map { entity ->
            entity.toGameItem(configs[entity.packageName] ?: getGameConfig(entity.packageName))
        }
    }

    private fun loadAllSavedGameConfigs(): Map<String, GameCustomConfig> {
        val result = mutableMapOf<String, GameCustomConfig>()
        try {
            for ((key, value) in prefs.all) {
                if (key.startsWith("game_cfg_") && value is String) {
                    val pkg = key.removePrefix("game_cfg_")
                    parseGameConfig(value)?.let { result[pkg] = it }
                }
            }
        } catch (_: Throwable) {
        }
        return result
    }

    private fun parseGameConfig(serialized: String): GameCustomConfig? {
        return try {
            val parts = serialized.split("|")
            if (parts.size >= 3) {
                val mode = GameOptimizationMode.entries.firstOrNull { it.name == parts[0] }
                    ?: GameOptimizationMode.STANDARD
                val scale = parts[1].toFloatOrNull()?.coerceIn(0.3f, 1.0f) ?: 1.0f
                val fps = parts[2].toIntOrNull() ?: 60
                val target = if (parts.size >= 4) {
                    GameApiTarget.entries.firstOrNull { it.name == parts[3] }
                        ?: GameApiTarget.detectDefaultForDevice()
                } else {
                    GameApiTarget.detectDefaultForDevice()
                }
                val texConfig = if (parts.size >= 8) {
                    val preset = TextureQuickPreset.entries.firstOrNull { it.name == parts[4] }
                        ?: TextureQuickPreset.RETRO
                    val detail = TextureDetailLevel.entries.firstOrNull { it.name == parts[5] }
                        ?: TextureDetailLevel.ORIGINAL
                    val sharp = parts[6].toBooleanStrictOrNull() ?: false
                    val hardCutoff = parts[7].toBooleanStrictOrNull() ?: false
                    val driverMode = if (parts.size >= 9) {
                        AngleDriverMode.entries.firstOrNull { it.name == parts[8] } ?: AngleDriverMode.ANGLE
                    } else {
                        AngleDriverMode.ANGLE
                    }
                    TextureConfig(
                        preset = preset,
                        detailLevel = detail,
                        sharpPixels = sharp,
                        hardDistanceCutoff = hardCutoff,
                        driverMode = driverMode
                    )
                } else {
                    TextureConfig()
                }
                GameCustomConfig(
                    mode = mode,
                    downscaleFactor = scale,
                    fpsLimit = fps,
                    apiTarget = target,
                    textureConfig = texConfig
                )
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    fun getGameConfig(packageName: String): GameCustomConfig {
        _gameConfigs.value[packageName]?.let { return it }
        val raw = prefs.getString("game_cfg_$packageName", null) ?: return GameCustomConfig()
        return parseGameConfig(raw) ?: GameCustomConfig()
    }

    suspend fun saveAndApplyGameConfig(packageName: String, config: GameCustomConfig): Result<String> {
        val sanitizedScale = config.downscaleFactor.coerceIn(0.3f, 1.0f)
        // Preserve existing saved texture config for this package if the incoming config is inactive
        val existingTex = parseTextureConfig(prefs.getString("tex_cfg_$packageName", null))
        val effectiveTex = if (config.textureConfig.isActive) {
            config.textureConfig
        } else {
            existingTex ?: config.textureConfig
        }
        val sanitizedConfig = config.copy(
            downscaleFactor = sanitizedScale,
            textureConfig = effectiveTex
        )
        val serialized = String.format(
            Locale.US,
            "%s|%.2f|%d|%s|%s|%s|%b|%b|%s",
            sanitizedConfig.mode.name,
            sanitizedConfig.downscaleFactor,
            sanitizedConfig.fpsLimit,
            sanitizedConfig.apiTarget.name,
            effectiveTex.preset.name,
            effectiveTex.detailLevel.name,
            effectiveTex.sharpPixels,
            effectiveTex.hardDistanceCutoff,
            effectiveTex.driverMode.name
        )
        val editor = prefs.edit().putString("game_cfg_$packageName", serialized)
        if (effectiveTex.isActive) {
            editor.putString("tex_cfg_$packageName", serializeTextureConfig(effectiveTex))
        }
        editor.apply()

        _gameConfigs.value = _gameConfigs.value.toMutableMap().apply {
            put(packageName, sanitizedConfig)
        }
        if (_textureTunerState.value.selectedTargetPackage == packageName && effectiveTex.isActive) {
            _textureTunerState.value = _textureTunerState.value.copy(currentConfig = effectiveTex)
        }

        val scaleStr = formatDownscaleClean(sanitizedConfig.downscaleFactor)
        val fpsStr = sanitizedConfig.fpsLimit.toString()
        val shellCmd = sanitizedConfig.buildShellCommand(packageName)

        return try {
            val cmdRes = executeCommand(shellCmd)
            if (cmdRes.isSuccess) {
                Result.success("Applied ${sanitizedConfig.mode.label} (${scaleStr}x • ${fpsStr} FPS • ${sanitizedConfig.apiTarget.shortTag}) via ${executionMethod.value.name}")
            } else {
                Result.success("Saved ${sanitizedConfig.mode.label} (${scaleStr}x • ${fpsStr} FPS • ${sanitizedConfig.apiTarget.shortTag}). Grant ${executionMethod.value.name} permission to apply.")
            }
        } catch (_: Throwable) {
            Result.success("Saved ${sanitizedConfig.mode.label} (${scaleStr}x • ${fpsStr} FPS • ${sanitizedConfig.apiTarget.shortTag})")
        }
    }

    suspend fun setAddedToHome(packageName: String, isAdded: Boolean) {
        withContext(Dispatchers.IO) {
            dao.setAddedToHome(packageName, isAdded)
        }
    }

    suspend fun recordGameLaunch(packageName: String) {
        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            dao.updateLastPlayed(packageName, now)
        }
    }

    fun launchGame(packageName: String): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                scope.launch {
                    val cfg = getGameConfig(packageName)
                    if (cfg.mode != GameOptimizationMode.STANDARD || cfg.downscaleFactor < 0.99f) {
                        executeCommand(cfg.buildShellCommand(packageName))
                    }
                    val activeTex = getTextureConfigForTarget(packageName)
                    if (activeTex.isActive) {
                        val existingAngle = GpuTextureCompatibilityProbe.readExistingAngleDriverMap(context)
                        executeCommand(
                            activeTex.buildShellCommand(
                                packageName = packageName,
                                existingAngleMap = existingAngle,
                                forceStopTargets = false
                            )
                        )
                    }
                    recordGameLaunch(packageName)
                }
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun boostAndLaunchGame(
        packageName: String,
        onProgress: (progress: Float, status: String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val cfg = _playBoostConfig.value
            val cleanTarget = packageName.trim()

            // Stage 1: Deep RAM & Cache Purge
            if (cfg.deepRamPurge) {
                onProgress(0.16f, "[${cfg.tier.title}] Purging system RAM & trimming memory caches...")
                try {
                    executeCommand(
                        "am trim-memory system RUNNING_CRITICAL 2>/dev/null || true; " +
                            "pm trim-caches 4G 2>/dev/null || true"
                    )
                } catch (_: Throwable) {
                }
                delay(420)
            } else {
                onProgress(0.16f, "[${cfg.tier.title}] Verifying game runtime environment...")
                delay(300)
            }

            // Stage 2: Force-Stop Non-Essential Background Apps
            if (cfg.forceStopBackgroundApps) {
                onProgress(0.34f, "[${cfg.tier.title}] Force-stopping background apps to free CPU & RAM...")
                try {
                    val protectedPackages = setOf(
                        cleanTarget,
                        context.packageName,
                        "com.nox.gamespace",
                        "com.kyro.game.space",
                        "moe.shizuku.privileged.api",
                        "moe.shizuku.manager",
                        "com.shizuku.manager",
                        "com.google.android.gms",
                        "com.android.vending",
                        "com.android.systemui"
                    )
                    val userAppsToStop = try {
                        dao.getAllGamesFlow().first()
                            .filter { !it.isSystemApp && it.packageName !in protectedPackages }
                            .map { it.packageName }
                            .take(28)
                    } catch (_: Throwable) {
                        emptyList()
                    }
                    val forceStopBatch = if (userAppsToStop.isNotEmpty()) {
                        userAppsToStop.joinToString("; ") { "am force-stop $it 2>/dev/null || true" } + "; "
                    } else ""
                    executeCommand("${forceStopBatch}am kill-all 2>/dev/null || true")
                } catch (_: Throwable) {
                }
                delay(480)
            }

            // Stage 3: Fixed CPU/GPU Performance Lock & Phantom Process Unlimiter
            if (cfg.fixedPerformanceLock) {
                onProgress(0.52f, "[${cfg.tier.title}] Locking Fixed CPU/GPU Performance Mode & Phantom Bypass...")
                try {
                    executeCommand(
                        "cmd power set-fixed-performance-mode-enabled true 2>/dev/null || true; " +
                            "device_config put activity_manager max_phantom_processes 2147483647 2>/dev/null || true; " +
                            "settings put global settings_enable_monitor_phantom_procs false 2>/dev/null || true; " +
                            "cmd game loading-boost $cleanTarget 5000 2>/dev/null || true"
                    )
                } catch (_: Throwable) {
                }
                delay(420)
            }

            // Stage 4: SurfaceFlinger Zero-Lag Compositor
            if (cfg.surfaceFlingerZeroLag) {
                onProgress(0.68f, "[${cfg.tier.title}] Enabling SurfaceFlinger Zero-Lag frame latch...")
                try {
                    executeCommand(
                        "setprop debug.sf.disable_backpressure 1 2>/dev/null || true; " +
                            "setprop debug.sf.latch_unsignaled 1 2>/dev/null || true"
                    )
                } catch (_: Throwable) {
                }
                delay(380)
            }

            // Stage 5: Ultra Touch Polling & Sensitivity
            if (cfg.ultraTouchResponse) {
                onProgress(0.82f, "[${cfg.tier.title}] Boosting touch pointer speed & input response...")
                try {
                    executeCommand(
                        "settings put system pointer_speed 7 2>/dev/null || true; " +
                            "settings put global block_untrusted_touches 0 2>/dev/null || true"
                    )
                } catch (_: Throwable) {
                }
                delay(360)
            }

            // Stage 6: Block In-Game Heads-Up Pop-Up Notifications
            if (cfg.blockHeadsUpNotifications) {
                onProgress(0.93f, "[${cfg.tier.title}] Activating In-Game Anti Pop-Up Notification Guard...")
                try {
                    executeCommand("settings put global heads_up_notifications_enabled 0 2>/dev/null || true")
                } catch (_: Throwable) {
                }
                delay(340)
            }

            // Resolve game display name for persistent status notification
            val gameDisplayName = try {
                dao.getGameByPackage(cleanTarget)?.appName?.takeIf { it.isNotBlank() }
                    ?: run {
                        val pm = context.packageManager
                        val info = pm.getApplicationInfo(cleanTarget, 0)
                        pm.getApplicationLabel(info).toString()
                    }
            } catch (_: Throwable) {
                cleanTarget.substringAfterLast(".")
            }

            // Ensure notification permission is active so the persistent Restore notification always appears
            try {
                executeCommand(
                    "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS 2>/dev/null || true; " +
                        "cmd appops set ${context.packageName} POST_NOTIFICATION allow 2>/dev/null || true"
                )
            } catch (_: Throwable) {
            }

            // Show persistent notification indicating NOX Boost is active (stays even if NOX is swiped away)
            com.kyro.game.space.service.PlayBoostNotificationManager.showBoostActiveNotification(
                context = context,
                gameName = gameDisplayName,
                packageName = cleanTarget,
                config = cfg
            )

            // Final Stage: Launch game cleanly
            onProgress(1.0f, "${cfg.tier.title} Boost Complete! Launching game...")
            withContext(Dispatchers.Main) {
                launchGame(packageName)
            }
            true
        } catch (_: Throwable) {
            withContext(Dispatchers.Main) {
                launchGame(packageName)
            }
            false
        }
    }

    suspend fun scanAndSyncInstalledApps() = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            val entities = mutableListOf<GameEntity>()
            val currentPackages = mutableListOf<String>()

            for (resolve in resolveInfos) {
                val pkgName = resolve.activityInfo.packageName
                if (pkgName == context.packageName) continue // omit self

                currentPackages.add(pkgName)
                val appName = resolve.loadLabel(pm).toString()
                val appInfo = resolve.activityInfo.applicationInfo

                val isGame = checkIfGame(appInfo, pkgName)
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                val existing = dao.getGameByPackage(pkgName)
                val addedToHome = existing?.isAddedToHome ?: isGame

                val pInfo = try {
                    pm.getPackageInfo(pkgName, 0)
                } catch (_: Exception) {
                    null
                }
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pInfo?.longVersionCode ?: 0L
                } else {
                    @Suppress("DEPRECATION")
                    pInfo?.versionCode?.toLong() ?: 0L
                }
                val versionName = pInfo?.versionName ?: ""

                entities.add(
                    GameEntity(
                        packageName = pkgName,
                        appName = appName,
                        isGame = isGame,
                        isAddedToHome = addedToHome,
                        totalPlaytimeMs = existing?.totalPlaytimeMs ?: 0L,
                        lastPlayedTimestamp = existing?.lastPlayedTimestamp ?: 0L,
                        versionCode = versionCode,
                        versionName = versionName,
                        isSystemApp = isSystem
                    )
                )
            }

            dao.insertInitial(entities)
            dao.deleteRemovedPackages(currentPackages)
            refreshPlaytimeFromUsageStats()
        } catch (_: Throwable) {
        }
    }

    private fun checkIfGame(appInfo: ApplicationInfo, packageName: String): Boolean {
        // 1. Check official Android CATEGORY_GAME (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (appInfo.category == ApplicationInfo.CATEGORY_GAME) {
                return true
            }
        }
        // 2. Check legacy FLAG_IS_GAME (API 21+)
        @Suppress("DEPRECATION")
        if ((appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0) {
            return true
        }

        // 3. Fallback heuristic for popular titles and common package naming patterns
        val lowerPkg = packageName.lowercase()
        val gameSignatures = listOf(
            ".game", "games", "gaming", "pubg", "genshin", "roblox", "minecraft",
            "codm", "freefire", "mobilelegends", "brawlstars", "clash", "asphalt",
            "fifa", "ea.gp", "riotgames", "unity", "unreal", "supercell", "epicgames"
        )
        return gameSignatures.any { lowerPkg.contains(it) }
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    suspend fun refreshPlaytimeFromUsageStats() = withContext(Dispatchers.IO) {
        if (!hasUsageStatsPermission()) return@withContext

        try {
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                    ?: return@withContext

            val cal = Calendar.getInstance()
            val endTime = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, -30) // last 30 days
            val startTime = cal.timeInMillis

            val statsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)

            for ((pkg, stats) in statsMap) {
                val totalForegroundTime = stats.totalTimeInForeground
                val lastTimeUsed = stats.lastTimeUsed
                if (totalForegroundTime > 0L) {
                    dao.updatePlaytimeAndLastPlayed(pkg, totalForegroundTime, lastTimeUsed)
                }
            }
        } catch (_: Throwable) {
        }
    }

    // Device telemetry & active execution method monitoring
    private fun startPeriodicMonitoring() {
        scope.launch {
            var tick = 0
            while (isActive) {
                sampleDeviceStats()
                tick++
                // Keep Lightweight Texture ANGLE & GLES session active while Kyro Game Space is running
                if (tick % 3 == 0) {
                    val texState = _textureTunerState.value
                    if (texState.isAppliedLive && texState.currentConfig.isActive) {
                        try {
                            if (!eglTextureSession.isSessionActive) {
                                eglTextureSession.activateSession(texState.currentConfig)
                            }
                            val keeperCmd = texState.currentConfig.buildRuntimeKeeperCommand(
                                texState.selectedTargetPackage
                            )
                            executorFactory.getExecutor(executionMethod.value)
                                .execute(keeperCmd, timeoutMs = 1800L)
                        } catch (_: Throwable) {
                        }
                    }
                }
                when (executionMethod.value) {
                    ExecutionMethod.MANUAL_ADB -> {
                        if (tick % 2 == 0 && (!executorFactory.libAdbExecutor.isDaemonInjected || tick % 6 == 0)) {
                            try {
                                checkDaemonAndSyncState()
                            } catch (_: Throwable) {
                            }
                        }
                    }
                    ExecutionMethod.SHIZUKU -> {
                        if (tick % 4 == 0) {
                            updateShizukuStatus()
                        }
                    }
                    ExecutionMethod.ROOT -> {
                        // Root status is verified on selection or explicit check
                    }
                }
                delay(refreshIntervalMs.value)
            }
        }
    }

    private suspend fun sampleDeviceStats() = withContext(Dispatchers.IO) {
        val memInfo = ActivityManager.MemoryInfo()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availRamMb = memInfo.availMem / (1024 * 1024)
        val usedRamMb = (memInfo.totalMem - memInfo.availMem) / (1024 * 1024)
        val ramUsagePercent = if (totalRamMb > 0) ((usedRamMb.toDouble() / totalRamMb) * 100).toInt() else 0

        // Battery telemetry
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatusIntent = context.registerReceiver(null, batteryFilter)
        var batteryLevel = 100
        var batteryTempC: Float? = null
        var isCharging = false
        var voltageMv = 0
        var healthStr = "Good"

        if (batteryStatusIntent != null) {
            val rawLevel = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (rawLevel >= 0 && scale > 0) {
                batteryLevel = (rawLevel * 100) / scale
            }

            val rawTemp = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            if (rawTemp > 0) {
                batteryTempC = rawTemp / 10f
            }

            val status = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            voltageMv = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            healthStr = when (batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                else -> "Normal"
            }
        }

        // CPU Temperature: read sysfs or thermal files
        val cpuTempC = readCpuThermalTemp() ?: batteryTempC

        // Detect active Network Transport (Wi-Fi vs Mobile Data)
        val (transportType, transportDetails) = detectNetworkTransport()
        val currentNetOpt = _networkOptimizerState.value
        if (currentNetOpt.activeTransport != transportType || currentNetOpt.transportDetails != transportDetails) {
            _networkOptimizerState.value = currentNetOpt.copy(
                activeTransport = transportType,
                transportDetails = transportDetails
            )
        }

        // Ping measurement (real TCP/Socket latency to active DNS / pingHost:53)
        val targetHost = _networkOptimizerState.value.activePingHost
        val (pingMs, isOnline) = measureRealPing(targetHost, 53)

        val shortNetLabel = when (transportType) {
            NetworkTransportType.WIFI -> "Wi-Fi"
            NetworkTransportType.MOBILE_DATA -> "Mobile Data"
            NetworkTransportType.OTHER -> "Network"
            NetworkTransportType.OFFLINE -> "Offline"
        }

        _deviceStats.value = DeviceStats(
            cpuTempC = cpuTempC,
            batteryTempC = batteryTempC,
            totalRamMb = totalRamMb,
            usedRamMb = usedRamMb,
            availRamMb = availRamMb,
            ramUsagePercent = ramUsagePercent,
            pingMs = pingMs,
            pingHost = targetHost,
            networkType = shortNetLabel,
            activeDnsName = _networkOptimizerState.value.activeDnsName,
            isOnline = isOnline,
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            batteryVoltageMv = voltageMv,
            batteryHealth = healthStr,
            androidVersion = "Android ${Build.VERSION.RELEASE}",
            sdkInt = Build.VERSION.SDK_INT,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }

    private fun loadInitialNetworkOptimizerState(): NetworkOptimizerState {
        val savedDnsId = prefs.getString("net_opt_dns_id", "cloudflare") ?: "cloudflare"
        val preset = NetworkOptimizerState.DEFAULT_DNS_PRESETS.firstOrNull { it.id == savedDnsId }
            ?: NetworkOptimizerState.DEFAULT_DNS_PRESETS.first()
        val wifiLowLat = prefs.getBoolean("net_opt_wifi_low_lat", false)
        val mobileBoost = prefs.getBoolean("net_opt_mobile_boost", false)
        val bgScanOff = prefs.getBoolean("net_opt_bg_scan_off", false)
        return NetworkOptimizerState(
            selectedDnsId = preset.id,
            activeDnsName = preset.name,
            activePingHost = preset.primaryIp,
            privateDnsHostname = preset.privateDnsHostname,
            wifiLowLatencyEnabled = wifiLowLat,
            mobileDataBoostEnabled = mobileBoost,
            backgroundScanDisabled = bgScanOff
        )
    }

    fun detectNetworkTransport(): Pair<NetworkTransportType, String> {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return Pair(NetworkTransportType.WIFI, "Standard Network Interface")
            val activeNet = cm.activeNetwork
                ?: return Pair(NetworkTransportType.OFFLINE, "No active network interface detected")
            val caps = cm.getNetworkCapabilities(activeNet)
                ?: return Pair(NetworkTransportType.OFFLINE, "Network capabilities unavailable")

            val downKbps = caps.linkDownstreamBandwidthKbps
            val upKbps = caps.linkUpstreamBandwidthKbps
            val bwSummary = if (downKbps > 0) " • ${downKbps / 1000} Mbps Down" else ""

            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    Pair(
                        NetworkTransportType.WIFI,
                        "Wi-Fi Wireless Interface Active$bwSummary"
                    )
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    Pair(
                        NetworkTransportType.MOBILE_DATA,
                        "Mobile Cellular Data (4G/5G) Active$bwSummary"
                    )
                }
                else -> {
                    Pair(
                        NetworkTransportType.OTHER,
                        "Active Network Interface$bwSummary"
                    )
                }
            }
        } catch (_: Throwable) {
            Pair(NetworkTransportType.WIFI, "Network Interface Ready")
        }
    }

    suspend fun benchmarkAllDnsAndSelectStable(
        autoApplyBest: Boolean = true,
        rounds: Int = if (autoApplyBest) 10 else 6,
        minRoundDurationMs: Long = if (autoApplyBest) 1000L else 700L
    ): DnsPreset? = withContext(Dispatchers.IO) {
        val totalRounds = rounds.coerceIn(2, 15)
        val (transportType, transportDetails) = detectNetworkTransport()
        val presets = NetworkOptimizerState.DEFAULT_DNS_PRESETS
        val samplesById = presets.associate { it.id to mutableListOf<Int>() }
        val approxSec = ((totalRounds * minRoundDurationMs) / 1000L).coerceAtLeast(totalRounds.toLong())

        _networkOptimizerState.value = _networkOptimizerState.value.copy(
            activeTransport = transportType,
            transportDetails = transportDetails,
            isBenchmarking = true,
            benchmarkProgress = 0.04f,
            currentRound = 1,
            totalRounds = totalRounds,
            statusLog = "Sampling all ${presets.size} DNS servers over ~${approxSec}s ($totalRounds passes) to calculate average latency & stability..."
        )

        for (round in 1..totalRounds) {
            val roundStartMs = System.currentTimeMillis()
            coroutineScope {
                val jobs = presets.map { preset ->
                    async {
                        val (ms, ok) = measureRealPing(preset.primaryIp, 53)
                        Pair(preset.id, if (ok) ms else null)
                    }
                }
                jobs.forEach { deferred ->
                    val (id, pingVal) = deferred.await()
                    if (pingVal != null) {
                        samplesById[id]?.add(pingVal)
                    }
                }
            }

            // Build live intermediate results after this round
            val intermediate = presets.map { preset ->
                val list = samplesById[preset.id].orEmpty()
                if (list.isNotEmpty()) {
                    val avg = list.sum() / list.size
                    val minVal = list.minOrNull() ?: avg
                    val maxVal = list.maxOrNull() ?: avg
                    val jitter = maxVal - minVal
                    val stability = ((list.size * 100) / round).coerceIn(0, 100)
                    DnsBenchmarkResult(
                        preset = preset,
                        avgPingMs = avg,
                        minPingMs = minVal,
                        maxPingMs = maxVal,
                        jitterMs = jitter,
                        sampleCount = list.size,
                        stabilityPercent = stability,
                        isReachable = true
                    )
                } else {
                    DnsBenchmarkResult(
                        preset = preset,
                        avgPingMs = null,
                        minPingMs = null,
                        maxPingMs = null,
                        jitterMs = null,
                        sampleCount = 0,
                        stabilityPercent = 0,
                        isReachable = false
                    )
                }
            }

            val progress = round.toFloat() / totalRounds.toFloat()
            _networkOptimizerState.value = _networkOptimizerState.value.copy(
                isBenchmarking = true,
                benchmarkProgress = progress,
                currentRound = round,
                totalRounds = totalRounds,
                benchmarkResults = intermediate,
                statusLog = "Sampling all DNS servers (Pass $round/$totalRounds • ${(progress * 100).toInt()}%) — Averaging ping & jitter..."
            )

            if (round < totalRounds && minRoundDurationMs > 0L) {
                val elapsed = System.currentTimeMillis() - roundStartMs
                val waitMs = (minRoundDurationMs - elapsed).coerceAtLeast((minRoundDurationMs * 4L) / 5L)
                delay(waitMs)
            }
        }

        val finalSorted = _networkOptimizerState.value.benchmarkResults.sortedWith(
            compareBy<DnsBenchmarkResult> { !it.isReachable }
                .thenByDescending { it.stabilityPercent }
                .thenBy { (it.avgPingMs ?: 9999) + ((it.jitterMs ?: 0) / 2) }
        )

        val bestResult = finalSorted.firstOrNull { it.isReachable }
        val bestCandidate = bestResult?.preset ?: presets.first()
        val bestAvgStr = bestResult?.avgPingMs?.let { "${it}ms avg" } ?: "ready"
        val bestJitterStr = bestResult?.jitterMs?.let { "±${it}ms jitter" } ?: ""

        _networkOptimizerState.value = _networkOptimizerState.value.copy(
            isBenchmarking = false,
            benchmarkProgress = 1f,
            currentRound = totalRounds,
            totalRounds = totalRounds,
            benchmarkResults = finalSorted,
            statusLog = if (autoApplyBest) {
                "Most stable DNS selected: ${bestCandidate.name} (${bestCandidate.primaryIp} • $bestAvgStr $bestJitterStr)"
            } else {
                "Multi-pass average complete. Most stable: ${bestCandidate.name} (${bestCandidate.primaryIp} • $bestAvgStr $bestJitterStr)"
            }
        )

        if (autoApplyBest) {
            applyDnsPreset(bestCandidate)
            // Keep the detailed multi-pass summary message visible
            _networkOptimizerState.value = _networkOptimizerState.value.copy(
                statusLog = "Applied most stable DNS: ${bestCandidate.name} (${bestCandidate.primaryIp} • $bestAvgStr $bestJitterStr)"
            )
        } else {
            sampleDeviceStats()
        }

        bestCandidate
    }

    suspend fun applyDnsPreset(preset: DnsPreset): Result<String> = withContext(Dispatchers.IO) {
        prefs.edit().putString("net_opt_dns_id", preset.id).apply()
        val (transportType, transportDetails) = detectNetworkTransport()
        val cmd = "settings put global private_dns_mode hostname; " +
            "settings put global private_dns_specifier ${preset.privateDnsHostname}; " +
            "settings put global default_dns_server ${preset.primaryIp} 2>/dev/null || true"

        val res = executeCommand(cmd)
        val statusMsg = if (res.isSuccess) {
            "Active Stable DNS: ${preset.name} (${preset.primaryIp} • DoT: ${preset.privateDnsHostname}) applied via ${executionMethod.value.name}."
        } else {
            "Switched telemetry target to ${preset.name} (${preset.primaryIp}). Grant ${executionMethod.value.name} permission to apply system-wide Private DNS."
        }

        _networkOptimizerState.value = _networkOptimizerState.value.copy(
            activeTransport = transportType,
            transportDetails = transportDetails,
            selectedDnsId = preset.id,
            activeDnsName = preset.name,
            activePingHost = preset.primaryIp,
            privateDnsHostname = preset.privateDnsHostname,
            statusLog = statusMsg
        )
        sampleDeviceStats()
        Result.success(statusMsg)
    }

    suspend fun resetDnsToAutomatic(): Result<String> = withContext(Dispatchers.IO) {
        val defaultPreset = NetworkOptimizerState.DEFAULT_DNS_PRESETS.first()
        prefs.edit().putString("net_opt_dns_id", defaultPreset.id).apply()
        val cmd = "settings put global private_dns_mode opportunistic; settings delete global private_dns_specifier 2>/dev/null || true"
        val res = executeCommand(cmd)
        val msg = if (res.isSuccess) {
            "Reset Private DNS to Automatic (ISP / Opportunistic)."
        } else {
            "Reset ping monitor to default (${defaultPreset.primaryIp})."
        }
        _networkOptimizerState.value = _networkOptimizerState.value.copy(
            selectedDnsId = defaultPreset.id,
            activeDnsName = "Auto / Default (${defaultPreset.primaryIp})",
            activePingHost = defaultPreset.primaryIp,
            statusLog = msg
        )
        sampleDeviceStats()
        Result.success(msg)
    }

    suspend fun setWifiLowLatencyMode(enabled: Boolean): Result<String> = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("net_opt_wifi_low_lat", enabled).apply()
        val cmd = if (enabled) {
            "cmd wifi force-low-latency-mode enabled 2>/dev/null || true; " +
                "cmd wifi force-hi-perf-mode enabled 2>/dev/null || true; " +
                "settings put global wifi_sleep_policy 2; " +
                "settings put global wifi_scan_always_enabled 0"
        } else {
            "cmd wifi force-low-latency-mode disabled 2>/dev/null || true; " +
                "cmd wifi force-hi-perf-mode disabled 2>/dev/null || true"
        }
        val res = executeCommand(cmd)
        val msg = if (enabled) {
            if (res.isSuccess) "Wi-Fi Low-Latency & High-Perf Lock enabled via ${executionMethod.value.name}."
            else "Wi-Fi Low-Latency preference enabled."
        } else {
            "Wi-Fi Low-Latency mode disabled."
        }
        _networkOptimizerState.value = _networkOptimizerState.value.copy(
            wifiLowLatencyEnabled = enabled,
            statusLog = msg
        )
        Result.success(msg)
    }

    suspend fun setMobileDataBoostMode(enabled: Boolean): Result<String> = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("net_opt_mobile_boost", enabled).apply()
        val cmd = if (enabled) {
            "settings put global mobile_data_always_on 1; " +
                "settings put global netstats_enabled 1; " +
                "cmd netpolicy set restrict-background true 2>/dev/null || true"
        } else {
            "cmd netpolicy set restrict-background false 2>/dev/null || true"
        }
        val res = executeCommand(cmd)
        val msg = if (enabled) {
            if (res.isSuccess) "Mobile Data 4G/5G Priority & Background Saver enabled via ${executionMethod.value.name}."
            else "Mobile Data 4G/5G Priority preference enabled."
        } else {
            "Mobile Data 4G/5G Priority disabled."
        }
        _networkOptimizerState.value = _networkOptimizerState.value.copy(
            mobileDataBoostEnabled = enabled,
            statusLog = msg
        )
        Result.success(msg)
    }

    suspend fun setBackgroundScanDisabled(disabled: Boolean): Result<String> = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("net_opt_bg_scan_off", disabled).apply()
        val flag = if (disabled) "0" else "1"
        val throttleFlag = if (disabled) "1" else "0"
        val cmd = "settings put global wifi_scan_always_enabled $flag; " +
            "settings put global ble_scan_always_enabled $flag; " +
            "settings put global wifi_scan_throttle_enabled $throttleFlag"
        val res = executeCommand(cmd)
        val msg = if (disabled) {
            if (res.isSuccess) "Background Wi-Fi & BLE scanning suppressed to prevent ping spikes."
            else "Background scan suppression saved."
        } else {
            "Background network scanning restored to normal."
        }
        _networkOptimizerState.value = _networkOptimizerState.value.copy(
            backgroundScanDisabled = disabled,
            statusLog = msg
        )
        Result.success(msg)
    }

    suspend fun repairAndFlushNetwork(): Result<String> = withContext(Dispatchers.IO) {
        _networkOptimizerState.value = _networkOptimizerState.value.copy(
            isRepairing = true,
            statusLog = "Flushing DNS resolver cache, resetting stalled sockets, and re-calibrating route..."
        )
        val activePreset = NetworkOptimizerState.DEFAULT_DNS_PRESETS.firstOrNull {
            it.id == _networkOptimizerState.value.selectedDnsId
        } ?: NetworkOptimizerState.DEFAULT_DNS_PRESETS.first()

        val cmd = "cmd netd resolver flushdefaultif 2>/dev/null || ndc resolver flushdefaultif 2>/dev/null || true; " +
            "settings put global private_dns_mode hostname; " +
            "settings put global private_dns_specifier ${activePreset.privateDnsHostname}; " +
            "cmd connectivity airplane-mode disable 2>/dev/null || true"
        val res = executeCommand(cmd)
        sampleDeviceStats()
        val pingAfter = _deviceStats.value.pingMs?.let { "${it}ms" } ?: "Online"
        val msg = if (res.isSuccess) {
            "Network repaired & DNS cache flushed! Active Ping: $pingAfter (${activePreset.primaryIp})"
        } else {
            "Socket route refreshed! Active Ping: $pingAfter (${activePreset.primaryIp})"
        }
        _networkOptimizerState.value = _networkOptimizerState.value.copy(
            isRepairing = false,
            statusLog = msg
        )
        Result.success(msg)
    }

    private fun readCpuThermalTemp(): Float? {
        try {
            val thermalZonePaths = listOf(
                "/sys/class/thermal/thermal_zone0/temp",
                "/sys/class/thermal/thermal_zone1/temp",
                "/sys/devices/virtual/thermal/thermal_zone0/temp"
            )
            for (path in thermalZonePaths) {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val content = file.readText().trim()
                    val rawVal = content.toLongOrNull()
                    if (rawVal != null && rawVal > 0) {
                        return if (rawVal > 1000) rawVal / 1000f else rawVal.toFloat()
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return null
    }

    private fun measureRealPing(host: String, port: Int): Pair<Int?, Boolean> {
        return try {
            val startNanos = System.nanoTime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 2000)
            }
            val elapsedMs = ((System.nanoTime() - startNanos) / 1_000_000L).toInt()
            Pair(elapsedMs.coerceAtLeast(1), true)
        } catch (_: Throwable) {
            Pair(null, false)
        }
    }

    companion object {
        val KNOWN_SHIZUKU_PACKAGES = listOf(
            "moe.shizuku.privileged.api",
            "moe.shizuku.manager",
            "com.shizuku.manager"
        )
    }

    fun updateShizukuStatus() {
        try {
            var isRunning = false
            var isPermissionGranted = false
            var version = 0
            var uid = -1

            try {
                if (Shizuku.pingBinder()) {
                    isRunning = true
                    version = Shizuku.getVersion()
                    uid = Shizuku.getUid()
                    isPermissionGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                }
            } catch (_: Throwable) {
            }

            val isInstalled = try {
                isRunning || KNOWN_SHIZUKU_PACKAGES.any { isPackageInstalled(it) }
            } catch (_: Throwable) {
                isRunning
            }

            val shizuku = ShizukuInfo(
                isInstalled = isInstalled,
                isRunning = isRunning,
                isPermissionGranted = isPermissionGranted,
                version = version,
                uid = uid
            )

            updateConnectionOverview(shizuku = shizuku)
        } catch (_: Throwable) {
        }
    }

    suspend fun refreshActiveExecutionMethodStatus() {
        when (executionMethod.value) {
            ExecutionMethod.SHIZUKU -> {
                updateShizukuStatus()
            }
            ExecutionMethod.MANUAL_ADB -> {
                try {
                    com.kyro.game.space.data.executor.AdbNativeBridge.initialize(context)
                } catch (_: Throwable) {
                }
                checkDaemonAndSyncState()
            }
            ExecutionMethod.ROOT -> {
                checkRootStatus(triggerPrompt = false)
            }
        }
    }

    suspend fun checkDaemonAndSyncState(): Boolean {
        isProbingDaemon = true
        updateConnectionOverview()
        val alive = try {
            executorFactory.libAdbExecutor.probeDaemonStatus()
        } catch (_: Throwable) {
            false
        }
        isProbingDaemon = false
        if (alive) {
            saveDaemonPrefs(
                executorFactory.libAdbExecutor.currentHost,
                executorFactory.libAdbExecutor.currentPort,
                executorFactory.libAdbExecutor.isDaemonInjected
            )
        }
        updateConnectionOverview()
        return alive
    }

    private fun saveDaemonPrefs(host: String, port: Int, daemonInjected: Boolean) {
        prefs.edit()
            .putString("last_adb_host", host)
            .putInt("last_adb_port", port)
            .putBoolean("daemon_was_injected", daemonInjected)
            .apply()
    }

    fun requestShizukuPermission(requestCode: Int = 1001): String {
        return try {
            if (Shizuku.pingBinder()) {
                val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    updateShizukuStatus()
                    "ALREADY_GRANTED"
                } else {
                    Shizuku.requestPermission(requestCode)
                    "REQUESTED"
                }
            } else {
                // Try requesting permission or open the manager app
                try {
                    Shizuku.requestPermission(requestCode)
                    "REQUESTED"
                } catch (_: Throwable) {
                    val installedPkg = KNOWN_SHIZUKU_PACKAGES.firstOrNull { isPackageInstalled(it) }
                    if (installedPkg != null) {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(installedPkg)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            "OPENED_SHIZUKU"
                        } else {
                            "SERVICE_NOT_RUNNING"
                        }
                    } else {
                        updateShizukuStatus()
                        "NOT_INSTALLED"
                    }
                }
            }
        } catch (e: Throwable) {
            "ERROR: ${e.localizedMessage ?: e.message}"
        }
    }

    fun startWirelessAdbScan(showInteractiveNotification: Boolean = false) {
        adbScanner.startScanning(showInteractiveNotification)
        updateConnectionOverview()
    }

    fun stopWirelessAdbScan() {
        adbScanner.stopScanning()
        updateConnectionOverview()
    }

    suspend fun pairManualAdb(host: String, pairingPort: Int, code: String): Result<String> {
        val res = executorFactory.libAdbExecutor.pair(host, pairingPort, code)
        if (res.isSuccess) {
            val scanState = adbScanner.state.value
            if (scanState.connectPort > 0 && !executorFactory.libAdbExecutor.isDaemonInjected) {
                executorFactory.libAdbExecutor.connect(host, scanState.connectPort)
            }
            if (executorFactory.libAdbExecutor.isConnectedSession) {
                saveDaemonPrefs(
                    executorFactory.libAdbExecutor.currentHost,
                    executorFactory.libAdbExecutor.currentPort,
                    executorFactory.libAdbExecutor.isDaemonInjected
                )
            }
        }
        updateConnectionOverview()
        return res
    }

    suspend fun connectManualAdb(host: String, connectPort: Int): Result<String> {
        val res = executorFactory.libAdbExecutor.connect(host, connectPort)
        if (res.isSuccess) {
            saveDaemonPrefs(host, connectPort, executorFactory.libAdbExecutor.isDaemonInjected)
        }
        updateConnectionOverview()
        return res
    }

    suspend fun injectManualAdbDaemon(): Result<String> {
        val res = executorFactory.libAdbExecutor.injectPersistentDaemonWithExecutor(
            fallbackExecutor = null
        )
        if (res.isSuccess) {
            saveDaemonPrefs(
                executorFactory.libAdbExecutor.currentHost,
                executorFactory.libAdbExecutor.currentPort,
                true
            )
        }
        updateConnectionOverview()
        return res
    }

    fun disconnectManualAdb() {
        executorFactory.libAdbExecutor.disconnect()
        adbScanner.resetLastAutoConnect()
        prefs.edit().putBoolean("daemon_was_injected", false).apply()
        updateConnectionOverview()
    }

    suspend fun checkRootStatus(triggerPrompt: Boolean = false): RootInfo {
        val info = executorFactory.rootExecutor.probeAndRequestRoot(triggerPrompt = triggerPrompt)
        updateConnectionOverview()
        return info
    }

    suspend fun requestRootPermission(): RootInfo {
        return checkRootStatus(triggerPrompt = true)
    }

    suspend fun executeCommand(command: String): CommandResult {
        val currentMethod = executionMethod.value
        val executor = executorFactory.getExecutor(currentMethod)
        val result = executor.execute(command)

        updateConnectionOverview()
        _connectionState.value = _connectionState.value.copy(
            lastCommandLog = "[$currentMethod] \$ $command\n${if (result.isSuccess) result.stdout else result.stderr}"
        )
        return result
    }

    private fun updateConnectionOverview(shizuku: ShizukuInfo? = null) {
        val currentMethod = executionMethod.value
        val currentShizuku = shizuku ?: _connectionState.value.shizuku
        val libAdb = executorFactory.libAdbExecutor
        val rootExec = executorFactory.rootExecutor
        val scan = adbScanner.state.value

        val effectiveHost = if (libAdb.isConnectedSession) {
            libAdb.currentHost
        } else {
            scan.detectedIp
        }
        val effectivePort = if (scan.connectPort > 0) {
            scan.connectPort
        } else {
            libAdb.currentPort
        }

        val manualAdb = ManualAdbInfo(
            isConnected = libAdb.isConnectedSession,
            isDaemonInjected = libAdb.isDaemonInjected,
            daemonPort = com.kyro.game.space.data.executor.LibAdbCommandExecutor.DAEMON_PORT,
            daemonStatus = libAdb.daemonStatusText,
            isLibAdbLoaded = com.kyro.game.space.data.executor.AdbNativeBridge.isNativeLoaded,
            libAdbDetails = com.kyro.game.space.data.executor.AdbNativeBridge.statusMessage,
            host = effectiveHost,
            port = effectivePort,
            pairingPort = scan.pairingPort,
            isScanning = scan.isScanning,
            scanStatus = scan.statusText,
            autoConnectEnabled = scan.autoConnectEnabled,
            error = libAdb.lastError
        )

        val rootInfo = RootInfo(
            isSuBinaryPresent = rootExec.isSuBinaryPresent,
            isRootGranted = rootExec.isRootGranted,
            suVersion = rootExec.suVersion,
            statusMessage = rootExec.statusMessage
        )

        val (status, details) = when (currentMethod) {
            ExecutionMethod.SHIZUKU -> {
                when {
                    !currentShizuku.isRunning -> Pair(
                        ConnectionStatus.DISCONNECTED,
                        if (currentShizuku.isInstalled) "Shizuku app installed, but service is NOT running." else "Shizuku is not installed."
                    )
                    !currentShizuku.isPermissionGranted -> Pair(
                        ConnectionStatus.PERMISSION_REQUIRED,
                        "Shizuku is running (v${currentShizuku.version}), but permission is not granted."
                    )
                    else -> Pair(
                        ConnectionStatus.CONNECTED,
                        "Shizuku service running (v${currentShizuku.version}, UID: ${currentShizuku.uid})."
                    )
                }
            }
            ExecutionMethod.MANUAL_ADB -> {
                when {
                    manualAdb.isDaemonInjected -> Pair(
                        ConnectionStatus.CONNECTED,
                        "Persistent Daemon Active (127.0.0.1:${manualAdb.daemonPort}) • Connection stays alive even when Wireless Debugging is OFF!"
                    )
                    manualAdb.isConnected -> Pair(
                        ConnectionStatus.CONNECTED,
                        "Connected to Wireless ADB at ${manualAdb.host}:${manualAdb.port}"
                    )
                    isProbingDaemon && prefs.getBoolean("daemon_was_injected", false) -> Pair(
                        ConnectionStatus.CONNECTING,
                        "Verifying active Persistent Daemon on 127.0.0.1:${manualAdb.daemonPort}..."
                    )
                    else -> Pair(
                        ConnectionStatus.DISCONNECTED,
                        manualAdb.error ?: "Disconnected. Open Setup to Auto-Scan & Connect Wireless ADB and inject the Persistent Daemon."
                    )
                }
            }
            ExecutionMethod.ROOT -> {
                when {
                    rootInfo.isRootGranted -> Pair(
                        ConnectionStatus.CONNECTED,
                        rootInfo.statusMessage
                    )
                    rootInfo.isSuBinaryPresent -> Pair(
                        ConnectionStatus.PERMISSION_REQUIRED,
                        rootInfo.statusMessage
                    )
                    else -> Pair(
                        ConnectionStatus.DISCONNECTED,
                        rootInfo.statusMessage
                    )
                }
            }
        }

        _connectionState.value = ConnectionOverview(
            method = currentMethod,
            status = status,
            statusDetails = details,
            shizuku = currentShizuku,
            manualAdb = manualAdb,
            root = rootInfo,
            androidVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            lastCommandLog = _connectionState.value.lastCommandLog
        )
    }

    fun setExecutionMethod(method: ExecutionMethod) {
        executionMethod.value = method
        prefs.edit().putString("execution_method", method.name).apply()
        updateConnectionOverview()
        scope.launch {
            try {
                refreshActiveExecutionMethodStatus()
            } catch (_: Throwable) {
            }
        }
    }

    fun setRefreshInterval(ms: Long) {
        refreshIntervalMs.value = ms
        prefs.edit().putLong("refresh_interval_ms", ms).apply()
    }

    fun setFloatingHudEnabled(enabled: Boolean) {
        floatingHudEnabled.value = enabled
        prefs.edit().putBoolean("floating_hud_enabled", enabled).apply()
    }

    // =========================================================================
    // Resolution Changer Engine + 10s Countdown + Crash-Safe Watchdog
    // =========================================================================

    private fun detectHardwareDisplayMetrics(): Triple<Int, Int, Int> {
        var physW = prefs.getInt("hw_native_width", 0)
        var physH = prefs.getInt("hw_native_height", 0)
        var physDpi = prefs.getInt("hw_native_density", 0)

        try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
            if (display != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val maxMode = display.supportedModes.maxByOrNull { it.physicalWidth * it.physicalHeight }
                    ?: display.mode
                if (maxMode != null) {
                    val w = minOf(maxMode.physicalWidth, maxMode.physicalHeight)
                    val h = maxOf(maxMode.physicalWidth, maxMode.physicalHeight)
                    if (w >= 360 && h >= 640 && w >= physW) {
                        physW = w
                        physH = h
                    }
                }
            }
        } catch (_: Throwable) {
        }

        val resMetrics = context.resources.displayMetrics
        if (physW < 360 || physH < 640) {
            val w = minOf(resMetrics.widthPixels, resMetrics.heightPixels).coerceAtLeast(720)
            val h = maxOf(resMetrics.widthPixels, resMetrics.heightPixels).coerceAtLeast(1600)
            physW = maxOf(physW, w)
            physH = maxOf(physH, h)
        }
        if (physDpi <= 0) {
            physDpi = resMetrics.densityDpi.coerceAtLeast(320)
        }

        prefs.edit()
            .putInt("hw_native_width", physW)
            .putInt("hw_native_height", physH)
            .putInt("hw_native_density", physDpi)
            .apply()

        return Triple(physW, physH, physDpi)
    }

    private fun loadInitialResolutionState(): ResolutionState {
        val (nativeW, nativeH, nativeDpi) = detectHardwareDisplayMetrics()
        val confirmedW = prefs.getInt("res_confirmed_width", nativeW).coerceIn(360, nativeW)
        val confirmedH = prefs.getInt("res_confirmed_height", nativeH)
        val confirmedDpi = prefs.getInt("res_confirmed_density", nativeDpi)
        val autoScaleDpi = prefs.getBoolean("res_auto_scale_dpi", true)

        val presets = ResolutionState.buildDevicePresets(
            nativeWidth = nativeW,
            nativeHeight = nativeH,
            nativeDensity = nativeDpi
        )

        return ResolutionState(
            nativeWidth = nativeW,
            nativeHeight = nativeH,
            nativeDensityDpi = nativeDpi,
            currentWidth = confirmedW,
            currentHeight = confirmedH,
            currentDensityDpi = confirmedDpi,
            minWidth = ResolutionState.MIN_SUPPORTED_WIDTH,
            maxWidth = nativeW,
            autoScaleDensity = autoScaleDpi,
            presets = presets,
            pendingWidth = confirmedW,
            pendingHeight = confirmedH,
            pendingDensity = confirmedDpi
        )
    }

    private suspend fun recoverUnconfirmedResolutionOnStartup() = withContext(Dispatchers.IO) {
        val cur = _resolutionState.value
        val prevW = prefs.getInt("res_confirmed_width", cur.nativeWidth).coerceIn(360, cur.nativeWidth)
        val prevH = prefs.getInt("res_confirmed_height", cur.nativeHeight)
        val prevDpi = prefs.getInt("res_confirmed_density", cur.nativeDensityDpi)

        val revertCmd = if (prevW == cur.nativeWidth) {
            "wm size reset; wm density reset"
        } else {
            "wm size ${prevW}x${prevH}; wm density $prevDpi"
        }
        executeCommand(revertCmd)
        if (!_resolutionState.value.isPendingConfirmation) {
            _resolutionState.value = _resolutionState.value.copy(
                currentWidth = prevW,
                currentHeight = prevH,
                currentDensityDpi = prevDpi,
                isPendingConfirmation = false,
                statusMessage = "Auto-Rollback Protection: Restored original resolution (${prevW}×${prevH}) after unexpected interruption."
            )
        }
    }

    fun setAutoScaleDensity(enabled: Boolean) {
        prefs.edit().putBoolean("res_auto_scale_dpi", enabled).apply()
        _resolutionState.value = _resolutionState.value.copy(autoScaleDensity = enabled)
    }

    suspend fun applyResolutionWithSafetyCountdown(targetWidthInput: Int): Result<String> = withContext(Dispatchers.IO) {
        resolutionCountdownJob?.cancel()
        val state = _resolutionState.value
        val targetW = ((targetWidthInput.coerceIn(state.minWidth, state.maxWidth)) / 2) * 2
        val targetH = state.computeHeightForWidth(targetW)
        val targetDpi = if (state.autoScaleDensity) {
            state.computeDensityForWidth(targetW)
        } else {
            state.nativeDensityDpi
        }

        val prevW = state.currentWidth
        val prevH = state.currentHeight
        val prevDpi = state.currentDensityDpi

        // Record pending confirmation flag synchronously BEFORE running wm size in case of app crash
        prefs.edit()
            .putBoolean("res_pending_confirmation", true)
            .putInt("res_confirmed_width", prevW)
            .putInt("res_confirmed_height", prevH)
            .putInt("res_confirmed_density", prevDpi)
            .commit()

        val applyCmd = if (targetW == state.nativeWidth) {
            "wm size reset; wm density reset"
        } else if (state.autoScaleDensity) {
            "wm size ${targetW}x${targetH}; wm density $targetDpi"
        } else {
            "wm size ${targetW}x${targetH}"
        }

        val revertCmd = if (prevW == state.nativeWidth) {
            "wm size reset; wm density reset"
        } else if (state.autoScaleDensity) {
            "wm size ${prevW}x${prevH}; wm density $prevDpi"
        } else {
            "wm size ${prevW}x${prevH}"
        }

        // Atomic shell command:
        // 1. Clear confirmation marker /data/local/tmp/kyro_res_confirmed
        // 2. Apply target resolution & density
        // 3. Spawn detached background shell watchdog (survives app crash / force close!) that reverts after 11s if not confirmed
        val fullShellCmd = "rm -f /data/local/tmp/kyro_res_confirmed 2>/dev/null || true; " +
            "$applyCmd; " +
            "nohup sh -c 'sleep 11; if [ ! -f /data/local/tmp/kyro_res_confirmed ]; then $revertCmd; fi' >/dev/null 2>&1 &"

        _resolutionState.value = state.copy(
            isApplying = true,
            statusMessage = "Applying ${targetW}×${targetH} (${targetDpi} DPI) with 10s fail-safe protection..."
        )

        val cmdRes = try {
            executeCommand(fullShellCmd)
        } catch (t: Throwable) {
            // If anything threw during execution, immediately roll back
            prefs.edit().putBoolean("res_pending_confirmation", false).commit()
            try {
                executeCommand(revertCmd)
            } catch (_: Throwable) {
            }
            _resolutionState.value = _resolutionState.value.copy(
                isApplying = false,
                isPendingConfirmation = false,
                statusMessage = "Error applying resolution; reverted to safe ${prevW}×${prevH}."
            )
            return@withContext Result.failure(t)
        }

        // Start 10-second confirmation dialog & in-app countdown timer
        _resolutionState.value = _resolutionState.value.copy(
            isApplying = false,
            isPendingConfirmation = true,
            countdownSeconds = ResolutionState.CONFIRMATION_TIMEOUT_SECONDS,
            pendingWidth = targetW,
            pendingHeight = targetH,
            pendingDensity = targetDpi,
            statusMessage = if (cmdRes.isSuccess) {
                "Testing ${targetW}×${targetH} (${targetDpi} DPI). Confirm within 10s or it will revert automatically."
            } else {
                "Previewing ${targetW}×${targetH} (${targetDpi} DPI). Grant ${executionMethod.value.name} permission for system-wide display scaling."
            }
        )

        startResolutionCountdownTimer()
        Result.success("Testing ${targetW}×${targetH}")
    }

    private fun startResolutionCountdownTimer() {
        resolutionCountdownJob?.cancel()
        resolutionCountdownJob = scope.launch {
            for (sec in ResolutionState.CONFIRMATION_TIMEOUT_SECONDS downTo 1) {
                if (!_resolutionState.value.isPendingConfirmation) return@launch
                _resolutionState.value = _resolutionState.value.copy(countdownSeconds = sec)
                delay(1000L)
            }
            if (_resolutionState.value.isPendingConfirmation) {
                revertResolutionToOriginal("10s confirmation timer expired. Automatically reverted to original resolution.")
            }
        }
    }

    suspend fun confirmResolutionChange(): Result<String> = withContext(Dispatchers.IO) {
        resolutionCountdownJob?.cancel()
        val cur = _resolutionState.value
        val newW = cur.pendingWidth
        val newH = cur.pendingHeight
        val newDpi = cur.pendingDensity

        // Signal the detached shell watchdog not to revert
        try {
            executeCommand("touch /data/local/tmp/kyro_res_confirmed 2>/dev/null || true")
        } catch (_: Throwable) {
        }

        prefs.edit()
            .putBoolean("res_pending_confirmation", false)
            .putInt("res_confirmed_width", newW)
            .putInt("res_confirmed_height", newH)
            .putInt("res_confirmed_density", newDpi)
            .commit()

        val msg = "Saved & confirmed resolution: ${newW}×${newH} (${newDpi} DPI)."
        _resolutionState.value = cur.copy(
            currentWidth = newW,
            currentHeight = newH,
            currentDensityDpi = newDpi,
            isPendingConfirmation = false,
            countdownSeconds = ResolutionState.CONFIRMATION_TIMEOUT_SECONDS,
            statusMessage = msg
        )
        Result.success(msg)
    }

    suspend fun revertResolutionToOriginal(
        reason: String = "Reverted to previous safe resolution."
    ): Result<String> = withContext(Dispatchers.IO) {
        resolutionCountdownJob?.cancel()
        val cur = _resolutionState.value
        val prevW = cur.currentWidth.coerceIn(cur.minWidth, cur.maxWidth)
        val prevH = cur.currentHeight
        val prevDpi = cur.currentDensityDpi

        prefs.edit().putBoolean("res_pending_confirmation", false).commit()

        val revertCmd = if (prevW == cur.nativeWidth) {
            "touch /data/local/tmp/kyro_res_confirmed 2>/dev/null || true; wm size reset; wm density reset"
        } else if (cur.autoScaleDensity) {
            "touch /data/local/tmp/kyro_res_confirmed 2>/dev/null || true; wm size ${prevW}x${prevH}; wm density $prevDpi"
        } else {
            "touch /data/local/tmp/kyro_res_confirmed 2>/dev/null || true; wm size ${prevW}x${prevH}"
        }

        try {
            executeCommand(revertCmd)
        } catch (_: Throwable) {
        }

        _resolutionState.value = cur.copy(
            isPendingConfirmation = false,
            countdownSeconds = ResolutionState.CONFIRMATION_TIMEOUT_SECONDS,
            pendingWidth = prevW,
            pendingHeight = prevH,
            pendingDensity = prevDpi,
            statusMessage = "$reason (${prevW}×${prevH})"
        )
        Result.success(reason)
    }

    suspend fun resetResolutionToNative(): Result<String> = withContext(Dispatchers.IO) {
        resolutionCountdownJob?.cancel()
        val cur = _resolutionState.value
        prefs.edit()
            .putBoolean("res_pending_confirmation", false)
            .putInt("res_confirmed_width", cur.nativeWidth)
            .putInt("res_confirmed_height", cur.nativeHeight)
            .putInt("res_confirmed_density", cur.nativeDensityDpi)
            .commit()

        val cmd = "touch /data/local/tmp/kyro_res_confirmed 2>/dev/null || true; wm size reset; wm density reset"
        val res = try {
            executeCommand(cmd)
        } catch (_: Throwable) {
            null
        }

        val msg = if (res?.isSuccess == true) {
            "Restored native hardware resolution: ${cur.nativeWidth}×${cur.nativeHeight} (${cur.nativeDensityDpi} DPI)."
        } else {
            "Reset to native profile: ${cur.nativeWidth}×${cur.nativeHeight} (${cur.nativeDensityDpi} DPI)."
        }

        _resolutionState.value = cur.copy(
            currentWidth = cur.nativeWidth,
            currentHeight = cur.nativeHeight,
            currentDensityDpi = cur.nativeDensityDpi,
            pendingWidth = cur.nativeWidth,
            pendingHeight = cur.nativeHeight,
            pendingDensity = cur.nativeDensityDpi,
            isPendingConfirmation = false,
            statusMessage = msg
        )
        Result.success(msg)
    }

    // =========================================================================
    // Lightweight Texture & GPU Texture Filter Tuner + Compatibility Engine
    // =========================================================================

    private fun serializeTextureConfig(config: TextureConfig): String {
        return "${config.preset.name}|${config.detailLevel.name}|${config.sharpPixels}|${config.hardDistanceCutoff}|${config.driverMode.name}"
    }

    private fun parseTextureConfig(raw: String?): TextureConfig? {
        if (raw.isNullOrBlank()) return null
        return try {
            val parts = raw.split("|")
            if (parts.size >= 4) {
                val preset = TextureQuickPreset.entries.firstOrNull { it.name == parts[0] }
                    ?: TextureQuickPreset.RETRO
                val detail = TextureDetailLevel.entries.firstOrNull { it.name == parts[1] }
                    ?: TextureDetailLevel.RETRO
                val sharp = parts[2].toBooleanStrictOrNull() ?: true
                val hardCutoff = parts[3].toBooleanStrictOrNull() ?: false
                val driverMode = if (parts.size >= 5) {
                    AngleDriverMode.entries.firstOrNull { it.name == parts[4] } ?: AngleDriverMode.ANGLE
                } else {
                    AngleDriverMode.ANGLE
                }
                TextureConfig(
                    preset = preset,
                    detailLevel = detail,
                    sharpPixels = sharp,
                    hardDistanceCutoff = hardCutoff,
                    driverMode = driverMode
                )
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun loadInitialTextureTunerState(): TextureTunerState {
        val savedTargetPkg = (prefs.getString("tex_selected_target_pkg", "") ?: "").trim()
        val compatibility = GpuTextureCompatibilityProbe.inspectDeviceCompatibility(
            context = context,
            selectedTargetPackage = savedTargetPkg
        )
        val key = if (savedTargetPkg.isBlank()) "tex_cfg_global" else "tex_cfg_$savedTargetPkg"
        val defaultRetro = TextureConfig(
            preset = TextureQuickPreset.RETRO,
            detailLevel = TextureDetailLevel.RETRO,
            sharpPixels = true,
            hardDistanceCutoff = false,
            driverMode = AngleDriverMode.ANGLE
        )
        val rawSaved = parseTextureConfig(prefs.getString(key, null))
        val savedConfig = when {
            rawSaved != null && rawSaved.isActive -> rawSaved
            savedTargetPkg.isNotBlank() && getGameConfig(savedTargetPkg).textureConfig.isActive -> {
                getGameConfig(savedTargetPkg).textureConfig
            }
            rawSaved != null -> rawSaved.copy(
                preset = TextureQuickPreset.RETRO,
                detailLevel = TextureDetailLevel.RETRO,
                sharpPixels = true
            )
            else -> defaultRetro
        }

        return TextureTunerState(
            selectedTargetPackage = savedTargetPkg,
            currentConfig = savedConfig,
            compatibility = compatibility,
            isAppliedLive = compatibility.targetGameInAngle || compatibility.activeAnglePackagesCount > 0,
            statusMessage = "${compatibility.compatibilityBadge} • Ready to apply ANGLE & Lightweight Texture."
        )
    }

    fun getTextureConfigForTarget(packageName: String): TextureConfig {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isNotEmpty()) {
            parseTextureConfig(prefs.getString("tex_cfg_$cleanPkg", null))
                ?.takeIf { it.isActive }
                ?.let { return it }
            val gameTex = getGameConfig(cleanPkg).textureConfig
            if (gameTex.isActive) return gameTex
        }
        return parseTextureConfig(prefs.getString("tex_cfg_global", null))
            ?.takeIf { it.isActive }
            ?: TextureConfig(
                preset = TextureQuickPreset.RETRO,
                detailLevel = TextureDetailLevel.RETRO,
                sharpPixels = true,
                hardDistanceCutoff = false,
                driverMode = AngleDriverMode.ANGLE
            )
    }

    private fun persistTextureConfigLocally(cleanPkg: String, config: TextureConfig) {
        val serialized = serializeTextureConfig(config)
        val prefKey = if (cleanPkg.isEmpty()) "tex_cfg_global" else "tex_cfg_$cleanPkg"
        prefs.edit()
            .putString("tex_selected_target_pkg", cleanPkg)
            .putString(prefKey, serialized)
            .apply()

        if (cleanPkg.isNotEmpty()) {
            val existingGameCfg = getGameConfig(cleanPkg)
            val updatedGameCfg = existingGameCfg.copy(textureConfig = config)
            val gameSerialized = String.format(
                Locale.US,
                "%s|%.2f|%d|%s|%s|%s|%b|%b|%s",
                updatedGameCfg.mode.name,
                updatedGameCfg.downscaleFactor,
                updatedGameCfg.fpsLimit,
                updatedGameCfg.apiTarget.name,
                config.preset.name,
                config.detailLevel.name,
                config.sharpPixels,
                config.hardDistanceCutoff,
                config.driverMode.name
            )
            prefs.edit().putString("game_cfg_$cleanPkg", gameSerialized).apply()
            _gameConfigs.value = _gameConfigs.value.toMutableMap().apply {
                put(cleanPkg, updatedGameCfg)
            }
        }
    }

    fun selectTextureTargetPackage(packageName: String) {
        val cleanPkg = packageName.trim()
        val currentDraft = _textureTunerState.value.currentConfig
        val savedTargetCfg = if (cleanPkg.isNotEmpty()) {
            parseTextureConfig(prefs.getString("tex_cfg_$cleanPkg", null))?.takeIf { it.isActive }
                ?: getGameConfig(cleanPkg).textureConfig.takeIf { it.isActive }
        } else {
            parseTextureConfig(prefs.getString("tex_cfg_global", null))?.takeIf { it.isActive }
        }
        val nextCfg = savedTargetCfg ?: if (currentDraft.isActive) {
            currentDraft
        } else {
            TextureConfig(
                preset = TextureQuickPreset.RETRO,
                detailLevel = TextureDetailLevel.RETRO,
                sharpPixels = true,
                hardDistanceCutoff = false,
                driverMode = AngleDriverMode.ANGLE
            )
        }
        persistTextureConfigLocally(cleanPkg, nextCfg)
        val updatedCompat = GpuTextureCompatibilityProbe.inspectDeviceCompatibility(
            context = context,
            selectedTargetPackage = cleanPkg
        )
        val targetLabel = if (cleanPkg.isEmpty()) "Global GPU Profile" else cleanPkg
        _textureTunerState.value = _textureTunerState.value.copy(
            selectedTargetPackage = cleanPkg,
            currentConfig = nextCfg,
            compatibility = updatedCompat,
            isAppliedLive = updatedCompat.targetGameInAngle,
            statusMessage = "Target set to $targetLabel (${nextCfg.summaryBadge}). Tap Save & Apply to activate."
        )
    }

    fun updateTextureConfigDraft(config: TextureConfig) {
        val cleanPkg = _textureTunerState.value.selectedTargetPackage.trim()
        persistTextureConfigLocally(cleanPkg, config)
        _textureTunerState.value = _textureTunerState.value.copy(
            currentConfig = config,
            isAppliedLive = false,
            statusMessage = "Selected: ${config.summaryBadge} — Tap Save & Apply to inject into GPU & ANGLE driver."
        )
    }

    suspend fun refreshTextureCompatibility(): DeviceTextureCompatibility = withContext(Dispatchers.IO) {
        val selectedPkg = _textureTunerState.value.selectedTargetPackage.trim()
        _textureTunerState.value = _textureTunerState.value.copy(
            isCheckingCompatibility = true,
            statusMessage = "Scanning GPU renderer, Vulkan support, and ANGLE driver registrations..."
        )
        val liveAngleMap = collectExistingAngleMapWithShellFallback(selectedPkg)
        val baseCompat = GpuTextureCompatibilityProbe.inspectDeviceCompatibility(
            context = context,
            selectedTargetPackage = selectedPkg,
            angleMapOverride = liveAngleMap,
            eglRendererOverride = eglTextureSession.activeGlRenderer
        )
        _textureTunerState.value = _textureTunerState.value.copy(
            compatibility = baseCompat,
            isCheckingCompatibility = false,
            isAppliedLive = baseCompat.targetGameInAngle,
            statusMessage = "Device Check: ${baseCompat.compatibilityBadge}"
        )
        baseCompat
    }

    private suspend fun collectExistingAngleMapWithShellFallback(
        keepPackage: String = "",
        keepPackages: Collection<String> = emptyList()
    ): Map<String, String> {
        val rawMap = LinkedHashMap<String, String>()
        rawMap.putAll(GpuTextureCompatibilityProbe.readExistingAngleDriverMap(context))
        if (rawMap.isEmpty()) {
            try {
                val res = executeCommand(
                    "echo PKGS:\$(settings get global angle_gl_driver_selection_pkgs 2>/dev/null); " +
                        "echo VALS:\$(settings get global angle_gl_driver_selection_values 2>/dev/null)"
                )
                if (res.isSuccess && res.stdout.isNotBlank()) {
                    val lines = res.stdout.lines()
                    val pkgsLine = lines.firstOrNull { it.startsWith("PKGS:") }?.removePrefix("PKGS:")?.trim().orEmpty()
                    val valsLine = lines.firstOrNull { it.startsWith("VALS:") }?.removePrefix("VALS:")?.trim().orEmpty()
                    if (pkgsLine.isNotEmpty() && pkgsLine != "null") {
                        val pkgs = pkgsLine.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val vals = valsLine.split(",").map { it.trim() }
                        for (i in pkgs.indices) {
                            rawMap[pkgs[i]] = vals.getOrNull(i)?.ifEmpty { "default" } ?: "default"
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }

        // Filter out uninstalled stale packages so angle_gl_driver_selection_pkgs only contains real apps
        val keepSet = (keepPackages.map { it.trim() } + keepPackage.trim()).filter { it.isNotEmpty() }.toSet()
        val cleaned = LinkedHashMap<String, String>()
        for ((pkg, driverVal) in rawMap) {
            if (pkg in keepSet || isPackageInstalled(pkg)) {
                cleaned[pkg] = driverVal
            }
        }
        return cleaned
    }

    suspend fun saveAndApplyTextureConfig(
        packageName: String = _textureTunerState.value.selectedTargetPackage,
        config: TextureConfig = _textureTunerState.value.currentConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanPkg = packageName.trim()
        val effectiveConfig = if (config.isActive) {
            config.copy(driverMode = AngleDriverMode.ANGLE)
        } else {
            config.copy(driverMode = AngleDriverMode.DEFAULT)
        }

        _textureTunerState.value = _textureTunerState.value.copy(
            selectedTargetPackage = cleanPkg,
            currentConfig = effectiveConfig,
            isApplying = true
        )

        persistTextureConfigLocally(cleanPkg, effectiveConfig)

        // Start or release the dedicated offscreen EGL14 + SurfaceTexture + GLES20 session
        if (effectiveConfig.isActive) {
            eglTextureSession.activateSession(effectiveConfig)
        } else {
            eglTextureSession.releaseSession()
        }

        // When Global is selected, gather all games added to the user's Game Space Home
        val homePkgs = if (cleanPkg.isEmpty()) {
            try {
                val dbHome = dao.getHomeGamesFlow().first().map { it.packageName }
                (dbHome + _gameConfigs.value.keys).distinct()
            } catch (_: Throwable) {
                _gameConfigs.value.keys.toList()
            }
        } else {
            emptyList()
        }

        val existingAngleMap = collectExistingAngleMapWithShellFallback(
            keepPackage = cleanPkg,
            keepPackages = homePkgs
        )

        val cmd = effectiveConfig.buildShellCommand(
            packageName = cleanPkg,
            additionalTargetPackages = homePkgs,
            existingAngleMap = existingAngleMap,
            forceStopTargets = true
        )
        val cmdRes = try {
            executeCommand(cmd)
        } catch (_: Throwable) {
            null
        }

        val appliedOk = cmdRes?.isSuccess == true
        prefs.edit()
            .putBoolean("tex_session_live", appliedOk && effectiveConfig.isActive)
            .putString(
                "tex_active_keeper_cmd",
                if (appliedOk && effectiveConfig.isActive) effectiveConfig.buildRuntimeKeeperCommand(cleanPkg) else ""
            )
            .apply()

        val postApplyMap = if (appliedOk) {
            val verified = collectExistingAngleMapWithShellFallback(
                keepPackage = cleanPkg,
                keepPackages = homePkgs
            ).toMutableMap()
            val expectedVal = if (effectiveConfig.isActive) effectiveConfig.driverMode.settingValue else "default"
            if (cleanPkg.isNotEmpty()) {
                verified.putIfAbsent(cleanPkg, expectedVal)
            } else {
                for (p in homePkgs) verified.putIfAbsent(p, expectedVal)
            }
            verified
        } else {
            existingAngleMap
        }

        val updatedCompat = GpuTextureCompatibilityProbe.inspectDeviceCompatibility(
            context = context,
            selectedTargetPackage = cleanPkg,
            angleMapOverride = postApplyMap,
            eglRendererOverride = eglTextureSession.activeGlRenderer
        )
        val targetDesc = if (cleanPkg.isEmpty()) {
            if (homePkgs.isNotEmpty()) "Global GPU (${homePkgs.size} Home games)" else "Global GPU Profile"
        } else {
            cleanPkg
        }

        val msg = if (appliedOk) {
            "Applied ${effectiveConfig.summaryBadge} to $targetDesc via ${executionMethod.value.name}."
        } else {
            "Saved ${effectiveConfig.summaryBadge} for $targetDesc. Connect ${executionMethod.value.name} to apply live."
        }

        _textureTunerState.value = _textureTunerState.value.copy(
            currentConfig = effectiveConfig,
            compatibility = updatedCompat,
            isApplying = false,
            isAppliedLive = appliedOk,
            statusMessage = msg
        )
        Result.success(msg)
    }

    suspend fun resetTextureConfigToOriginal(
        packageName: String = _textureTunerState.value.selectedTargetPackage
    ): Result<String> {
        val orig = TextureConfig(
            preset = TextureQuickPreset.CUSTOM,
            detailLevel = TextureDetailLevel.ORIGINAL,
            sharpPixels = false,
            hardDistanceCutoff = false,
            driverMode = AngleDriverMode.DEFAULT
        )
        return saveAndApplyTextureConfig(packageName = packageName, config = orig)
    }

    private fun loadInitialPlayBoostConfig(): PlayBoostConfig {
        val tierId = prefs.getString("play_boost_tier", PlayBoostTier.VELOCITY_CORE.id) ?: PlayBoostTier.VELOCITY_CORE.id
        val tier = PlayBoostTier.fromId(tierId)
        val defaultCfg = PlayBoostConfig.forTier(tier)
        val deepRam = prefs.getBoolean("play_boost_deep_ram", defaultCfg.deepRamPurge)
        val forceStop = prefs.getBoolean("play_boost_force_stop", defaultCfg.forceStopBackgroundApps)
        val fixedPerf = prefs.getBoolean("play_boost_fixed_perf", defaultCfg.fixedPerformanceLock)
        val sfZeroLag = prefs.getBoolean("play_boost_sf_zero_lag", defaultCfg.surfaceFlingerZeroLag)
        val ultraTouch = prefs.getBoolean("play_boost_ultra_touch", defaultCfg.ultraTouchResponse)
        val blockHeadsUp = prefs.getBoolean("play_boost_block_headsup", defaultCfg.blockHeadsUpNotifications)

        val resolvedTier = PlayBoostConfig.resolveMatchingTier(
            deepRamPurge = deepRam,
            forceStopBackgroundApps = forceStop,
            fixedPerformanceLock = fixedPerf,
            surfaceFlingerZeroLag = sfZeroLag,
            ultraTouchResponse = ultraTouch,
            blockHeadsUpNotifications = blockHeadsUp
        )

        return PlayBoostConfig(
            tier = resolvedTier,
            deepRamPurge = deepRam,
            forceStopBackgroundApps = forceStop,
            fixedPerformanceLock = fixedPerf,
            surfaceFlingerZeroLag = sfZeroLag,
            ultraTouchResponse = ultraTouch,
            blockHeadsUpNotifications = blockHeadsUp
        )
    }

    fun savePlayBoostConfig(config: PlayBoostConfig) {
        val resolvedTier = PlayBoostConfig.resolveMatchingTier(
            deepRamPurge = config.deepRamPurge,
            forceStopBackgroundApps = config.forceStopBackgroundApps,
            fixedPerformanceLock = config.fixedPerformanceLock,
            surfaceFlingerZeroLag = config.surfaceFlingerZeroLag,
            ultraTouchResponse = config.ultraTouchResponse,
            blockHeadsUpNotifications = config.blockHeadsUpNotifications
        )
        val normalized = config.copy(tier = resolvedTier)
        prefs.edit()
            .putString("play_boost_tier", normalized.tier.id)
            .putBoolean("play_boost_deep_ram", normalized.deepRamPurge)
            .putBoolean("play_boost_force_stop", normalized.forceStopBackgroundApps)
            .putBoolean("play_boost_fixed_perf", normalized.fixedPerformanceLock)
            .putBoolean("play_boost_sf_zero_lag", normalized.surfaceFlingerZeroLag)
            .putBoolean("play_boost_ultra_touch", normalized.ultraTouchResponse)
            .putBoolean("play_boost_block_headsup", normalized.blockHeadsUpNotifications)
            .apply()
        _playBoostConfig.value = normalized
    }

    suspend fun restorePlayBoostSystemDefaults(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cmd = PlayBoostConfig.buildRestoreSystemDefaultsCommand()
            val res = executeCommand(cmd)
            com.kyro.game.space.service.PlayBoostNotificationManager.cancelBoostNotification(context)
            if (res.isSuccess) {
                Result.success("System defaults restored (CPU Lock, SurfaceFlinger, Touch Speed & Heads-Up notifications reset).")
            } else {
                Result.failure(Exception(res.stderr.ifBlank { "Failed to restore defaults via ${executionMethod.value.name}" }))
            }
        } catch (e: Throwable) {
            com.kyro.game.space.service.PlayBoostNotificationManager.cancelBoostNotification(context)
            Result.failure(e)
        }
    }

    private fun loadInitialAotCompilerState(): AotCompilerState {
        val lastPkg = prefs.getString("aot_selected_pkg", "").orEmpty()
        val lastModeName = prefs.getString("aot_selected_mode", AotCompileMode.SPEED_PROFILE.name)
            ?: AotCompileMode.SPEED_PROFILE.name
        val mode = try {
            AotCompileMode.valueOf(lastModeName)
        } catch (_: Throwable) {
            AotCompileMode.SPEED_PROFILE
        }
        val lastCount = prefs.getInt("aot_last_compiled_count", 0)
        val lastDur = prefs.getLong("aot_last_duration_ms", 0L)
        val lastLabel = prefs.getString("aot_last_mode_label", "None") ?: "None"
        return AotCompilerState(
            selectedTargetPackage = lastPkg,
            selectedMode = mode,
            lastCompiledCount = lastCount,
            lastCompileDurationMs = lastDur,
            lastCompiledModeLabel = lastLabel
        )
    }

    fun selectAotTargetPackage(packageName: String) {
        val clean = packageName.trim()
        prefs.edit().putString("aot_selected_pkg", clean).apply()
        _aotCompilerState.value = _aotCompilerState.value.copy(selectedTargetPackage = clean)
    }

    fun selectAotCompileMode(mode: AotCompileMode) {
        prefs.edit().putString("aot_selected_mode", mode.name).apply()
        _aotCompilerState.value = _aotCompilerState.value.copy(selectedMode = mode)
    }

    suspend fun runAotGameCompile(
        targetPackage: String = _aotCompilerState.value.selectedTargetPackage,
        mode: AotCompileMode = _aotCompilerState.value.selectedMode
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanPkg = targetPackage.trim()
        val targetList: List<String> = if (cleanPkg.isNotEmpty()) {
            listOf(cleanPkg)
        } else {
            try {
                dao.getHomeGamesFlow().first().map { it.packageName }.filter { it.isNotBlank() }
            } catch (_: Throwable) {
                emptyList()
            }
        }

        if (targetList.isEmpty()) {
            val msg = "No games found in Home Hub. Add a game first or select a specific package."
            _aotCompilerState.value = _aotCompilerState.value.copy(
                isCompiling = false,
                lastStatusMessage = msg
            )
            return@withContext Result.failure(Exception(msg))
        }

        val startTime = System.currentTimeMillis()
        val logs = mutableListOf<String>()
        logs.add(">>> Starting AOT DEX Compiler [${mode.shortLabel}] via ${executionMethod.value.name}")
        logs.add(">>> Targets (${targetList.size}): ${targetList.joinToString(", ")}")

        _aotCompilerState.value = _aotCompilerState.value.copy(
            selectedTargetPackage = cleanPkg,
            selectedMode = mode,
            isCompiling = true,
            progress = 0.05f,
            currentPackageBeingCompiled = targetList.first(),
            lastStatusMessage = "Compiling ${targetList.size} game(s) with ${mode.shortLabel}...",
            compiledLogLines = logs.toList()
        )

        var successCount = 0
        for ((index, pkg) in targetList.withIndex()) {
            val stepProgress = ((index.toFloat()) / targetList.size.toFloat()).coerceIn(0.08f, 0.92f)
            val pkgStart = System.currentTimeMillis()
            val cmd = mode.buildCompileCommand(pkg)
            logs.add("[$pkg] $ $cmd")

            _aotCompilerState.value = _aotCompilerState.value.copy(
                progress = stepProgress,
                currentPackageBeingCompiled = pkg,
                lastStatusMessage = "Compiling (${index + 1}/${targetList.size}): $pkg...",
                compiledLogLines = logs.toList()
            )

            val res = try {
                executeCommand(cmd)
            } catch (e: Throwable) {
                CommandResult(-1, "", e.message ?: "Execution error", false)
            }
            val pkgDuration = (System.currentTimeMillis() - pkgStart).coerceAtLeast(1L)
            val rawOut = (res.stdout + "\n" + res.stderr).trim().ifEmpty {
                if (res.isSuccess) "Success" else "Failed (Exit ${res.exitCode})"
            }
            if (res.isSuccess || rawOut.contains("Success", ignoreCase = true)) {
                successCount++
                logs.add("[$pkg] -> OK ($rawOut) in ${pkgDuration}ms")
            } else {
                logs.add("[$pkg] -> ERROR: $rawOut")
            }
            _aotCompilerState.value = _aotCompilerState.value.copy(
                compiledLogLines = logs.toList()
            )
        }

        val totalDuration = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
        val summaryMsg = if (successCount > 0) {
            val actionWord = if (mode.isReset) "Reset compilation for" else "Compiled"
            "$actionWord $successCount/${targetList.size} game(s) [${mode.shortLabel}] in ${totalDuration}ms."
        } else {
            "AOT Compilation failed. Ensure ${executionMethod.value.name} is connected and active."
        }
        logs.add(">>> $summaryMsg")

        if (successCount > 0) {
            prefs.edit()
                .putInt("aot_last_compiled_count", successCount)
                .putLong("aot_last_duration_ms", totalDuration)
                .putString("aot_last_mode_label", mode.shortLabel)
                .apply()
        }

        _aotCompilerState.value = _aotCompilerState.value.copy(
            isCompiling = false,
            progress = 1.0f,
            currentPackageBeingCompiled = "",
            lastStatusMessage = summaryMsg,
            compiledLogLines = logs.toList(),
            lastCompiledCount = if (successCount > 0) successCount else _aotCompilerState.value.lastCompiledCount,
            lastCompileDurationMs = if (successCount > 0) totalDuration else _aotCompilerState.value.lastCompileDurationMs,
            lastCompiledModeLabel = if (successCount > 0) mode.shortLabel else _aotCompilerState.value.lastCompiledModeLabel
        )

        if (successCount > 0) {
            Result.success(summaryMsg)
        } else {
            Result.failure(Exception(summaryMsg))
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun GameEntity.toGameItem(config: GameCustomConfig = GameCustomConfig()) = GameItem(
        packageName = packageName,
        appName = appName,
        isGame = isGame,
        isAddedToHome = isAddedToHome,
        totalPlaytimeMs = totalPlaytimeMs,
        lastPlayedTimestamp = lastPlayedTimestamp,
        versionCode = versionCode,
        versionName = versionName,
        isSystemApp = isSystemApp,
        customConfig = config
    )
}
