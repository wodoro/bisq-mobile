package network.bisq.mobile.presentation.common.ui.release_update

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import network.bisq.mobile.data.utils.AppUpdateLinker
import network.bisq.mobile.domain.service.app_update.InAppUpdateProvider
import network.bisq.mobile.domain.service.app_update.InAppUpdateState
import network.bisq.mobile.domain.service.app_update.ReleaseUpdate
import network.bisq.mobile.domain.service.app_update.ReleaseUpdateService
import network.bisq.mobile.presentation.common.ui.base.BasePresenter
import network.bisq.mobile.presentation.main.MainPresenter

/**
 * Drives the release-update card on the dashboard and its re-entry point on the resources screen.
 *
 * @param isNodeApp only changes the restart notice: the node app restarts its trading node.
 */
@Stable
class ReleaseUpdatePresenter(
    mainPresenter: MainPresenter,
    private val releaseUpdateService: ReleaseUpdateService,
    private val inAppUpdateProvider: InAppUpdateProvider,
    private val appUpdateLinker: AppUpdateLinker,
    private val isNodeApp: Boolean,
) : BasePresenter(mainPresenter) {
    // The installer of a running app cannot change, so it is looked up once.
    private val updateChannel by lazy { appUpdateLinker.getUpdateChannel() }

    private val isReleaseNotesSheetOpen = MutableStateFlow(false)

    val uiState: StateFlow<ReleaseUpdateUiState> =
        combine(
            releaseUpdateService.releaseUpdate,
            inAppUpdateProvider.state,
            isReleaseNotesSheetOpen,
        ) { releaseUpdate, inAppUpdateState, isSheetOpen ->
            releaseUpdate?.toUiState(inAppUpdateState, isSheetOpen) ?: ReleaseUpdateUiState(isNodeApp = isNodeApp)
        }.stateIn(
            presenterScope,
            SharingStarted.WhileSubscribed(5_000),
            ReleaseUpdateUiState(isNodeApp = isNodeApp),
        )

    override fun onViewAttached() {
        super.onViewAttached()
        presenterScope.launch {
            // The store is asked only once a newer release is known, and again whenever it changes,
            // which also picks up a download that finished while the app was closed.
            releaseUpdateService.releaseUpdate
                .map { it?.version }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { inAppUpdateProvider.refresh() }
        }
    }

    fun onAction(action: ReleaseUpdateUiAction) {
        when (action) {
            ReleaseUpdateUiAction.OnOpenReleaseNotes -> isReleaseNotesSheetOpen.value = true
            ReleaseUpdateUiAction.OnCloseReleaseNotes -> isReleaseNotesSheetOpen.value = false
            is ReleaseUpdateUiAction.OnDismissVersion -> dismissVersion(action.version)
            ReleaseUpdateUiAction.OnStartInAppUpdate -> inAppUpdateProvider.startUpdate()
            ReleaseUpdateUiAction.OnRestartToUpdate -> inAppUpdateProvider.completeUpdate()
            ReleaseUpdateUiAction.OnOpenExternalUpdateLink -> openAppUpdatePage(appUpdateLinker)
        }
    }

    private fun dismissVersion(version: String) {
        isReleaseNotesSheetOpen.value = false
        presenterScope.launch { releaseUpdateService.dismiss(version) }
    }

    private fun ReleaseUpdate.toUiState(
        inAppUpdateState: InAppUpdateState,
        isSheetOpen: Boolean,
    ): ReleaseUpdateUiState {
        val playPhase = inAppUpdateState.toPlayUpdatePhase()
        val isMidFlow = playPhase == PlayUpdatePhase.DOWNLOADING || playPhase == PlayUpdatePhase.READY_TO_RESTART
        return ReleaseUpdateUiState(
            // A download in progress or finished keeps the card even for an ignored version.
            visibility = if (isDismissed && !isMidFlow) ReleaseUpdateVisibility.DISMISSED else ReleaseUpdateVisibility.CARD,
            versionString = version,
            releaseNotes = releaseNotes,
            isMandatory = isMandatory,
            minVersionString = minVersionRequiredForTrading.orEmpty(),
            playPhase = playPhase,
            downloadProgress = (inAppUpdateState as? InAppUpdateState.Downloading)?.progress ?: 0f,
            externalLinkChannel = updateChannel,
            isNodeApp = isNodeApp,
            isReleaseNotesSheetVisible = isSheetOpen,
        )
    }

    private fun InAppUpdateState.toPlayUpdatePhase(): PlayUpdatePhase =
        when (this) {
            InAppUpdateState.Unavailable -> PlayUpdatePhase.NOT_APPLICABLE
            InAppUpdateState.Available -> PlayUpdatePhase.AVAILABLE
            is InAppUpdateState.Downloading -> PlayUpdatePhase.DOWNLOADING
            InAppUpdateState.Failed -> PlayUpdatePhase.FAILED
            InAppUpdateState.ReadyToInstall -> PlayUpdatePhase.READY_TO_RESTART
        }
}
