package network.bisq.mobile.presentation.common.ui.release_update

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import network.bisq.mobile.data.utils.AppUpdateChannel
import network.bisq.mobile.i18n.has
import network.bisq.mobile.i18n.i18n
import network.bisq.mobile.test.presentation.compose.BisqComposeUiTestBase
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReleaseUpdateCardUiTest : BisqComposeUiTestBase() {
    private val actions = mutableListOf<ReleaseUpdateUiAction>()

    private fun state(
        playPhase: PlayUpdatePhase = PlayUpdatePhase.NOT_APPLICABLE,
        channel: AppUpdateChannel = AppUpdateChannel.GITHUB_RELEASES,
        isMandatory: Boolean = false,
        isNodeApp: Boolean = false,
    ) = ReleaseUpdateUiState(
        visibility = ReleaseUpdateVisibility.CARD,
        versionString = "0.15.0",
        releaseNotes = "\nFaster sync\nFewer crashes",
        isMandatory = isMandatory,
        minVersionString = if (isMandatory) "2.1.14" else "",
        playPhase = playPhase,
        downloadProgress = 0.62f,
        externalLinkChannel = channel,
        isNodeApp = isNodeApp,
    )

    private fun showCard(uiState: ReleaseUpdateUiState) {
        setTestContent { ReleaseUpdateCard(uiState) { actions += it } }
    }

    private fun assertPrimaryAction(
        label: String,
        action: ReleaseUpdateUiAction,
    ) {
        composeTestRule.onNodeWithTag(ReleaseUpdateTestTags.PRIMARY_ACTION).assertIsDisplayed()
        composeTestRule.onNodeWithText(label).performClick()
        assertEquals(action, actions.last())
    }

    @Test
    fun `every string the card uses exists in the English bundle`() {
        listOf(
            "mobile.dashboard.releaseUpdate.headline",
            "mobile.dashboard.releaseUpdate.notesPreviewLink",
            "mobile.dashboard.releaseUpdate.mandatory.badge",
            "mobile.dashboard.releaseUpdate.action.update",
            "mobile.dashboard.releaseUpdate.action.retry",
            "mobile.dashboard.releaseUpdate.action.restart",
            "mobile.dashboard.releaseUpdate.action.viewOnGithub",
            "mobile.dashboard.releaseUpdate.action.openPlayStore",
            "mobile.dashboard.releaseUpdate.action.openFdroid",
            "mobile.dashboard.releaseUpdate.action.openInstallPage",
            "mobile.dashboard.releaseUpdate.downloading",
            "mobile.dashboard.releaseUpdate.downloadProgressPercent",
            "mobile.dashboard.releaseUpdate.downloadFailed",
            "mobile.dashboard.releaseUpdate.restartNotice.connect",
            "mobile.dashboard.releaseUpdate.restartNotice.node.title",
            "mobile.dashboard.releaseUpdate.restartNotice.node.body",
            "mobile.resources.version.updateAvailable",
            "mobile.alert.update.minimum",
            "updater.releaseNotesHeadline",
            "updater.ignore",
        ).forEach { assertTrue(has(it), "missing i18n key $it") }
    }

    @Test
    fun `shows the version and the first line of the release notes`() {
        showCard(state())

        composeTestRule.onNodeWithText("mobile.dashboard.releaseUpdate.headline".i18n("0.15.0")).assertIsDisplayed()
        composeTestRule.onNodeWithText("Faster sync").assertIsDisplayed()

        composeTestRule.onNodeWithTag(ReleaseUpdateTestTags.CARD_NOTES_PREVIEW).performClick()
        assertEquals(ReleaseUpdateUiAction.OnOpenReleaseNotes, actions.last())
    }

    @Test
    fun `the external link is labelled after the place it opens`() {
        showCard(state(channel = AppUpdateChannel.GITHUB_RELEASES))
        assertPrimaryAction("mobile.dashboard.releaseUpdate.action.viewOnGithub".i18n(), ReleaseUpdateUiAction.OnOpenExternalUpdateLink)
    }

    @Test
    fun `a Play install without an in-app update opens the store listing`() {
        showCard(state(channel = AppUpdateChannel.PLAY_STORE))
        assertPrimaryAction("mobile.dashboard.releaseUpdate.action.openPlayStore".i18n(), ReleaseUpdateUiAction.OnOpenExternalUpdateLink)
    }

    @Test
    fun `an F-Droid install opens F-Droid`() {
        showCard(state(channel = AppUpdateChannel.FDROID))
        assertPrimaryAction("mobile.dashboard.releaseUpdate.action.openFdroid".i18n(), ReleaseUpdateUiAction.OnOpenExternalUpdateLink)
    }

    @Test
    fun `iOS opens the install page`() {
        showCard(state(channel = AppUpdateChannel.IOS_INSTALL_PAGE))
        assertPrimaryAction("mobile.dashboard.releaseUpdate.action.openInstallPage".i18n(), ReleaseUpdateUiAction.OnOpenExternalUpdateLink)
    }

    @Test
    fun `an available in-app update starts from the card and can be ignored`() {
        showCard(state(playPhase = PlayUpdatePhase.AVAILABLE, channel = AppUpdateChannel.PLAY_STORE))

        assertPrimaryAction("mobile.dashboard.releaseUpdate.action.update".i18n(), ReleaseUpdateUiAction.OnStartInAppUpdate)
        composeTestRule.onNodeWithContentDescription("updater.ignore".i18n()).performClick()
        assertEquals(ReleaseUpdateUiAction.OnDismissVersion("0.15.0"), actions.last())
    }

    @Test
    fun `a running download shows progress with no button and no dismiss`() {
        showCard(state(playPhase = PlayUpdatePhase.DOWNLOADING, channel = AppUpdateChannel.PLAY_STORE))

        composeTestRule.onNodeWithTag(ReleaseUpdateTestTags.CARD_DOWNLOADING).assertIsDisplayed()
        composeTestRule.onNodeWithText("mobile.dashboard.releaseUpdate.downloadProgressPercent".i18n(62)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReleaseUpdateTestTags.PRIMARY_ACTION).assertDoesNotExist()
        composeTestRule.onNodeWithTag(ReleaseUpdateTestTags.CARD_DISMISS).assertDoesNotExist()
    }

    @Test
    fun `a failed download offers a retry`() {
        showCard(state(playPhase = PlayUpdatePhase.FAILED, channel = AppUpdateChannel.PLAY_STORE))

        composeTestRule.onNodeWithTag(ReleaseUpdateTestTags.CARD_DOWNLOAD_FAILED).assertIsDisplayed()
        assertPrimaryAction("mobile.dashboard.releaseUpdate.action.retry".i18n(), ReleaseUpdateUiAction.OnStartInAppUpdate)
    }

    @Test
    fun `a finished download on the node warns that the node restarts`() {
        showCard(state(playPhase = PlayUpdatePhase.READY_TO_RESTART, channel = AppUpdateChannel.PLAY_STORE, isNodeApp = true))

        composeTestRule.onNodeWithText("mobile.dashboard.releaseUpdate.restartNotice.node.title".i18n()).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReleaseUpdateTestTags.CARD_DISMISS).assertDoesNotExist()
        assertPrimaryAction("mobile.dashboard.releaseUpdate.action.restart".i18n(), ReleaseUpdateUiAction.OnRestartToUpdate)
    }

    @Test
    fun `a finished download on Connect says the app restarts`() {
        showCard(state(playPhase = PlayUpdatePhase.READY_TO_RESTART, channel = AppUpdateChannel.PLAY_STORE))

        composeTestRule
            .onNodeWithTag(ReleaseUpdateTestTags.CARD_RESTART_NOTICE)
            .assertTextEquals("mobile.dashboard.releaseUpdate.restartNotice.connect".i18n())
    }

    @Test
    fun `a mandatory update says so in words and cannot be dismissed`() {
        showCard(state(isMandatory = true))

        composeTestRule.onNodeWithTag(ReleaseUpdateTestTags.MANDATORY_BADGE).assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(ReleaseUpdateTestTags.CARD_MANDATORY_NOTICE)
            .assertTextEquals("mobile.alert.update.minimum".i18n("2.1.14"))
        composeTestRule.onNodeWithTag(ReleaseUpdateTestTags.CARD_DISMISS).assertDoesNotExist()
    }

    @Test
    fun `the sheet shows the full notes and offers ignoring an optional update`() {
        setTestContent { ReleaseNotesSheet(state()) { actions += it } }

        composeTestRule.onNodeWithText("updater.releaseNotesHeadline".i18n("0.15.0")).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReleaseUpdateTestTags.SHEET_IGNORE_VERSION).performClick()
        assertEquals(ReleaseUpdateUiAction.OnDismissVersion("0.15.0"), actions.last())
    }

    @Test
    fun `the sheet of a mandatory update has no ignore link`() {
        setTestContent { ReleaseNotesSheet(state(isMandatory = true)) { actions += it } }

        composeTestRule.onNodeWithTag(ReleaseUpdateTestTags.SHEET_MANDATORY_NOTICE).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReleaseUpdateTestTags.SHEET_IGNORE_VERSION).assertDoesNotExist()
    }

    @Test
    fun `the resources entry reopens an ignored version`() {
        var opened = false
        setTestContent { DismissedReleaseUpdateEntry("0.15.0") { opened = true } }

        composeTestRule.onNodeWithTag(ReleaseUpdateTestTags.RESOURCES_ENTRY).performClick()
        assertEquals(true, opened)
    }
}
