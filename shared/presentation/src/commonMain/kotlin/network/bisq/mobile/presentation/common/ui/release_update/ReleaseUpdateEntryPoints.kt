package network.bisq.mobile.presentation.common.ui.release_update

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import network.bisq.mobile.i18n.i18n
import network.bisq.mobile.presentation.common.ui.components.atoms.BisqText
import network.bisq.mobile.presentation.common.ui.theme.BisqTheme
import network.bisq.mobile.presentation.common.ui.theme.BisqUIConstants
import network.bisq.mobile.presentation.common.ui.utils.RememberPresenterLifecycle
import org.koin.compose.koinInject

/** The dashboard card and its release-notes sheet. Renders nothing while no update is offered. */
@Composable
fun DashboardReleaseUpdate() {
    val presenter: ReleaseUpdatePresenter = koinInject()
    RememberPresenterLifecycle(presenter)
    val uiState by presenter.uiState.collectAsState()

    if (uiState.visibility == ReleaseUpdateVisibility.CARD) {
        ReleaseUpdateCard(uiState, presenter::onAction)
        if (uiState.isReleaseNotesSheetVisible) {
            ReleaseNotesSheet(uiState, presenter::onAction)
        }
    }
}

/**
 * The way back to a version the user ignored, under the resources screen's version info. A
 * mandatory update is never dismissible, so this only ever offers an optional one.
 */
@Composable
fun ResourcesReleaseUpdateEntry() {
    val presenter: ReleaseUpdatePresenter = koinInject()
    RememberPresenterLifecycle(presenter)
    val uiState by presenter.uiState.collectAsState()

    if (uiState.visibility == ReleaseUpdateVisibility.DISMISSED) {
        DismissedReleaseUpdateEntry(uiState.versionString) { presenter.onAction(ReleaseUpdateUiAction.OnOpenReleaseNotes) }
        if (uiState.isReleaseNotesSheetVisible) {
            ReleaseNotesSheet(uiState, presenter::onAction)
        }
    }
}

@Composable
fun DismissedReleaseUpdateEntry(
    versionString: String,
    onOpen: () -> Unit,
) {
    BisqText.SmallMedium(
        text = "mobile.resources.version.updateAvailable".i18n(versionString),
        color = BisqTheme.colors.primary,
        modifier =
            Modifier
                .padding(
                    horizontal = BisqUIConstants.ScreenPadding2X,
                    vertical = BisqUIConstants.ScreenPaddingHalf,
                ).clickable(onClick = onOpen)
                .testTag(ReleaseUpdateTestTags.RESOURCES_ENTRY),
    )
}
