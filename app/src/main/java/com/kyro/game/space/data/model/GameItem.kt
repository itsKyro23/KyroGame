package com.kyro.game.space.data.model

import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class GameOptimizationMode(val label: String, val systemModeId: Int) {
    STANDARD("Standard", 1),
    PERFORMANCE("Performance", 2),
    CUSTOM("Custom", 2)
}

enum class GameApiTarget(val label: String, val shortTag: String, val commandHint: String) {
    ANDROID_15_BELOW(
        label = "Under Android 15 (≤ A15)",
        shortTag = "≤A15",
        commandHint = "Legacy Game Overlay Engine"
    ),
    ANDROID_16_PLUS(
        label = "Android 16+ (A16+)",
        shortTag = "A16+",
        commandHint = "Native Game Mode Engine"
    );

    companion object {
        fun detectDefaultForDevice(): GameApiTarget {
            val releaseMajor = Build.VERSION.RELEASE
                ?.takeWhile { it.isDigit() }
                ?.toIntOrNull() ?: 0
            return if (Build.VERSION.SDK_INT >= 36 || releaseMajor >= 16) {
                ANDROID_16_PLUS
            } else {
                ANDROID_15_BELOW
            }
        }
    }
}

fun formatDownscaleClean(value: Float): String {
    val clamped = ((value * 20f).roundToInt() / 20f).coerceIn(0.3f, 1.0f)
    val twoDec = String.format(Locale.US, "%.2f", clamped)
    return if (twoDec.endsWith("0")) {
        twoDec.dropLast(1)
    } else {
        twoDec
    }
}

data class GameCustomConfig(
    val mode: GameOptimizationMode = GameOptimizationMode.STANDARD,
    val downscaleFactor: Float = 1.0f, // 0.3f .. 1.0f
    val fpsLimit: Int = 60,
    val apiTarget: GameApiTarget = GameApiTarget.detectDefaultForDevice(),
    val textureConfig: TextureConfig = TextureConfig()
) {
    fun buildShellCommand(packageName: String): String {
        val scaleStr = formatDownscaleClean(downscaleFactor)
        val fpsStr = fpsLimit.toString()
        val modeId = mode.systemModeId

        return when (apiTarget) {
            GameApiTarget.ANDROID_16_PLUS -> {
                "cmd game set --mode $modeId --downscale $scaleStr --fps $fpsStr $packageName"
            }
            GameApiTarget.ANDROID_15_BELOW -> {
                if (mode == GameOptimizationMode.STANDARD && downscaleFactor >= 0.99f) {
                    "device_config delete game_overlay $packageName; cmd game mode 1 $packageName 2>/dev/null || true"
                } else {
                    val overlaySpec = "mode=2,downscaleFactor=$scaleStr,fps=$fpsStr:mode=3,downscaleFactor=$scaleStr,fps=$fpsStr"
                    "device_config put game_overlay $packageName $overlaySpec; cmd game mode $modeId $packageName 2>/dev/null || true"
                }
            }
        }
    }
}

data class GameItem(
    val packageName: String,
    val appName: String,
    val isGame: Boolean,
    val isAddedToHome: Boolean,
    val totalPlaytimeMs: Long = 0L,
    val lastPlayedTimestamp: Long = 0L,
    val versionCode: Long = 0L,
    val versionName: String = "",
    val isSystemApp: Boolean = false,
    val customConfig: GameCustomConfig = GameCustomConfig()
) {
    val formattedPlaytime: String
        get() = formatPlaytime(totalPlaytimeMs)

    val formattedLastPlayed: String
        get() = formatLastPlayed(lastPlayedTimestamp)

    val formattedConfigSummary: String
        get() {
            val scaleStr = "${formatDownscaleClean(customConfig.downscaleFactor)}x"
            return "${customConfig.mode.label} • $scaleStr • ${customConfig.fpsLimit} FPS • ${customConfig.apiTarget.shortTag}"
        }

    companion object {
        fun formatPlaytime(millis: Long): String {
            if (millis <= 0L) return "0m"
            val totalSeconds = millis / 1000
            val totalMinutes = totalSeconds / 60
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                else -> "${minutes}m"
            }
        }

        fun formatLastPlayed(timestamp: Long): String {
            if (timestamp <= 0L) return "Never"
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            val oneDayMs = 24 * 60 * 60 * 1000L

            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val timeStr = timeFormat.format(Date(timestamp))

            return when {
                diff < oneDayMs -> "Today, $timeStr"
                diff < 2 * oneDayMs -> "Yesterday, $timeStr"
                else -> {
                    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    dateFormat.format(Date(timestamp))
                }
            }
        }
    }
}
