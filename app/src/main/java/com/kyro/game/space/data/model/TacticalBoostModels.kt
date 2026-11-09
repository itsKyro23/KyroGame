package com.kyro.game.space.data.model

enum class AotCompileMode(
    val cliFlag: String,
    val title: String,
    val shortLabel: String,
    val description: String,
    val isReset: Boolean = false
) {
    SPEED_PROFILE(
        cliFlag = "-m speed-profile -f",
        title = "Speed-Profile (Recommended)",
        shortLabel = "Speed-Profile",
        description = "Compiles hot-path game methods & primary classes into native ARM64 machine code for fast loading and smooth frame pacing."
    ),
    FULL_SPEED(
        cliFlag = "-m speed -f",
        title = "Full Speed AOT (Max Zero-JIT)",
        shortLabel = "Full Speed",
        description = "Compiles 100% of all game DEX bytecode into native ARM64 instructions ahead-of-time to eliminate skill-cast & teamfight micro-stutter."
    ),
    RESET(
        cliFlag = "--reset",
        title = "Reset to System Default (Free)",
        shortLabel = "Reset",
        description = "Clears custom AOT compiled OAT/ART profiles for the target game and restores stock Android runtime behavior.",
        isReset = true
    );

    fun buildCompileCommand(packageName: String): String {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isEmpty()) return "true"
        return if (isReset) {
            "cmd package compile --reset $cleanPkg 2>&1"
        } else {
            "cmd package compile $cliFlag $cleanPkg 2>&1"
        }
    }
}

enum class GameAotStatus(
    val label: String,
    val shortBadge: String
) {
    NOT_COMPILED(
        label = "Not Compiled (Stock JIT)",
        shortBadge = "AOT: Not Compiled"
    ),
    SPEED_PROFILE(
        label = "Compiled • Speed-Profile ✓",
        shortBadge = "AOT: Speed-Profile ✓"
    ),
    FULL_SPEED(
        label = "Compiled • Full Speed ✓",
        shortBadge = "AOT: Full Speed ✓"
    ),
    RESET_DEFAULT(
        label = "Reset • System Default",
        shortBadge = "AOT: Default"
    ),
    FAILED(
        label = "Compilation Failed ✗",
        shortBadge = "AOT: Failed ✗"
    );

    companion object {
        fun fromName(name: String?): GameAotStatus {
            if (name.isNullOrBlank()) return NOT_COMPILED
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: NOT_COMPILED
        }
    }
}

data class AotCompilerState(
    val selectedTargetPackage: String = "", // "" means All Added Home Games
    val selectedMode: AotCompileMode = AotCompileMode.SPEED_PROFILE,
    val isCompiling: Boolean = false,
    val progress: Float = 0f,
    val currentPackageBeingCompiled: String = "",
    val lastStatusMessage: String = "Select a target game and compile DEX bytecode into native ARM64 machine code.",
    val compiledLogLines: List<String> = emptyList(),
    val lastCompiledCount: Int = 0,
    val lastCompileDurationMs: Long = 0L,
    val lastCompiledModeLabel: String = "None",
    val packageAotStatuses: Map<String, GameAotStatus> = emptyMap()
) {
    fun statusForPackage(packageName: String): GameAotStatus {
        return packageAotStatuses[packageName.trim()] ?: GameAotStatus.NOT_COMPILED
    }
}

enum class PlayBoostTier(
    val id: String,
    val title: String,
    val levelTag: String,
    val subtitle: String
) {
    ECO_PULSE(
        id = "ECO_PULSE",
        title = "Eco Pulse",
        levelTag = "LOW • SAFE",
        subtitle = "Gentle RAM cache trim & game profile sync without closing background chat or music apps."
    ),
    VELOCITY_CORE(
        id = "VELOCITY_CORE",
        title = "Velocity Core",
        levelTag = "MID • PRO",
        subtitle = "Deep RAM purge + SurfaceFlinger Zero-Lag + Ultra Touch Polling + Anti Pop-Up Notification block."
    ),
    NOX_OVERDRIVE(
        id = "NOX_OVERDRIVE",
        title = "Nox Overdrive",
        levelTag = "HIGH • MAX",
        subtitle = "Maximum boost: Force-stops background apps, purges RAM, and enables Zero-Lag Display & Touch."
    ),
    CUSTOM(
        id = "CUSTOM",
        title = "Custom Tactical",
        levelTag = "CUSTOM",
        subtitle = "User-defined checklist of pre-launch hardware and system optimization stages."
    );

    companion object {
        fun fromId(id: String): PlayBoostTier {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: VELOCITY_CORE
        }
    }
}

