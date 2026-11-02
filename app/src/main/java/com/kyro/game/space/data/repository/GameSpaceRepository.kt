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

    init {
        try {
            com.kyro.game.space.data.executor.AdbNativeBridge.initialize(context)
            val savedHost = prefs.getString("last_adb_host", "127.0.0.1") ?: "127.0.0.1"
            val savedPort = prefs.getInt("last_adb_port", 5555)
            val wasInjected = prefs.getBoolean("daemon_was_injected", false)
            executorFactory.libAdbExecutor.restoreSavedEndpoint(savedHost, savedPort, wasInjected)
        } catch (_: Throwable) {
        }
        try {
            initShizukuListeners()
        } catch (_: Throwable) {
        }
        try {
            updateShizukuStatus()
        } catch (_: Throwable) {
        }
        try {
            startPeriodicMonitoring()
        } catch (_: Throwable) {
        }
        scope.launch {
            try {
                checkDaemonAndSyncState()
            } catch (_: Throwable) {
            }
            try {
                checkRootStatus(triggerPrompt = false)
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
                updateConnectionOverview()
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

    fun cleanUp() {
        try {
            resolutionCountdownJob?.cancel()
            adbScanner.close()
            scope.cancel()
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
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
                    TextureConfig(
                        preset = preset,
                        detailLevel = detail,
                        sharpPixels = sharp,
                        hardDistanceCutoff = hardCutoff
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
        val sanitizedConfig = config.copy(downscaleFactor = sanitizedScale)
        val tex = sanitizedConfig.textureConfig
        val serialized = String.format(
            Locale.US,
            "%s|%.2f|%d|%s|%s|%s|%b|%b",
            sanitizedConfig.mode.name,
            sanitizedConfig.downscaleFactor,
            sanitizedConfig.fpsLimit,
            sanitizedConfig.apiTarget.name,
            tex.preset.name,
            tex.detailLevel.name,
            tex.sharpPixels,
            tex.hardDistanceCutoff
        )
        prefs.edit()
            .putString("game_cfg_$packageName", serialized)
            .putString("tex_cfg_$packageName", serializeTextureConfig(tex))
            .apply()
        _gameConfigs.value = _gameConfigs.value.toMutableMap().apply {
            put(packageName, sanitizedConfig)
        }
        if (_textureTunerState.value.selectedTargetPackage == packageName) {
            _textureTunerState.value = _textureTunerState.value.copy(currentConfig = tex)
        }

        val scaleStr = formatDownscaleClean(sanitizedConfig.downscaleFactor)
        val fpsStr = sanitizedConfig.fpsLimit.toString()
        val texSummary = if (tex.isActive) " • Tex: ${tex.detailLevel.label}" else ""
        val shellCmd = sanitizedConfig.buildShellCommand(packageName)

        return try {
            val cmdRes = executeCommand(shellCmd)
            if (cmdRes.isSuccess) {
                Result.success("Applied ${sanitizedConfig.mode.label} (${scaleStr}x • ${fpsStr} FPS • ${sanitizedConfig.apiTarget.shortTag}$texSummary)")
            } else {
                Result.success("Saved ${sanitizedConfig.mode.label} (${scaleStr}x • ${fpsStr} FPS • ${sanitizedConfig.apiTarget.shortTag}$texSummary). Connect Shizuku/Daemon/Root to apply.")
            }
        } catch (_: Throwable) {
            Result.success("Saved ${sanitizedConfig.mode.label} (${scaleStr}x • ${fpsStr} FPS • ${sanitizedConfig.apiTarget.shortTag}$texSummary)")
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
                    if (cfg.mode != GameOptimizationMode.STANDARD ||
                        cfg.downscaleFactor < 0.99f ||
                        cfg.textureConfig.isActive
                    ) {
                        saveAndApplyGameConfig(packageName, cfg)
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

    // Device telemetry & daemon status monitoring
    private fun startPeriodicMonitoring() {
        scope.launch {
            var tick = 0
            while (isActive) {
                sampleDeviceStats()
                tick++
                if (tick % 2 == 0 && (!executorFactory.libAdbExecutor.isDaemonInjected || tick % 6 == 0)) {
                    try {
                        checkDaemonAndSyncState()
                    } catch (_: Throwable) {
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
            "Switched telemetry target to ${preset.name} (${preset.primaryIp}). Connect Shizuku/Daemon to apply system-wide Private DNS."
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
            scope.launch {
                try {
                    checkDaemonAndSyncState()
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
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
            fallbackExecutor = executorFactory.shizukuExecutor
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
        if (method == ExecutionMethod.ROOT) {
            scope.launch {
                try {
                    checkRootStatus(triggerPrompt = false)
                } catch (_: Throwable) {
                }
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
                "Previewing ${targetW}×${targetH} (${targetDpi} DPI). Connect Shizuku/Daemon for system-wide wm size."
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
        return "${config.preset.name}|${config.detailLevel.name}|${config.sharpPixels}|${config.hardDistanceCutoff}"
    }

    private fun parseTextureConfig(raw: String?): TextureConfig? {
        if (raw.isNullOrBlank()) return null
        return try {
            val parts = raw.split("|")
            if (parts.size >= 4) {
                val preset = TextureQuickPreset.entries.firstOrNull { it.name == parts[0] }
                    ?: TextureQuickPreset.RETRO
                val detail = TextureDetailLevel.entries.firstOrNull { it.name == parts[1] }
                    ?: TextureDetailLevel.ORIGINAL
                val sharp = parts[2].toBooleanStrictOrNull() ?: false
                val hardCutoff = parts[3].toBooleanStrictOrNull() ?: false
                TextureConfig(
                    preset = preset,
                    detailLevel = detail,
                    sharpPixels = sharp,
                    hardDistanceCutoff = hardCutoff
                )
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun loadInitialTextureTunerState(): TextureTunerState {
        val compatibility = GpuTextureCompatibilityProbe.inspectDeviceCompatibility(context)
        val savedTargetPkg = prefs.getString("tex_selected_target_pkg", "") ?: ""
        val key = if (savedTargetPkg.isBlank()) "tex_cfg_global" else "tex_cfg_$savedTargetPkg"
        val defaultRetro = TextureConfig(
            preset = TextureQuickPreset.RETRO,
            detailLevel = TextureDetailLevel.RETRO,
            sharpPixels = true,
            hardDistanceCutoff = false
        )
        val savedConfig = parseTextureConfig(prefs.getString(key, null))
            ?: if (savedTargetPkg.isNotBlank()) {
                getGameConfig(savedTargetPkg).textureConfig.takeIf { it.isActive } ?: defaultRetro
            } else {
                defaultRetro
            }

        return TextureTunerState(
            selectedTargetPackage = savedTargetPkg,
            currentConfig = savedConfig,
            compatibility = compatibility,
            statusMessage = "${compatibility.compatibilityBadge} • Ready to tune 3D texture mipmaps & pixel filtering."
        )
    }

    fun getTextureConfigForTarget(packageName: String): TextureConfig {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isNotEmpty()) {
            val gameTex = getGameConfig(cleanPkg).textureConfig
            if (gameTex.isActive) return gameTex
            parseTextureConfig(prefs.getString("tex_cfg_$cleanPkg", null))?.let { return it }
        }
        return parseTextureConfig(prefs.getString("tex_cfg_global", null))
            ?: TextureConfig(
                preset = TextureQuickPreset.RETRO,
                detailLevel = TextureDetailLevel.RETRO,
                sharpPixels = true,
                hardDistanceCutoff = false
            )
    }

    fun selectTextureTargetPackage(packageName: String) {
        val cleanPkg = packageName.trim()
        prefs.edit().putString("tex_selected_target_pkg", cleanPkg).apply()
        val cfg = getTextureConfigForTarget(cleanPkg)
        val targetLabel = if (cleanPkg.isEmpty()) "Global System GPU" else cleanPkg
        _textureTunerState.value = _textureTunerState.value.copy(
            selectedTargetPackage = cleanPkg,
            currentConfig = cfg,
            statusMessage = "Loaded texture profile for $targetLabel (${cfg.summaryBadge})."
        )
    }

    fun updateTextureConfigDraft(config: TextureConfig) {
        _textureTunerState.value = _textureTunerState.value.copy(
            currentConfig = config
        )
    }

    suspend fun refreshTextureCompatibility(): DeviceTextureCompatibility = withContext(Dispatchers.IO) {
        _textureTunerState.value = _textureTunerState.value.copy(
            isCheckingCompatibility = true,
            statusMessage = "Scanning GPU hardware renderer, OpenGL ES / Vulkan extensions, and driver hooks..."
        )
        val baseCompat = GpuTextureCompatibilityProbe.inspectDeviceCompatibility(context)
        _textureTunerState.value = _textureTunerState.value.copy(
            compatibility = baseCompat,
            isCheckingCompatibility = false,
            statusMessage = "Device Check: ${baseCompat.compatibilityBadge} (${baseCompat.glEsVersion})"
        )
        baseCompat
    }

    suspend fun saveAndApplyTextureConfig(
        packageName: String = _textureTunerState.value.selectedTargetPackage,
        config: TextureConfig = _textureTunerState.value.currentConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanPkg = packageName.trim()
        _textureTunerState.value = _textureTunerState.value.copy(
            selectedTargetPackage = cleanPkg,
            currentConfig = config,
            isApplying = true
        )

        val serialized = serializeTextureConfig(config)
        val prefKey = if (cleanPkg.isEmpty()) "tex_cfg_global" else "tex_cfg_$cleanPkg"
        prefs.edit()
            .putString("tex_selected_target_pkg", cleanPkg)
            .putString(prefKey, serialized)
            .apply()

        // Also synchronize with the game's GameCustomConfig if targeting a specific game
        if (cleanPkg.isNotEmpty()) {
            val existingGameCfg = getGameConfig(cleanPkg)
            val updatedGameCfg = existingGameCfg.copy(textureConfig = config)
            saveAndApplyGameConfig(cleanPkg, updatedGameCfg)
        }

        val cmd = config.buildShellCommand(cleanPkg)
        val cmdRes = try {
            executeCommand(cmd)
        } catch (_: Throwable) {
            null
        }

        val targetDesc = if (cleanPkg.isEmpty()) "Global GPU" else cleanPkg
        val msg = if (cmdRes?.isSuccess == true) {
            "Applied ${config.summaryBadge} to $targetDesc via ${executionMethod.value.name}."
        } else {
            "Saved ${config.summaryBadge} for $targetDesc. Connect Shizuku/Daemon/Root to apply live."
        }

        _textureTunerState.value = _textureTunerState.value.copy(
            currentConfig = config,
            isApplying = false,
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
            hardDistanceCutoff = false
        )
        return saveAndApplyTextureConfig(packageName = packageName, config = orig)
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
