package com.kyro.game.space.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.kyro.game.space.data.executor.AdbNativeBridge
import com.kyro.game.space.data.executor.AdbWirelessScanner
import com.kyro.game.space.data.executor.LibAdbCommandExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Handles the inline 6-digit Pairing Code submitted from the Notification shade
 * while the user is inside Developer Options -> Wireless Debugging.
 */
class AdbPairingReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REPLY_PAIRING_CODE = "com.kyro.game.space.ACTION_REPLY_PAIRING_CODE"
        const val ACTION_ADB_STATE_CHANGED = "com.kyro.game.space.ACTION_ADB_STATE_CHANGED"
        const val EXTRA_HOST = "extra_host"
        const val EXTRA_PAIRING_PORT = "extra_pairing_port"
        const val EXTRA_CONNECT_PORT = "extra_connect_port"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY_PAIRING_CODE) return

        val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
        val rawInput = remoteInput.getCharSequence(AdbWirelessScanner.KEY_TEXT_REPLY)?.toString()?.trim() ?: return

        val prefs = context.getSharedPreferences("kyro_game_space_prefs", Context.MODE_PRIVATE)

        // Support either "123456" (when pairing port was auto-detected) or "37812 123456"
        val tokens = rawInput.split(Regex("[\\s:]+")).filter { it.isNotBlank() }
        val host = intent.getStringExtra(EXTRA_HOST)?.takeIf { it.isNotBlank() } 
            ?: prefs.getString("last_adb_host", "127.0.0.1") ?: "127.0.0.1"

        var pairingPort = intent.getIntExtra(EXTRA_PAIRING_PORT, 0)
        if (pairingPort <= 0) {
            pairingPort = prefs.getInt("last_adb_pairing_port", 0)
        }

        var connectPort = intent.getIntExtra(EXTRA_CONNECT_PORT, 0)
        if (connectPort <= 0) {
            connectPort = prefs.getInt("last_adb_port", 0)
        }

        val code = if (tokens.size >= 2) {
            pairingPort = tokens[0].filter { it.isDigit() }.toIntOrNull() ?: pairingPort
            tokens[1].filter { it.isDigit() }
        } else {
            rawInput.filter { it.isDigit() }
        }

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                AdbNativeBridge.initialize(context)
                val executor = LibAdbCommandExecutor()

                // If pairingPort wasn't known yet, check `libadb.so mdns services`
                if (pairingPort <= 0 && AdbNativeBridge.isNativeLoaded) {
                    val mdns = AdbNativeBridge.runAdbBinary("mdns", "services", timeoutMs = 3000L)
                    val line = mdns.stdout.lines().firstOrNull { it.contains("_adb-tls-pairing") }
                    val portStr = line?.split(Regex("\\s+"))?.lastOrNull()?.substringAfterLast(":")
                    pairingPort = portStr?.toIntOrNull() ?: 0
                }

                postStatusNotification(
                    context,
                    "Pairing with $host:$pairingPort as NOX...",
                    "Executing libadb.so pair $host:$pairingPort $code"
                )

                val pairRes = executor.pair(host, pairingPort, code)
                if (pairRes.isSuccess) {
                    // Give Wireless Debugging service time to transition from pairing to active connection
                    delay(800L)

                    // Multiple-pass resolution of connectPort via mDNS and devices table
                    var resolvedConnectPort = connectPort
                    if (resolvedConnectPort <= 0 && AdbNativeBridge.isNativeLoaded) {
                        for (attempt in 1..4) {
                            val mdns = AdbNativeBridge.runAdbBinary("mdns", "services", timeoutMs = 2500L)
                            val line = mdns.stdout.lines().firstOrNull { it.contains("_adb-tls-connect") }
                            val portStr = line?.split(Regex("\\s+"))?.lastOrNull()?.substringAfterLast(":")
                            val parsed = portStr?.toIntOrNull() ?: 0
                            if (parsed > 0) {
                                resolvedConnectPort = parsed
                                break
                            }
                            delay(500L)
                        }

                        if (resolvedConnectPort <= 0) {
                            val devRes = AdbNativeBridge.runAdbBinary("devices", timeoutMs = 2000L)
                            val devLine = devRes.stdout.lines().firstOrNull { it.contains(":") && it.contains("device") }
                            val portStr = devLine?.substringBefore("\t")?.substringAfterLast(":")
                            resolvedConnectPort = portStr?.toIntOrNull() ?: 0
                        }
                    }

                    if (resolvedConnectPort > 0) {
                        postStatusNotification(
                            context,
                            "Connecting & Injecting Daemon...",
                            "Target: $host:$resolvedConnectPort • Spawning persistent daemon on port 38555..."
                        )

                        val connectRes = executor.connect(host, resolvedConnectPort)
                        // Make sure daemon script is definitely injected
                        executor.injectPersistentDaemonViaAdb("$host:$resolvedConnectPort")

                        // Save state to SharedPreferences
                        prefs.edit()
                            .putString("last_adb_host", host)
                            .putInt("last_adb_port", resolvedConnectPort)
                            .putBoolean("daemon_was_injected", true)
                            .putLong("last_adb_update_time", System.currentTimeMillis())
                            .apply()

                        // Broadcast state change so repository updates immediately
                        try {
                            context.sendBroadcast(
                                Intent(ACTION_ADB_STATE_CHANGED).setPackage(context.packageName)
                            )
                        } catch (_: Throwable) {
                        }

                        postStatusNotification(
                            context,
                            "✓ Wireless ADB Paired & Daemon Injected!",
                            "Persistent Daemon is active on port 38555. Safe to turn OFF Wireless Debugging."
                        )
                    } else {
                        // Pairing succeeded, save host and notify repository to auto-connect
                        prefs.edit()
                            .putString("last_adb_host", host)
                            .apply()

                        try {
                            context.sendBroadcast(
                                Intent(ACTION_ADB_STATE_CHANGED).setPackage(context.packageName)
                            )
                        } catch (_: Throwable) {
                        }

                        postStatusNotification(
                            context,
                            "Paired Successfully as NOX!",
                            "Return to NOX - Game Space to auto-connect and activate persistent daemon."
                        )
                    }
                } else {
                    postStatusNotification(
                        context,
                        "Pairing Failed",
                        pairRes.exceptionOrNull()?.message ?: "Check the 6-digit pairing code and try again."
                    )
                }
            } catch (e: Throwable) {
                postStatusNotification(
                    context,
                    "Pairing Error",
                    e.localizedMessage ?: "Unexpected error during ADB pairing"
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postStatusNotification(context: Context, title: String, message: String) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val notification = NotificationCompat.Builder(context, AdbWirelessScanner.PAIRING_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify(AdbWirelessScanner.PAIRING_NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
        }
    }
}
