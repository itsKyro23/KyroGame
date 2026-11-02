package com.kyro.game.space.data.model

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.io.File
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
        lodBias = 1.0f,
        mipmapDivisor = 2,
        filterTag = "1/2 Mipmap",
        description = "Subtle mipmap reduction (+1.0 LOD bias) for lighter GPU memory bandwidth."
    ),
    RETRO(
        stepIndex = 2,
        label = "Retro",
        lodBias = 2.0f,
        mipmapDivisor = 4,
        filterTag = "1/4 Mipmap",
        description = "Medium mipmap shift (+2.0 LOD bias) for classic retro pixel-art or smooth clay detail."
    ),
    BLOCKY(
        stepIndex = 3,
        label = "Blocky",
        lodBias = 3.0f,
        mipmapDivisor = 8,
        filterTag = "1/8 Blocky",
        description = "High mipmap reduction (+3.0 LOD bias) turning walls and terrain into clean blocks."
    ),
    EXTREME(
        stepIndex = 4,
        label = "Extreme",
        lodBias = 4.0f,
        mipmapDivisor = 16,
        filterTag = "1/16 Ultra-Low",
        description = "Maximum mipmap compression (+4.0 LOD bias) for ultra-low latency competitive FPS."
    );

    companion object {
        fun fromStep(step: Int): TextureDetailLevel {
            return entries.firstOrNull { it.stepIndex == step.coerceIn(0, 4) } ?: ORIGINAL
        }
    }
}

