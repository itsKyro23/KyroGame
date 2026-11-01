package com.kyro.game.space.data.executor

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method

class ShizukuCommandExecutor : CommandExecutor {

    override val name: String = "Shizuku"

    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!Shizuku.pingBinder()) return@withContext false
            val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            granted
        } catch (_: Throwable) {
            false
        }
    }

    override fun getVersion(): String {
        return try {
            if (Shizuku.pingBinder()) {
                "Shizuku API v${Shizuku.getVersion()} (UID: ${Shizuku.getUid()})"
            } else {
                "Shizuku Service Not Running"
            }
        } catch (e: Throwable) {
            "Unavailable (${e.message ?: "Binder error"})"
        }
    }

    override fun disconnect() {
        // Shizuku is a system binder service; disconnecting state is handled through repository/settings.
    }

    private fun createProcess(cmd: Array<String>): Process {
        val method: Method = try {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
        } catch (_: NoSuchMethodException) {
            // Alternative signature without dir/env or single array
            Shizuku::class.java.getMethod("newProcess", Array<String>::class.java).apply {
                isAccessible = true
            }
        }

        return if (method.parameterCount == 3) {
            method.invoke(null, cmd, null, null) as Process
        } else {
            method.invoke(null, cmd) as Process
        }
    }

    override suspend fun execute(command: String, timeoutMs: Long): CommandResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            if (!Shizuku.pingBinder()) {
                return@withContext CommandResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = "Shizuku service is not running. Please start Shizuku and try again.",
                    isSuccess = false,
                    durationMs = 0L,
                    command = command
                )
            }

            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return@withContext CommandResult(
                    exitCode = -2,
                    stdout = "",
                    stderr = "Shizuku permission has not been granted to Game Space.",
                    isSuccess = false,
                    durationMs = 0L,
                    command = command
                )
            }

            try {
                val result = withTimeoutOrNull(timeoutMs) {
                    val process = createProcess(arrayOf("sh", "-c", command))

                    val stdoutBuilder = StringBuilder()
                    val stderrBuilder = StringBuilder()

                    val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
                    val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

                    var line: String?
                    while (stdoutReader.readLine().also { line = it } != null) {
                        stdoutBuilder.appendLine(line)
                    }
                    while (stderrReader.readLine().also { line = it } != null) {
                        stderrBuilder.appendLine(line)
                    }

                    val exitCode = process.waitFor()
                    val duration = System.currentTimeMillis() - startTime

                    CommandResult(
                        exitCode = exitCode,
                        stdout = stdoutBuilder.toString().trimEnd(),
                        stderr = stderrBuilder.toString().trimEnd(),
                        isSuccess = (exitCode == 0),
                        durationMs = duration,
                        command = command
                    )
                }

                result ?: CommandResult(
                    exitCode = -3,
                    stdout = "",
                    stderr = "Command timed out after ${timeoutMs}ms.",
                    isSuccess = false,
                    durationMs = timeoutMs,
                    command = command
                )
            } catch (e: Throwable) {
                val duration = System.currentTimeMillis() - startTime
                CommandResult(
                    exitCode = -4,
                    stdout = "",
                    stderr = "Execution failed: ${e.localizedMessage ?: e.javaClass.simpleName}",
                    isSuccess = false,
                    durationMs = duration,
                    command = command
                )
            }
        }

    override fun executeStream(command: String): Flow<String> = flow {
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            emit("[Error] Shizuku is not running or permission not granted.")
            return@flow
        }
        try {
            val process = createProcess(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { emit(it) }
            }
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            while (errorReader.readLine().also { line = it } != null) {
                line?.let { emit("[ERR] $it") }
            }
            process.waitFor()
        } catch (e: Throwable) {
            emit("[Exception] ${e.message}")
        }
    }.flowOn(Dispatchers.IO)
}
