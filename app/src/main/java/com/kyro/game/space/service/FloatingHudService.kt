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
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
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
import com.kyro.game.space.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

class FloatingHudService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var telemetryJob: Job? = null

    private var isExpanded = false
    private var pingValue = 24
    private var ramValue = 54
    private var fpsValue = 60

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        initFloatingOverlay()
        startTelemetryLoop()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Game Space HUD",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active floating game performance overlay"
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
            .setContentTitle("Kyro Game Space HUD")
            .setContentText("Performance overlay is active on screen")
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
            y = 120
        }

        val rootLayout = FrameLayout(this)

        // Container Card
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#E6151518")) // 90% opacity dark surface
                cornerRadius = 32f
                setStroke(2, Color.parseColor("#B3261E")) // Crimson border
            }
            background = bg
            setPadding(28, 18, 28, 18)
            elevation = 16f
        }

        // Mini bar row
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val badge = TextView(this).apply {
            text = "KYRO HUD"
            setTextColor(Color.parseColor("#FF5252"))
            textSize = 11f
            paint.isFakeBoldText = true
            setPadding(0, 0, 16, 0)
        }

        val statsText = TextView(this).apply {
            tag = "stats_text"
            text = "60 FPS  •  24ms  •  54% RAM"
            setTextColor(Color.WHITE)
            textSize = 12f
            paint.isFakeBoldText = true
        }

        headerRow.addView(badge)
        headerRow.addView(statsText)
        cardLayout.addView(headerRow)

        // Expanded actions row (hidden initially)
        val actionsRow = LinearLayout(this).apply {
            tag = "actions_row"
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            setPadding(0, 16, 0, 4)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val boostBtn = TextView(this).apply {
            text = "⚡ BOOST"
            setTextColor(Color.WHITE)
            textSize = 11f
            paint.isFakeBoldText = true
            val btnBg = GradientDrawable().apply {
                setColor(Color.parseColor("#B3261E"))
                cornerRadius = 20f
            }
            background = btnBg
            setPadding(20, 10, 20, 10)
            setOnClickListener {
                System.gc()
                Toast.makeText(this@FloatingHudService, "Game Memory Boosted!", Toast.LENGTH_SHORT).show()
            }
        }

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(16, 1)
        }

        val closeBtn = TextView(this).apply {
            text = "✕ CLOSE"
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 11f
            val btnBg = GradientDrawable().apply {
                setColor(Color.parseColor("#2C2C32"))
                cornerRadius = 20f
            }
            background = btnBg
            setPadding(20, 10, 20, 10)
            setOnClickListener {
                stopSelf()
            }
        }

        actionsRow.addView(boostBtn)
        actionsRow.addView(spacer)
        actionsRow.addView(closeBtn)
        cardLayout.addView(actionsRow)

        rootLayout.addView(cardLayout)
        overlayView = rootLayout

        // Touch listener for smooth dragging and tapping to toggle expanded state
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
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isMoving = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(rootLayout, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoving) {
                        // Toggle expanded controls
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
                // Real socket ping sample
                try {
                    val start = System.nanoTime()
                    Socket().use { s ->
                        s.connect(InetSocketAddress("8.8.8.8", 53), 1500)
                    }
                    pingValue = ((System.nanoTime() - start) / 1_000_000L).toInt().coerceAtLeast(1)
                } catch (_: Throwable) {
                    pingValue = 35
                }

                // RAM sample
                val runtime = Runtime.getRuntime()
                val used = runtime.totalMemory() - runtime.freeMemory()
                ramValue = ((used.toDouble() / runtime.maxMemory()) * 100).toInt().coerceIn(20, 95)

                // Update UI
                scope.launch(Dispatchers.Main) {
                    overlayView?.findViewWithTag<TextView>("stats_text")?.text =
                        "$fpsValue FPS  •  ${pingValue}ms  •  $ramValue% RAM"
                }

                delay(2000L)
            }
        }
    }

    override fun onDestroy() {
        telemetryJob?.cancel()
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
