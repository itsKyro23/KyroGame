package com.kyro.game.space.data.repository

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.kyro.game.space.data.db.GameDao
import com.kyro.game.space.data.db.GameEntity
import com.kyro.game.space.data.db.GameSpaceDatabase
import com.kyro.game.space.data.executor.CommandResult
import com.kyro.game.space.data.executor.ExecutorFactory
import com.kyro.game.space.data.model.ConnectionOverview
import com.kyro.game.space.data.model.ConnectionStatus
import com.kyro.game.space.data.model.DeviceStats
import com.kyro.game.space.data.model.ExecutionMethod
import com.kyro.game.space.data.model.GameItem
import com.kyro.game.space.data.model.ManualAdbInfo
import com.kyro.game.space.data.model.ShizukuInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Calendar

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

    val themeMode = MutableStateFlow(prefs.getString("theme_mode", "DARK") ?: "DARK")
    val dynamicColor = MutableStateFlow(prefs.getBoolean("dynamic_color", false))
    val refreshIntervalMs = MutableStateFlow(prefs.getLong("refresh_interval_ms", 2000L))
    val floatingHudEnabled = MutableStateFlow(prefs.getBoolean("floating_hud_enabled", false))

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

    init {
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
                scanAndSyncInstalledApps()
            } catch (_: Throwable) {
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
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        } catch (_: Throwable) {
        }
    }

    val allGamesFlow: Flow<List<GameItem>> = dao.getAllGamesFlow().map { entities ->
        entities.map { it.toGameItem() }
    }

    val homeGamesFlow: Flow<List<GameItem>> = dao.getHomeGamesFlow().map { entities ->
        entities.map { it.toGameItem() }
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
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                scope.launch { recordGameLaunch(packageName) }
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

    // Device telemetry monitoring
    private fun startPeriodicMonitoring() {
        scope.launch {
            while (isActive) {
                sampleDeviceStats()
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

        // Ping measurement (real TCP/Socket latency to 8.8.8.8:53)
        val (pingMs, isOnline) = measureRealPing("8.8.8.8", 53)

        _deviceStats.value = DeviceStats(
            cpuTempC = cpuTempC,
            batteryTempC = batteryTempC,
            totalRamMb = totalRamMb,
            usedRamMb = usedRamMb,
            availRamMb = availRamMb,
            ramUsagePercent = ramUsagePercent,
            pingMs = pingMs,
            pingHost = "8.8.8.8",
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

    suspend fun pairManualAdb(host: String, pairingPort: Int, code: String): Result<String> {
        val res = executorFactory.libAdbExecutor.pair(host, pairingPort, code)
        updateConnectionOverview()
        return res
    }

    suspend fun connectManualAdb(host: String, connectPort: Int): Result<String> {
        val res = executorFactory.libAdbExecutor.connect(host, connectPort)
        updateConnectionOverview()
        return res
    }

    fun disconnectManualAdb() {
        executorFactory.libAdbExecutor.disconnect()
        updateConnectionOverview()
    }

    suspend fun executeCommand(command: String): CommandResult {
        val currentMethod = executionMethod.value
        val executor = executorFactory.getExecutor(currentMethod)
        val result = executor.execute(command)

        _connectionState.value = _connectionState.value.copy(
            lastCommandLog = "[$currentMethod] \$ $command\n${if (result.isSuccess) result.stdout else result.stderr}"
        )
        return result
    }

    private fun updateConnectionOverview(shizuku: ShizukuInfo? = null) {
        val currentMethod = executionMethod.value
        val currentShizuku = shizuku ?: _connectionState.value.shizuku
        val libAdb = executorFactory.libAdbExecutor

        val manualAdb = ManualAdbInfo(
            isConnected = libAdb.isConnectedSession,
            isLibAdbLoaded = com.kyro.game.space.data.executor.AdbNativeBridge.isNativeLoaded,
            libAdbDetails = com.kyro.game.space.data.executor.AdbNativeBridge.statusMessage,
            host = libAdb.currentHost,
            port = libAdb.currentPort,
            error = libAdb.lastError
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
                if (manualAdb.isConnected) {
                    Pair(
                        ConnectionStatus.CONNECTED,
                        "Connected to Wireless ADB at ${manualAdb.host}:${manualAdb.port}"
                    )
                } else {
                    Pair(
                        ConnectionStatus.DISCONNECTED,
                        manualAdb.error ?: "Disconnected. Enter pairing details in Settings."
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
            androidVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            lastCommandLog = _connectionState.value.lastCommandLog
        )
    }

    fun setExecutionMethod(method: ExecutionMethod) {
        executionMethod.value = method
        prefs.edit().putString("execution_method", method.name).apply()
        updateConnectionOverview()
    }

    fun setThemeMode(mode: String) {
        themeMode.value = mode
        prefs.edit().putString("theme_mode", mode).apply()
    }

    fun setDynamicColor(enabled: Boolean) {
        dynamicColor.value = enabled
        prefs.edit().putBoolean("dynamic_color", enabled).apply()
    }

    fun setRefreshInterval(ms: Long) {
        refreshIntervalMs.value = ms
        prefs.edit().putLong("refresh_interval_ms", ms).apply()
    }

    fun setFloatingHudEnabled(enabled: Boolean) {
        floatingHudEnabled.value = enabled
        prefs.edit().putBoolean("floating_hud_enabled", enabled).apply()
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun GameEntity.toGameItem() = GameItem(
        packageName = packageName,
        appName = appName,
        isGame = isGame,
        isAddedToHome = isAddedToHome,
        totalPlaytimeMs = totalPlaytimeMs,
        lastPlayedTimestamp = lastPlayedTimestamp,
        versionCode = versionCode,
        versionName = versionName,
        isSystemApp = isSystemApp
    )
}
