package com.kyro.game.space.data.executor

/**
 * JNI abstraction bridge for libadb.so.
 *
 * Attempts to load `libadb.so` via System.loadLibrary("adb") dynamically.
 * If the native library is present on device, native hooks can be invoked.
 * If not bundled, it safely reports the exact loading status and allows
 * fallback to the pure-Kotlin Wireless ADB protocol implementation.
 */
object AdbNativeBridge {

    val isNativeLoaded: Boolean
    val statusMessage: String

    init {
        var loaded = false
        var message = ""
        try {
            System.loadLibrary("adb")
            loaded = true
            message = "libadb.so: Loaded successfully (Native JNI active)"
        } catch (e: UnsatisfiedLinkError) {
            loaded = false
            message = "libadb.so: Not Loaded (Native binary not compiled in APK; socket abstraction active)"
        } catch (e: Throwable) {
            loaded = false
            message = "libadb.so: Load Error (${e.message ?: "Unknown error"})"
        }
        isNativeLoaded = loaded
        statusMessage = message
    }

    // JNI Native signatures (invoked if libadb.so is bundled)
    // external fun nativePair(host: String, port: Int, code: String): Int
    // external fun nativeConnect(host: String, port: Int): Int
    // external fun nativeExecute(command: String): String
}
