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
    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    private val installListener = InstallStateUpdatedListener { state ->
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
                _updateState.value = AppUpdateState.Error("Update download failed. Please retry or update via Google Play.")
            }
            InstallStatus.CANCELED -> {
                _updateState.value = AppUpdateState.Idle
            }
            else -> {
                // Other statuses
            }
        }
    }

    fun init(context: Context) {
        if (appUpdateManager == null) {
            val manager = AppUpdateManagerFactory.create(context.applicationContext)
            appUpdateManager = manager
            manager.registerListener(installListener)
        }
    }

    /**
     * Checks Google Play for available in-app updates.
     */
    fun checkForUpdate(context: Context, onResult: ((AppUpdateState) -> Unit)? = null) {
        val manager = appUpdateManager ?: AppUpdateManagerFactory.create(context.applicationContext).also {
            appUpdateManager = it
            it.registerListener(installListener)
        }

        _updateState.value = AppUpdateState.Checking
        onResult?.invoke(AppUpdateState.Checking)

        manager.appUpdateInfo.addOnSuccessListener { info ->
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
                        AppUpdateState.Downloading(info.bytesDownloaded(), info.totalBytesToDownload())
                    }
                }
                UpdateAvailability.UPDATE_NOT_AVAILABLE -> {
                    AppUpdateState.UpToDate(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
                }
                else -> {
                    AppUpdateState.UpToDate(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
                }
            }
            _updateState.value = state
            onResult?.invoke(state)
        }.addOnFailureListener { exception ->
            val msg = exception.localizedMessage ?: "Unable to connect to Google Play"
            val errorState = AppUpdateState.Error(msg)
            _updateState.value = errorState
            onResult?.invoke(errorState)
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
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Starts update directly with Activity.
     */
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
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Completes a flexible in-app update by restarting the app.
     */
    fun completeUpdate() {
        appUpdateManager?.completeUpdate()
    }

    /**
     * Prompts for Google Play In-App Review with fallback to opening Play Store review page.
     */
    fun launchInAppReview(
        activity: Activity,
        onComplete: (() -> Unit)? = null
    ) {
        val manager = ReviewManagerFactory.create(activity)
        val request = manager.requestReviewFlow()

        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = manager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            activity,
                            "Thank you for rating & supporting NOX - Game Space!",
                            Toast.LENGTH_SHORT
                        ).show()
                        onComplete?.invoke()
                    }
                }
            } else {
                // If In-App Review fails or is rate-limited by Google Play quota, fallback to Play Store listing
                openPlayStore(activity)
                onComplete?.invoke()
            }
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
        } catch (_: Exception) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    fun cleanUp() {
        try {
            appUpdateManager?.unregisterListener(installListener)
        } catch (_: Exception) {
        }
        appUpdateManager = null
    }
}
