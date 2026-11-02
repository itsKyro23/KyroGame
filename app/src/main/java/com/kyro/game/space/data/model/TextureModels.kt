package com.kyro.game.space.data.model

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import java.util.Locale

enum class TextureDetailLevel(
    val stepIndex: Int,
    val label: String,
    val lodBias: Float,
    val mipmapDivisor: Int,
    val filterTag: String,
    val description: String
) {
    ORIGINAL(
        stepIndex = 0,
        label = "Original",
        lodBias = 0.0f,
        mipmapDivisor = 1,
        filterTag = "1:1 Native",
        description = "Native full-resolution game textures with standard trilinear mipmaps."
    ),
    LIGHT(
        stepIndex = 1,
        label = "Light",
        lodBias = 0.75f,
        mipmapDivisor = 2,
        filterTag = "1/2 Mipmap",
        description = "Subtle mipmap reduction (+0.75 LOD bias) for lighter GPU memory bandwidth."
    ),
    RETRO(
        stepIndex = 2,
        label = "Retro",
        lodBias = 1.6f,
        mipmapDivisor = 4,
        filterTag = "1/4 Mipmap",
        description = "Medium mipmap shift (+1.6 LOD bias) for classic retro pixel-art surface detail."
    ),
    BLOCKY(
        stepIndex = 3,
        label = "Blocky",
        lodBias = 2.6f,
        mipmapDivisor = 8,
        filterTag = "1/8 Blocky",
        description = "High mipmap reduction (+2.6 LOD bias) turning walls and terrain into clean blocks."
    ),
    EXTREME(
        stepIndex = 4,
        label = "Extreme",
        lodBias = 3.5f,
        mipmapDivisor = 16,
        filterTag = "1/16 Ultra-Low",
        description = "Maximum mipmap compression (+3.5 LOD bias) for ultra-low latency competitive FPS."
    );

    companion object {
        fun fromStep(step: Int): TextureDetailLevel {
            return entries.firstOrNull { it.stepIndex == step.coerceIn(0, 4) } ?: ORIGINAL
        }
    }
}

enum class TextureQuickPreset(
    val label: String,
    val shortLabel: String,
    val defaultDetail: TextureDetailLevel,
    val defaultSharpPixels: Boolean,
    val defaultHardCutoff: Boolean
) {
    SMOOTH(
        label = "Smooth",
        shortLabel = "Smooth",
        defaultDetail = TextureDetailLevel.RETRO,
        defaultSharpPixels = false,
        defaultHardCutoff = false
    ),
    RETRO(
        label = "Retro",
        shortLabel = "Retro",
        defaultDetail = TextureDetailLevel.RETRO,
        defaultSharpPixels = true,
        defaultHardCutoff = false
    ),
    TOTAL_BLOCKY(
        label = "Total Blocky",
        shortLabel = "Blocky",
        defaultDetail = TextureDetailLevel.BLOCKY,
        defaultSharpPixels = true,
        defaultHardCutoff = true
    ),
    CUSTOM(
        label = "Custom",
        shortLabel = "Custom",
        defaultDetail = TextureDetailLevel.RETRO,
        defaultSharpPixels = true,
        defaultHardCutoff = false
    );

    companion object {
        fun resolveMatchingPreset(
            detailLevel: TextureDetailLevel,
            sharpPixels: Boolean,
            hardCutoff: Boolean
        ): TextureQuickPreset {
            return when {
                detailLevel == SMOOTH.defaultDetail &&
                    sharpPixels == SMOOTH.defaultSharpPixels &&
                    hardCutoff == SMOOTH.defaultHardCutoff -> SMOOTH

                detailLevel == RETRO.defaultDetail &&
                    sharpPixels == RETRO.defaultSharpPixels &&
                    hardCutoff == RETRO.defaultHardCutoff -> RETRO

                (detailLevel == TextureDetailLevel.BLOCKY || detailLevel == TextureDetailLevel.EXTREME) &&
                    sharpPixels && hardCutoff -> TOTAL_BLOCKY

                else -> CUSTOM
            }
        }
    }
}

