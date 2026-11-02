package com.kyro.game.space.data.model

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLU
import android.os.Handler
import android.os.HandlerThread
import java.util.Locale

/**
 * Offscreen EGL14 + GLES20 + SurfaceTexture session controller (`o03` pattern).
 * Runs exclusively on a dedicated background [HandlerThread] so it never touches
 * Jetpack Compose's UI or RenderThread.
 *
 * Maintains an active EGL/GLES texture context while Lightweight Texture is enabled,
 * configures GL texture filter states, probes the active EGL driver/renderer string
 * (e.g., ANGLE vs native GPU driver), and tears down cleanly when Lightweight Texture
 * is reset or when Kyro Game Space exits.
 */
class EglTextureSessionController : SurfaceTexture.OnFrameAvailableListener, Runnable {

    companion object {
        private val EGL_CONFIG_ATTRIBS = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0,
            EGL14.EGL_CONFIG_CAVEAT, EGL14.EGL_NONE,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE
        )
        private const val EGL_EXT_PROTECTED_CONTENT = 0x32C0 // 12992
    }

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private val textureIdHolder = IntArray(1)

    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null
    private var surfaceTexture: SurfaceTexture? = null

    @Volatile
    var isSessionActive: Boolean = false
        private set

    @Volatile
    var activeGlRenderer: String = ""
        private set

    @Volatile
    var activeGlVendor: String = ""
        private set

    @Volatile
    var activeGlVersion: String = ""
        private set

    @Synchronized
    private fun ensureHandler(): Handler {
        var current = handler
        if (current != null && handlerThread?.isAlive == true) {
            return current
        }
        val thread = HandlerThread("KyroEglTextureSession").apply { start() }
        handlerThread = thread
        current = Handler(thread.looper)
        handler = current
        return current
    }

    /**
     * Starts or updates the offscreen EGL14 + SurfaceTexture session on the dedicated GL thread.
     */
    fun activateSession(config: TextureConfig, secureMode: Int = 0) {
        try {
            val targetHandler = ensureHandler()
            targetHandler.post {
                try {
                    releaseInternal()
                    initEglAndSurfaceTexture(secureMode, config)
                } catch (_: Throwable) {
                    isSessionActive = false
                }
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * Stops and releases the offscreen EGL14 + SurfaceTexture session.
     */
    fun releaseSession() {
        try {
            val h = handler
            if (h != null && handlerThread?.isAlive == true) {
                h.post {
                    try {
                        releaseInternal()
                    } catch (_: Throwable) {
                    }
                }
            } else {
                isSessionActive = false
            }
        } catch (_: Throwable) {
            isSessionActive = false
        }
    }

    /**
     * Shuts down both the EGL session and the background HandlerThread when the app closes.
     */
    @Synchronized
    fun destroy() {
        try {
            val h = handler
            val ht = handlerThread
            if (h != null && ht != null && ht.isAlive) {
                h.post {
                    try {
                        releaseInternal()
                    } catch (_: Throwable) {
                    }
                    try {
                        ht.quitSafely()
                    } catch (_: Throwable) {
                    }
                }
            }
        } catch (_: Throwable) {
        } finally {
            handler = null
            handlerThread = null
            isSessionActive = false
        }
    }

    private fun initEglAndSurfaceTexture(mode: Int, config: TextureConfig) {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == null || display == EGL14.EGL_NO_DISPLAY) {
            throw IllegalStateException("eglGetDisplay failed")
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw IllegalStateException("eglInitialize failed")
        }
        eglDisplay = display

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        val chooseOk = EGL14.eglChooseConfig(
            display,
            EGL_CONFIG_ATTRIBS,
            0,
            configs,
            0,
            1,
            numConfigs,
            0
        )
        val validConfig = chooseOk && numConfigs[0] > 0 && configs[0] != null
        if (!validConfig) {
            throw IllegalStateException(
                String.format(
                    Locale.US,
                    "eglChooseConfig failed: success=%b, numConfigs[0]=%d, configs[0]=%s",
                    chooseOk,
                    numConfigs[0],
                    configs[0]
                )
            )
        }

        val chosenConfig = configs[0]!!
        val contextAttribs = if (mode == 0) {
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        } else {
            intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL_EXT_PROTECTED_CONTENT, 1,
                EGL14.EGL_NONE
            )
        }
        val context = EGL14.eglCreateContext(
            display,
            chosenConfig,
            EGL14.EGL_NO_CONTEXT,
            contextAttribs,
            0
        )
        if (context == null || context == EGL14.EGL_NO_CONTEXT) {
            throw IllegalStateException("eglCreateContext failed")
        }
        eglContext = context

        val surface: EGLSurface = if (mode == 1) {
            EGL14.EGL_NO_SURFACE
        } else {
            val pbufferAttribs = if (mode == 2) {
                intArrayOf(
                    EGL14.EGL_WIDTH, 1,
                    EGL14.EGL_HEIGHT, 1,
                    EGL_EXT_PROTECTED_CONTENT, 1,
                    EGL14.EGL_NONE
                )
            } else {
                intArrayOf(
                    EGL14.EGL_WIDTH, 1,
                    EGL14.EGL_HEIGHT, 1,
                    EGL14.EGL_NONE
                )
            }
            val pb = EGL14.eglCreatePbufferSurface(display, chosenConfig, pbufferAttribs, 0)
            if (pb == null || pb == EGL14.EGL_NO_SURFACE) {
                throw IllegalStateException("eglCreatePbufferSurface failed")
            }
            pb
        }

        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            throw IllegalStateException("eglMakeCurrent failed")
        }
        eglSurface = surface

        // Probe live GL strings on the dedicated EGL context thread
        try {
            activeGlVendor = GLES20.glGetString(GLES20.GL_VENDOR).orEmpty()
            activeGlRenderer = GLES20.glGetString(GLES20.GL_RENDERER).orEmpty()
            activeGlVersion = GLES20.glGetString(GLES20.GL_VERSION).orEmpty()
        } catch (_: Throwable) {
        }

        GLES20.glGenTextures(1, textureIdHolder, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIdHolder[0])

        val minFilter = when {
            config.sharpPixels && config.hardDistanceCutoff -> GLES20.GL_NEAREST_MIPMAP_NEAREST
            config.sharpPixels -> GLES20.GL_NEAREST_MIPMAP_LINEAR
            config.hardDistanceCutoff -> GLES20.GL_LINEAR_MIPMAP_NEAREST
            else -> GLES20.GL_LINEAR_MIPMAP_LINEAR
        }
        val magFilter = if (config.sharpPixels) GLES20.GL_NEAREST else GLES20.GL_LINEAR
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, minFilter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, magFilter)

        val errBuilder = StringBuilder()
        var hasGlError = false
        while (true) {
            val glErr = GLES20.glGetError()
            if (glErr == GLES20.GL_NO_ERROR) break
            if (hasGlError) errBuilder.append('\n')
            val errStr = GLU.gluErrorString(glErr)
                ?: "error code: 0x${Integer.toHexString(glErr)}"
            errBuilder.append("glError: ").append(errStr)
            hasGlError = true
        }
        if (hasGlError) {
            throw IllegalStateException(errBuilder.toString())
        }

        val st = SurfaceTexture(textureIdHolder[0])
        st.setDefaultBufferSize(16, 16)
        st.setOnFrameAvailableListener(this, handler)
        surfaceTexture = st
        isSessionActive = true
    }

    private fun releaseInternal() {
        handler?.removeCallbacks(this)
        try {
            surfaceTexture?.let { st ->
                try {
                    st.release()
                } catch (_: Throwable) {
                }
                try {
                    GLES20.glDeleteTextures(1, textureIdHolder, 0)
                } catch (_: Throwable) {
                }
            }
            val dpy = eglDisplay
            if (dpy != null && dpy != EGL14.EGL_NO_DISPLAY) {
                try {
                    EGL14.eglMakeCurrent(
                        dpy,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT
                    )
                } catch (_: Throwable) {
                }
            }
            val surf = eglSurface
            if (dpy != null && surf != null && surf != EGL14.EGL_NO_SURFACE) {
                try {
                    EGL14.eglDestroySurface(dpy, surf)
                } catch (_: Throwable) {
                }
            }
            val ctx = eglContext
            if (dpy != null && ctx != null && ctx != EGL14.EGL_NO_CONTEXT) {
                try {
                    EGL14.eglDestroyContext(dpy, ctx)
                } catch (_: Throwable) {
                }
            }
            try {
                EGL14.eglReleaseThread()
            } catch (_: Throwable) {
            }
            if (dpy != null && dpy != EGL14.EGL_NO_DISPLAY) {
                try {
                    EGL14.eglTerminate(dpy)
                } catch (_: Throwable) {
                }
            }
        } finally {
            eglDisplay = null
            eglContext = null
            eglSurface = null
            surfaceTexture = null
            isSessionActive = false
        }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        handler?.post(this)
    }

    override fun run() {
        val st = surfaceTexture ?: return
        try {
            st.updateTexImage()
        } catch (_: RuntimeException) {
        }
    }
}
