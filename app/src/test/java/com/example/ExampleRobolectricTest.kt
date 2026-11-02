package com.kyro.game.space

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Game Space", appName)
  }

  @Test
  fun `main activity launches without crashing`() {
    val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
    org.junit.Assert.assertNotNull(controller.get())
  }

  @Test
  fun `device fps limit detection and game custom config persistence work`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val (maxFps, options) = com.kyro.game.space.ui.components.getDeviceSupportedFpsLimits(context)
    org.junit.Assert.assertTrue(maxFps >= 60)
    org.junit.Assert.assertTrue(options.contains(60))

    val repo = com.kyro.game.space.data.repository.GameSpaceRepository(context)
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
    org.junit.Assert.assertTrue(
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
    org.junit.Assert.assertTrue(resInit.maxWidth >= 360)
    org.junit.Assert.assertTrue(resInit.presets.isNotEmpty())

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

    repo.cleanUp()
  }
}
