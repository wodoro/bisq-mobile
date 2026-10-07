package network.bisq.mobile.presentation.common.ui.release_update

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import network.bisq.mobile.data.utils.AppUpdateChannel
import network.bisq.mobile.i18n.i18n
import network.bisq.mobile.presentation.common.ui.components.atoms.AutoResizeText
import network.bisq.mobile.presentation.common.ui.components.atoms.BisqButton
import network.bisq.mobile.presentation.common.ui.components.atoms.BisqCard
import network.bisq.mobile.presentation.common.ui.components.atoms.BisqProgressBar
import network.bisq.mobile.presentation.common.ui.components.atoms.BisqText
import network.bisq.mobile.presentation.common.ui.components.atoms.icons.AppLinkIcon
import network.bisq.mobile.presentation.common.ui.components.atoms.layout.BisqGap
import network.bisq.mobile.presentation.common.ui.theme.BisqTheme
import network.bisq.mobile.presentation.common.ui.theme.BisqUIConstants
import kotlin.math.roundToInt

/**
 * The dashboard's release-update card, as specified in
 * `presentation/design/release_notification/ReleaseNotificationDesign.kt`.
 */
@Composable
fun ReleaseUpdateCard(
    uiState: ReleaseUpdateUiState,
    onAction: (ReleaseUpdateUiAction) -> Unit,
) {
    BisqCard(
        modifier =
            if (uiState.isMandatory) {
                Modifier.border(
                    width = 2.dp,
                    color = BisqTheme.colors.danger,
                    shape = RoundedCornerShape(BisqUIConstants.BorderRadius),
                )
            } else {
                Modifier
            }.testTag(ReleaseUpdateTestTags.CARD),
        padding = BisqUIConstants.ScreenPadding2X,
        backgroundColor = if (uiState.isMandatory) BisqTheme.colors.danger.copy(alpha = 0.10f) else BisqTheme.colors.dark_grey40,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BisqUIConstants.ScreenPaddingHalf),
            ) {
                AutoResizeText(
                    text = "mobile.dashboard.releaseUpdate.headline".i18n(uiState.versionString),
                    maxLines = 1,
                    textStyle = BisqTheme.typography.h5Light,
                    color = BisqTheme.colors.white,
                    overflow = TextOverflow.Ellipsis,
                )
                if (uiState.isMandatory) {
                    RequiredUpdateBadge()
                }
            }
            if (uiState.isDismissVisible) {
                IconButton(
                    onClick = { onAction(ReleaseUpdateUiAction.OnDismissVersion(uiState.versionString)) },
                    modifier = Modifier.testTag(ReleaseUpdateTestTags.CARD_DISMISS),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "updater.ignore".i18n(),
                        tint = BisqTheme.colors.light_grey20,
                        modifier = Modifier.height(20.dp),
                    )
                }
            }
        }

        BisqGap.VHalf()

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onAction(ReleaseUpdateUiAction.OnOpenReleaseNotes) }
                    .testTag(ReleaseUpdateTestTags.CARD_NOTES_PREVIEW),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BisqText.StyledText(
                text =
                    uiState.releaseNotes
                        .lineSequence()
                        .firstOrNull { it.isNotBlank() }
                        .orEmpty(),
                style = BisqTheme.typography.smallLight,
                color = BisqTheme.colors.mid_grey20,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            BisqGap.H1()
            BisqText.SmallMedium(
                text = "mobile.dashboard.releaseUpdate.notesPreviewLink".i18n() + " ›",
                color = BisqTheme.colors.primary,
            )
        }

        BisqGap.V1()
        ReleaseUpdateBody(uiState)
        BisqGap.V1()
        ReleaseUpdatePrimaryButton(uiState, onAction)
    }
}

/** Carries the meaning in words, so the danger color is never its only signal. */
@Composable
internal fun RequiredUpdateBadge() {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(BisqUIConstants.BorderRadius))
                .background(BisqTheme.colors.danger.copy(alpha = 0.20f))
                .padding(
                    horizontal = BisqUIConstants.ScreenPaddingHalf,
                    vertical = BisqUIConstants.ScreenPaddingQuarter,
                ).testTag(ReleaseUpdateTestTags.MANDATORY_BADGE),
    ) {
        BisqText.XSmallMedium(
            text = "mobile.dashboard.releaseUpdate.mandatory.badge".i18n(),
            color = BisqTheme.colors.danger,
        )
    }
}

/** Reuses the alert banner's own minimum-version string, so the two surfaces read as one message. */
@Composable
internal fun MandatoryUpdateNotice(
    minVersionString: String,
    modifier: Modifier = Modifier,
) {
    BisqText.SmallMedium(
        text = "mobile.alert.update.minimum".i18n(minVersionString),
        color = BisqTheme.colors.danger,
        modifier = modifier,
    )
}

