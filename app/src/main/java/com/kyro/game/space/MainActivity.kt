package com.kyro.game.space

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.kyro.game.space.data.repository.GameSpaceRepository
import com.kyro.game.space.ui.GameSpaceApp
import com.kyro.game.space.ui.theme.GameSpaceTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: GameSpaceRepository

    private var isNotificationGranted by mutableStateOf(false)
    private var isUsageAccessGranted by mutableStateOf(false)
    private var isOverlayGranted by mutableStateOf(false)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            isNotificationGranted = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.decorView.isForceDarkAllowed = false
        }

        repository = GameSpaceRepository(applicationContext)

        checkPermissions()

        setContent {
            val themeMode by repository.themeMode.collectAsState()
            val dynamicColor by repository.dynamicColor.collectAsState()

            val isDarkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            GameSpaceTheme(
                darkTheme = isDarkTheme,
                dynamicColor = dynamicColor
            ) {
                GameSpaceApp(
                    repository = repository,
                    isUsageAccessGranted = isUsageAccessGranted,
                    isOverlayGranted = isOverlayGranted,
                    isNotificationGranted = isNotificationGranted,
                    onRequestNotification = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            isNotificationGranted = true
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        repository.updateShizukuStatus()
    }

    override fun onDestroy() {
        repository.cleanUp()
        super.onDestroy()
    }

    private fun checkPermissions() {
        isUsageAccessGranted = repository.hasUsageStatsPermission()
        isOverlayGranted = repository.hasOverlayPermission()
        isNotificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
