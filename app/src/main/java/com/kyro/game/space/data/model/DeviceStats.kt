package com.kyro.game.space.data.model

data class DeviceStats(
    val cpuTempC: Float? = null,
    val batteryTempC: Float? = null,
    val totalRamMb: Long = 0L,
    val usedRamMb: Long = 0L,
    val availRamMb: Long = 0L,
    val ramUsagePercent: Int = 0,
    val pingMs: Int? = null,
    val pingHost: String = "8.8.8.8",
    val isOnline: Boolean = true,
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val batteryVoltageMv: Int = 0,
    val batteryHealth: String = "Good",
    val androidVersion: String = "Android",
    val sdkInt: Int = 34,
    val deviceModel: String = "Unknown Device"
)
