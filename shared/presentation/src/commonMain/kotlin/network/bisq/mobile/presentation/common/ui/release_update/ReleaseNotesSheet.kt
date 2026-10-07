package network.bisq.mobile.presentation.common.ui.release_update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import network.bisq.mobile.i18n.i18n
import network.bisq.mobile.presentation.common.ui.components.atoms.BisqButton
import network.bisq.mobile.presentation.common.ui.components.atoms.BisqButtonType
import network.bisq.mobile.presentation.common.ui.components.atoms.BisqText
import network.bisq.mobile.presentation.common.ui.components.atoms.layout.BisqGap
import network.bisq.mobile.presentation.common.ui.components.molecules.bottom_sheet.BisqBottomSheet
import network.bisq.mobile.presentation.common.ui.theme.BisqTheme
import network.bisq.mobile.presentation.common.ui.theme.BisqUIConstants

/**
 * The full release notes. A sheet rather than a dialog because reading them is optional; the
 * sheet scrolls internally, which covers bisq2's 10,000 character limit. The notes render as plain
 * text, as desktop renders the same field.
 */
@Composable
fun ReleaseNotesSheet(
    uiState: ReleaseUpdateUiState,
    onAction: (ReleaseUpdateUiAction) -> Unit,
) {
    BisqBottomSheet(onDismissRequest = { onAction(ReleaseUpdateUiAction.OnCloseReleaseNotes) }) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BisqUIConstants.ScreenPadding2X)
                    .padding(bottom = BisqUIConstants.ScreenPadding2X),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BisqText.H5Light(
                    text = "updater.releaseNotesHeadline".i18n(uiState.versionString),
                    modifier = Modifier.weight(1f),
                )
                if (uiState.isMandatory) {
                    RequiredUpdateBadge()
                }
            }

            if (uiState.isMandatory) {
                BisqGap.VHalf()
                MandatoryUpdateNotice(uiState.minVersionString, Modifier.testTag(ReleaseUpdateTestTags.SHEET_MANDATORY_NOTICE))
            }

            BisqGap.V1()
            BisqText.SmallLight(
                text = uiState.releaseNotes,
                color = BisqTheme.colors.light_grey10,
            )

            BisqGap.V2()
            ReleaseUpdatePrimaryButton(uiState, onAction)

            if (uiState.isDismissVisible) {
                BisqGap.VHalf()
                BisqButton(
                    text = "updater.ignore".i18n(),
                    type = BisqButtonType.Underline,
                    onClick = { onAction(ReleaseUpdateUiAction.OnDismissVersion(uiState.versionString)) },
                    modifier = Modifier.testTag(ReleaseUpdateTestTags.SHEET_IGNORE_VERSION),
                )
            }
        }
    }
}
