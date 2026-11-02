package com.kyro.game.space.data.model

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
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
        description = "Routes OpenGL ES through Android's ANGLE-on-Vulkan driver for the selected game."
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

data class TextureConfig(
    val preset: TextureQuickPreset = TextureQuickPreset.RETRO,
    val detailLevel: TextureDetailLevel = TextureDetailLevel.ORIGINAL,
    val sharpPixels: Boolean = false,
    val hardDistanceCutoff: Boolean = false,
    val driverMode: AngleDriverMode = AngleDriverMode.ANGLE
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
            val edgeTag = if (sharpPixels) "Sharp Pixel" else "Smooth Clay"
            val distTag = if (hardDistanceCutoff) " • Hard Cutoff" else ""
            "${preset.shortLabel} (${detailLevel.label} • $edgeTag$distTag • ANGLE)"
        }

    /**
     * Returns the exact Google ANGLE Vulkan (`vk_features.json`) feature flag name
     * for the active texture mipmap LOD offset level (1..4).
     */
    val angleLodOffsetFeature: String
        get() {
            val effective = if (detailLevel == TextureDetailLevel.ORIGINAL && isActive) {
                TextureDetailLevel.RETRO
            } else {
                detailLevel
            }
            return when (effective) {
                TextureDetailLevel.ORIGINAL -> ""
                TextureDetailLevel.LIGHT -> "forceTextureLodOffset1"
                TextureDetailLevel.RETRO -> "forceTextureLodOffset2"
                TextureDetailLevel.BLOCKY -> "forceTextureLodOffset3"
                TextureDetailLevel.EXTREME -> "forceTextureLodOffset4"
            }
        }

    /**
     * Colon-separated `debug.angle.feature_overrides_enabled` value (always <= 69 chars,
     * safely below Android `setprop` PROP_VALUE_MAX = 91 bytes).
     */
    val angleEnabledFeaturesString: String
        get() = if (!isActive) {
            ""
        } else {
            buildList {
                val lodFeature = angleLodOffsetFeature
                if (lodFeature.isNotEmpty()) {
                    add(lodFeature)
                }
                if (sharpPixels) {
                    add("forceNearestFiltering")
                }
                if (hardDistanceCutoff) {
                    add("forceNearestMipFiltering")
                }
            }.joinToString(":")
        }

    /**
     * Colon-separated `debug.angle.feature_overrides_disabled` value (always <= 46 chars,
     * safely below Android `setprop` PROP_VALUE_MAX = 91 bytes).
     */
    val angleDisabledFeaturesString: String
        get() = if (!isActive) {
            ""
        } else {
            buildList {
                if (!sharpPixels) {
                    add("forceNearestFiltering")
                }
                if (!hardDistanceCutoff) {
                    add("forceNearestMipFiltering")
                }
            }.joinToString(":")
        }

    /**
     * Lightweight runtime keeper command executed periodically while Kyro Game Space is running
     * and right before launching a game so OEM game boosters (e.g. HyperOS Joyose / MediaTek GED)
     * do not clear the active ANGLE Vulkan texture overrides.
     */
    fun buildRuntimeKeeperCommand(packageName: String = ""): String {
        if (!isActive) return "true"
        val cleanPkg = packageName.trim()
        val enabledStr = angleEnabledFeaturesString
        val disabledStr = angleDisabledFeaturesString
        val allAngleFlag = if (driverMode == AngleDriverMode.ANGLE) "1" else "0"
        val gameOverlayCmd = if (cleanPkg.isNotEmpty() && driverMode == AngleDriverMode.ANGLE) {
            "device_config put game_overlay $cleanPkg mode=2,useAngle=true:mode=3,useAngle=true 2>/dev/null || true; " +
                "cmd game mode 2 $cleanPkg 2>/dev/null || true; "
        } else ""

        return "settings delete global angle_debug_package 2>/dev/null || true; " +
            "settings put global show_angle_in_use_dialog_box 0 2>/dev/null || true; " +
            "settings put global angle_gl_driver_all_angle $allAngleFlag 2>/dev/null || true; " +
            gameOverlayCmd +
            (if (enabledStr.isNotEmpty()) {
                "setprop debug.angle.feature_overrides_enabled $enabledStr 2>/dev/null || true; "
            } else {
                "setprop debug.angle.feature_overrides_enabled \"\" 2>/dev/null || true; "
            }) +
            (if (disabledStr.isNotEmpty()) {
                "setprop debug.angle.feature_overrides_disabled $disabledStr 2>/dev/null || true"
            } else {
                "setprop debug.angle.feature_overrides_disabled \"\" 2>/dev/null || true"
            })
    }

    /**
     * Builds the full shell command sequence for applying Lightweight Texture + ANGLE / GPU Driver
     * for the selected target game (or added Home games when Global is selected).
     *
     * Key Android GraphicsEnvironment & libANGLE mechanisms:
     * 1. `angle_debug_package` is deleted so Android never looks for an external debug ANGLE APK.
     * 2. `angle_gl_driver_all_angle 1` (while Kyro Game Space is active in ANGLE mode) +
     *    `angle_gl_driver_selection_pkgs` / `angle_gl_driver_selection_values` +
     *    `angle_whitelist` / `angle_allowlist` + `device_config put game_overlay <pkg> mode=2,useAngle=true:mode=3,useAngle=true`
     *    ensure ANGLE (`libGLESv2_angle.so`) is loaded for the game across AOSP and OEM ROMs.
     * 3. Real `libANGLE` Vulkan feature overrides (`forceTextureLodOffset1..4`,
     *    `forceNearestFiltering`, `forceNearestMipFiltering`) are applied via `debug.angle.feature_overrides_enabled`
     *    and `debug.angle.feature_overrides_disabled` (kept strictly under the 91-byte `setprop` limit).
     */
    fun buildShellCommand(
        packageName: String = "",
        additionalTargetPackages: Collection<String> = emptyList(),
        existingAngleMap: Map<String, String> = emptyMap(),
        forceStopTargets: Boolean = true
    ): String {
        val targetPkg = packageName.trim()

        // Target strictly the selected game, or all added Home games if Global is selected
        val targetSet = LinkedHashSet<String>()
        if (targetPkg.isNotEmpty()) {
            targetSet.add(targetPkg)
        } else {
            for (extra in additionalTargetPackages) {
                val clean = extra.trim()
                if (clean.isNotEmpty()) targetSet.add(clean)
            }
        }

        // Merge with existing valid angle_gl_driver_selection_pkgs so parallel lists stay 1-to-1
        val mergedAngleMap = LinkedHashMap<String, String>()
        for ((k, v) in existingAngleMap) {
            val cleanK = k.trim()
            val cleanV = v.trim().ifEmpty { "default" }
            if (cleanK.isNotEmpty()) {
                mergedAngleMap[cleanK] = cleanV
            }
        }

        if (!isActive) {
            for (pkg in targetSet) {
                mergedAngleMap[pkg] = "default"
            }
            val pkgsCsv = mergedAngleMap.keys.joinToString(",")
            val valsCsv = mergedAngleMap.values.joinToString(",")

            val resetAngleCmd = if (pkgsCsv.isNotEmpty()) {
                "settings put global angle_gl_driver_all_angle 0 2>/dev/null || true; " +
                    "settings put global angle_gl_driver_selection_pkgs $pkgsCsv 2>/dev/null || true; " +
                    "settings put global angle_gl_driver_selection_values $valsCsv 2>/dev/null || true; "
            } else {
                "settings put global angle_gl_driver_all_angle 0 2>/dev/null || true; " +
                    "settings delete global angle_gl_driver_selection_pkgs 2>/dev/null || true; " +
                    "settings delete global angle_gl_driver_selection_values 2>/dev/null || true; "
            }

            val resetOverlayCmd = if (targetSet.isNotEmpty()) {
                targetSet.joinToString("; ") {
                    "device_config delete game_overlay $it 2>/dev/null || true; cmd game mode 1 $it 2>/dev/null || true"
                } + "; "
            } else ""

            val forceStopCmd = if (forceStopTargets && targetSet.isNotEmpty()) {
                "; " + targetSet.joinToString("; ") { "am force-stop $it 2>/dev/null || true" }
            } else ""

            return resetAngleCmd +
                resetOverlayCmd +
                "settings delete global angle_debug_package 2>/dev/null || true; " +
                "settings delete global gpu_debug_app 2>/dev/null || true; " +
                "settings delete global enable_gpu_debug_layers 2>/dev/null || true; " +
                "settings delete global gpu_texture_lod_bias 2>/dev/null || true; " +
                "settings delete global gpu_texture_filter_mode 2>/dev/null || true; " +
                "settings delete global angle_texture_lod_bias 2>/dev/null || true; " +
                "settings delete global angle_texture_filter 2>/dev/null || true; " +
                "setprop debug.angle.feature_overrides_enabled \"\" 2>/dev/null || true; " +
                "setprop debug.angle.feature_overrides_disabled \"\" 2>/dev/null || true; " +
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

        val pkgsCsv = mergedAngleMap.keys.joinToString(",")
        val valsCsv = mergedAngleMap.values.joinToString(",")
        val allowlistCsv = (mergedAngleMap.keys + targetSet).distinct().joinToString(",")

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

        // Real libANGLE Vulkan feature overrides (strictly <= 69 chars so setprop never fails)
        val angleEnabledFeatures = angleEnabledFeaturesString
        val angleDisabledFeatures = angleDisabledFeaturesString

        val allAngleFlag = if (driverMode == AngleDriverMode.ANGLE) "1" else "0"
        val angleSelectionCmd = if (pkgsCsv.isNotEmpty()) {
            "settings put global angle_gl_driver_all_angle $allAngleFlag 2>/dev/null || true; " +
                "settings put global angle_gl_driver_selection_pkgs $pkgsCsv 2>/dev/null || true; " +
                "settings put global angle_gl_driver_selection_values $valsCsv 2>/dev/null || true; " +
                "settings put global angle_whitelist $allowlistCsv 2>/dev/null || true; " +
                "settings put global angle_allowlist $allowlistCsv 2>/dev/null || true; "
        } else {
            "settings put global angle_gl_driver_all_angle $allAngleFlag 2>/dev/null || true; "
        }

        // Configure Android GameManagerService game_overlay with useAngle=true for target packages
        val gameOverlayAngleCmd = if (driverMode == AngleDriverMode.ANGLE && targetSet.isNotEmpty()) {
            targetSet.joinToString("; ") { pkg ->
                "device_config put game_overlay $pkg mode=2,useAngle=true:mode=3,useAngle=true 2>/dev/null || true; " +
                    "cmd game mode 2 $pkg 2>/dev/null || true"
            } + "; "
        } else ""

        // Prevent vendor Game Driver from overriding ANGLE when ANGLE mode is selected
        val vendorDriverCmd = when (driverMode) {
            AngleDriverMode.ANGLE -> {
                "settings delete global updatable_driver_production_opt_in_apps 2>/dev/null || true; " +
                    "settings delete global updatable_driver_prerelease_opt_in_apps 2>/dev/null || true; " +
                    "settings delete global game_driver_opt_in_apps 2>/dev/null || true; "
            }
            AngleDriverMode.NATIVE -> {
                if (allowlistCsv.isNotEmpty()) {
                    "settings put global updatable_driver_production_opt_in_apps $allowlistCsv 2>/dev/null || true; " +
                        "settings put global game_driver_opt_in_apps $allowlistCsv 2>/dev/null || true; "
                } else ""
            }
            AngleDriverMode.DEFAULT -> {
                "settings delete global updatable_driver_production_opt_in_apps 2>/dev/null || true; " +
                    "settings delete global game_driver_opt_in_apps 2>/dev/null || true; "
            }
        }

        val gpuDebugAppCmd = if (targetPkg.isNotEmpty()) {
            "settings put global gpu_debug_app $targetPkg 2>/dev/null || true; "
        } else {
            "settings delete global gpu_debug_app 2>/dev/null || true; "
        }

        val forceStopCmd = if (forceStopTargets && targetSet.isNotEmpty()) {
            targetSet.joinToString("; ") { "am force-stop $it 2>/dev/null || true" }
        } else {
            "true"
        }

        return "settings delete global angle_debug_package 2>/dev/null || true; " +
            "settings put global show_angle_in_use_dialog_box 0 2>/dev/null || true; " +
            angleSelectionCmd +
            gameOverlayAngleCmd +
            vendorDriverCmd +
            gpuDebugAppCmd +
            "settings put global gpu_texture_lod_bias $biasStr 2>/dev/null || true; " +
            "settings put global gpu_texture_filter_mode $filterModeLabel 2>/dev/null || true; " +
            "settings put global angle_texture_lod_bias $biasStr 2>/dev/null || true; " +
            "settings put global angle_texture_filter $filterStr 2>/dev/null || true; " +
            "setprop debug.angle.feature_overrides_enabled \"\" 2>/dev/null || true; " +
            "setprop debug.angle.feature_overrides_disabled \"\" 2>/dev/null || true; " +
            (if (angleEnabledFeatures.isNotEmpty()) {
                "setprop debug.angle.feature_overrides_enabled $angleEnabledFeatures 2>/dev/null || true; "
            } else "") +
            (if (angleDisabledFeatures.isNotEmpty()) {
                "setprop debug.angle.feature_overrides_disabled $angleDisabledFeatures 2>/dev/null || true; "
            } else "") +
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

    companion object {
        /**
         * Cleans up the global ANGLE / feature override session when Kyro Game Space is closed,
         * so reopening a game after closing Kyro Game Space returns graphics to normal.
         */
        fun buildSessionExitCleanupCommand(): String {
            return "settings put global angle_gl_driver_all_angle 0 2>/dev/null || true; " +
                "settings delete global angle_debug_package 2>/dev/null || true; " +
                "setprop debug.angle.feature_overrides_enabled \"\" 2>/dev/null || true; " +
                "setprop debug.angle.feature_overrides_disabled \"\" 2>/dev/null || true"
        }
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
    val targetGameInAngle: Boolean = false,
    val activeAnglePackagesCount: Int = 0,
    val supportsLodBiasControl: Boolean = true,
    val supportsNearestNeighborFilter: Boolean = true,
    val supportsAnisotropicExtension: Boolean = true,
    val supportsPerGameGpuDriver: Boolean = true,
    val compatibilityBadge: String = "Compatible • ANGLE & GPU LOD Supported",
    val compatibilitySummary: String = "Hardware supports ANGLE (OpenGL ES on Vulkan), Mipmap LOD Bias, and Sharp Pixel sampling for the selected game."
)

data class TextureTunerState(
    val selectedTargetPackage: String = "",
    val currentConfig: TextureConfig = TextureConfig(
        preset = TextureQuickPreset.RETRO,
        detailLevel = TextureDetailLevel.RETRO,
        sharpPixels = true,
        hardDistanceCutoff = false,
        driverMode = AngleDriverMode.ANGLE
    ),
    val compatibility: DeviceTextureCompatibility = DeviceTextureCompatibility(),
    val isCheckingCompatibility: Boolean = false,
    val isApplying: Boolean = false,
    val isAppliedLive: Boolean = false,
    val statusMessage: String = "Select a target game, choose a texture preset or customize detail, and tap Save & Apply (ANGLE Driver auto-enabled)."
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
     * Thread-safe hardware GPU + ANGLE driver probe.
     */
    fun inspectDeviceCompatibility(
        context: Context,
        selectedTargetPackage: String = "",
        angleMapOverride: Map<String, String>? = null,
        eglRendererOverride: String = ""
    ): DeviceTextureCompatibility {
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
            val angleIntentResolves = try {
                pm.queryIntentActivities(Intent("android.app.action.ANGLE_FOR_ANDROID"), 0).isNotEmpty()
            } catch (_: Throwable) {
                false
            }
            val anglePkgInstalled = listOf("com.android.angle", "com.google.android.angle", "org.chromium.angle").any { pkg ->
                try {
                    pm.getPackageInfo(pkg, 0)
                    true
                } catch (_: Throwable) {
                    false
                }
            }
            val angleLibExists = listOf(
                "/system/lib64/egl/libGLESv2_angle.so",
                "/system/lib/egl/libGLESv2_angle.so",
                "/vendor/lib64/egl/libGLESv2_angle.so",
                "/apex/com.android.angle",
                "/system/app/ANGLE",
                "/system/priv-app/ANGLE"
            ).any { File(it).exists() }
            angleIntentResolves || anglePkgInstalled || angleLibExists || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasVulkan)
        } catch (_: Throwable) {
            true
        }

        val angleMap = angleMapOverride ?: readExistingAngleDriverMap(context)
        val activeAngleCount = angleMap.count { it.value.equals("angle", ignoreCase = true) }
        val cleanTarget = selectedTargetPackage.trim()
        val targetInAngle = if (cleanTarget.isNotEmpty()) {
            angleMap[cleanTarget]?.equals("angle", ignoreCase = true) == true
        } else {
            activeAngleCount > 0
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
            eglRendererOverride.isNotBlank() -> eglRendererOverride
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
            cleanTarget.isNotEmpty() && targetInAngle -> "ANGLE Active (Target Game)"
            activeAngleCount > 0 -> "ANGLE Active ($activeAngleCount games)"
            hasAnglePackageOrLib -> "ANGLE Driver Supported"
            else -> "GLES Driver Hook Ready"
        }

        val badge = if (isSupported) {
            "Compatible • $renderer • $angleStatusText"
        } else {
            "Limited Support • $renderer"
        }
        val vulkanTag = if (hasVulkan) "Vulkan + $glVersionText" else glVersionText
        val targetNote = when {
            cleanTarget.isNotEmpty() && targetInAngle -> "Target ($cleanTarget) is active in ANGLE Support Driver."
            cleanTarget.isNotEmpty() -> "Target ($cleanTarget) ready for ANGLE & Lightweight Texture injection."
            activeAngleCount > 0 -> "$activeAngleCount game(s) active in ANGLE Support Driver."
            else -> "Select a game from your Game Space list to apply ANGLE & Lightweight Texture."
        }
        val summary = "GPU: $vendor $renderer ($vulkanTag) • $angleStatusText • $targetNote"

        return DeviceTextureCompatibility(
            isSupported = isSupported,
            gpuVendor = vendor,
            gpuRenderer = renderer,
            glEsVersion = glVersionText,
            supportsVulkan = hasVulkan,
            supportsAngleDriver = hasAnglePackageOrLib,
            angleDriverStatus = angleStatusText,
            targetGameInAngle = targetInAngle,
            activeAnglePackagesCount = activeAngleCount,
            supportsLodBiasControl = supportsLodBias,
            supportsNearestNeighborFilter = supportsNearest,
            supportsAnisotropicExtension = supportsAniso,
            supportsPerGameGpuDriver = supportsGameDriver,
            compatibilityBadge = badge,
            compatibilitySummary = summary
        )
    }
}
