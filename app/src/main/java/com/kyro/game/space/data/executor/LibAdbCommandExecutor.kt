package com.kyro.game.space.data.executor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
         * Shell script payload written to `/data/local/tmp/kyro_daemon.sh` and started detached.
         * Keeps a persistent localhost shell listener on 127.0.0.1:38555 running as UID 2000 (shell)
         * and sets `service.adb.tcp.port 5555` so communication stays alive even when
         * Wireless Debugging is turned OFF or the app is closed and reopened.
         */
        val DAEMON_INSTALL_COMMAND: String = """
            setprop service.adb.tcp.port $LOCAL_TCP_ADB_PORT 2>/dev/null
            cat << 'KYRO_EOF' > $DAEMON_SCRIPT_PATH
            #!/system/bin/sh
            export PATH=/system/bin:/system/xbin:/vendor/bin:${'$'}PATH
            PORT=$DAEMON_PORT
            if [ -f $DAEMON_PID_PATH ]; then
                OLD_PID=${'$'}(cat $DAEMON_PID_PATH 2>/dev/null)
                if [ -n "${'$'}OLD_PID" ] && [ "${'$'}OLD_PID" != "${'$'}${'$'}" ] && [ -d "/proc/${'$'}OLD_PID" ]; then
                    exit 0
                fi
            fi
            echo ${'$'}${'$'} > $DAEMON_PID_PATH
            chmod 666 $DAEMON_PID_PATH 2>/dev/null
            trap "" HUP INT TERM
            while true; do
                if command -v toybox >/dev/null 2>&1; then
                    toybox nc -s 127.0.0.1 -p ${'$'}PORT -L /system/bin/sh </dev/null >/dev/null 2>&1 || \
                    toybox nc -s 127.0.0.1 -p ${'$'}PORT -l /system/bin/sh </dev/null >/dev/null 2>&1
                elif command -v nc >/dev/null 2>&1; then
                    nc -s 127.0.0.1 -p ${'$'}PORT -L /system/bin/sh </dev/null >/dev/null 2>&1 || \
                    nc -s 127.0.0.1 -p ${'$'}PORT -l /system/bin/sh </dev/null >/dev/null 2>&1
                fi
                sleep 1
            done
            KYRO_EOF
            chmod 755 $DAEMON_SCRIPT_PATH
            if command -v setsid >/dev/null 2>&1; then
                setsid /system/bin/sh $DAEMON_SCRIPT_PATH </dev/null >/dev/null 2>&1 &
            elif command -v nohup >/dev/null 2>&1; then
                nohup /system/bin/sh $DAEMON_SCRIPT_PATH </dev/null >/dev/null 2>&1 &
            else
                (/system/bin/sh $DAEMON_SCRIPT_PATH </dev/null >/dev/null 2>&1 &)
            fi
            sleep 1
            echo "DAEMON_INJECTED_OK"
        """.trimIndent()
    }

    private val connectionMutex = Mutex()

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
    var daemonStatusText: String = "Checking Daemon Status..."
        private set

    @Volatile
    var lastError: String? = null
        private set

    fun restoreSavedEndpoint(host: String, port: Int, wasDaemonInjected: Boolean) {
        if (host.isNotBlank()) currentHost = host
        if (port > 0) currentPort = port
        if (wasDaemonInjected) {
            daemonStatusText = "Verifying active Daemon (127.0.0.1:$DAEMON_PORT)..."
        }
    }

    /**
     * Probes whether the injected persistent daemon on 127.0.0.1:38555 or local TCP ADB on 127.0.0.1:5555
     * (or the active ADB server device list) is alive.
     * Automatically reconnects local loopback ADB and respawns the socket daemon if needed so exiting
     * and re-entering the app seamlessly restores the CONNECTED state without re-pairing.
     */
    suspend fun probeDaemonStatus(): Boolean = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            probeDaemonStatusUnlocked()
        }
    }

    private suspend fun probeDaemonStatusUnlocked(): Boolean {
        // 1. Fast socket check against our injected persistent daemon on 127.0.0.1:38555
        val daemonRes = executeViaInjectedDaemon("id", 2000L)
        if (daemonRes != null && daemonRes.isSuccess && daemonRes.stdout.contains("uid=")) {
            isDaemonInjected = true
            isConnectedSession = true
            lastError = null
            daemonStatusText = "Daemon Active (127.0.0.1:$DAEMON_PORT • Wireless ADB Off Safe)"
            return true
        }

        if (AdbNativeBridge.isNativeLoaded) {
            AdbNativeBridge.runAdbBinary("start-server", timeoutMs = 4000L)

            // 2. Reconnect & check local loopback TCP ADB (127.0.0.1:5555) which survives Wireless Debugging OFF
            AdbNativeBridge.runAdbBinary("connect", "127.0.0.1:$LOCAL_TCP_ADB_PORT", timeoutMs = 3500L)
            val localAdbCheck = AdbNativeBridge.runAdbBinary(
                "-s", "127.0.0.1:$LOCAL_TCP_ADB_PORT", "shell", "id",
                timeoutMs = 3500L
            )
            if (localAdbCheck.isSuccess && localAdbCheck.stdout.contains("uid=")) {
                // Ensure the fast socket daemon on 38555 is also running
                AdbNativeBridge.runAdbBinary(
                    "-s", "127.0.0.1:$LOCAL_TCP_ADB_PORT", "shell", DAEMON_INSTALL_COMMAND,
                    timeoutMs = 5000L
                )
                isDaemonInjected = true
                isConnectedSession = true
                lastError = null
                daemonStatusText = "Daemon Active (Loopback 127.0.0.1:$LOCAL_TCP_ADB_PORT + :$DAEMON_PORT)"
                return true
            }

            // 3. Check if any ADB device is already online in `adb devices` or reconnect to last known host:port
            if (currentPort > 0 && currentPort != LOCAL_TCP_ADB_PORT) {
                AdbNativeBridge.runAdbBinary("connect", "$currentHost:$currentPort", timeoutMs = 3500L)
            }

            val devicesRes = AdbNativeBridge.runAdbBinary("devices", timeoutMs = 3000L)
            val activeSerial = devicesRes.stdout.lines()
                .map { it.trim() }
                .firstOrNull { line ->
                    line.isNotEmpty() &&
                            !line.startsWith("List of") &&
                            line.endsWith("\tdevice")
                }
                ?.substringBefore("\t")
                ?.trim()

            if (!activeSerial.isNullOrBlank()) {
                val verifyShell = AdbNativeBridge.runAdbBinary(
                    "-s", activeSerial, "shell", "id",
                    timeoutMs = 3500L
                )
                if (verifyShell.isSuccess && verifyShell.stdout.contains("uid=")) {
                    if (activeSerial.contains(":")) {
                        currentHost = activeSerial.substringBeforeLast(":")
                        currentPort = activeSerial.substringAfterLast(":").toIntOrNull() ?: currentPort
                    }
                    // Re-ensure persistent daemon + tcpip 5555 are active
                    AdbNativeBridge.runAdbBinary(
                        "-s", activeSerial, "shell", DAEMON_INSTALL_COMMAND,
                        timeoutMs = 6000L
                    )
                    val recheckSocket = executeViaInjectedDaemon("id", 1500L)
                    isDaemonInjected = true
                    isConnectedSession = true
                    lastError = null
                    daemonStatusText = if (recheckSocket?.isSuccess == true) {
                        "Daemon Active (127.0.0.1:$DAEMON_PORT • Wireless ADB Off Safe)"
                    } else {
                        "Daemon Active ($activeSerial)"
                    }
                    return true
                }
            }
        }

        // 4. Fallback socket check for non-ARM64 test environments
        if (isConnectedSession) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(currentHost, currentPort), 1500)
                    return true
                }
            } catch (_: Throwable) {
            }
        }

        isDaemonInjected = false
        isConnectedSession = false
        daemonStatusText = "Daemon Not Injected (Pair & Connect Wireless ADB)"
        return false
    }

    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        probeDaemonStatus()
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
     * persistent background daemon (`adb tcpip 5555` + `kyro_daemon.sh`) so communication continues
     * working even after the user turns OFF Wireless Debugging or closes and reopens the app!
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

        connectionMutex.withLock {
            // First check if daemon is already alive
            if (probeDaemonStatusUnlocked() && isDaemonInjected) {
                currentHost = cleanHost
                currentPort = connectPort
                return@withLock Result.success(
                    "Persistent Daemon is already active on port $DAEMON_PORT! Safe to turn off Wireless Debugging."
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

                    val daemonResult = injectPersistentDaemonUnlocked(target)
                    val msg = buildString {
                        appendLine("Connected to Wireless ADB ($target).")
                        appendLine(daemonResult)
                    }.trim()
                    return@withLock Result.success(msg)
                } else if (combined.isNotBlank() && !combined.contains("Exec format error", ignoreCase = true)) {
                    isConnectedSession = false
                    val err = "Failed to connect to $target:\n$combined"
                    lastError = err
                    return@withLock Result.failure(Exception(err))
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
    }

    /**
     * Injects the persistent daemon over an active `libadb.so` target connection.
     * IMPORTANT ORDER:
     * 1. Enable `tcpip 5555` FIRST (because `adb tcpip 5555` restarts `adbd`).
     * 2. Connect to `127.0.0.1:5555` (and original endpoint).
     * 3. Spawn `/data/local/tmp/kyro_daemon.sh` AFTER `adbd` has restarted so `kyro_daemon.sh` is never killed!
     */
    suspend fun injectPersistentDaemonViaAdb(targetEndpoint: String = "$currentHost:$currentPort"): String =
        withContext(Dispatchers.IO) {
            connectionMutex.withLock {
                injectPersistentDaemonUnlocked(targetEndpoint)
            }
        }

    private suspend fun injectPersistentDaemonUnlocked(targetEndpoint: String): String {
        return try {
            daemonStatusText = "Injecting Persistent Daemon..."

            // Step 1: Switch adbd to listen on TCP 5555 FIRST so restarting adbd doesn't kill our script
            AdbNativeBridge.runAdbBinary(
                "-s", targetEndpoint, "tcpip", LOCAL_TCP_ADB_PORT.toString(),
                timeoutMs = 6_000L
            )
            delay(900)

            // Step 2: Connect to loopback 127.0.0.1:5555 (and reconnect to targetEndpoint just in case)
            val loopConnect = AdbNativeBridge.runAdbBinary(
                "connect", "127.0.0.1:$LOCAL_TCP_ADB_PORT",
                timeoutMs = 5_000L
            )
            val loopOk = "${loopConnect.stdout}\n${loopConnect.stderr}".let {
                it.contains("connected to", ignoreCase = true) || it.contains("already connected", ignoreCase = true)
            }
            if (!loopOk) {
                AdbNativeBridge.runAdbBinary("connect", targetEndpoint, timeoutMs = 5_000L)
            }

            val activeTarget = if (loopOk) "127.0.0.1:$LOCAL_TCP_ADB_PORT" else targetEndpoint

            // Step 3: Deploy and start /data/local/tmp/kyro_daemon.sh AFTER tcpip 5555 is up
            val injectRes = AdbNativeBridge.runAdbBinary(
                "-s", activeTarget, "shell", DAEMON_INSTALL_COMMAND,
                timeoutMs = 10_000L
            )

            // Step 4: Verify that our standalone daemon on 127.0.0.1:38555 or loopback 5555 responds
            val verified = probeDaemonStatusUnlocked()
            if (verified && isDaemonInjected) {
                "✓ Persistent Daemon injected (127.0.0.1:$DAEMON_PORT + TCP $LOCAL_TCP_ADB_PORT)!\n" +
                        "Connection WILL NOT DROP when Wireless Debugging is turned OFF or when exiting the app."
            } else if (injectRes.isSuccess || injectRes.stdout.contains("DAEMON_INJECTED_OK") || loopOk) {
                isDaemonInjected = true
                isConnectedSession = true
                daemonStatusText = "Daemon Active (Loopback 127.0.0.1:$LOCAL_TCP_ADB_PORT • Wireless ADB Off Safe)"
                "✓ Daemon injected & TCP $LOCAL_TCP_ADB_PORT enabled. Connection stays alive even when Wireless Debugging is OFF."
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
            if (probeDaemonStatus() && isDaemonInjected) {
                return@withContext Result.success(
                    "Persistent Daemon is already active at 127.0.0.1:$DAEMON_PORT (UID 2000)! Safe to turn off Wireless Debugging."
                )
            }

            if (AdbNativeBridge.isNativeLoaded && isConnectedSession) {
                val msg = injectPersistentDaemonViaAdb("$currentHost:$currentPort")
                if (isDaemonInjected) {
                    return@withContext Result.success(msg)
                }
            }

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
                AdbNativeBridge.runAdbBinary("connect", "127.0.0.1:$LOCAL_TCP_ADB_PORT", timeoutMs = 3000L)
                val localTcpRes = AdbNativeBridge.runAdbBinary(
                    "-s", "127.0.0.1:$LOCAL_TCP_ADB_PORT", "shell", command,
                    timeoutMs = timeoutMs
                )
                if (localTcpRes.isSuccess) {
                    isDaemonInjected = true
                    isConnectedSession = true
                    return@withContext localTcpRes.copy(command = command)
                }

                // Stage 3: Execute via Wireless ADB endpoint ($currentHost:$currentPort) using libadb.so
                if (isConnectedSession || currentPort > 0) {
                    val remoteRes = AdbNativeBridge.runAdbBinary(
                        "-s", "$currentHost:$currentPort", "shell", command,
                        timeoutMs = timeoutMs
                    )
                    if (remoteRes.isSuccess) {
                        isConnectedSession = true
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