data class PlayBoostConfig(
    val tier: PlayBoostTier = PlayBoostTier.VELOCITY_CORE,
    val deepRamPurge: Boolean = true,
    val forceStopBackgroundApps: Boolean = false,
    val surfaceFlingerZeroLag: Boolean = true,
    val ultraTouchResponse: Boolean = true,
    val blockHeadsUpNotifications: Boolean = true
) {
    val activeStagesCount: Int
        get() = listOf(
            deepRamPurge,
            forceStopBackgroundApps,
            surfaceFlingerZeroLag,
            ultraTouchResponse,
            blockHeadsUpNotifications
        ).count { it }

    fun activeStepLabels(): List<String> {
        val list = mutableListOf<String>()
        if (deepRamPurge) list.add("Deep RAM & Cache Purge")
        if (forceStopBackgroundApps) list.add("Force-Stop Background Apps")
        if (surfaceFlingerZeroLag) list.add("SurfaceFlinger Zero-Lag Compositor")
        if (ultraTouchResponse) list.add("Ultra Touch Polling & Sensitivity")
        if (blockHeadsUpNotifications) list.add("In-Game Anti Pop-Up Notification Guard")
        if (list.isEmpty()) {
            list.add("Standard Game Environment Launch")
        }
        return list
    }

    companion object {
        const val TOTAL_STAGES = 5

        fun forTier(tier: PlayBoostTier): PlayBoostConfig {
            return when (tier) {
                PlayBoostTier.ECO_PULSE -> PlayBoostConfig(
                    tier = PlayBoostTier.ECO_PULSE,
                    deepRamPurge = true,
                    forceStopBackgroundApps = false,
                    surfaceFlingerZeroLag = false,
                    ultraTouchResponse = false,
                    blockHeadsUpNotifications = false
                )
                PlayBoostTier.VELOCITY_CORE -> PlayBoostConfig(
                    tier = PlayBoostTier.VELOCITY_CORE,
                    deepRamPurge = true,
                    forceStopBackgroundApps = false,
                    surfaceFlingerZeroLag = true,
                    ultraTouchResponse = true,
                    blockHeadsUpNotifications = true
                )
                PlayBoostTier.NOX_OVERDRIVE -> PlayBoostConfig(
                    tier = PlayBoostTier.NOX_OVERDRIVE,
                    deepRamPurge = true,
                    forceStopBackgroundApps = true,
                    surfaceFlingerZeroLag = true,
                    ultraTouchResponse = true,
                    blockHeadsUpNotifications = true
                )
                PlayBoostTier.CUSTOM -> PlayBoostConfig(
                    tier = PlayBoostTier.CUSTOM
                )
            }
        }

        fun resolveMatchingTier(
            deepRamPurge: Boolean,
            forceStopBackgroundApps: Boolean,
            surfaceFlingerZeroLag: Boolean,
            ultraTouchResponse: Boolean,
            blockHeadsUpNotifications: Boolean
        ): PlayBoostTier {
            val eco = forTier(PlayBoostTier.ECO_PULSE)
            val velocity = forTier(PlayBoostTier.VELOCITY_CORE)
            val overdrive = forTier(PlayBoostTier.NOX_OVERDRIVE)

            return when {
                deepRamPurge == eco.deepRamPurge &&
                    forceStopBackgroundApps == eco.forceStopBackgroundApps &&
                    surfaceFlingerZeroLag == eco.surfaceFlingerZeroLag &&
                    ultraTouchResponse == eco.ultraTouchResponse &&
                    blockHeadsUpNotifications == eco.blockHeadsUpNotifications -> PlayBoostTier.ECO_PULSE

                deepRamPurge == velocity.deepRamPurge &&
                    forceStopBackgroundApps == velocity.forceStopBackgroundApps &&
                    surfaceFlingerZeroLag == velocity.surfaceFlingerZeroLag &&
                    ultraTouchResponse == velocity.ultraTouchResponse &&
                    blockHeadsUpNotifications == velocity.blockHeadsUpNotifications -> PlayBoostTier.VELOCITY_CORE

                deepRamPurge == overdrive.deepRamPurge &&
                    forceStopBackgroundApps == overdrive.forceStopBackgroundApps &&
                    surfaceFlingerZeroLag == overdrive.surfaceFlingerZeroLag &&
                    ultraTouchResponse == overdrive.ultraTouchResponse &&
                    blockHeadsUpNotifications == overdrive.blockHeadsUpNotifications -> PlayBoostTier.NOX_OVERDRIVE

                else -> PlayBoostTier.CUSTOM
            }
        }

        fun buildRestoreSystemDefaultsCommand(): String {
            return "cmd power set-fixed-performance-mode-enabled false 2>/dev/null || true; " +
                "setprop debug.sf.disable_backpressure 0 2>/dev/null || true; " +
                "setprop debug.sf.latch_unsignaled 0 2>/dev/null || true; " +
                "settings put system pointer_speed 0 2>/dev/null || true; " +
                "settings delete global block_untrusted_touches 2>/dev/null || true; " +
                "settings put global heads_up_notifications_enabled 1 2>/dev/null || true; " +
                "settings put global angle_gl_driver_all_angle 0 2>/dev/null || true; " +
                "settings delete global angle_debug_package 2>/dev/null || true; " +
                "settings delete global gpu_debug_app 2>/dev/null || true; " +
                "settings delete global enable_gpu_debug_layers 2>/dev/null || true; " +
                "cmd netpolicy set restrict-background false 2>/dev/null || true"
        }

        fun buildRestorePlayIntegrityAndSystemCommand(): String {
            return buildRestoreSystemDefaultsCommand() + "; " +
                "settings delete global angle_gl_driver_selection_pkgs 2>/dev/null || true; " +
                "settings delete global angle_gl_driver_selection_values 2>/dev/null || true; " +
                "settings delete global angle_whitelist 2>/dev/null || true; " +
                "settings delete global angle_allowlist 2>/dev/null || true; " +
                "settings delete global gpu_texture_lod_bias 2>/dev/null || true; " +
                "settings delete global gpu_texture_filter_mode 2>/dev/null || true; " +
                "settings delete global angle_texture_lod_bias 2>/dev/null || true; " +
                "settings delete global angle_texture_filter 2>/dev/null || true; " +
                "setprop debug.angle.feature_overrides_enabled \"\" 2>/dev/null || true; " +
                "setprop debug.angle.feature_overrides_disabled \"\" 2>/dev/null || true; " +
                "setprop debug.hwui.texture_lod_bias 0.0 2>/dev/null || true; " +
                "setprop debug.egl.texture_lod_bias 0.0 2>/dev/null || true; " +
                "setprop debug.angle.texture_lod_bias 0.0 2>/dev/null || true; " +
                "setprop debug.vulkan.texture_lod_bias 0.0 2>/dev/null || true"
        }
    }
}
