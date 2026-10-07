package network.bisq.mobile.presentation.common.ui.release_update

sealed interface ReleaseUpdateUiAction {
    /** The card's "What's new" line, or the resources screen entry of an ignored version. */
    data object OnOpenReleaseNotes : ReleaseUpdateUiAction

    data object OnCloseReleaseNotes : ReleaseUpdateUiAction

    /** The card's dismiss icon or the sheet's "Ignore this version". Persists per version. */
    data class OnDismissVersion(
        val version: String,
    ) : ReleaseUpdateUiAction

    /** Starts the in-app download, or restarts it after a failure. */
    data object OnStartInAppUpdate : ReleaseUpdateUiAction

    /** Installs the downloaded update, which restarts the app. */
    data object OnRestartToUpdate : ReleaseUpdateUiAction

    /** Opens the page that updates this install when the store cannot do it in place. */
    data object OnOpenExternalUpdateLink : ReleaseUpdateUiAction
}
