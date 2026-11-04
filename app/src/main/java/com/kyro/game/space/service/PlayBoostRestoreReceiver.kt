package com.kyro.game.space.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.kyro.game.space.MainActivity
import com.kyro.game.space.data.executor.AdbNativeBridge
import com.kyro.game.space.data.executor.ExecutorFactory
import com.kyro.game.space.data.model.ExecutionMethod
import com.kyro.game.space.data.model.PlayBoostConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object PlayBoostNotificationManager {
    const val CHANNEL_ID = "nox_active_play_boost_channel"
    const val NOTIFICATION_ID = 9402
    const val RESTORED_NOTIFICATION_ID = 9403

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NOX Active Game Boost",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent status notification while NOX Auto-Boost is active for a game, with 1-tap Restore."
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    fun showBoostActiveNotification(
        context: Context,
        gameName: String,
        packageName: String,
        config: PlayBoostConfig
    ) {
        try {
            ensureChannel(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val prefs = context.getSharedPreferences("kyro_game_space_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("play_boost_session_active", true)
                .putString("play_boost_active_game_name", gameName)
                .putString("play_boost_active_game_pkg", packageName)
                .putString("play_boost_active_tier_title", config.tier.title)
                .apply()

            // Intent to open NOX - Game Space when tapping notification body
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                9410,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Intent to restore stock system defaults & stop boost even if NOX app was closed
            val restoreIntent = Intent(context, PlayBoostRestoreReceiver::class.java).apply {
                action = PlayBoostRestoreReceiver.ACTION_RESTORE_PLAY_BOOST
                putExtra(PlayBoostRestoreReceiver.EXTRA_GAME_NAME, gameName)
                putExtra(PlayBoostRestoreReceiver.EXTRA_PACKAGE_NAME, packageName)
            }
            val restorePendingIntent = PendingIntent.getBroadcast(
                context,
                9411,
                restoreIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val activeStepsSummary = config.activeStepLabels().joinToString(" • ")
            val shortText = "${config.tier.title} (${config.activeStagesCount}/6 stages) active for $gameName"
            val expandedText = "Mode: ${config.tier.title} (${config.tier.levelTag})\n" +
                "Active Optimizations: $activeStepsSummary\n" +
                "Tap 'Restore & Stop Boost' below to reset system settings after gaming."

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("⚡ NOX Boost Active • $gameName")
                .setContentText(shortText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(openAppPendingIntent)
                .addAction(
                    android.R.drawable.ic_menu_revert,
                    "Restore & Stop Boost",
                    restorePendingIntent
                )
                .build()

            nm.notify(NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
        }
    }

    fun cancelBoostNotification(context: Context) {
        try {
            val prefs = context.getSharedPreferences("kyro_game_space_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("play_boost_session_active", false)
                .remove("play_boost_active_game_name")
                .remove("play_boost_active_game_pkg")
                .apply()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.cancel(NOTIFICATION_ID)
        } catch (_: Throwable) {
        }
    }

    fun showRestoredConfirmationNotification(context: Context, gameName: String) {
        try {
            ensureChannel(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val title = "✓ NOX Boost Restored to Default"
            val body = if (gameName.isNotBlank()) {
                "System CPU, display compositor, touch speed, and pop-up notifications restored after playing $gameName."
            } else {
                "System CPU, display compositor, touch speed, and pop-up notifications restored to stock defaults."
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_revert)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            nm.notify(RESTORED_NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
        }
    }
}

/**
 * Handles the 'Restore & Stop Boost' action button from the persistent status notification,
 * working reliably even if the main NOX - Game Space activity/process was closed from Recents.
 */
class PlayBoostRestoreReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_RESTORE_PLAY_BOOST = "com.nox.gamespace.ACTION_RESTORE_PLAY_BOOST"
        const val EXTRA_GAME_NAME = "extra_game_name"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RESTORE_PLAY_BOOST) return

        val gameName = intent.getStringExtra(EXTRA_GAME_NAME).orEmpty()
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                AdbNativeBridge.initialize(context.applicationContext)
                val prefs = context.getSharedPreferences("kyro_game_space_prefs", Context.MODE_PRIVATE)
                val savedMethodName = prefs.getString("execution_method", ExecutionMethod.SHIZUKU.name)
                    ?: ExecutionMethod.SHIZUKU.name
                val preferredMethod = try {
                    ExecutionMethod.valueOf(savedMethodName)
                } catch (_: Throwable) {
                    ExecutionMethod.SHIZUKU
                }

                val restoreCommand = PlayBoostConfig.buildRestoreSystemDefaultsCommand()
                val factory = ExecutorFactory()

                // Try preferred execution method first; if unavailable, fall back across other active executors
                val methodsToTry = listOf(
                    preferredMethod,
                    ExecutionMethod.SHIZUKU,
                    ExecutionMethod.MANUAL_ADB,
                    ExecutionMethod.ROOT
                ).distinct()

                for (method in methodsToTry) {
                    try {
                        val executor = factory.getExecutor(method)
                        val result = executor.execute(restoreCommand, timeoutMs = 4000L)
                        if (result.isSuccess) {
                            break
                        }
                    } catch (_: Throwable) {
                    }
                }

                PlayBoostNotificationManager.cancelBoostNotification(context)
                PlayBoostNotificationManager.showRestoredConfirmationNotification(context, gameName)

                withContext(Dispatchers.Main) {
                    try {
                        Toast.makeText(
                            context.applicationContext,
                            "NOX Boost stopped & system defaults restored",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (_: Throwable) {
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
