package com.kyro.game.space.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewManagerFactory
import com.kyro.game.space.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class AppUpdateState {
    object Idle : AppUpdateState()
    object Checking : AppUpdateState()
    data class UpToDate(val versionName: String, val versionCode: Int) : AppUpdateState()
    data class Available(
        val appUpdateInfo: AppUpdateInfo,
        val availableVersionCode: Int,
        val isImmediateAllowed: Boolean,
        val isFlexibleAllowed: Boolean
    ) : AppUpdateState()
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : AppUpdateState()
    object Downloaded : AppUpdateState()
    data class Error(val message: String) : AppUpdateState()
}

object GooglePlayManager {

    private var appUpdateManager: AppUpdateManager? = null
    private val _updateState = MutableStateFlow<AppUpdateState>(
        AppUpdateState.UpToDate(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    )
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    private val installListener = InstallStateUpdatedListener { state ->
        try {
            when (state.installStatus()) {
                InstallStatus.DOWNLOADING -> {
                    val bytes = state.bytesDownloaded()
                    val total = state.totalBytesToDownload()
                    _updateState.value = AppUpdateState.Downloading(bytes, total)
                }
                InstallStatus.DOWNLOADED -> {
                    _updateState.value = AppUpdateState.Downloaded
                }
                InstallStatus.FAILED -> {
                    _updateState.value = AppUpdateState.UpToDate(
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE
                    )
                }
                InstallStatus.CANCELED -> {
                    _updateState.value = AppUpdateState.UpToDate(
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE
                    )
                }
                else -> Unit
            }
        } catch (_: Throwable) {
        }
    }

    fun init(context: Context) {
        try {
            if (appUpdateManager == null) {
                val manager = AppUpdateManagerFactory.create(context.applicationContext)
                appUpdateManager = manager
                manager.registerListener(installListener)
            }
        } catch (_: Throwable) {
            _updateState.value = AppUpdateState.UpToDate(
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE
            )
        }
    }

    /**
     * Checks Google Play for available in-app updates.
     * Gracefully falls back to UpToDate on sideloaded builds or devices without Play Store ownership.
     */
    fun checkForUpdate(context: Context, onResult: ((AppUpdateState) -> Unit)? = null) {
        try {
            val manager = appUpdateManager ?: AppUpdateManagerFactory.create(context.applicationContext).also {
                appUpdateManager = it
                it.registerListener(installListener)
            }

            _updateState.value = AppUpdateState.Checking
            onResult?.invoke(AppUpdateState.Checking)

            manager.appUpdateInfo.addOnSuccessListener { info ->
                try {
                    val availability = info.updateAvailability()
                    val state = when (availability) {
                        UpdateAvailability.UPDATE_AVAILABLE -> {
                            val isImmediate = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                            val isFlexible = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                            AppUpdateState.Available(
                                appUpdateInfo = info,
                                availableVersionCode = info.availableVersionCode(),
                                isImmediateAllowed = isImmediate,
                                isFlexibleAllowed = isFlexible
                            )
                        }
                        UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                                AppUpdateState.Downloaded
                            } else {
                                AppUpdateState.Downloading(
                                    info.bytesDownloaded(),
                                    info.totalBytesToDownload()
                                )
                            }
                        }
                        else -> {
                            AppUpdateState.UpToDate(
                                BuildConfig.VERSION_NAME,
                                BuildConfig.VERSION_CODE
                            )
                        }
                    }
                    _updateState.value = state
                    onResult?.invoke(state)
                } catch (_: Throwable) {
                    val fallback = AppUpdateState.UpToDate(
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE
                    )
                    _updateState.value = fallback
                    onResult?.invoke(fallback)
                }
            }.addOnFailureListener {
                // Sideloaded APKs or test environments report ERROR_APP_NOT_OWNED (-10) or
                // ERROR_PLAY_STORE_NOT_FOUND (-6). Treat as UpToDate so no error is shown in UI.
                val upToDateState = AppUpdateState.UpToDate(
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                )
                _updateState.value = upToDateState
                onResult?.invoke(upToDateState)
            }
        } catch (_: Throwable) {
            val fallback = AppUpdateState.UpToDate(
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE
            )
            _updateState.value = fallback
            onResult?.invoke(fallback)
        }
    }

    /**
     * Starts the in-app update flow (Flexible or Immediate) via ActivityResultLauncher.
     */
    fun startUpdate(
        appUpdateInfo: AppUpdateInfo,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        preferImmediate: Boolean = false
    ): Boolean {
        val manager = appUpdateManager ?: return false
        val updateType = if (preferImmediate && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            AppUpdateType.IMMEDIATE
        } else if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
            AppUpdateType.FLEXIBLE
        } else if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            AppUpdateType.IMMEDIATE
        } else {
            return false
        }

        return try {
            manager.startUpdateFlowForResult(
                appUpdateInfo,
                launcher,
                AppUpdateOptions.newBuilder(updateType).build()
            )
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Starts update directly with Activity.
     */
    @Suppress("DEPRECATION")
    fun startUpdate(
        activity: Activity,
        appUpdateInfo: AppUpdateInfo,
        requestCode: Int = 5300,
        preferImmediate: Boolean = false
    ): Boolean {
        val manager = appUpdateManager ?: return false
        val updateType = if (preferImmediate && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            AppUpdateType.IMMEDIATE
        } else if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
            AppUpdateType.FLEXIBLE
        } else if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            AppUpdateType.IMMEDIATE
        } else {
            return false
        }

        return try {
            manager.startUpdateFlowForResult(
                appUpdateInfo,
                activity,
                AppUpdateOptions.newBuilder(updateType).build(),
                requestCode
            )
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Completes a flexible in-app update by restarting the app.
     */
    fun completeUpdate() {
        try {
            appUpdateManager?.completeUpdate()
        } catch (_: Throwable) {
        }
    }

    /**
     * Prompts for Google Play In-App Review with fallback to opening Play Store review page.
     */
    fun launchInAppReview(
        activity: Activity,
        onComplete: (() -> Unit)? = null
    ) {
        try {
            val manager = ReviewManagerFactory.create(activity)
            val request = manager.requestReviewFlow()

            request.addOnCompleteListener { task ->
                try {
                    if (task.isSuccessful) {
                        val reviewInfo = task.result
                        val flow = manager.launchReviewFlow(activity, reviewInfo)
                        flow.addOnCompleteListener {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    activity,
                                    "Thank you for rating NOX - Game Space!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onComplete?.invoke()
                            }
                        }
                    } else {
                        openPlayStore(activity)
                        onComplete?.invoke()
                    }
                } catch (_: Throwable) {
                    openPlayStore(activity)
                    onComplete?.invoke()
                }
            }
        } catch (_: Throwable) {
            openPlayStore(activity)
            onComplete?.invoke()
        }
    }

    /**
     * Opens the app's Google Play Store listing directly for rating or updating.
     */
    fun openPlayStore(context: Context) {
        val packageName = context.packageName
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
            try {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            } catch (_: Throwable) {
            }
        }
    }

    fun cleanUp() {
        try {
            appUpdateManager?.unregisterListener(installListener)
        } catch (_: Throwable) {
        }
        appUpdateManager = null
    }
}
