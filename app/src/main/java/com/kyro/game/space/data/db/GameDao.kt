package com.kyro.game.space.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {

    @Query("SELECT * FROM games ORDER BY appName COLLATE NOCASE ASC")
    fun getAllGamesFlow(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE isAddedToHome = 1 ORDER BY lastPlayedTimestamp DESC, appName COLLATE NOCASE ASC")
    fun getHomeGamesFlow(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE packageName = :packageName LIMIT 1")
    suspend fun getGameByPackage(packageName: String): GameEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInitial(games: List<GameEntity>)

    @Query("UPDATE games SET isAddedToHome = :isAdded WHERE packageName = :packageName")
    suspend fun setAddedToHome(packageName: String, isAdded: Boolean)

    @Query("UPDATE games SET totalPlaytimeMs = :playtimeMs, lastPlayedTimestamp = :lastPlayed WHERE packageName = :packageName")
    suspend fun updatePlaytimeAndLastPlayed(packageName: String, playtimeMs: Long, lastPlayed: Long)

    @Query("UPDATE games SET totalPlaytimeMs = :playtimeMs WHERE packageName = :packageName")
    suspend fun updatePlaytime(packageName: String, playtimeMs: Long)

    @Query("UPDATE games SET lastPlayedTimestamp = :lastPlayed WHERE packageName = :packageName")
    suspend fun updateLastPlayed(packageName: String, lastPlayed: Long)

    @Query("DELETE FROM games WHERE packageName NOT IN (:currentPackages)")
    suspend fun deleteRemovedPackages(currentPackages: List<String>)
}
