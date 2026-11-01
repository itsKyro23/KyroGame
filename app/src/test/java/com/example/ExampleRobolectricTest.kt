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
    val customCfg = com.kyro.game.space.data.model.GameCustomConfig(
      mode = com.kyro.game.space.data.model.GameOptimizationMode.CUSTOM,
      downscaleFactor = 0.65f,
      fpsLimit = maxFps
    )
    repo.saveAndApplyGameConfig("com.example.testgame", customCfg)
    val loaded = repo.getGameConfig("com.example.testgame")
    assertEquals(com.kyro.game.space.data.model.GameOptimizationMode.CUSTOM, loaded.mode)
    assertEquals(0.65f, loaded.downscaleFactor, 0.01f)
    assertEquals(maxFps, loaded.fpsLimit)
    repo.cleanUp()
  }
}
