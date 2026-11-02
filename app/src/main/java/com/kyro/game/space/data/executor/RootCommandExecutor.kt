package com.kyro.game.space.data.executor

import com.kyro.game.space.data.model.RootInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class RootCommandExecutor : CommandExecutor {
    override val name: String = "Root (su)"

    @Volatile
    var isRootGranted: Boolean = false
        private set

    @Volatile
    var suVersion: String = "Not Checked"
        private set

    @Volatile
    var statusMessage: String = "Superuser (su) ready to verify. Tap Grant Root to request Magisk / KernelSU access."
        private set

    private val commonSuPaths = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/debug_ramdisk/su",
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su",
        "/su/bin/su",
        "/vendor/bin/su"
    )

    val isSuBinaryPresent: Boolean
        get() = try {
            commonSuPaths.any { File(it).exists() }
        } catch (_: Throwable) {
            false
        }

    suspend fun probeAndRequestRoot(triggerPrompt: Boolean = false): RootInfo = withContext(Dispatchers.IO) {
        val binaryFound = isSuBinaryPresent
        if (!binaryFound && !triggerPrompt) {
            isRootGranted = false
            suVersion = "su binary not found"
            statusMessage = "Root binary (su) not detected. Ensure Magisk, KernelSU, or APatch is installed."
            return@withContext RootInfo(
                isSuBinaryPresent = false,
                isRootGranted = false,
                suVersion = suVersion,
                uid = -1,
                statusMessage = statusMessage
            )
        }

        val idRes = execute("id; su -v 2>/dev/null || true", timeoutMs = if (triggerPrompt) 15000L else 3500L)
        val combined = "${idRes.stdout}\n${idRes.stderr}".trim()
        val granted = idRes.isSuccess && combined.contains("uid=0")
        isRootGranted = granted

        val versionLine = combined.lines()
            .firstOrNull { !it.startsWith("uid=") && it.isNotBlank() }
            ?: if (granted) "Superuser (UID 0)" else "Permission Required"
        suVersion = versionLine
        statusMessage = when {
            granted -> "Root Access Granted (UID 0 • $versionLine)"
            binaryFound -> "su binary found ($versionLine), but Root permission was denied or not granted."
            else -> "Root access unavailable (su binary not found on this device)."
        }

        RootInfo(
            isSuBinaryPresent = binaryFound || granted,
            isRootGranted = granted,
            suVersion = versionLine,
            uid = if (granted) 0 else -1,
            statusMessage = statusMessage
        )
    }

    override suspend fun execute(command: String, timeoutMs: Long): CommandResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            val process = ProcessBuilder("su", "-c", command).start()
            try {
                process.outputStream.close()
            } catch (_: Throwable) {
            }
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext CommandResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = "Root command timed out after ${timeoutMs}ms",
                    isSuccess = false,
                    durationMs = System.currentTimeMillis() - startMs,
                    command = command
                )
            }
            val out = process.inputStream.bufferedReader().readText().trim()
            val err = process.errorStream.bufferedReader().readText().trim()
            val code = process.exitValue()
            if (code == 0) {
                isRootGranted = true
            }
            CommandResult(
                exitCode = code,
                stdout = out,
                stderr = err,
                isSuccess = code == 0,
                durationMs = System.currentTimeMillis() - startMs,
                command = command
            )
        } catch (t: Throwable) {
            CommandResult(
                exitCode = -1,
                stdout = "",
                stderr = "Root (su) error: ${t.localizedMessage ?: t.message ?: "su unavailable"}",
                isSuccess = false,
                durationMs = System.currentTimeMillis() - startMs,
                command = command
            )
        }
    }

    override fun executeStream(command: String): Flow<String> = flow {
        val res = execute(command)
        if (res.stdout.isNotEmpty()) emit(res.stdout)
        if (res.stderr.isNotEmpty()) emit(res.stderr)
    }.flowOn(Dispatchers.IO)

    override suspend fun isConnected(): Boolean = isRootGranted

    override fun getVersion(): String = suVersion

    override fun disconnect() {
        isRootGranted = false
    }
}
