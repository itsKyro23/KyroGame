package com.kyro.game.space.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val isGame: Boolean,
    val isAddedToHome: Boolean,
    val totalPlaytimeMs: Long = 0L,
    val lastPlayedTimestamp: Long = 0L,
    val versionCode: Long = 0L,
    val versionName: String = "",
    val isSystemApp: Boolean = false
)
