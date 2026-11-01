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
        const val EXTRA_HOST = "extra_host"
        const val EXTRA_PAIRING_PORT = "extra_pairing_port"
        const val EXTRA_CONNECT_PORT = "extra_connect_port"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY_PAIRING_CODE) return

        val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
        val rawInput = remoteInput.getCharSequence(AdbWirelessScanner.KEY_TEXT_REPLY)?.toString()?.trim() ?: return

        // Support either "123456" (when pairing port was auto-detected) or "37812 123456"
        val tokens = rawInput.split(Regex("[\\s:]+")).filter { it.isNotBlank() }
        val host = intent.getStringExtra(EXTRA_HOST)?.takeIf { it.isNotBlank() } ?: "127.0.0.1"
        var pairingPort = intent.getIntExtra(EXTRA_PAIRING_PORT, 0)
        val connectPort = intent.getIntExtra(EXTRA_CONNECT_PORT, 0)

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

                // If pairingPort wasn't in the intent yet, check `libadb.so mdns services` right now
                if (pairingPort <= 0 && AdbNativeBridge.isNativeLoaded) {
                    val mdns = AdbNativeBridge.runAdbBinary("mdns", "services", timeoutMs = 3000L)
                    val line = mdns.stdout.lines().firstOrNull { it.contains("_adb-tls-pairing") }
                    val portStr = line?.split(Regex("\\s+"))?.lastOrNull()?.substringAfterLast(":")
                    pairingPort = portStr?.toIntOrNull() ?: 0
                }

                postStatusNotification(
                    context,
                    "Pairing with $host:$pairingPort...",
                    "Executing libadb.so pair $host:$pairingPort $code"
                )

                val pairRes = executor.pair(host, pairingPort, code)
                if (pairRes.isSuccess) {
                    delay(800L)
                    // Resolve connectPort via mDNS if not yet known
                    var resolvedConnectPort = connectPort
                    if (resolvedConnectPort <= 0 && AdbNativeBridge.isNativeLoaded) {
                        val mdns = AdbNativeBridge.runAdbBinary("mdns", "services", timeoutMs = 3000L)
                        val line = mdns.stdout.lines().firstOrNull { it.contains("_adb-tls-connect") }
                        val portStr = line?.split(Regex("\\s+"))?.lastOrNull()?.substringAfterLast(":")
                        resolvedConnectPort = portStr?.toIntOrNull() ?: 0
                    }

                    if (resolvedConnectPort > 0) {
                        val connectRes = executor.connect(host, resolvedConnectPort)
                        if (connectRes.isSuccess) {
                            postStatusNotification(
                                context,
                                "Wireless ADB Paired & Daemon Injected!",
                                connectRes.getOrNull() ?: "Persistent Daemon is now active on port 38555."
                            )
                        } else {
                            postStatusNotification(
                                context,
                                "Paired Successfully!",
                                "Return to Game Space to finish connecting ($host:$resolvedConnectPort)."
                            )
                        }
                    } else {
                        postStatusNotification(
                            context,
                            "Paired Successfully!",
                            "Return to Game Space — auto-connecting & injecting persistent daemon..."
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
