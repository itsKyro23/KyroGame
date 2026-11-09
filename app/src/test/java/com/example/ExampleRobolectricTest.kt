package com.kyro.game.space

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kyro.game.space.data.db.GameSpaceDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @After
  fun tearDown() {
    GameSpaceDatabase.closeAndReset()
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("NOX - Game Space", appName)
  }

  @Test
  fun `nox energy manager initial energy, ad reward, consumption, and anti-tamper work`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // Clear any previous state
    com.kyro.game.space.data.energy.NoxEnergyManager.resetForTesting()
    context.getSharedPreferences("nox_secure_vault", Context.MODE_PRIVATE).edit().clear().commit()
    com.kyro.game.space.data.energy.NoxEnergyManager.init(context)

    // Initial energy must be 40
    assertEquals(40, com.kyro.game.space.data.energy.NoxEnergyManager.getEnergy(context))

    // Watch ad gives +40 (40 + 40 = 80)
    val afterAd1 = com.kyro.game.space.data.energy.NoxEnergyManager.addEnergyFromAd(context)
    assertEquals(80, afterAd1)

    // Watch ad again capped at 120 (80 + 40 = 120)
    val afterAd2 = com.kyro.game.space.data.energy.NoxEnergyManager.addEnergyFromAd(context)
    assertEquals(120, afterAd2)

    // Consume 40 energy for Lightweight Texture (All Games) (120 - 40 = 80)
    val consumedAll = com.kyro.game.space.data.energy.NoxEnergyManager.consumeEnergy(
        context,
        com.kyro.game.space.data.energy.NoxEnergyManager.COST_LIGHTWEIGHT_TEXTURE_ALL
    )
    assertTrue(consumedAll)
    assertEquals(80, com.kyro.game.space.data.energy.NoxEnergyManager.getEnergy(context))

    // Consume 20 energy for Lightweight Texture (Single Package) (80 - 20 = 60)
    val consumedSingle = com.kyro.game.space.data.energy.NoxEnergyManager.consumeEnergy(
        context,
        com.kyro.game.space.data.energy.NoxEnergyManager.COST_LIGHTWEIGHT_TEXTURE_SINGLE
    )
    assertTrue(consumedSingle)
    assertEquals(60, com.kyro.game.space.data.energy.NoxEnergyManager.getEnergy(context))

    // Consume 15 energy for Resolution Changer (60 - 15 = 45)
    val consumedRes = com.kyro.game.space.data.energy.NoxEnergyManager.consumeEnergy(
        context,
        com.kyro.game.space.data.energy.NoxEnergyManager.COST_RESOLUTION_CHANGER
    )
    assertTrue(consumedRes)
    assertEquals(45, com.kyro.game.space.data.energy.NoxEnergyManager.getEnergy(context))

    // Consume 10 energy for Game Tune (45 - 10 = 35)
    val consumedTune = com.kyro.game.space.data.energy.NoxEnergyManager.consumeEnergy(
        context,
        com.kyro.game.space.data.energy.NoxEnergyManager.COST_GAME_TUNE
    )
    assertTrue(consumedTune)
    assertEquals(35, com.kyro.game.space.data.energy.NoxEnergyManager.getEnergy(context))

    // External modification simulation: modify stored XML value illegally without matching signature
    val prefs = context.getSharedPreferences("nox_secure_vault", Context.MODE_PRIVATE)
    prefs.edit().putInt("nox_k7_val", 9999).commit() // Illegal mod
    com.kyro.game.space.data.energy.NoxEnergyManager.resetForTesting()

    // Next access must detect signature mismatch and penalize to -120!
    val tamperedEnergy = com.kyro.game.space.data.energy.NoxEnergyManager.getEnergy(context)
    assertEquals(-120, tamperedEnergy)
    assertTrue(com.kyro.game.space.data.energy.NoxEnergyManager.isTampered.value)
  }

  @Test
  fun `main activity launches without crashing`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    try {
      assertNotNull(controller.get())
    } finally {
      controller.pause().stop().destroy()
    }
  }

  @Test
  fun `device fps limit detection and game custom config persistence work`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val (maxFps, options) = com.kyro.game.space.ui.components.getDeviceSupportedFpsLimits(context)
    assertTrue(maxFps >= 60)
    assertTrue(options.contains(60))

    val repo = com.kyro.game.space.data.repository.GameSpaceRepository(context)
    try {
      val a16Cfg = com.kyro.game.space.data.model.GameCustomConfig(
        mode = com.kyro.game.space.data.model.GameOptimizationMode.PERFORMANCE,
        downscaleFactor = 0.3f,
        fpsLimit = 120,
        apiTarget = com.kyro.game.space.data.model.GameApiTarget.ANDROID_16_PLUS
      )
      assertEquals(
        "cmd game set --mode 2 --downscale 0.3 --fps 120 com.garena.game.codm",
        a16Cfg.buildShellCommand("com.garena.game.codm")
      )

      val a15Cfg = com.kyro.game.space.data.model.GameCustomConfig(
        mode = com.kyro.game.space.data.model.GameOptimizationMode.CUSTOM,
        downscaleFactor = 0.65f,
        fpsLimit = maxFps,
        apiTarget = com.kyro.game.space.data.model.GameApiTarget.ANDROID_15_BELOW
      )
      assertTrue(
        a15Cfg.buildShellCommand("com.garena.game.codm").contains("device_config put game_overlay com.garena.game.codm")
      )

      repo.saveAndApplyGameConfig("com.example.testgame", a15Cfg)
      val loaded = repo.getGameConfig("com.example.testgame")
      assertEquals(com.kyro.game.space.data.model.GameOptimizationMode.CUSTOM, loaded.mode)
      assertEquals(0.65f, loaded.downscaleFactor, 0.01f)
      assertEquals(maxFps, loaded.fpsLimit)
      assertEquals(com.kyro.game.space.data.model.GameApiTarget.ANDROID_15_BELOW, loaded.apiTarget)

      // Verify Network Optimizer DNS preset selection & Wi-Fi / Mobile Data modes
      val quad9 = com.kyro.game.space.data.model.NetworkOptimizerState.DEFAULT_DNS_PRESETS.first { it.id == "quad9" }
      repo.applyDnsPreset(quad9)
      assertEquals("quad9", repo.networkOptimizerState.value.selectedDnsId)
      assertEquals("9.9.9.9", repo.networkOptimizerState.value.activePingHost)
      assertEquals("dns.quad9.net", repo.networkOptimizerState.value.privateDnsHostname)

      repo.setWifiLowLatencyMode(true)
      assertEquals(true, repo.networkOptimizerState.value.wifiLowLatencyEnabled)

      repo.setMobileDataBoostMode(true)
      assertEquals(true, repo.networkOptimizerState.value.mobileDataBoostEnabled)

      // Verify Resolution Changer min 360p, presets, 10s pending confirmation & rollback
      val resInit = repo.resolutionState.value
      assertEquals(360, resInit.minWidth)
      assertTrue(resInit.maxWidth >= 360)
      assertTrue(resInit.presets.isNotEmpty())

      repo.applyResolutionWithSafetyCountdown(540)
      assertEquals(true, repo.resolutionState.value.isPendingConfirmation)
      assertEquals(540, repo.resolutionState.value.pendingWidth)

      repo.confirmResolutionChange()
      assertEquals(false, repo.resolutionState.value.isPendingConfirmation)
      assertEquals(540, repo.resolutionState.value.currentWidth)

      repo.applyResolutionWithSafetyCountdown(360)
      assertEquals(true, repo.resolutionState.value.isPendingConfirmation)
      repo.revertResolutionToOriginal("Test revert")
      assertEquals(false, repo.resolutionState.value.isPendingConfirmation)
      assertEquals(540, repo.resolutionState.value.currentWidth)

      // Verify Root execution method selection and state
      repo.setExecutionMethod(com.kyro.game.space.data.model.ExecutionMethod.ROOT)
      assertEquals(com.kyro.game.space.data.model.ExecutionMethod.ROOT, repo.connectionState.value.method)
      assertEquals(false, repo.networkOptimizerState.value.isBenchmarking)

      // Verify Lightweight Texture Tuner & Device GPU Compatibility Probe for the selected game
      val compat = repo.refreshTextureCompatibility()
      assertTrue(compat.isSupported)
      assertTrue(compat.glEsVersion.contains("OpenGL ES"))

      val blockyConfig = com.kyro.game.space.data.model.TextureConfig(
        preset = com.kyro.game.space.data.model.TextureQuickPreset.TOTAL_BLOCKY,
        detailLevel = com.kyro.game.space.data.model.TextureDetailLevel.BLOCKY,
        sharpPixels = true,
        hardDistanceCutoff = true,
        driverMode = com.kyro.game.space.data.model.AngleDriverMode.ANGLE
      )
      assertEquals("GL_NEAREST_MIPMAP_NEAREST", blockyConfig.filterModeLabel)
      assertEquals("forceTextureLodOffset3", blockyConfig.angleLodOffsetFeature)
      assertEquals(
        "forceTextureLodOffset3:forceNearestFiltering:forceNearestMipFiltering",
        blockyConfig.angleEnabledFeaturesString
      )
      assertTrue(blockyConfig.angleEnabledFeaturesString.length < 91)
      assertTrue(blockyConfig.angleDisabledFeaturesString.length < 91)

      val smoothConfig = com.kyro.game.space.data.model.TextureConfig(
        preset = com.kyro.game.space.data.model.TextureQuickPreset.SMOOTH,
        detailLevel = com.kyro.game.space.data.model.TextureDetailLevel.BLOCKY,
        sharpPixels = false,
        hardDistanceCutoff = false,
        driverMode = com.kyro.game.space.data.model.AngleDriverMode.ANGLE
      )
      assertEquals("forceTextureLodOffset3", smoothConfig.angleEnabledFeaturesString)
      assertEquals("forceNearestFiltering:forceNearestMipFiltering", smoothConfig.angleDisabledFeaturesString)

      val shellCmd = blockyConfig.buildShellCommand("com.dts.freefiremax")
      assertTrue(shellCmd.contains("settings delete global angle_debug_package"))
      assertTrue(shellCmd.contains("settings delete global gpu_debug_app"))
      assertTrue(shellCmd.contains("angle_gl_driver_all_angle 0"))
      assertTrue(shellCmd.contains("angle_gl_driver_selection_pkgs com.dts.freefiremax"))
      assertTrue(shellCmd.contains("angle_gl_driver_selection_values angle"))
      assertTrue(shellCmd.contains("angle_allowlist com.dts.freefiremax"))
      assertTrue(shellCmd.contains("device_config put game_overlay com.dts.freefiremax mode=2,useAngle=true:mode=3,useAngle=true"))
      assertTrue(shellCmd.contains("setprop debug.angle.feature_overrides_enabled forceTextureLodOffset3:forceNearestFiltering:forceNearestMipFiltering"))
      assertTrue(shellCmd.contains("debug.hwui.texture_lod_bias 3.00"))
      assertTrue(!shellCmd.contains("com.garena.game.codm"))

      repo.saveAndApplyTextureConfig("com.dts.freefiremax", blockyConfig)
      assertEquals("com.dts.freefiremax", repo.textureTunerState.value.selectedTargetPackage)
      assertEquals(com.kyro.game.space.data.model.TextureDetailLevel.BLOCKY, repo.textureTunerState.value.currentConfig.detailLevel)
      assertEquals(true, repo.getGameConfig("com.dts.freefiremax").textureConfig.sharpPixels)
      assertEquals(true, repo.getGameConfig("com.dts.freefiremax").textureConfig.hardDistanceCutoff)

      repo.resetTextureConfigToOriginal("com.dts.freefiremax")
      assertEquals(com.kyro.game.space.data.model.TextureDetailLevel.ORIGINAL, repo.textureTunerState.value.currentConfig.detailLevel)
      assertEquals(false, repo.textureTunerState.value.currentConfig.isActive)

      val exitCleanupCmd = com.kyro.game.space.data.model.TextureConfig.buildSessionExitCleanupCommand()
      assertTrue(exitCleanupCmd.contains("angle_gl_driver_all_angle 0"))
      assertTrue(exitCleanupCmd.contains("debug.angle.feature_overrides_enabled \"\""))

      // Verify Multi-Language support (English default, Bahasa Indonesia, Malaysia, Philippines)
      assertEquals(com.kyro.game.space.ui.theme.AppLanguage.ENGLISH, repo.appLanguage.value)
      for (lang in com.kyro.game.space.ui.theme.AppLanguage.entries) {
        repo.setAppLanguage(lang)
        assertEquals(lang, repo.appLanguage.value)
        val strings = com.kyro.game.space.ui.theme.stringsForLanguage(lang)
        assertEquals(lang, strings.language)
        assertTrue(strings.tabHome.isNotBlank())
        assertTrue(strings.formatAdFallbackWaitMessage(20).contains("20"))
      }
      repo.setAppLanguage(com.kyro.game.space.ui.theme.AppLanguage.ENGLISH)
    } finally {
      repo.cleanUp()
    }
  }
}
