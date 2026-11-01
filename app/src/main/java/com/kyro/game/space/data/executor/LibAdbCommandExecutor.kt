package com.kyro.game.space.data.executor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

class LibAdbCommandExecutor : CommandExecutor {

    override val name: String = "Manual ADB (libadb.so + Daemon)"

    companion object {
        const val DAEMON_PORT = 38555
        const val LOCAL_TCP_ADB_PORT = 5555
        const val DAEMON_SCRIPT_PATH = "/data/local/tmp/kyro_daemon.sh"
        const val DAEMON_PID_PATH = "/data/local/tmp/kyro_daemon.pid"

        /**
         * Shell script payload written to `/data/local/tmp/kyro_daemon.sh` and started with `setsid nohup`.
         * Keeps a persistent localhost shell listener on 127.0.0.1:38555 running as UID 2000 (shell)
         * so communication stays alive even when Wireless Debugging is turned OFF.
         */
        val DAEMON_INSTALL_COMMAND: String = """
            cat << 'KYRO_EOF' > $DAEMON_SCRIPT_PATH
            #!/system/bin/sh
            export PATH=/system/bin:/system/xbin:/vendor/bin:${'$'}PATH
            PORT=$DAEMON_PORT
            if [ -f $DAEMON_PID_PATH ]; then
                OLD_PID=${'$'}(cat $DAEMON_PID_PATH 2>/dev/null)
                if [ -n "${'$'}OLD_PID" ] && [ "${'$'}OLD_PID" != "${'$'}${'$'}" ]; then
                    kill -9 "${'$'}OLD_PID" 2>/dev/null
                fi
            fi
            echo ${'$'}${'$'} > $DAEMON_PID_PATH
            chmod 666 $DAEMON_PID_PATH 2>/dev/null
            while true; do
                if command -v toybox >/dev/null 2>&1; then
                    toybox nc -s 127.0.0.1 -p ${'$'}PORT -L /system/bin/sh 2>/dev/null || \
                    toybox nc -s 127.0.0.1 -p ${'$'}PORT -l /system/bin/sh 2>/dev/null
                elif command -v nc >/dev/null 2>&1; then
                    nc -s 127.0.0.1 -p ${'$'}PORT -L /system/bin/sh 2>/dev/null || \
                    nc -s 127.0.0.1 -p ${'$'}PORT -l /system/bin/sh 2>/dev/null
                fi
                sleep 1
            done
            KYRO_EOF
            chmod 755 $DAEMON_SCRIPT_PATH
            if command -v setsid >/dev/null 2>&1; then
                setsid nohup /system/bin/sh $DAEMON_SCRIPT_PATH </dev/null >/dev/null 2>&1 &
            else
                nohup /system/bin/sh $DAEMON_SCRIPT_PATH </dev/null >/dev/null 2>&1 &
            fi
            sleep 1
            echo "DAEMON_INJECTED_OK"
        """.trimIndent()
    }

    @Volatile
    var currentHost: String = "127.0.0.1"
        private set

    @Volatile
    var currentPort: Int = 5555
        private set

    @Volatile
    var isConnectedSession: Boolean = false
        private set

    @Volatile
    var isDaemonInjected: Boolean = false
        private set

    @Volatile
    var daemonStatusText: String = "Daemon Not Injected (Connect Wireless ADB first)"
        private set

    @Volatile
    var lastError: String? = null
        private set

    /**
     * Probes whether the injected persistent daemon on 127.0.0.1:38555 or local TCP ADB on 127.0.0.1:5555 is alive.
     * This allows the app to stay connected even after Wireless Debugging is turned OFF.
     */
    suspend fun probeDaemonStatus(): Boolean = withContext(Dispatchers.IO) {
        // 1. Check injected persistent daemon on 127.0.0.1:38555
        val daemonRes = executeViaInjectedDaemon("id", 2000L)
        if (daemonRes != null && daemonRes.isSuccess && daemonRes.stdout.contains("uid=")) {
            isDaemonInjected = true
            isConnectedSession = true
            lastError = null
            daemonStatusText = "Daemon Active (127.0.0.1:$DAEMON_PORT • Wireless ADB Off Safe)"
            return@withContext true
        }

        // 2. Check if local loopback ADB 127.0.0.1:5555 is responsive via libadb.so
        if (AdbNativeBridge.isNativeLoaded) {
            val adbCheck = AdbNativeBridge.runAdbBinary(
                "-s", "127.0.0.1:$LOCAL_TCP_ADB_PORT", "shell", "id",
                timeoutMs = 3000L
            )
            if (adbCheck.isSuccess && adbCheck.stdout.contains("uid=")) {
                isConnectedSession = true
                lastError = null
                daemonStatusText = "Local Loopback ADB Active (127.0.0.1:$LOCAL_TCP_ADB_PORT)"
                return@withContext true
            }
        }

        isDaemonInjected = false
        if (!isConnectedSession) {
            daemonStatusText = "Daemon Not Injected (Pair & Connect Wireless ADB)"
        }
        false
    }

    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        if (probeDaemonStatus()) {
            return@withContext true
        }
        if (!isConnectedSession) return@withContext false

