package com.kyro.game.space.data.model

enum class ExecutionMethod {
    SHIZUKU,
    MANUAL_ADB,
    ROOT
}

enum class ConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    CONNECTING,
    PERMISSION_REQUIRED,
    ERROR
}

data class ShizukuInfo(
    val isInstalled: Boolean = false,
    val isRunning: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val version: Int = 0,
    val uid: Int = -1
)

data class RootInfo(
    val isSuBinaryPresent: Boolean = false,
    val isRootGranted: Boolean = false,
    val suVersion: String = "Not Detected",
    val uid: Int = -1,
    val statusMessage: String = "Root (su) status not checked yet."
)

data class ManualAdbInfo(
    val isConnected: Boolean = false,
    val isDaemonInjected: Boolean = false,
    val daemonPort: Int = 38555,
    val daemonStatus: String = "Daemon Not Injected",
    val isLibAdbLoaded: Boolean = false,
    val libAdbDetails: String = "libadb.so: Ready",
    val host: String = "127.0.0.1",
    val port: Int = 5555,
    val pairingPort: Int = 0,
    val pairingCode: String = "",
    val isScanning: Boolean = false,
    val scanStatus: String = "Auto-Scan Ready",
    val autoConnectEnabled: Boolean = true,
    val adbVersion: String = "Android Debug Bridge 1.0.41",
    val error: String? = null
)

data class ConnectionOverview(
    val method: ExecutionMethod = ExecutionMethod.SHIZUKU,
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val statusDetails: String = "Initializing connection...",
    val shizuku: ShizukuInfo = ShizukuInfo(),
    val manualAdb: ManualAdbInfo = ManualAdbInfo(),
    val root: RootInfo = RootInfo(),
    val androidVersion: String = "",
    val lastCommandLog: String? = null
) {
    /**
     * Checks if at least 1 of the 3 execution permissions (Shizuku, Wireless/Manual ADB, or Root)
     * is currently active and authorized.
     */
    val isAnyPermissionActive: Boolean
        get() = (shizuku.isRunning && shizuku.isPermissionGranted) ||
                manualAdb.isConnected ||
                manualAdb.isDaemonInjected ||
                root.isRootGranted ||
                status == ConnectionStatus.CONNECTED

    val isCurrentMethodActive: Boolean
        get() = when (method) {
            ExecutionMethod.SHIZUKU -> shizuku.isRunning && shizuku.isPermissionGranted
            ExecutionMethod.MANUAL_ADB -> manualAdb.isConnected || manualAdb.isDaemonInjected
            ExecutionMethod.ROOT -> root.isRootGranted
        } || status == ConnectionStatus.CONNECTED

    val activePermissionName: String?
        get() = when {
            root.isRootGranted -> "Root (su)"
            shizuku.isRunning && shizuku.isPermissionGranted -> "Shizuku"
            manualAdb.isConnected || manualAdb.isDaemonInjected -> "Wireless ADB"
            status == ConnectionStatus.CONNECTED -> method.name
            else -> null
        }
}
