package com.kyro.game.space.data.executor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.kyro.game.space.MainActivity
import com.kyro.game.space.service.AdbPairingReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

data class DiscoveredAdbState(
    val isScanning: Boolean = false,
    val detectedIp: String = "127.0.0.1",
    val pairingPort: Int = 0,
    val connectPort: Int = 0,
    val statusText: String = "Idle — Tap Auto-Scan or Open Wireless Debugging",
    val autoConnectEnabled: Boolean = true
)

/**
 * Automatically scans for Android 11+ Wireless Debugging mDNS services:
 * - `_adb-tls-pairing._tcp.` (Pairing Port & IP)
 * - `_adb-tls-connect._tcp.` (Connection Port & IP)
 *
 * Combines Android `NsdManager`, `libadb.so mdns services`, and local Wi-Fi network interface inspection.
 * Also posts an interactive Notification with inline `RemoteInput` so users can enter the 6-digit
 * pairing code directly while viewing Developer Options -> Wireless Debugging.
 */
class AdbWirelessScanner(
    private val context: Context,
    private val onEndpointDiscovered: suspend (ip: String, connectPort: Int, pairingPort: Int) -> Unit
) {
    companion object {
        private const val SERVICE_TYPE_PAIRING = "_adb-tls-pairing._tcp."
        private const val SERVICE_TYPE_CONNECT = "_adb-tls-connect._tcp."

        const val PAIRING_CHANNEL_ID = "kyro_adb_wireless_pairing"
        const val PAIRING_NOTIFICATION_ID = 3005
        const val KEY_TEXT_REPLY = "key_adb_pairing_code"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var nsdManager: NsdManager? = null
    private var pairingDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var connectDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var pollJob: Job? = null

    private val _state = MutableStateFlow(
        DiscoveredAdbState(detectedIp = detectLocalWifiIp())
    )
    val state: StateFlow<DiscoveredAdbState> = _state.asStateFlow()

    private var lastAutoConnectedEndpoint: String = ""

    init {
        createNotificationChannel()
    }

    fun startScanning(showInteractiveNotification: Boolean = false) {
        val localIp = detectLocalWifiIp()
        _state.value = _state.value.copy(
            isScanning = true,
            detectedIp = if (_state.value.detectedIp == "127.0.0.1") localIp else _state.value.detectedIp,
            statusText = "Auto-scanning Wireless ADB IP, Pairing Port & Connect Port..."
        )

        stopNsdListeners()
        startNsdDiscovery()

        if (showInteractiveNotification) {
            updatePairingNotification(
                title = "Auto-Scanning Wireless ADB...",
                body = "Open Developer Options > Wireless Debugging > Pair device with pairing code."
            )
        }

        pollJob?.cancel()
        pollJob = scope.launch {
            if (AdbNativeBridge.isNativeLoaded) {
                AdbNativeBridge.runAdbBinary("start-server", timeoutMs = 4000L)
            }
            while (isActive && _state.value.isScanning) {
                val refreshedIp = detectLocalWifiIp()
                if (refreshedIp != "127.0.0.1" && _state.value.detectedIp == "127.0.0.1") {
                    _state.value = _state.value.copy(detectedIp = refreshedIp)
                }

                // Query `libadb.so mdns services` as an additional discovery channel
                if (AdbNativeBridge.isNativeLoaded) {
                    val mdnsRes = AdbNativeBridge.runAdbBinary("mdns", "services", timeoutMs = 3000L)
                    if (mdnsRes.isSuccess && mdnsRes.stdout.isNotBlank()) {
                        parseAdbMdnsOutput(mdnsRes.stdout, showInteractiveNotification)
                    }
                }
                delay(2000L)
            }
        }
    }

    fun stopScanning() {
        pollJob?.cancel()
        pollJob = null
        stopNsdListeners()
        _state.value = _state.value.copy(
            isScanning = false,
            statusText = buildSummaryStatus(_state.value.detectedIp, _state.value.pairingPort, _state.value.connectPort)
        )
    }

    fun setAutoConnectEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(autoConnectEnabled = enabled)
    }

    fun resetLastAutoConnect() {
        lastAutoConnectedEndpoint = ""
    }

    private fun startNsdDiscovery() {
        try {
            val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = manager

            val pListener = createDiscoveryListener(SERVICE_TYPE_PAIRING, isPairing = true)
            val cListener = createDiscoveryListener(SERVICE_TYPE_CONNECT, isPairing = false)

            pairingDiscoveryListener = pListener
            connectDiscoveryListener = cListener

            manager.discoverServices(SERVICE_TYPE_PAIRING, NsdManager.PROTOCOL_DNS_SD, pListener)
            manager.discoverServices(SERVICE_TYPE_CONNECT, NsdManager.PROTOCOL_DNS_SD, cListener)
        } catch (_: Throwable) {
        }
    }

    private fun stopNsdListeners() {
        val manager = nsdManager ?: return
        pairingDiscoveryListener?.let {
            try {
                manager.stopServiceDiscovery(it)
            } catch (_: Throwable) {
            }
        }
        connectDiscoveryListener?.let {
            try {
                manager.stopServiceDiscovery(it)
            } catch (_: Throwable) {
            }
        }
        pairingDiscoveryListener = null
        connectDiscoveryListener = null
    }

    private fun createDiscoveryListener(serviceType: String, isPairing: Boolean): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String?) {}
            @Suppress("DEPRECATION")
            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                val info = serviceInfo ?: return
                try {
                    nsdManager?.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
                        @Suppress("DEPRECATION")
                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                            val r = resolvedInfo ?: return
                            val hostIp = r.host?.hostAddress?.takeIf { !it.contains(":") } ?: detectLocalWifiIp()
                            val port = r.port
                            if (port > 0) {
                                onPortResolved(hostIp, port, isPairing = isPairing, updateNotif = true)
                            }
                        }
                    })
                } catch (_: Throwable) {
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
            override fun onDiscoveryStopped(serviceType: String?) {}
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
        }
    }

    private fun parseAdbMdnsOutput(output: String, updateNotif: Boolean) {
        // Format: <service_name> _adb-tls-pairing._tcp. 192.168.1.10:37812
        val lines = output.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("List of")) continue
            val isPairing = trimmed.contains("_adb-tls-pairing")
            val isConnect = trimmed.contains("_adb-tls-connect")
            if (!isPairing && !isConnect) continue

            val endpointToken = trimmed.split(Regex("\\s+")).lastOrNull { it.contains(":") } ?: continue
            val hostPart = endpointToken.substringBeforeLast(":")
            val portPart = endpointToken.substringAfterLast(":").toIntOrNull() ?: continue
            if (portPart > 0) {
                val cleanHost = if (hostPart.isBlank() || hostPart.contains(":")) detectLocalWifiIp() else hostPart
                onPortResolved(cleanHost, portPart, isPairing = isPairing, updateNotif = updateNotif)
            }
        }
    }

    private fun onPortResolved(hostIp: String, port: Int, isPairing: Boolean, updateNotif: Boolean) {
        val current = _state.value
        val resolvedIp = if (hostIp.isNotBlank() && hostIp != "0.0.0.0") hostIp else current.detectedIp
        val newPairingPort = if (isPairing) port else current.pairingPort
        val newConnectPort = if (!isPairing) port else current.connectPort

        val changed = resolvedIp != current.detectedIp ||
                newPairingPort != current.pairingPort ||
                newConnectPort != current.connectPort

        val summary = buildSummaryStatus(resolvedIp, newPairingPort, newConnectPort)
        _state.value = current.copy(
            detectedIp = resolvedIp,
            pairingPort = newPairingPort,
            connectPort = newConnectPort,
            statusText = summary
        )

        if (isPairing && updateNotif && changed) {
            updatePairingNotification(
                title = "Pairing Port Found: $resolvedIp:$newPairingPort",
                body = "Tap 'Enter Pairing Code' below and type the 6-digit code shown on screen."
            )
        }

        if (!isPairing && newConnectPort > 0 && _state.value.autoConnectEnabled) {
            val endpointKey = "$resolvedIp:$newConnectPort"
            if (endpointKey != lastAutoConnectedEndpoint) {
                lastAutoConnectedEndpoint = endpointKey
                scope.launch {
                    onEndpointDiscovered(resolvedIp, newConnectPort, newPairingPort)
                }
            }
        }
    }

    private fun buildSummaryStatus(ip: String, pairingPort: Int, connectPort: Int): String {
        val parts = mutableListOf<String>()
        parts.add("IP: $ip")
        if (pairingPort > 0) parts.add("Pairing Port: $pairingPort")
        if (connectPort > 0) parts.add("Connect Port: $connectPort")
        return if (pairingPort == 0 && connectPort == 0) {
            "IP: $ip • Waiting for Wireless Debugging mDNS signal..."
        } else {
            "Detected • ${parts.joinToString(" | ")}"
        }
    }

    fun detectLocalWifiIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            var fallbackIp: String? = null
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val name = intf.name.lowercase()
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("eth")) {
                            return ip
                        }
                        if (fallbackIp == null) {
                            fallbackIp = ip
                        }
                    }
                }
            }
            return fallbackIp ?: "127.0.0.1"
        } catch (_: Throwable) {
            return "127.0.0.1"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PAIRING_CHANNEL_ID,
                "Wireless ADB Auto-Scanner",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Interactive Wireless ADB auto-discovery and pairing notification"
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    fun updatePairingNotification(title: String, body: String, showCodeInput: Boolean = true) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val launchIntent = Intent(context, MainActivity::class.java)
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(context, PAIRING_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contentPendingIntent)
                .setOngoing(showCodeInput)
                .setAutoCancel(!showCodeInput)

            if (showCodeInput) {
                val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
                    .setLabel("Enter 6-digit Pairing Code")
                    .build()

                val replyIntent = Intent(context, AdbPairingReceiver::class.java).apply {
                    action = AdbPairingReceiver.ACTION_REPLY_PAIRING_CODE
                    putExtra(AdbPairingReceiver.EXTRA_HOST, _state.value.detectedIp)
                    putExtra(AdbPairingReceiver.EXTRA_PAIRING_PORT, _state.value.pairingPort)
                    putExtra(AdbPairingReceiver.EXTRA_CONNECT_PORT, _state.value.connectPort)
                }

                val replyFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

                val replyPendingIntent = PendingIntent.getBroadcast(
                    context,
                    3006,
                    replyIntent,
                    replyFlags
                )

                val action = NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_send,
                    "Enter Pairing Code",
                    replyPendingIntent
                ).addRemoteInput(remoteInput).build()

                builder.addAction(action)
            }

            nm.notify(PAIRING_NOTIFICATION_ID, builder.build())
        } catch (_: Throwable) {
        }
    }

    fun dismissNotification() {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(PAIRING_NOTIFICATION_ID)
        } catch (_: Throwable) {
        }
    }

    fun close() {
        stopScanning()
        scope.cancel()
    }
}