        if (AdbNativeBridge.isNativeLoaded) {
            val check = AdbNativeBridge.runAdbBinary(
                "-s", "$currentHost:$currentPort", "shell", "echo ok",
                timeoutMs = 3500L
            )
            if (check.isSuccess && check.stdout.contains("ok")) {
                return@withContext true
            }
        }

        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(currentHost, currentPort), 2000)
                true
            }
        } catch (e: Throwable) {
            isConnectedSession = false
            lastError = "Connection lost: ${e.message}"
            false
        }
    }

    override fun getVersion(): String {
        return "Android Debug Bridge 1.0.41 | ${AdbNativeBridge.statusMessage}"
    }

    override fun disconnect() {
        isConnectedSession = false
        isDaemonInjected = false
        daemonStatusText = "Session Disconnected"
        lastError = null
        try {
            if (AdbNativeBridge.isNativeLoaded) {
                AdbNativeBridge.runAdbBinary("disconnect", timeoutMs = 3000L)
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * Stops and removes the injected background daemon if the user explicitly wants to kill it.
     */
    suspend fun stopInjectedDaemon(): Result<String> = withContext(Dispatchers.IO) {
        try {
            executeViaInjectedDaemon(
                "if [ -f $DAEMON_PID_PATH ]; then kill -9 \$(cat $DAEMON_PID_PATH) 2>/dev/null; rm -f $DAEMON_PID_PATH; fi; pkill -f kyro_daemon.sh 2>/dev/null",
                2500L
            )
            isDaemonInjected = false
            isConnectedSession = false
            daemonStatusText = "Daemon Stopped"
            Result.success("Persistent daemon stopped and cleaned up.")
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Pairs with Android 11+ Wireless Debugging pairing service using `libadb.so pair <host>:<port> <code>`.
     */
    suspend fun pair(
        host: String,
        pairingPort: Int,
        pairingCode: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanHost = host.trim()
        val cleanCode = pairingCode.trim()
        if (cleanHost.isBlank() || pairingPort <= 0 || cleanCode.length != 6) {
            val err = "Invalid parameters. Enter a valid IP Address, Pairing Port > 0, and 6-digit Pairing Code."
            lastError = err
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        if (AdbNativeBridge.isNativeLoaded) {
            // Start adb server first
            AdbNativeBridge.runAdbBinary("start-server", timeoutMs = 5000L)
            val res = AdbNativeBridge.runAdbBinary(
                "pair", "$cleanHost:$pairingPort", cleanCode,
                timeoutMs = 12_000L
            )
            val combined = "${res.stdout}\n${res.stderr}".trim()
            if (res.isSuccess || combined.contains("Successfully paired", ignoreCase = true)) {
                lastError = null
                // Check if Wireless ADB connection port is already advertised via mDNS and auto-connect!
                val mdnsRes = AdbNativeBridge.runAdbBinary("mdns", "services", timeoutMs = 3000L)
                val connectLine = mdnsRes.stdout.lines().firstOrNull { it.contains("_adb-tls-connect") }
                val discoveredPort = connectLine?.split(Regex("\\s+"))?.lastOrNull()
                    ?.substringAfterLast(":")?.toIntOrNull() ?: 0

                if (discoveredPort > 0) {
                    val autoConnectRes = connect(cleanHost, discoveredPort)
                    if (autoConnectRes.isSuccess) {
                        return@withContext Result.success(
                            "Paired with $cleanHost:$pairingPort & Auto-Connected to port $discoveredPort!\n${autoConnectRes.getOrNull()}"
                        )
                    }
                }

                return@withContext Result.success(
                    "Successfully paired with $cleanHost:$pairingPort!\n$combined\nNow tap '2. Connect & Inject' (or let Auto-Connect finish)."
                )
            } else if (combined.isNotBlank() && !combined.contains("Exec format error", ignoreCase = true)) {
                val err = "Pairing failed for $cleanHost:$pairingPort:\n$combined"
                lastError = err
                return@withContext Result.failure(Exception(err))
            }
        }

        // Fallback socket check (for non-ARM64 host/test environments)
        try {
            Socket().use { socket ->
                socket.soTimeout = 4000
                socket.connect(InetSocketAddress(cleanHost, pairingPort), 3000)
            }
            lastError = null
            Result.success("Pairing request sent to $cleanHost:$pairingPort.")
        } catch (e: Throwable) {
            val msg = "Pairing failed for $cleanHost:$pairingPort: ${e.localizedMessage ?: e.javaClass.simpleName}"
            lastError = msg
            Result.failure(Exception(msg, e))
        }
    }

    /**
     * Connects to Wireless ADB (`libadb.so connect <host>:<port>`) and IMMEDIATELY injects the
     * persistent background daemon (`kyro_daemon.sh` + `adb tcpip 5555`) so communication continues
     * working even after the user turns OFF Wireless Debugging!
     */
    suspend fun connect(
        host: String,
        connectPort: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanHost = host.trim()
        if (cleanHost.isBlank() || connectPort <= 0) {
            val err = "Invalid Host or Connection Port ($cleanHost:$connectPort)."
            lastError = err
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        // First check if daemon is already alive and user is reconnecting
        if (probeDaemonStatus()) {
            currentHost = cleanHost
            currentPort = connectPort
            return@withContext Result.success(
                "Persistent Daemon is already active on port $DAEMON_PORT! You can safely turn off Wireless Debugging."
            )
        }

        val target = "$cleanHost:$connectPort"
        if (AdbNativeBridge.isNativeLoaded) {
            AdbNativeBridge.runAdbBinary("start-server", timeoutMs = 5000L)
            val connectRes = AdbNativeBridge.runAdbBinary("connect", target, timeoutMs = 10_000L)
            val combined = "${connectRes.stdout}\n${connectRes.stderr}".trim()

            val connectedOk = combined.contains("connected to", ignoreCase = true) ||
                    combined.contains("already connected", ignoreCase = true)

            if (connectedOk) {
                currentHost = cleanHost
                currentPort = connectPort
                isConnectedSession = true
                lastError = null

                // Automatically inject the Persistent Daemon so turning off Wireless Debugging won't disconnect!
                val daemonResult = injectPersistentDaemonViaAdb(target)
                val msg = buildString {
                    appendLine("Connected to Wireless ADB ($target).")
                    appendLine(daemonResult)
                }.trim()
                return@withContext Result.success(msg)
            } else if (combined.isNotBlank() && !combined.contains("Exec format error", ignoreCase = true)) {
                isConnectedSession = false
                val err = "Failed to connect to $target:\n$combined"
                lastError = err
                return@withContext Result.failure(Exception(err))
            }
        }

        // Fallback socket check (for non-ARM64 test environments)
        try {
            Socket().use { socket ->
                socket.soTimeout = 3000
                socket.connect(InetSocketAddress(cleanHost, connectPort), 3000)
            }
            currentHost = cleanHost
            currentPort = connectPort
            isConnectedSession = true
            lastError = null
            Result.success("Connected to ADB daemon at $cleanHost:$connectPort")
        } catch (e: Throwable) {
            isConnectedSession = false
            val msg = "Failed to connect to $cleanHost:$connectPort (${e.localizedMessage ?: e.javaClass.simpleName})"
            lastError = msg
            Result.failure(Exception(msg, e))
        }
    }

    /**
     * Injects the persistent daemon over an active `libadb.so` target connection AND enables `tcpip 5555`.
     */
    suspend fun injectPersistentDaemonViaAdb(targetEndpoint: String = "$currentHost:$currentPort"): String =
        withContext(Dispatchers.IO) {
            try {
                daemonStatusText = "Injecting Persistent Daemon..."

                // 1. Deploy and start /data/local/tmp/kyro_daemon.sh via setsid nohup
                val injectRes = AdbNativeBridge.runAdbBinary(
                    "-s", targetEndpoint, "shell", DAEMON_INSTALL_COMMAND,
                    timeoutMs = 10_000L
                )

                // 2. Also enable local loopback TCP 5555 so adbd listens on 127.0.0.1:5555 independent of Wi-Fi
                AdbNativeBridge.runAdbBinary(
                    "-s", targetEndpoint, "tcpip", LOCAL_TCP_ADB_PORT.toString(),
                    timeoutMs = 6_000L
                )
                delay(600)
                AdbNativeBridge.runAdbBinary(
                    "connect", "127.0.0.1:$LOCAL_TCP_ADB_PORT",
                    timeoutMs = 5_000L
                )

                // 3. Verify that our standalone daemon on 127.0.0.1:38555 responds
                val verified = probeDaemonStatus()
                if (verified && isDaemonInjected) {
                    "✓ Persistent Daemon injected (127.0.0.1:$DAEMON_PORT + TCP 5555)!\n" +
                            "Connection WILL NOT DROP even when Wireless Debugging is turned OFF."
                } else if (injectRes.isSuccess || injectRes.stdout.contains("DAEMON_INJECTED_OK")) {
                    isDaemonInjected = true
                    isConnectedSession = true
                    daemonStatusText = "Daemon Injected (Loopback Active • Wireless ADB Off Safe)"
                    "✓ Daemon injected & TCP 5555 enabled. Connection stays alive even when Wireless Debugging is OFF."
                } else {
                    daemonStatusText = "Connected (Direct ADB Mode)"
                    "Connected, but daemon injection reported: ${injectRes.stderr.ifBlank { injectRes.stdout }}"
                }
            } catch (e: Throwable) {
                daemonStatusText = "Daemon Injection Error: ${e.message}"
                "Daemon injection error: ${e.localizedMessage ?: e.message}"
            }
        }

    /**
     * Allows injecting the `libadb` persistent daemon using an already-authorized executor (such as Shizuku)
     * or the current active ADB session with 1 click!
     */
    suspend fun injectPersistentDaemonWithExecutor(fallbackExecutor: CommandExecutor? = null): Result<String> =
        withContext(Dispatchers.IO) {
            // Check if already running
            if (probeDaemonStatus() && isDaemonInjected) {
                return@withContext Result.success(
                    "Persistent Daemon is already active at 127.0.0.1:$DAEMON_PORT (UID 2000)! Safe to turn off Wireless Debugging."
                )
            }

            // Try via active libadb.so session first
            if (AdbNativeBridge.isNativeLoaded && isConnectedSession) {
                val msg = injectPersistentDaemonViaAdb("$currentHost:$currentPort")
                if (isDaemonInjected) {
                    return@withContext Result.success(msg)
                }
            }

            // Try via fallback executor (e.g. Shizuku if running and granted)
            if (fallbackExecutor != null && fallbackExecutor.isConnected()) {
                val res = fallbackExecutor.execute(DAEMON_INSTALL_COMMAND, timeoutMs = 10_000L)
                delay(500)
                if (probeDaemonStatus() || res.stdout.contains("DAEMON_INJECTED_OK")) {
                    isDaemonInjected = true
                    isConnectedSession = true
                    daemonStatusText = "Daemon Active (127.0.0.1:$DAEMON_PORT • Wireless ADB Off Safe)"
                    return@withContext Result.success(
                        "✓ Persistent Daemon injected! libadb communication now stays active even when Wireless Debugging is turned OFF."
                    )
                }
            }

            Result.failure(
                Exception("Please Pair & Connect Wireless ADB first (or enable Shizuku) to inject the Persistent Daemon.")
            )
        }

    /**
     * Executes a shell command directly through the injected persistent daemon on 127.0.0.1:38555.
     */
    private fun executeViaInjectedDaemon(command: String, timeoutMs: Long): CommandResult? {
        val startTime = System.currentTimeMillis()
        return try {
            Socket().use { socket ->
                socket.soTimeout = timeoutMs.toInt().coerceAtLeast(2500)
                socket.connect(InetSocketAddress("127.0.0.1", DAEMON_PORT), 1500)

                val out = socket.getOutputStream()
                val exitMarker = "__KYRO_EXIT_CODE:"
                val payload = "$command 2>&1\necho \"${exitMarker}\$?__\"\nexit\n"
                out.write(payload.toByteArray(Charsets.UTF_8))
                out.flush()

                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val lines = mutableListOf<String>()
                var exitCode = 0
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: continue
                    if (l.startsWith(exitMarker) && l.endsWith("__")) {
                        val codeStr = l.removePrefix(exitMarker).removeSuffix("__")
                        exitCode = codeStr.toIntOrNull() ?: 0
                        break
                    } else {
                        lines.add(l)
                    }
                }

                val output = lines.joinToString("\n").trimEnd()
                val duration = System.currentTimeMillis() - startTime
                CommandResult(
                    exitCode = exitCode,
                    stdout = if (exitCode == 0) output else "",
                    stderr = if (exitCode != 0) output else "",
                    isSuccess = exitCode == 0,
                    durationMs = duration,
                    command = command
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

    override suspend fun execute(command: String, timeoutMs: Long): CommandResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            // Stage 1: Execute via Injected Persistent Daemon on 127.0.0.1:38555 (Works even when Wireless Debugging is OFF!)
            val daemonResult = executeViaInjectedDaemon(command, timeoutMs)
            if (daemonResult != null) {
                isDaemonInjected = true
                isConnectedSession = true
                daemonStatusText = "Daemon Active (127.0.0.1:$DAEMON_PORT • Wireless ADB Off Safe)"
                return@withContext daemonResult
            }

            // Stage 2: Execute via local loopback TCP ADB (127.0.0.1:5555) using libadb.so
            if (AdbNativeBridge.isNativeLoaded) {
                val localTcpRes = AdbNativeBridge.runAdbBinary(
                    "-s", "127.0.0.1:$LOCAL_TCP_ADB_PORT", "shell", command,
                    timeoutMs = timeoutMs
                )
                if (localTcpRes.isSuccess) {
                    isConnectedSession = true
                    return@withContext localTcpRes.copy(command = command)
                }

                // Stage 3: Execute via Wireless ADB endpoint ($currentHost:$currentPort) using libadb.so
                if (isConnectedSession) {
                    val remoteRes = AdbNativeBridge.runAdbBinary(
                        "-s", "$currentHost:$currentPort", "shell", command,
                        timeoutMs = timeoutMs
                    )
                    if (remoteRes.isSuccess) {
                        return@withContext remoteRes.copy(command = command)
                    }
                }
            }

            if (!isConnectedSession) {
                return@withContext CommandResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = "Manual ADB & Daemon not connected. Please Pair & Connect Wireless ADB in Settings to inject the Daemon.",
                    isSuccess = false,
                    durationMs = System.currentTimeMillis() - startTime,
                    command = command
                )
            }

            // Fallback socket execution for test environments
            try {
                var output = ""
                Socket().use { socket ->
                    socket.soTimeout = timeoutMs.toInt()
                    socket.connect(InetSocketAddress(currentHost, currentPort), 3000)

                    val outputStream = socket.getOutputStream()
                    outputStream.write("shell:$command\n".toByteArray(Charsets.UTF_8))
                    outputStream.flush()

                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.appendLine(line)
                    }
                    output = sb.toString().trimEnd()
                }

                val duration = System.currentTimeMillis() - startTime
                CommandResult(
                    exitCode = 0,
                    stdout = output,
                    stderr = "",
                    isSuccess = true,
                    durationMs = duration,
                    command = command
                )
            } catch (e: Throwable) {
                val duration = System.currentTimeMillis() - startTime
                CommandResult(
                    exitCode = -2,
                    stdout = "",
                    stderr = "ADB / Daemon communication error: ${e.localizedMessage ?: e.message}",
                    isSuccess = false,
                    durationMs = duration,
                    command = command
                )
            }
        }

    override fun executeStream(command: String): Flow<String> = flow {
        try {
            val res = execute(command)
            if (res.isSuccess) {
                emit(res.stdout)
            } else {
                emit("[ERR] ${res.stderr}")
            }
        } catch (e: Throwable) {
            emit("[Exception] ${e.message}")
        }
    }.flowOn(Dispatchers.IO)
}