@Composable
private fun ReleaseUpdateBody(uiState: ReleaseUpdateUiState) {
    Column {
        if (uiState.isMandatory) {
            MandatoryUpdateNotice(uiState.minVersionString, Modifier.testTag(ReleaseUpdateTestTags.CARD_MANDATORY_NOTICE))
        }

        when (uiState.playPhase) {
            PlayUpdatePhase.DOWNLOADING -> {
                Column(modifier = Modifier.testTag(ReleaseUpdateTestTags.CARD_DOWNLOADING)) {
                    BisqText.SmallLight(
                        text = "mobile.dashboard.releaseUpdate.downloading".i18n(),
                        color = BisqTheme.colors.mid_grey20,
                    )
                    BisqGap.VQuarter()
                    BisqProgressBar(
                        progress = uiState.downloadProgress,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                    )
                    BisqGap.VQuarter()
                    BisqText.SmallLight(
                        // Whole percent: Play reports coarse progress, decimals would overstate it.
                        text = "mobile.dashboard.releaseUpdate.downloadProgressPercent".i18n((uiState.downloadProgress * 100).roundToInt()),
                        color = BisqTheme.colors.mid_grey30,
                    )
                }
            }

            PlayUpdatePhase.FAILED -> {
                BisqText.SmallLight(
                    text = "mobile.dashboard.releaseUpdate.downloadFailed".i18n(),
                    color = BisqTheme.colors.warning,
                    modifier = Modifier.testTag(ReleaseUpdateTestTags.CARD_DOWNLOAD_FAILED),
                )
            }

            PlayUpdatePhase.READY_TO_RESTART -> {
                if (uiState.isNodeApp) {
                    Column(modifier = Modifier.testTag(ReleaseUpdateTestTags.CARD_RESTART_NOTICE)) {
                        BisqText.SmallMedium(
                            text = "mobile.dashboard.releaseUpdate.restartNotice.node.title".i18n(),
                            color = BisqTheme.colors.warning,
                        )
                        BisqGap.VQuarter()
                        BisqText.SmallLight(
                            text = "mobile.dashboard.releaseUpdate.restartNotice.node.body".i18n(),
                            color = BisqTheme.colors.warning,
                        )
                    }
                } else {
                    BisqText.SmallLight(
                        text = "mobile.dashboard.releaseUpdate.restartNotice.connect".i18n(),
                        color = BisqTheme.colors.warning,
                        modifier = Modifier.testTag(ReleaseUpdateTestTags.CARD_RESTART_NOTICE),
                    )
                }
            }

            // The headline, notes preview and button already say all there is to say.
            PlayUpdatePhase.AVAILABLE, PlayUpdatePhase.NOT_APPLICABLE -> Unit
        }
    }
}

/** The single primary action, or none while a download runs (Play offers no way to cancel it). */
@Composable
internal fun ReleaseUpdatePrimaryButton(
    uiState: ReleaseUpdateUiState,
    onAction: (ReleaseUpdateUiAction) -> Unit,
) {
    val (text, action) =
        when (uiState.playPhase) {
            PlayUpdatePhase.DOWNLOADING -> return
            PlayUpdatePhase.NOT_APPLICABLE -> externalLinkLabel(uiState.externalLinkChannel) to ReleaseUpdateUiAction.OnOpenExternalUpdateLink
            PlayUpdatePhase.AVAILABLE -> "mobile.dashboard.releaseUpdate.action.update".i18n() to ReleaseUpdateUiAction.OnStartInAppUpdate
            PlayUpdatePhase.FAILED -> "mobile.dashboard.releaseUpdate.action.retry".i18n() to ReleaseUpdateUiAction.OnStartInAppUpdate
            PlayUpdatePhase.READY_TO_RESTART -> "mobile.dashboard.releaseUpdate.action.restart".i18n() to ReleaseUpdateUiAction.OnRestartToUpdate
        }
    val opensExternalPage = uiState.playPhase == PlayUpdatePhase.NOT_APPLICABLE

    BisqButton(
        text = text,
        fullWidth = true,
        leftIcon = if (opensExternalPage) ({ AppLinkIcon(modifier = Modifier.height(16.dp)) }) else null,
        onClick = { onAction(action) },
        modifier = Modifier.testTag(ReleaseUpdateTestTags.PRIMARY_ACTION),
    )
}

/** Names the place the button actually opens, never a channel it does not. */
private fun externalLinkLabel(channel: AppUpdateChannel): String =
    when (channel) {
        AppUpdateChannel.PLAY_STORE -> "mobile.dashboard.releaseUpdate.action.openPlayStore".i18n()
        AppUpdateChannel.FDROID -> "mobile.dashboard.releaseUpdate.action.openFdroid".i18n()
        AppUpdateChannel.GITHUB_RELEASES -> "mobile.dashboard.releaseUpdate.action.viewOnGithub".i18n()
        AppUpdateChannel.IOS_INSTALL_PAGE -> "mobile.dashboard.releaseUpdate.action.openInstallPage".i18n()
    }

object ReleaseUpdateTestTags {
    const val CARD = "release_update_card"
    const val CARD_DISMISS = "release_update_card_dismiss"
    const val CARD_NOTES_PREVIEW = "release_update_card_notes_preview"
    const val CARD_MANDATORY_NOTICE = "release_update_card_mandatory_notice"
    const val CARD_DOWNLOADING = "release_update_card_downloading"
    const val CARD_DOWNLOAD_FAILED = "release_update_card_download_failed"
    const val CARD_RESTART_NOTICE = "release_update_card_restart_notice"
    const val MANDATORY_BADGE = "release_update_mandatory_badge"
    const val PRIMARY_ACTION = "release_update_primary_action"
    const val SHEET_MANDATORY_NOTICE = "release_notes_sheet_mandatory_notice"
    const val SHEET_IGNORE_VERSION = "release_notes_sheet_ignore_version"
    const val RESOURCES_ENTRY = "resources_dismissed_update_entry"
}