data class TextureConfig(
    val preset: TextureQuickPreset = TextureQuickPreset.RETRO,
    val detailLevel: TextureDetailLevel = TextureDetailLevel.ORIGINAL,
    val sharpPixels: Boolean = false,
    val hardDistanceCutoff: Boolean = false
) {
    val isActive: Boolean
        get() = detailLevel != TextureDetailLevel.ORIGINAL || sharpPixels || hardDistanceCutoff

    val filterModeLabel: String
        get() = when {
            sharpPixels && hardDistanceCutoff -> "GL_NEAREST_MIPMAP_NEAREST"
            sharpPixels -> "GL_NEAREST_MIPMAP_LINEAR"
            hardDistanceCutoff -> "GL_LINEAR_MIPMAP_NEAREST"
            else -> "GL_LINEAR_MIPMAP_LINEAR"
        }

    val summaryBadge: String
        get() = if (!isActive) {
            "Original (Native)"
        } else {
            val edgeTag = if (sharpPixels) "Sharp Pixel" else "Smooth Blur"
            val distTag = if (hardDistanceCutoff) " • Hard Cutoff" else ""
            "${preset.shortLabel} (${detailLevel.label} • $edgeTag$distTag)"
        }

    fun buildShellCommand(packageName: String = ""): String {
        val targetPkg = packageName.trim()
        if (!isActive) {
            val pkgReset = if (targetPkg.isNotEmpty()) {
                "settings delete global gpu_debug_app 2>/dev/null || true; " +
                    "settings delete global angle_debug_package 2>/dev/null || true; "
            } else ""
            return pkgReset +
                "setprop debug.hwui.texture_lod_bias 0.0 2>/dev/null || true; " +
                "setprop debug.egl.texture_lod_bias 0.0 2>/dev/null || true; " +
                "setprop debug.hwui.tex_filter linear 2>/dev/null || true; " +
                "setprop debug.egl.tex_mipmap_mode trilinear 2>/dev/null || true"
        }

        val biasStr = String.format(Locale.US, "%.2f", detailLevel.lodBias)
        val filterStr = if (sharpPixels) "nearest" else "linear"
        val mipmapStr = if (hardDistanceCutoff) "nearest" else "linear"
        val anisoVal = if (sharpPixels || hardDistanceCutoff) "1" else "2"

        val pkgCommands = if (targetPkg.isNotEmpty()) {
            "settings put global gpu_debug_app $targetPkg 2>/dev/null || true; " +
                "settings put global updatable_driver_production_opt_in_apps $targetPkg 2>/dev/null || true; "
        } else {
            ""
        }

        return pkgCommands +
            "setprop debug.hwui.texture_lod_bias $biasStr 2>/dev/null || true; " +
            "setprop debug.egl.texture_lod_bias $biasStr 2>/dev/null || true; " +
            "setprop debug.hwui.tex_filter $filterStr 2>/dev/null || true; " +
            "setprop debug.egl.tex_mipmap_mode $mipmapStr 2>/dev/null || true; " +
            "setprop debug.egl.force_aniso $anisoVal 2>/dev/null || true; " +
            "setprop debug.hwui.renderer opengl 2>/dev/null || true"
    }
}

data class DeviceTextureCompatibility(
    val isSupported: Boolean = true,
    val gpuVendor: String = "Mobile GPU",
    val gpuRenderer: String = "OpenGL ES 3.2 Renderer",
    val glEsVersion: String = "OpenGL ES 3.2",
    val supportsVulkan: Boolean = true,
    val supportsLodBiasControl: Boolean = true,
    val supportsNearestNeighborFilter: Boolean = true,
    val supportsAnisotropicExtension: Boolean = true,
    val supportsPerGameGpuDriver: Boolean = true,
    val compatibilityBadge: String = "Compatible • GPU Texture & LOD Supported",
    val compatibilitySummary: String = "Hardware supports OpenGL ES / Vulkan Mipmap LOD Bias, Nearest-Neighbor (GL_NEAREST) sharp pixel sampling, and per-game GPU driver hooks."
)

data class TextureTunerState(
    val selectedTargetPackage: String = "", // "" = Global System Default, or specific game packageName
    val currentConfig: TextureConfig = TextureConfig(
        preset = TextureQuickPreset.RETRO,
        detailLevel = TextureDetailLevel.RETRO,
        sharpPixels = true,
        hardDistanceCutoff = false
    ),
    val compatibility: DeviceTextureCompatibility = DeviceTextureCompatibility(),
    val isCheckingCompatibility: Boolean = false,
    val isApplying: Boolean = false,
    val statusMessage: String = "Select a texture preset or fine-tune detail level and pixel filtering."
)

object GpuTextureCompatibilityProbe {

    fun inspectDeviceCompatibility(context: Context): DeviceTextureCompatibility {
        var glVersionText = "OpenGL ES 3.0+"
        var majorGl = 3
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val reqGl = am?.deviceConfigurationInfo?.reqGlEsVersion ?: 0x00030000
            majorGl = (reqGl shr 16).coerceAtLeast(2)
            val minorGl = reqGl and 0xFFFF
            glVersionText = "OpenGL ES $majorGl.$minorGl"
        } catch (_: Throwable) {
        }

