package com.kyro.game.space.data.executor

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val isSuccess: Boolean,
    val durationMs: Long = 0L,
    val command: String = ""
)
