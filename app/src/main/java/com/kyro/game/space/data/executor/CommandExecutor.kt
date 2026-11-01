package com.kyro.game.space.data.executor

import kotlinx.coroutines.flow.Flow

interface CommandExecutor {
    val name: String

    suspend fun execute(command: String, timeoutMs: Long = 10000L): CommandResult

    suspend fun executeAsync(command: String, timeoutMs: Long = 10000L): CommandResult =
        execute(command, timeoutMs)

    fun executeStream(command: String): Flow<String>

    suspend fun isConnected(): Boolean

    fun getVersion(): String

    fun disconnect()
}