enum class AngleDriverMode(
    val label: String,
    val shortLabel: String,
    val settingValue: String,
    val description: String
) {
    ANGLE(
        label = "ANGLE (Vulkan GLES)",
        shortLabel = "ANGLE",
        settingValue = "angle",
        description = "Routes OpenGL ES through Vulkan ANGLE driver (Required for Zayon-style Lightweight Texture & CODM)."
    ),
    NATIVE(
        label = "Native Game Driver",
        shortLabel = "Native",
        settingValue = "native",
        description = "Uses hardware vendor Game Driver with direct OpenGL ES / SkiaVK mipmap hooks."
    ),
    DEFAULT(
        label = "System Default",
        shortLabel = "Default",
        settingValue = "default",
        description = "Standard Android system graphics driver without ANGLE override."
    );
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
        defaultDetail = TextureDetailLevel.BLOCKY,
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
        defaultDetail = TextureDetailLevel.EXTREME,
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
            if (detailLevel == TextureDetailLevel.ORIGINAL) return CUSTOM
            return when {
                !sharpPixels && !hardCutoff &&
                    (detailLevel == TextureDetailLevel.RETRO || detailLevel == TextureDetailLevel.BLOCKY) -> SMOOTH

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

object AngleDriverPackages {
    val CODM_PACKAGES: List<String> = listOf(
        "com.garena.game.codm",              // Call of Duty: Mobile (Garena)
        "com.activision.callofduty.shooter", // Call of Duty: Mobile (Global)
        "com.vng.codmvn",                    // Call of Duty: Mobile (VNG)
        "com.tencent.tmgp.kr.codm",          // Call of Duty: Mobile (KR)
        "com.tencent.tmgp.cod"               // Call of Duty: Mobile (CN)
    )

    val COMMON_COMPETITIVE_GAMES: List<String> = listOf(
        "com.garena.game.codm",
        "com.activision.callofduty.shooter",
        "com.vng.codmvn",
        "com.tencent.tmgp.kr.codm",
        "com.dts.freefireth",
        "com.dts.freefiremax",
        "com.mobile.legends",
        "com.tencent.ig",
        "com.pubg.imobile",
        "com.pubg.krmobile",
        "com.roblox.client"
    )

    fun isCodmPackage(packageName: String): Boolean {
        val clean = packageName.trim()
        return clean in CODM_PACKAGES || clean.contains("callofduty", ignoreCase = true) || clean.endsWith(".codm")
    }
}

data class TextureConfig(
    val preset: TextureQuickPreset = TextureQuickPreset.RETRO,
    val detailLevel: TextureDetailLevel = TextureDetailLevel.ORIGINAL,
    val sharpPixels: Boolean = false,
    val hardDistanceCutoff: Boolean = false,
    val driverMode: AngleDriverMode = AngleDriverMode.ANGLE,
    val codmAngleSupport: Boolean = true
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
            "Original (Native • ${driverMode.shortLabel})"
        } else {
            val edgeTag = if (sharpPixels) "Sharp Pixel" else "Smooth Clay"
            val distTag = if (hardDistanceCutoff) " • Hard Cutoff" else ""
            "${preset.shortLabel} (${detailLevel.label} • $edgeTag$distTag • ${driverMode.shortLabel})"
        }

    /**
     * Builds the full shell command sequence for applying Lightweight Texture + ANGLE Support Driver
     * (including CODM ANGLE driver registration and GPU LOD bias / filter overrides).
     */
    fun buildShellCommand(
        packageName: String = "",
        additionalTargetPackages: Collection<String> = emptyList(),
        existingAngleMap: Map<String, String> = emptyMap(),
        forceStopTargets: Boolean = true
    ): String {
        val targetPkg = packageName.trim()

        // Build target package list for ANGLE / Game Driver
        val targetSet = LinkedHashSet<String>()
        if (targetPkg.isNotEmpty()) {
            targetSet.add(targetPkg)
        }
        if (codmAngleSupport) {
            targetSet.addAll(AngleDriverPackages.CODM_PACKAGES)
        }
        for (extra in additionalTargetPackages) {
            val clean = extra.trim()
            if (clean.isNotEmpty()) targetSet.add(clean)
        }
        if (targetSet.isEmpty()) {
            targetSet.addAll(AngleDriverPackages.COMMON_COMPETITIVE_GAMES)
        }

        // Merge with existing angle_gl_driver_selection_pkgs so we never corrupt parallel lists
        val mergedAngleMap = LinkedHashMap<String, String>()
        for ((k, v) in existingAngleMap) {
            val cleanK = k.trim()
            val cleanV = v.trim().ifEmpty { "default" }
            if (cleanK.isNotEmpty()) {
                mergedAngleMap[cleanK] = cleanV
            }
        }

        if (!isActive) {
            val resetDriverVal = if (codmAngleSupport && driverMode == AngleDriverMode.ANGLE) {
                "default"
            } else {
                driverMode.settingValue
            }
            for (pkg in targetSet) {
                mergedAngleMap[pkg] = resetDriverVal
            }
            val pkgsCsv = mergedAngleMap.keys.joinToString(",")
            val valsCsv = mergedAngleMap.values.joinToString(",")

            val resetAngleCmd = if (pkgsCsv.isNotEmpty()) {
                "settings put global angle_gl_driver_all_angle 0 2>/dev/null || true; " +
                    "settings put global angle_gl_driver_selection_pkgs $pkgsCsv 2>/dev/null || true; " +
                    "settings put global angle_gl_driver_selection_values $valsCsv 2>/dev/null || true; "
            } else {
                "settings put global angle_gl_driver_all_angle 0 2>/dev/null || true; "
            }

            val forceStopCmd = if (forceStopTargets && targetPkg.isNotEmpty()) {
                "; am force-stop $targetPkg 2>/dev/null || true"
            } else ""

            return resetAngleCmd +
                "settings delete global gpu_debug_app 2>/dev/null || true; " +
                "settings delete global angle_debug_package 2>/dev/null || true; " +
                "settings delete global gpu_texture_lod_bias 2>/dev/null || true; " +
                "settings delete global gpu_texture_filter_mode 2>/dev/null || true; " +
                "setprop debug.hwui.texture_lod_bias 0.0 2>/dev/null || true; " +
                "setprop debug.egl.texture_lod_bias 0.0 2>/dev/null || true; " +
                "setprop debug.angle.texture_lod_bias 0.0 2>/dev/null || true; " +
                "setprop debug.vulkan.texture_lod_bias 0.0 2>/dev/null || true; " +
                "setprop debug.hwui.tex_filter linear 2>/dev/null || true; " +
                "setprop debug.egl.tex_filter linear 2>/dev/null || true; " +
                "setprop debug.angle.tex_filter linear 2>/dev/null || true; " +
                "setprop debug.egl.tex_mipmap_mode trilinear 2>/dev/null || true" +
                forceStopCmd
        }

        val activeDriverValue = driverMode.settingValue
        for (pkg in targetSet) {
            mergedAngleMap[pkg] = activeDriverValue
        }
        // Ensure CODM packages explicitly get "angle" when codmAngleSupport is enabled
        if (codmAngleSupport) {
            for (codmPkg in AngleDriverPackages.CODM_PACKAGES) {
                mergedAngleMap[codmPkg] = "angle"
            }
        }

        val pkgsCsv = mergedAngleMap.keys.joinToString(",")
        val valsCsv = mergedAngleMap.values.joinToString(",")
        val whitelistCsv = (mergedAngleMap.keys + AngleDriverPackages.CODM_PACKAGES).distinct().joinToString(",")

        val effectiveDetail = if (detailLevel == TextureDetailLevel.ORIGINAL) {
            TextureDetailLevel.RETRO
        } else {
            detailLevel
        }
        val biasStr = String.format(Locale.US, "%.2f", effectiveDetail.lodBias)
        val filterStr = if (sharpPixels) "nearest" else "linear"
        val mipmapStr = if (hardDistanceCutoff) "nearest" else "linear"
        val anisoVal = if (sharpPixels || hardDistanceCutoff) "1" else "2"
        val rendererVal = if (driverMode == AngleDriverMode.ANGLE) "skiavk" else "opengl"
        val primaryDebugPkg = targetPkg.ifEmpty { AngleDriverPackages.CODM_PACKAGES.first() }

        val forceStopList = buildList {
            if (targetPkg.isNotEmpty()) add(targetPkg)
            if (codmAngleSupport) addAll(AngleDriverPackages.CODM_PACKAGES)
        }.distinct()

        val forceStopCmd = if (forceStopTargets && forceStopList.isNotEmpty()) {
            forceStopList.joinToString("; ") { "am force-stop $it 2>/dev/null || true" }
        } else {
            "true"
        }

        return "settings put global angle_gl_driver_all_angle 0 2>/dev/null || true; " +
            "settings put global show_angle_in_use_dialog_box 0 2>/dev/null || true; " +
            "settings put global angle_gl_driver_selection_pkgs $pkgsCsv 2>/dev/null || true; " +
            "settings put global angle_gl_driver_selection_values $valsCsv 2>/dev/null || true; " +
            "settings put global angle_whitelist $whitelistCsv 2>/dev/null || true; " +
            "settings put global angle_debug_package $primaryDebugPkg 2>/dev/null || true; " +
            "settings put global gpu_debug_app $primaryDebugPkg 2>/dev/null || true; " +
            "settings put global enable_gpu_debug_layers 1 2>/dev/null || true; " +
            "settings put global updatable_driver_production_opt_in_apps $whitelistCsv 2>/dev/null || true; " +
            "settings put global updatable_driver_prerelease_opt_in_apps $whitelistCsv 2>/dev/null || true; " +
            "settings put global game_driver_opt_in_apps $whitelistCsv 2>/dev/null || true; " +
            "settings put global gpu_texture_lod_bias $biasStr 2>/dev/null || true; " +
            "settings put global gpu_texture_filter_mode $filterModeLabel 2>/dev/null || true; " +
            "settings put global angle_texture_lod_bias $biasStr 2>/dev/null || true; " +
            "settings put global angle_texture_filter $filterStr 2>/dev/null || true; " +
            "setprop debug.hwui.texture_lod_bias $biasStr 2>/dev/null || true; " +
            "setprop debug.egl.texture_lod_bias $biasStr 2>/dev/null || true; " +
            "setprop debug.angle.texture_lod_bias $biasStr 2>/dev/null || true; " +
            "setprop debug.vulkan.texture_lod_bias $biasStr 2>/dev/null || true; " +
            "setprop debug.hwui.tex_filter $filterStr 2>/dev/null || true; " +
            "setprop debug.egl.tex_filter $filterStr 2>/dev/null || true; " +
            "setprop debug.angle.tex_filter $filterStr 2>/dev/null || true; " +
            "setprop debug.egl.tex_mipmap_mode $mipmapStr 2>/dev/null || true; " +
            "setprop debug.angle.tex_mipmap_mode $mipmapStr 2>/dev/null || true; " +
            "setprop debug.egl.force_aniso $anisoVal 2>/dev/null || true; " +
            "setprop debug.egl.force_msaa 0 2>/dev/null || true; " +
            "setprop debug.hwui.renderer $rendererVal 2>/dev/null || true; " +
            forceStopCmd
    }
}

data class DeviceTextureCompatibility(
    val isSupported: Boolean = true,
    val gpuVendor: String = "Mobile GPU",
    val gpuRenderer: String = "OpenGL ES 3.2 Renderer",
    val glEsVersion: String = "OpenGL ES 3.2",
    val supportsVulkan: Boolean = true,
    val supportsAngleDriver: Boolean = true,
    val angleDriverStatus: String = "ANGLE Vulkan Driver Ready",
    val codmAngleActive: Boolean = false,
    val activeAnglePackagesCount: Int = 0,
    val detectedCodmPackages: List<String> = emptyList(),
    val supportsLodBiasControl: Boolean = true,
    val supportsNearestNeighborFilter: Boolean = true,
    val supportsAnisotropicExtension: Boolean = true,
    val supportsPerGameGpuDriver: Boolean = true,
    val compatibilityBadge: String = "Compatible • ANGLE & GPU LOD Supported",
    val compatibilitySummary: String = "Hardware supports ANGLE (OpenGL ES on Vulkan), Mipmap LOD Bias, Sharp Pixel sampling, and CODM driver hooks."
)

data class TextureTunerState(
    val selectedTargetPackage: String = "",
    val currentConfig: TextureConfig = TextureConfig(
        preset = TextureQuickPreset.RETRO,
        detailLevel = TextureDetailLevel.RETRO,
        sharpPixels = true,
        hardDistanceCutoff = false,
        driverMode = AngleDriverMode.ANGLE,
        codmAngleSupport = true
    ),
    val compatibility: DeviceTextureCompatibility = DeviceTextureCompatibility(),
    val isCheckingCompatibility: Boolean = false,
    val isApplying: Boolean = false,
    val isAppliedLive: Boolean = false,
    val statusMessage: String = "Select a texture preset, ANGLE driver mode, or CODM support and tap Save & Apply."
)

object GpuTextureCompatibilityProbe {

    fun readExistingAngleDriverMap(context: Context): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        try {
            val pkgsRaw = Settings.Global.getString(context.contentResolver, "angle_gl_driver_selection_pkgs").orEmpty()
            val valsRaw = Settings.Global.getString(context.contentResolver, "angle_gl_driver_selection_values").orEmpty()
            val pkgs = pkgsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val vals = valsRaw.split(",").map { it.trim() }
            for (i in pkgs.indices) {
                val driverVal = vals.getOrNull(i)?.ifEmpty { "default" } ?: "default"
                result[pkgs[i]] = driverVal
            }
        } catch (_: Throwable) {
        }
        return result
    }

    /**
     * Thread-safe, zero-EGL-context hardware GPU + ANGLE driver probe.
     * Avoids touching EGL14 default display so Compose RenderThread is never interrupted or crashed.
     */
    fun inspectDeviceCompatibility(context: Context): DeviceTextureCompatibility {
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

        // Detect ANGLE binaries / packages / Android 10+ GraphicsEnv ANGLE support
        val hasAnglePackageOrLib = try {
            val anglePkgInstalled = try {
                pm.getPackageInfo("com.android.angle", 0)
                true
            } catch (_: Throwable) {
                false
            }
            val angleLibExists = listOf(
                "/system/lib64/egl/libGLESv2_angle.so",
                "/vendor/lib64/egl/libGLESv2_angle.so",
                "/apex/com.android.angle",
                "/system/app/ANGLE",
                "/system/priv-app/ANGLE"
            ).any { File(it).exists() }
            anglePkgInstalled || angleLibExists || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasVulkan)
        } catch (_: Throwable) {
            true
        }

        // Check installed CODM packages on device
        val installedCodm = AngleDriverPackages.CODM_PACKAGES.filter { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: Throwable) {
                false
            }
        }

        // Check current Global Settings for ANGLE driver registrations
        val angleMap = readExistingAngleDriverMap(context)
        val activeAngleCount = angleMap.count { it.value.equals("angle", ignoreCase = true) }
        val codmInAngle = AngleDriverPackages.CODM_PACKAGES.any { pkg ->
            angleMap[pkg]?.equals("angle", ignoreCase = true) == true
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
        val angleStatusText = when {
            codmInAngle -> "ANGLE Active (CODM + $activeAngleCount pkgs)"
            activeAngleCount > 0 -> "ANGLE Active ($activeAngleCount pkgs)"
            hasAnglePackageOrLib -> "ANGLE Driver Supported"
            else -> "GLES Driver Hook Ready"
        }

        val badge = if (isSupported) {
            "Compatible • $renderer • $angleStatusText"
        } else {
            "Limited Support • $renderer"
        }
        val vulkanTag = if (hasVulkan) "Vulkan + $glVersionText" else glVersionText
        val codmNote = if (codmInAngle) {
            "CODM is registered in ANGLE Support Driver."
        } else {
            "CODM ready for ANGLE Support Driver injection."
        }
        val summary = "GPU: $vendor $renderer ($vulkanTag) • $angleStatusText • $codmNote"

        return DeviceTextureCompatibility(
            isSupported = isSupported,
            gpuVendor = vendor,
            gpuRenderer = renderer,
            glEsVersion = glVersionText,
            supportsVulkan = hasVulkan,
            supportsAngleDriver = hasAnglePackageOrLib,
            angleDriverStatus = angleStatusText,
            codmAngleActive = codmInAngle,
            activeAnglePackagesCount = activeAngleCount,
            detectedCodmPackages = installedCodm,
            supportsLodBiasControl = supportsLodBias,
            supportsNearestNeighborFilter = supportsNearest,
            supportsAnisotropicExtension = supportsAniso,
            supportsPerGameGpuDriver = supportsGameDriver,
            compatibilityBadge = badge,
            compatibilitySummary = summary
        )
    }
}
