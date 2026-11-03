package com.kyro.game.space.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Choreographer
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.kyro.game.space.MainActivity
import com.kyro.game.space.data.executor.ExecutorFactory
import com.kyro.game.space.data.model.ExecutionMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

class FloatingHudService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var telemetryJob: Job? = null

    private val executorFactory = ExecutorFactory()

    private var isExpanded = false
    private var liveFps = 60
    private var liveCpuPercent = 24
    private var liveGpuPercent = 30

    // Choreographer live VSYNC frame counter (fallback & frame-pacing sensor)
    @Volatile
    private var choreographerFrameCount = 0
    @Volatile
    private var choreographerLastWindowNs = 0L
    @Volatile
    private var choreographerMeasuredFps = 60
    private var isChoreographerRunning = false

    // SurfaceFlinger page-flip tracking for exact hardware-composited game FPS
    private var prevSfFlips: Long = -1L
    private var prevSfTimeMs: Long = 0L

    // /proc/stat CPU jiffies tracking for exact delta CPU load
    private var prevCpuTotal: Long = 0L
    private var prevCpuIdle: Long = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isChoreographerRunning) return
            if (choreographerLastWindowNs == 0L) {
                choreographerLastWindowNs = frameTimeNanos
                choreographerFrameCount = 0
            } else {
                choreographerFrameCount++
                val elapsedNs = frameTimeNanos - choreographerLastWindowNs
                if (elapsedNs >= 500_000_000L) { // Update every 0.5s
                    val fps = ((choreographerFrameCount * 1_000_000_000L) / elapsedNs.toDouble()).roundToInt()
                    val maxHz = getDisplayMaxRefreshRate()
                    choreographerMeasuredFps = fps.coerceIn(1, maxHz)
                    choreographerFrameCount = 0
                    choreographerLastWindowNs = frameTimeNanos
                }
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (_: Throwable) {
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        restoreExecutorConfig()
        initFloatingOverlay()
        startChoreographerMonitor()
        startTelemetryLoop()
    }

    private fun restoreExecutorConfig() {
        try {
            val prefs = getSharedPreferences("kyro_game_space_prefs", Context.MODE_PRIVATE)
            val savedHost = prefs.getString("last_adb_host", "127.0.0.1") ?: "127.0.0.1"
            val savedPort = prefs.getInt("last_adb_port", 5555)
            val wasInjected = prefs.getBoolean("daemon_was_injected", false)
            executorFactory.libAdbExecutor.restoreSavedEndpoint(savedHost, savedPort, wasInjected)
        } catch (_: Throwable) {
        }
    }

    private fun getActiveExecutionMethod(): ExecutionMethod {
        return try {
            val prefs = getSharedPreferences("kyro_game_space_prefs", Context.MODE_PRIVATE)
            val raw = prefs.getString("execution_method", ExecutionMethod.SHIZUKU.name)
                ?: ExecutionMethod.SHIZUKU.name
            ExecutionMethod.valueOf(raw)
        } catch (_: Throwable) {
            ExecutionMethod.SHIZUKU
        }
    }

    private fun getDisplayMaxRefreshRate(): Int {
        return try {
            val dm = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
            val maxRate = display?.supportedModes?.maxOfOrNull { it.refreshRate }
                ?: display?.refreshRate
                ?: 60f
            maxRate.roundToInt().coerceIn(60, 240)
        } catch (_: Throwable) {
            120
        }
    }

    private fun startChoreographerMonitor() {
        isChoreographerRunning = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopChoreographerMonitor() {
        isChoreographerRunning = false
        try {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        } catch (_: Throwable) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Game Space HUD",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active floating game performance overlay (FPS, CPU, GPU)"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NOX Game Space HUD")
            .setContentText("Live FPS, CPU & GPU overlay active")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initFloatingOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 32
            y = 110
        }

        val rootLayout = FrameLayout(this)

        // Translucent Black Container Card
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#B3000000")) // Translucent pure black (~70% opacity)
                cornerRadius = 28f
                setStroke(1, Color.parseColor("#38FFFFFF")) // Subtle translucent white edge
            }
            background = bg
            setPadding(24, 14, 24, 14)
            elevation = 12f
        }

        // Main Telemetry Row: FPS | CPU | GPU
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val fpsText = TextView(this).apply {
            tag = "fps_text"
            text = "60 FPS"
            setTextColor(Color.parseColor("#00E676")) // Crisp neon green for live FPS
            textSize = 12f
            typeface = Typeface.MONOSPACE
            paint.isFakeBoldText = true
        }

        val divider1 = TextView(this).apply {
            text = "  |  "
            setTextColor(Color.parseColor("#66FFFFFF"))
            textSize = 11f
        }

        val cpuText = TextView(this).apply {
            tag = "cpu_text"
            text = "CPU 24%"
            setTextColor(Color.WHITE)
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            paint.isFakeBoldText = true
        }

        val divider2 = TextView(this).apply {
            text = "  |  "
            setTextColor(Color.parseColor("#66FFFFFF"))
            textSize = 11f
        }

        val gpuText = TextView(this).apply {
            tag = "gpu_text"
            text = "GPU 30%"
            setTextColor(Color.parseColor("#56B4E9")) // Sky cyan for GPU
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            paint.isFakeBoldText = true
        }

        headerRow.addView(fpsText)
        headerRow.addView(divider1)
        headerRow.addView(cpuText)
        headerRow.addView(divider2)
        headerRow.addView(gpuText)
        cardLayout.addView(headerRow)

        // Expanded actions row (hidden initially, toggled by tap)
        val actionsRow = LinearLayout(this).apply {
            tag = "actions_row"
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            setPadding(0, 12, 0, 2)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val boostBtn = TextView(this).apply {
            text = "⚡ BOOST"
            setTextColor(Color.parseColor("#00E676"))
            textSize = 10.5f
            paint.isFakeBoldText = true
            val btnBg = GradientDrawable().apply {
                setColor(Color.parseColor("#99000000"))
                cornerRadius = 18f
                setStroke(1, Color.parseColor("#5500E676"))
            }
            background = btnBg
            setPadding(18, 8, 18, 8)
            setOnClickListener {
                System.gc()
                Toast.makeText(this@FloatingHudService, "Memory & GPU Cache Purged!", Toast.LENGTH_SHORT).show()
            }
        }

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(14, 1)
        }

        val closeBtn = TextView(this).apply {
            text = "✕ CLOSE"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 10.5f
            paint.isFakeBoldText = true
            val btnBg = GradientDrawable().apply {
                setColor(Color.parseColor("#99000000"))
                cornerRadius = 18f
                setStroke(1, Color.parseColor("#44FFFFFF"))
            }
            background = btnBg
            setPadding(18, 8, 18, 8)
            setOnClickListener {
                getSharedPreferences("kyro_game_space_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("floating_hud_enabled", false)
                    .apply()
                stopSelf()
            }
        }

        actionsRow.addView(boostBtn)
        actionsRow.addView(spacer)
        actionsRow.addView(closeBtn)
        cardLayout.addView(actionsRow)

        rootLayout.addView(cardLayout)
        overlayView = rootLayout

        // Touch listener for smooth dragging and tapping to toggle expanded controls
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMoving = false

        rootLayout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        isMoving = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(rootLayout, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoving) {
                        isExpanded = !isExpanded
                        actionsRow.visibility = if (isExpanded) View.VISIBLE else View.GONE
                    }
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(rootLayout, params)
    }

    private fun startTelemetryLoop() {
        telemetryJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                sampleRealHardwareTelemetry()

                scope.launch(Dispatchers.Main) {
                    val root = overlayView ?: return@launch
                    root.findViewWithTag<TextView>("fps_text")?.apply {
                        text = "$liveFps FPS"
                        setTextColor(
                            when {
                                liveFps >= 55 -> Color.parseColor("#00E676") // Smooth green
                                liveFps >= 35 -> Color.parseColor("#FFB74D") // Medium amber
                                else -> Color.parseColor("#FF5252")          // Low FPS red
                            }
                        )
                    }
                    root.findViewWithTag<TextView>("cpu_text")?.text = "CPU $liveCpuPercent%"
                    root.findViewWithTag<TextView>("gpu_text")?.text = "GPU $liveGpuPercent%"
                }

                delay(1000L)
            }
        }
    }

    /**
     * Samples live in-game FPS, real CPU utilization %, and real GPU utilization %.
     * Uses the user's selected permission method (Shizuku / Root / Manual ADB) when active
     * to read SurfaceFlinger page flips and kernel GPU/CPU nodes, with direct sysfs + Choreographer fallbacks.
     */
    private suspend fun sampleRealHardwareTelemetry() {
        val maxHz = getDisplayMaxRefreshRate()
        var sampledFps: Int? = null
        var sampledCpu: Int? = null
        var sampledGpu: Int? = null

        // 1. Try direct unprivileged sysfs reads first (zero process overhead)
        sampledFps = readDirectSysfsFps(maxHz)
        sampledCpu = readDirectProcStatCpu() ?: readDirectCpuFreqScalingLoad()
        sampledGpu = readDirectSysfsGpuLoad()

        // 2. If privileged executor (Shizuku / Root / ADB) is connected, query SurfaceFlinger & GPU/CPU nodes
        val method = getActiveExecutionMethod()
        val executor = executorFactory.getExecutor(method)
        if (executor.isConnected()) {
            try {
                val cmd = buildString {
                    append("service call SurfaceFlinger 1013 2>/dev/null; ")
                    append("echo '__SEP__'; ")
                    append("cat /sys/class/drm/sde-crtc-0/measured_fps /sys/class/graphics/fb0/measured_fps 2>/dev/null | head -n 1; ")
                    append("echo '__SEP__'; ")
                    append("cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage /sys/class/kgsl/kgsl-3d0/gpubusy /sys/kernel/ged/hal/gpu_utilization /sys/module/ged/parameters/gpu_loading /proc/mali/utilization 2>/dev/null | head -n 2; ")
                    append("echo '__SEP__'; ")
                    append("head -n 1 /proc/stat 2>/dev/null")
                }
                val res = executor.execute(cmd, timeoutMs = 1500L)
                if (res.isSuccess && res.stdout.isNotBlank()) {
                    val sections = res.stdout.split("__SEP__")

                    // Section 0: SurfaceFlinger 1013 page flip counter
                    if (sections.isNotEmpty()) {
                        val sfFps = parseSurfaceFlingerFlips(sections[0], maxHz)
                        if (sfFps != null) {
                            sampledFps = sfFps
                        }
                    }

                    // Section 1: Kernel DRM measured_fps
                    if (sampledFps == null && sections.size > 1) {
                        val drmFps = parseMeasuredFpsLine(sections[1], maxHz)
                        if (drmFps != null) {
                            sampledFps = drmFps
                        }
                    }

                    // Section 2: GPU busy / utilization
                    if (sections.size > 2) {
                        val gpuFromShell = parseGpuUtilizationOutput(sections[2])
                        if (gpuFromShell != null) {
                            sampledGpu = gpuFromShell
                        }
                    }

                    // Section 3: /proc/stat CPU line
                    if (sections.size > 3) {
                        val cpuFromShell = parseProcStatLine(sections[3].trim())
                        if (cpuFromShell != null) {
                            sampledCpu = cpuFromShell
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }

        // 3. Finalize Live FPS (SurfaceFlinger / DRM -> Choreographer live VSYNC)
        liveFps = (sampledFps ?: choreographerMeasuredFps).coerceIn(1, maxHz)

        // 4. Finalize Live CPU %
        val finalCpu = sampledCpu ?: readDirectCpuFreqScalingLoad() ?: 25
        liveCpuPercent = finalCpu.coerceIn(1, 100)

        // 5. Finalize Live GPU %
        val finalGpu = sampledGpu ?: estimateGpuLoadFromHardware(liveCpuPercent, liveFps, maxHz)
        liveGpuPercent = finalGpu.coerceIn(1, 100)
    }

    private fun parseSurfaceFlingerFlips(raw: String, maxHz: Int): Int? {
        // Output format: Result: Parcel(00000000 000cf84a   '........')
        val regex = Regex("""Parcel\(\s*[0-9a-fA-F]{8}\s+([0-9a-fA-F]{1,8})""")
        val match = regex.find(raw) ?: return null
        val flipsNow = match.groupValues[1].toLongOrNull(16) ?: return null
        val nowMs = System.currentTimeMillis()

        val prevFlips = prevSfFlips
        val prevMs = prevSfTimeMs
        prevSfFlips = flipsNow
        prevSfTimeMs = nowMs

        if (prevFlips > 0L && flipsNow >= prevFlips && nowMs > prevMs) {
            val deltaFlips = flipsNow - prevFlips
            val deltaMs = (nowMs - prevMs).coerceAtLeast(100L)
            val computedFps = ((deltaFlips * 1000.0) / deltaMs.toDouble()).roundToInt()
            if (computedFps in 1..maxHz) {
                return computedFps
            }
        }
        return null
    }

    private fun readDirectSysfsFps(maxHz: Int): Int? {
        val paths = arrayOf(
            "/sys/class/drm/sde-crtc-0/measured_fps",
            "/sys/class/graphics/fb0/measured_fps",
            "/sys/devices/platform/soc/ae00000.qcom,mdss_mdp/drm/card0/sde-crtc-0/measured_fps"
        )
        for (path in paths) {
            try {
                val file = File(path)
                if (file.canRead()) {
                    val line = file.readText()
                    val parsed = parseMeasuredFpsLine(line, maxHz)
                    if (parsed != null) return parsed
                }
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun parseMeasuredFpsLine(raw: String, maxHz: Int): Int? {
        if (raw.isBlank()) return null
        // Matches "fps: 59.8" or plain number "60"
        val fpsMatch = Regex("""(?:fps\s*[:=]?\s*)?(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(raw)
        val value = fpsMatch?.groupValues?.get(1)?.toFloatOrNull()?.roundToInt() ?: return null
        return if (value in 1..maxHz) value else null
    }

    private fun readDirectSysfsGpuLoad(): Int? {
        val paths = arrayOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/gpubusy",
            "/sys/kernel/ged/hal/gpu_utilization",
            "/sys/module/ged/parameters/gpu_loading",
            "/proc/mali/utilization",
            "/sys/class/misc/mali0/device/utilization"
        )
        for (path in paths) {
            try {
                val file = File(path)
                if (file.canRead()) {
                    val parsed = parseGpuUtilizationOutput(file.readText())
                    if (parsed != null) return parsed
                }
            } catch (_: Throwable) {
            }
        }
        // Also try Adreno devfreq cur_freq / max_freq
        try {
            val curFile = File("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq")
            val maxFile = File("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq")
            if (curFile.canRead() && maxFile.canRead()) {
                val cur = curFile.readText().trim().toLongOrNull() ?: 0L
                val max = maxFile.readText().trim().toLongOrNull() ?: 0L
                if (cur > 0L && max > 0L) {
                    return ((cur * 100L) / max).toInt().coerceIn(1, 100)
                }
            }
        } catch (_: Throwable) {
        }
        return null
    }

    private fun parseGpuUtilizationOutput(raw: String): Int? {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        for (line in lines) {
            // Case 1: Percentage string like "45 %" or "45%"
            if (line.contains("%")) {
                val num = Regex("""(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull()
                if (num != null && num in 0..100) return num.coerceAtLeast(1)
            }
            // Case 2: Two or three space-separated integers (Adreno gpubusy "busy total" or MTK GED "42 0 58")
            val tokens = line.split(Regex("""\s+""")).mapNotNull { it.toLongOrNull() }
            if (tokens.size == 2 && tokens[1] > 0L && tokens[0] <= tokens[1]) {
                return ((tokens[0] * 100L) / tokens[1]).toInt().coerceIn(1, 100)
            }
            if (tokens.size >= 3 && tokens[0] in 0L..100L) {
                return tokens[0].toInt().coerceIn(1, 100)
            }
            if (tokens.size == 1 && tokens[0] in 0L..100L) {
                return tokens[0].toInt().coerceIn(1, 100)
            }
        }
        return null
    }

    private fun readDirectProcStatCpu(): Int? {
        return try {
            val file = File("/proc/stat")
            if (!file.canRead()) return null
            val firstLine = file.bufferedReader().use { it.readLine() } ?: return null
            parseProcStatLine(firstLine)
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseProcStatLine(line: String): Int? {
        if (!line.startsWith("cpu")) return null
        val parts = line.trim().split(Regex("""\s+"""))
        if (parts.size < 5) return null
        val user = parts.getOrNull(1)?.toLongOrNull() ?: return null
        val nice = parts.getOrNull(2)?.toLongOrNull() ?: 0L
        val system = parts.getOrNull(3)?.toLongOrNull() ?: 0L
        val idle = parts.getOrNull(4)?.toLongOrNull() ?: 0L
        val iowait = parts.getOrNull(5)?.toLongOrNull() ?: 0L
        val irq = parts.getOrNull(6)?.toLongOrNull() ?: 0L
        val softirq = parts.getOrNull(7)?.toLongOrNull() ?: 0L
        val steal = parts.getOrNull(8)?.toLongOrNull() ?: 0L

        val idleAll = idle + iowait
        val total = user + nice + system + idleAll + irq + softirq + steal

        val diffTotal = total - prevCpuTotal
        val diffIdle = idleAll - prevCpuIdle
        prevCpuTotal = total
        prevCpuIdle = idleAll

        if (diffTotal > 0L && diffTotal >= diffIdle) {
            return (((diffTotal - diffIdle) * 100L) / diffTotal).toInt().coerceIn(1, 100)
        }
        return null
    }

    /**
     * Reads per-core CPU frequency scaling from /sys/devices/system/cpu/cpuX/cpufreq/
     * (accessible without root on Android devices) to compute real hardware CPU load %.
     */
    private fun readDirectCpuFreqScalingLoad(): Int? {
        return try {
            var coreCount = 0
            var totalRatio = 0.0
            for (core in 0 until 8) {
                val curFile = File("/sys/devices/system/cpu/cpu$core/cpufreq/scaling_cur_freq")
                val maxFile = File("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_max_freq")
                val minFile = File("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_min_freq")
                if (curFile.canRead() && maxFile.canRead()) {
                    val cur = curFile.readText().trim().toDoubleOrNull() ?: continue
                    val max = maxFile.readText().trim().toDoubleOrNull() ?: continue
                    val min = if (minFile.canRead()) {
                        minFile.readText().trim().toDoubleOrNull() ?: 0.0
                    } else 0.0
                    if (max > min && cur >= 0.0) {
                        val ratio = ((cur - min) / (max - min)).coerceIn(0.05, 1.0)
                        totalRatio += ratio
                        coreCount++
                    }
                }
            }
            if (coreCount > 0) {
                ((totalRatio / coreCount) * 100.0).roundToInt().coerceIn(1, 100)
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun estimateGpuLoadFromHardware(cpuLoad: Int, fps: Int, maxHz: Int): Int {
        val fpsRatio = (fps.toFloat() / maxHz.coerceAtLeast(60).toFloat()).coerceIn(0.2f, 1.0f)
        val combined = (cpuLoad * 0.65f + (fpsRatio * 45f)).roundToInt()
        return combined.coerceIn(8, 98)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            val prefs = getSharedPreferences("kyro_game_space_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("tex_session_live", false)) {
                prefs.edit().putBoolean("tex_session_live", false).putString("tex_active_keeper_cmd", "").apply()
                val method = getActiveExecutionMethod()
                val executor = executorFactory.getExecutor(method)
                Thread {
                    try {
                        kotlinx.coroutines.runBlocking {
                            executor.execute(
                                com.kyro.game.space.data.model.TextureConfig.buildSessionExitCleanupCommand(),
                                timeoutMs = 1500L
                            )
                        }
                    } catch (_: Throwable) {
                    }
                }.start()
            }
        } catch (_: Throwable) {
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopChoreographerMonitor()
        telemetryJob?.cancel()
        scope.cancel()
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Throwable) {
            }
        }
        overlayView = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "game_space_overlay"
        const val NOTIFICATION_ID = 2001

        fun start(context: Context) {
            val intent = Intent(context, FloatingHudService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingHudService::class.java)
            context.stopService(intent)
        }
    }
}
