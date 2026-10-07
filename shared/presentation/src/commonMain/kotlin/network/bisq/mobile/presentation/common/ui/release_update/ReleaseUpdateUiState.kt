package network.bisq.mobile.presentation.common.ui.release_update

import androidx.compose.runtime.Immutable
import network.bisq.mobile.data.utils.AppUpdateChannel

/** Where the offered release shows up. */
enum class ReleaseUpdateVisibility {
    HIDDEN,

    /** The dashboard card. */
    CARD,

    /** The user ignored this version; only the resources screen still offers it. */
    DISMISSED,
}

/** The in-app update phase. Anything but [NOT_APPLICABLE] means the store updates the app in place. */
enum class PlayUpdatePhase {
    NOT_APPLICABLE,
    AVAILABLE,
    DOWNLOADING,
    FAILED,
    READY_TO_RESTART,
}

/**
 * @param minVersionString the bisq2 version trading requires, named by the mandatory notice; only
 * meaningful while [isMandatory].
 * @param externalLinkChannel where the primary action leads while [playPhase] is
 * [PlayUpdatePhase.NOT_APPLICABLE].
 */
@Immutable
data class ReleaseUpdateUiState(
    val visibility: ReleaseUpdateVisibility = ReleaseUpdateVisibility.HIDDEN,
    val versionString: String = "",
    val releaseNotes: String = "",
    val isMandatory: Boolean = false,
    val minVersionString: String = "",
    val playPhase: PlayUpdatePhase = PlayUpdatePhase.NOT_APPLICABLE,
    val downloadProgress: Float = 0f,
    val externalLinkChannel: AppUpdateChannel = AppUpdateChannel.GITHUB_RELEASES,
    val isNodeApp: Boolean = false,
    val isReleaseNotesSheetVisible: Boolean = false,
) {
    /**
     * Ignoring is offered for an optional update that is not mid-flow. Play shows no notification
     * outside the app for a finished download, so hiding the card then would strand it.
     */
    val isDismissVisible: Boolean
        get() =
            visibility == ReleaseUpdateVisibility.CARD &&
                !isMandatory &&
                playPhase != PlayUpdatePhase.DOWNLOADING &&
                playPhase != PlayUpdatePhase.READY_TO_RESTART
}
