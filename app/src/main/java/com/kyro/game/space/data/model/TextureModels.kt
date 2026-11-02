package com.kyro.game.space.data.model

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
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
    val selectedTargetPackage: String = "",
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

    @Volatile
    private var cachedCompatibility: DeviceTextureCompatibility? = null

    /**
     * Thread-safe, zero-EGL-context hardware GPU probe.
     * Avoids touching EGL14 default display so Compose RenderThread is never interrupted or crashed.
     */
    fun inspectDeviceCompatibility(context: Context): DeviceTextureCompatibility {
        cachedCompatibility?.let { return it }

        var glVersionText = "OpenGL ES 3.2"
        var majorGl = 3
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val reqGl = am?.deviceConfigurationInfo?.reqGlEsVersion ?: 0x00030002
            if (reqGl > 0) {
                majorGl = (reqGl shr 16).coerceAtLeast(2)
                val minorGl = reqGl and 0xFFFF
                glVersionText = "OpenGL ES $majorGl.$minorGl"
            }
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

        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }.orEmpty()
        } else {
            ""
        }
        val socMaker = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MANUFACTURER.takeIf { it.isNotBlank() && it != Build.UNKNOWN }.orEmpty()
        } else {
            ""
        }
        val hwBoard = listOf(socModel, Build.HARDWARE, Build.BOARD)
            .firstOrNull { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
            .orEmpty()

        val combinedTag = "$socMaker $socModel ${Build.HARDWARE} ${Build.BOARD}".lowercase(Locale.US)

        val vendor = when {
            combinedTag.contains("qcom") || combinedTag.contains("qualcomm") ||
                combinedTag.contains("sm") || combinedTag.contains("sdm") ||
                combinedTag.contains("snapdragon") -> "Qualcomm"
            combinedTag.contains("mt") || combinedTag.contains("mediatek") ||
                combinedTag.contains("dimensity") || combinedTag.contains("helio") ||
                combinedTag.contains("mali") -> "ARM / MediaTek"
            combinedTag.contains("exynos") || combinedTag.contains("s5e") -> "Samsung / ARM"
            combinedTag.contains("tensor") || combinedTag.contains("gs") -> "Google Tensor GPU"
            combinedTag.contains("unisoc") || combinedTag.contains("ums") -> "ARM / Unisoc"
            else -> socMaker.ifBlank { Build.MANUFACTURER.replaceFirstChar { it.uppercase() } }.ifBlank { "Mobile GPU" }
        }

        val renderer = when {
            vendor.contains("Qualcomm", ignoreCase = true) -> {
                if (hwBoard.isNotBlank()) "Adreno GPU ($hwBoard)" else "Adreno GPU"
            }
            vendor.contains("ARM", ignoreCase = true) || vendor.contains("MediaTek", ignoreCase = true) -> {
                if (hwBoard.isNotBlank()) "Mali GPU ($hwBoard)" else "Mali GPU"
            }
            else -> {
                if (hwBoard.isNotBlank()) "$hwBoard ($glVersionText)" else glVersionText
            }
        }

        val supportsLodBias = majorGl >= 2
        val supportsNearest = majorGl >= 2
        val supportsAniso = majorGl >= 3
        val supportsGameDriver = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        val isSupported = supportsLodBias && supportsNearest
        val badge = if (isSupported) {
            "Compatible • $renderer"
        } else {
            "Limited Support • $renderer"
        }
        val vulkanTag = if (hasVulkan) "Vulkan + $glVersionText" else glVersionText
        val summary = "GPU: $vendor $renderer ($vulkanTag) • Supports Mipmap LOD Bias, GL_NEAREST sharp pixel filtering, and ${if (supportsGameDriver) "Android Game Driver per-app hooks" else "system GLES properties"}."

        val result = DeviceTextureCompatibility(
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
        cachedCompatibility = result
        return result
    }
}