        val pm = context.packageManager
        val hasVulkan = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL) ||
                    pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }

        var vendor = ""
        var renderer = ""
        var extensions = ""

        // Query offscreen EGL14 context for real hardware GPU Vendor / Renderer / Extensions
        try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display != null && display != EGL14.EGL_NO_DISPLAY) {
                val vers = IntArray(2)
                if (EGL14.eglInitialize(display, vers, 0, vers, 1)) {
                    val configAttribs = intArrayOf(
                        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                        EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                        EGL14.EGL_NONE
                    )
                    val configs = arrayOfNulls<EGLConfig>(1)
                    val numConfigs = IntArray(1)
                    if (EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) &&
                        numConfigs[0] > 0 && configs[0] != null
                    ) {
                        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
                        val eglCtx = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
                        val surfAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
                        val eglSurf = EGL14.eglCreatePbufferSurface(display, configs[0], surfAttribs, 0)
                        if (eglCtx != null && eglSurf != null &&
                            EGL14.eglMakeCurrent(display, eglSurf, eglSurf, eglCtx)
                        ) {
                            vendor = GLES20.glGetString(GLES20.GL_VENDOR).orEmpty().trim()
                            renderer = GLES20.glGetString(GLES20.GL_RENDERER).orEmpty().trim()
                            extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS).orEmpty()
                            EGL14.eglMakeCurrent(
                                display,
                                EGL14.EGL_NO_SURFACE,
                                EGL14.EGL_NO_SURFACE,
                                EGL14.EGL_NO_CONTEXT
                            )
                        }
                        if (eglSurf != null) EGL14.eglDestroySurface(display, eglSurf)
                        if (eglCtx != null) EGL14.eglDestroyContext(display, eglCtx)
                    }
                    EGL14.eglTerminate(display)
                }
            }
        } catch (_: Throwable) {
        }

        val socOrHardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN } ?: Build.HARDWARE
        } else {
            Build.HARDWARE
        }.orEmpty()

        if (vendor.isBlank() || vendor.contains("robolectric", ignoreCase = true)) {
            vendor = when {
                socOrHardware.contains("qcom", ignoreCase = true) ||
                    socOrHardware.contains("sm", ignoreCase = true) ||
                    socOrHardware.contains("sdm", ignoreCase = true) -> "Qualcomm"
                socOrHardware.contains("mt", ignoreCase = true) ||
                    socOrHardware.contains("mali", ignoreCase = true) ||
                    socOrHardware.contains("exynos", ignoreCase = true) -> "ARM"
                socOrHardware.contains("tensor", ignoreCase = true) -> "ARM / Google"
                else -> Build.MANUFACTURER.replaceFirstChar { it.uppercase() }.ifBlank { "Mobile GPU" }
            }
        }

        if (renderer.isBlank() || renderer.contains("robolectric", ignoreCase = true)) {
            renderer = when {
                vendor.contains("Qualcomm", ignoreCase = true) -> "Adreno GPU ($socOrHardware)"
                vendor.contains("ARM", ignoreCase = true) -> "Mali GPU ($socOrHardware)"
                else -> "${socOrHardware.ifBlank { "Hardware" }} ($glVersionText)"
            }
        }

        val supportsLodBias = majorGl >= 2
        val supportsNearest = majorGl >= 2
        val supportsAniso = extensions.isBlank() ||
            extensions.contains("GL_EXT_texture_filter_anisotropic", ignoreCase = true) ||
            majorGl >= 3
        val supportsGameDriver = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        val isSupported = supportsLodBias && supportsNearest
        val badge = if (isSupported) {
            "Compatible • $renderer"
        } else {
            "Limited Support • $renderer"
        }
        val vulkanTag = if (hasVulkan) "Vulkan + $glVersionText" else glVersionText
        val summary = "GPU: $vendor $renderer ($vulkanTag) • Supports Mipmap LOD Bias, GL_NEAREST sharp pixel filtering, and ${if (supportsGameDriver) "Android Game Driver / ANGLE per-app hooks" else "system GLES properties"}."

        return DeviceTextureCompatibility(
            isSupported = isSupported,
            gpuVendor = vendor,
            gpuRenderer = renderer,
            glEsVersion = glVersionText,
            supportsVulkan = hasVulkan,
            supportsLodBiasControl = supportsLodBias,
            supportsNearestNeighborFilter = supportsNearest,
            supportsAnisotropicExtension = supportsAniso,
            supportsPerGameGpuDriver = supportsGameDriver,
            compatibilityBadge = badge,
            compatibilitySummary = summary
        )
    }
}
