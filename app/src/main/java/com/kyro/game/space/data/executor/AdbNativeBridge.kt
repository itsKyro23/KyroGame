package com.kyro.game.space.data.executor

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Native binary execution bridge for `libadb.so` (ARM64 Android Debug Bridge PIE executable).
 *
 * Android PackageManager extracts `jniLibs/arm64-v8a/libadb.so` into
 * `context.applicationInfo.nativeLibraryDir/libadb.so` with `0755` executable permissions
 * when `extractNativeLibs="true"` and `useLegacyPackaging = true`.
 */
object AdbNativeBridge {

    @Volatile
    var isNativeLoaded: Boolean = false
        private set

    @Volatile
    var statusMessage: String = "libadb.so: Initializing..."
        private set

    @Volatile
    var binaryPath: String = ""
        private set

    @Volatile
    var adbVersionText: String = "Android Debug Bridge 1.0.41"
        private set

    private var homeDir: String = ""
    private var tmpDir: String = ""

    fun initialize(context: Context) {
        try {
            val nativeDir = context.applicationInfo.nativeLibraryDir
            val adbFile = File(nativeDir, "libadb.so")
            homeDir = File(context.filesDir, "adb_home").apply {
                mkdirs()
                File(this, ".android").mkdirs()
            }.absolutePath
            tmpDir = File(context.cacheDir, "adb_tmp").apply { mkdirs() }.absolutePath

            if (adbFile.exists()) {
                if (!adbFile.canExecute()) {
                    adbFile.setExecutable(true, false)
                }
                binaryPath = adbFile.absolutePath
                isNativeLoaded = true
                val sizeMb = String.format("%.2f MB", adbFile.length() / (1024.0 * 1024.0))
                statusMessage = "libadb.so: Ready (ARM64 Native ELF • $sizeMb)"
            } else {
                // Check fallback inside filesDir if copied manually
                val fallbackFile = File(context.filesDir, "libadb.so")
                if (fallbackFile.exists()) {
                    fallbackFile.setExecutable(true, false)
                    binaryPath = fallbackFile.absolutePath
                    isNativeLoaded = true
                    statusMessage = "libadb.so: Ready (Local Binary)"
                } else {
                    isNativeLoaded = false
                    statusMessage = "libadb.so: Binary not found in nativeLibraryDir ($nativeDir)"
                }
            }
        } catch (e: Throwable) {
            isNativeLoaded = false
            statusMessage = "libadb.so: Init error (${e.message ?: "Unknown"})"
        }
    }

    /**
     * Executes `libadb.so` with the specified arguments and proper Android environment variables.
     */
    fun runAdbBinary(
        vararg args: String,
        timeoutMs: Long = 12_000L
    ): CommandResult {
        val startTime = System.currentTimeMillis()
        if (!isNativeLoaded || binaryPath.isBlank()) {
            return CommandResult(
                exitCode = -1,
                stdout = "",
                stderr = "libadb.so binary is not available ($statusMessage)",
                isSuccess = false,
                durationMs = 0L,
                command = "libadb.so ${args.joinToString(" ")}"
            )
        }

        return try {
            val cmdList = mutableListOf(binaryPath).apply { addAll(args) }
            val pb = ProcessBuilder(cmdList)
            val env = pb.environment()
            if (homeDir.isNotBlank()) {
                env["HOME"] = homeDir
                env["ADB_VENDOR_KEYS"] = homeDir
            }
            if (tmpDir.isNotBlank()) {
                env["TMPDIR"] = tmpDir
            }
            env["ANDROID_ADB_SERVER_PORT"] = "5037"

            val process = pb.start()
            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            val stdoutThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            stdoutBuilder.appendLine(line)
                        }
                    }
                } catch (_: Throwable) {
                }
            }

            val stderrThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            stderrBuilder.appendLine(line)
                        }
                    }
                } catch (_: Throwable) {
                }
            }

            stdoutThread.start()
            stderrThread.start()

            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return CommandResult(
                    exitCode = -2,
                    stdout = stdoutBuilder.toString().trimEnd(),
                    stderr = "libadb.so command timed out after ${timeoutMs}ms",
                    isSuccess = false,
                    durationMs = System.currentTimeMillis() - startTime,
                    command = "libadb.so ${args.joinToString(" ")}"
                )
            }

            stdoutThread.join(1000)
            stderrThread.join(1000)

            val exitCode = process.exitValue()
            val outStr = stdoutBuilder.toString().trimEnd()
            val errStr = stderrBuilder.toString().trimEnd()

            CommandResult(
                exitCode = exitCode,
                stdout = outStr,
                stderr = errStr,
                isSuccess = exitCode == 0,
                durationMs = System.currentTimeMillis() - startTime,
                command = "libadb.so ${args.joinToString(" ")}"
            )
        } catch (e: Throwable) {
            CommandResult(
                exitCode = -3,
                stdout = "",
                stderr = "libadb.so execution error: ${e.localizedMessage ?: e.message}",
                isSuccess = false,
                durationMs = System.currentTimeMillis() - startTime,
                command = "libadb.so ${args.joinToString(" ")}"
            )
        }
    }
}
