package com.kyro.game.space.data.model

import kotlin.math.roundToInt

data class ResolutionPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val width: Int,
    val height: Int,
    val densityDpi: Int,
    val scalePercent: Int,
    val isRecommended: Boolean = false
) {
    val resolutionLabel: String
        get() = "${width} × ${height}"
}

data class ResolutionState(
    val nativeWidth: Int = 1080,
    val nativeHeight: Int = 2400,
    val nativeDensityDpi: Int = 420,
    val currentWidth: Int = 1080,
    val currentHeight: Int = 2400,
    val currentDensityDpi: Int = 420,
    val minWidth: Int = 360,
    val maxWidth: Int = 1080,
    val autoScaleDensity: Boolean = true,
    val presets: List<ResolutionPreset> = emptyList(),
    val isApplying: Boolean = false,
    val isPendingConfirmation: Boolean = false,
    val countdownSeconds: Int = 10,
    val pendingWidth: Int = 1080,
    val pendingHeight: Int = 2400,
    val pendingDensity: Int = 420,
    val statusMessage: String = "Ready. Max resolution is locked to your phone's hardware display, minimum is 360p."
) {
    val aspectRatio: Double
        get() = if (nativeWidth > 0) nativeHeight.toDouble() / nativeWidth.toDouble() else (20.0 / 9.0)

    val isCustomModified: Boolean
        get() = currentWidth != nativeWidth || currentHeight != nativeHeight

    fun computeHeightForWidth(targetWidth: Int): Int {
        val clampedW = targetWidth.coerceIn(minWidth, maxWidth)
        if (clampedW == nativeWidth) return nativeHeight
        val rawH = (clampedW.toDouble() * aspectRatio).roundToInt()
        // Round to even integer for GPU & surfaceflinger alignment
        return if (rawH % 2 == 0) rawH else rawH + 1
    }

    fun computeDensityForWidth(targetWidth: Int): Int {
        val clampedW = targetWidth.coerceIn(minWidth, maxWidth)
        if (clampedW == nativeWidth) return nativeDensityDpi
        val ratio = clampedW.toDouble() / nativeWidth.toDouble()
        return (nativeDensityDpi.toDouble() * ratio).roundToInt().coerceIn(120, nativeDensityDpi)
    }

    companion object {
        const val MIN_SUPPORTED_WIDTH = 360
        const val CONFIRMATION_TIMEOUT_SECONDS = 10

        fun buildDevicePresets(
            nativeWidth: Int,
            nativeHeight: Int,
            nativeDensity: Int
        ): List<ResolutionPreset> {
            val safeNativeW = nativeWidth.coerceAtLeast(MIN_SUPPORTED_WIDTH)
            val safeNativeH = nativeHeight.coerceAtLeast(640)
            val aspect = safeNativeH.toDouble() / safeNativeW.toDouble()

            fun makePreset(
                id: String,
                title: String,
                subtitle: String,
                badge: String,
                targetW: Int,
                recommended: Boolean
            ): ResolutionPreset {
                val w = if (targetW % 2 == 0) targetW else targetW + 1
                val rawH = if (w == safeNativeW) safeNativeH else (w * aspect).roundToInt()
                val h = if (rawH % 2 == 0) rawH else rawH + 1
                val scalePct = ((w.toDouble() / safeNativeW.toDouble()) * 100.0).roundToInt().coerceIn(25, 100)
                val dpi = if (w == safeNativeW) {
                    nativeDensity
                } else {
                    ((nativeDensity.toDouble() * w.toDouble()) / safeNativeW.toDouble())
                        .roundToInt()
                        .coerceIn(120, nativeDensity)
                }
                return ResolutionPreset(
                    id = id,
                    title = title,
                    subtitle = subtitle,
                    badge = badge,
                    width = w,
                    height = h,
                    densityDpi = dpi,
                    scalePercent = scalePct,
                    isRecommended = recommended
                )
            }

            val list = mutableListOf<ResolutionPreset>()

            // 1. Native Max Resolution (100%)
            list.add(
                makePreset(
                    id = "native_max",
                    title = "Native Display (${safeNativeW}p)",
                    subtitle = "Original factory physical resolution with maximum sharpness.",
                    badge = "Max Hardware",
                    targetW = safeNativeW,
                    recommended = false
                )
            )

            // 2. If phone is > 1150p (e.g. 1220p 1.5K or 1440p QHD+), add 1080p FHD+ Recommended
            if (safeNativeW >= 1180) {
                list.add(
                    makePreset(
                        id = "fhd_recommended",
                        title = "FHD+ Gaming (1080p)",
                        subtitle = "Crisp visuals with significantly lower GPU heat than QHD+/1.5K.",
                        badge = "Recommended",
                        targetW = 1080,
                        recommended = true
                    )
                )
            }

            // 3. Balanced 85% / 900p Sweet Spot (Recommended for 1080p phones)
            val sweetSpotW = if (safeNativeW in 1000..1179) {
                900
            } else {
                ((safeNativeW * 0.83).roundToInt() / 2) * 2
            }
            if (sweetSpotW in (MIN_SUPPORTED_WIDTH + 60) until safeNativeW &&
                list.none { kotlin.math.abs(it.width - sweetSpotW) < 60 }
            ) {
                list.add(
                    makePreset(
                        id = "balanced_recommended",
                        title = "Balanced Sweet Spot (${sweetSpotW}p)",
                        subtitle = "Best balance between sharp text/UI and stable high FPS.",
                        badge = "Recommended",
                        targetW = sweetSpotW,
                        recommended = true
                    )
                )
            }

            // 4. HD+ 720p Competitive Preset (Recommended for high FPS gaming)
            if (safeNativeW > 760) {
                list.add(
                    makePreset(
                        id = "hd_720p",
                        title = "HD+ High FPS (720p)",
                        subtitle = "Reduces GPU pixel load by ~55% for sustained 90/120 FPS gaming.",
                        badge = "Recommended FPS",
                        targetW = 720,
                        recommended = true
                    )
                )
            }

            // 5. qHD 540p Battery & Thermal Saver
            if (safeNativeW > 580) {
                list.add(
                    makePreset(
                        id = "qhd_540p",
                        title = "qHD Ultra Smooth (540p)",
                        subtitle = "Heavy GPU downscale for demanding 3D titles and thermal control.",
                        badge = "Low Heat",
                        targetW = 540,
                        recommended = false
                    )
                )
            }

            // 6. Minimum 360p Extreme Boost
            if (safeNativeW > MIN_SUPPORTED_WIDTH) {
                list.add(
                    makePreset(
                        id = "min_360p",
                        title = "Minimum Limit (360p)",
                        subtitle = "Lowest supported width (360px) for maximum GPU bottleneck relief.",
                        badge = "Min 360p",
                        targetW = MIN_SUPPORTED_WIDTH,
                        recommended = false
                    )
                )
            }

            return list
        }
    }
}
