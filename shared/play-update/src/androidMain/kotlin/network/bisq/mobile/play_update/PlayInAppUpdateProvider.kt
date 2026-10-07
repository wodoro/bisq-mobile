package network.bisq.mobile.play_update

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import network.bisq.mobile.domain.service.app_update.InAppUpdateProvider
import network.bisq.mobile.domain.service.app_update.InAppUpdateState
import network.bisq.mobile.domain.utils.Logging
import network.bisq.mobile.domain.utils.redactedSummary
import network.bisq.mobile.domain.utils.resultCatching
import java.lang.ref.WeakReference

/**
 * The Play In-App Updates flexible flow: Play downloads in the background while the app keeps
 * running, then installs and restarts it on [completeUpdate]. Only bind it for an install that
 * Google Play made; for a sideloaded APK Play has nothing to update.
 *
 * @param prepareForRestart runs right before Play installs the update, which kills the process
 * without running shutdown hooks. The node app uses it to persist state that is otherwise written
 * lazily.
 */
class PlayInAppUpdateProvider(
    application: Application,
    private val prepareForRestart: suspend () -> Unit = {},
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(application),
    // Play's flow starts an activity, so it is driven from the main thread.
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : InAppUpdateProvider,
    Logging {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow<InAppUpdateState>(InAppUpdateState.Unavailable)
    override val state: StateFlow<InAppUpdateState> = _state.asStateFlow()

    // Play shows its confirmation over an activity. Held weakly, so tracking it leaks nothing.
    private var resumedActivity: WeakReference<Activity>? = null

    private val installStateListener = InstallStateUpdatedListener { installState -> onInstallStateUpdated(installState) }

    init {
        application.registerActivityLifecycleCallbacks(ResumedActivityTracker())
        appUpdateManager.registerListener(installStateListener)
    }

    override fun refresh() {
        scope.launch {
            resultCatching { appUpdateManager.appUpdateInfo.await() }
                .onSuccess { _state.value = it.toInAppUpdateState() }
                .onFailure { log.w { "Could not query Play for an update: ${it.redactedSummary()}" } }
        }
    }

    override fun startUpdate() {
        scope.launch {
            val activity = resumedActivity?.get()
            if (activity == null) {
                log.w { "No visible activity to show Play's update confirmation over" }
                return@launch
            }
            resultCatching {
                val appUpdateInfo = appUpdateManager.appUpdateInfo.await()
                val updateState = appUpdateInfo.toInAppUpdateState()
                if (updateState != InAppUpdateState.Available) {
                    // Play's answer moved on since the last refresh, e.g. a download already runs.
                    _state.value = updateState
                    return@resultCatching
                }
                val resultCode =
                    appUpdateManager
                        .startUpdateFlow(appUpdateInfo, activity, AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE))
                        .await()
                _state.value =
                    when (resultCode) {
                        // The download itself is reported through installStateListener.
                        Activity.RESULT_OK -> InAppUpdateState.Downloading(progress = 0f)
                        // The user declined Play's dialog, nothing was downloaded.
                        Activity.RESULT_CANCELED -> InAppUpdateState.Available
                        // ActivityResult.RESULT_IN_APP_UPDATE_FAILED, or anything Play adds later.
                        else -> InAppUpdateState.Failed
                    }
            }.onFailure {
                log.w { "Could not start the Play update: ${it.redactedSummary()}" }
                _state.value = InAppUpdateState.Failed
            }
        }
    }

    override fun completeUpdate() {
        scope.launch {
            // Installing kills the process either way, so a failed preparation does not stop it:
            // the update would otherwise stay downloaded and never applied.
            resultCatching { prepareForRestart() }
                .onFailure { log.w { "Preparing for the update restart failed: ${it.redactedSummary()}" } }
            appUpdateManager.completeUpdate()
        }
    }

    private fun onInstallStateUpdated(installState: InstallState) {
        when (installState.installStatus()) {
            InstallStatus.PENDING -> _state.value = InAppUpdateState.Downloading(progress = 0f)
            InstallStatus.DOWNLOADING ->
                _state.value = InAppUpdateState.Downloading(downloadProgress(installState.bytesDownloaded(), installState.totalBytesToDownload()))
            InstallStatus.DOWNLOADED -> _state.value = InAppUpdateState.ReadyToInstall
            InstallStatus.FAILED -> _state.value = InAppUpdateState.Failed
            // Cancelled before anything was installed, so the update can be started again.
            InstallStatus.CANCELED -> _state.value = InAppUpdateState.Available
            else -> Unit
        }
    }

    private fun AppUpdateInfo.toInAppUpdateState(): InAppUpdateState =
        when {
            installStatus() == InstallStatus.DOWNLOADED -> InAppUpdateState.ReadyToInstall
            installStatus() == InstallStatus.DOWNLOADING ->
                InAppUpdateState.Downloading(downloadProgress(bytesDownloaded(), totalBytesToDownload()))
            installStatus() == InstallStatus.PENDING -> InAppUpdateState.Downloading(progress = 0f)
            updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) ->
                InAppUpdateState.Available
            else -> InAppUpdateState.Unavailable
        }

    private fun downloadProgress(
        bytesDownloaded: Long,
        totalBytesToDownload: Long,
    ): Float = if (totalBytesToDownload > 0) bytesDownloaded.toFloat() / totalBytesToDownload else 0f

    private inner class ResumedActivityTracker : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            resumedActivity = WeakReference(activity)
        }

        override fun onActivityPaused(activity: Activity) {
            if (resumedActivity?.get() === activity) resumedActivity = null
        }

        override fun onActivityCreated(
            activity: Activity,
            savedInstanceState: Bundle?,
        ) = Unit

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(
            activity: Activity,
            outState: Bundle,
        ) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
