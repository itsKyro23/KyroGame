package com.kyro.game.space.data.model

enum class ExecutionMethod {
    SHIZUKU,
    MANUAL_ADB
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
    val adbVersion: String = "Android Debug Bridge 1.0.41",
    val error: String? = null
)

data class ConnectionOverview(
    val method: ExecutionMethod = ExecutionMethod.SHIZUKU,
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val statusDetails: String = "Initializing connection...",
    val shizuku: ShizukuInfo = ShizukuInfo(),
    val manualAdb: ManualAdbInfo = ManualAdbInfo(),
    val androidVersion: String = "",
    val lastCommandLog: String? = null
)
