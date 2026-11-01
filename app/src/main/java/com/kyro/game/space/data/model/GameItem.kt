package com.kyro.game.space.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GameItem(
    val packageName: String,
    val appName: String,
    val isGame: Boolean,
    val isAddedToHome: Boolean,
    val totalPlaytimeMs: Long = 0L,
    val lastPlayedTimestamp: Long = 0L,
    val versionCode: Long = 0L,
    val versionName: String = "",
    val isSystemApp: Boolean = false
) {
    val formattedPlaytime: String
        get() = formatPlaytime(totalPlaytimeMs)

    val formattedLastPlayed: String
        get() = formatLastPlayed(lastPlayedTimestamp)

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
