package com.kyro.game.space.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [GameEntity::class], version = 1, exportSchema = false)
abstract class GameSpaceDatabase : RoomDatabase() {

    abstract fun gameDao(): GameDao

    companion object {
        @Volatile
        private var INSTANCE: GameSpaceDatabase? = null

        fun getDatabase(context: Context): GameSpaceDatabase {
            return INSTANCE?.takeIf { it.isOpen } ?: synchronized(this) {
                INSTANCE?.takeIf { it.isOpen } ?: Room.databaseBuilder(
                    context.applicationContext,
                    GameSpaceDatabase::class.java,
                    "game_space_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        fun closeAndReset() {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                } catch (_: Throwable) {
                }
                INSTANCE = null
            }
        }
    }
}
