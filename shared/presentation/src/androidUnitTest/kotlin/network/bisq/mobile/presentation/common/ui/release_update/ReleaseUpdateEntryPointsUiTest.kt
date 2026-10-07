package network.bisq.mobile.presentation.common.ui.release_update

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import network.bisq.mobile.data.model.Settings
import network.bisq.mobile.data.service.alert.TradeRestrictingAlertServiceFacade
import network.bisq.mobile.data.service.release.ReleaseNotificationsServiceFacade
import network.bisq.mobile.domain.model.alert.AuthorizedAlertData
import network.bisq.mobile.domain.model.release.ReleaseNotification
import network.bisq.mobile.domain.model.release.ReleaseVersion
import network.bisq.mobile.domain.service.app_update.ReleaseUpdateService
import network.bisq.mobile.domain.service.app_update.UnsupportedInAppUpdateProvider
import network.bisq.mobile.domain.utils.VersionProvider
import network.bisq.mobile.i18n.i18n
import network.bisq.mobile.presentation.common.test_utils.FakeAppUpdateLinker
import network.bisq.mobile.presentation.common.test_utils.MainPresenterTestFactory
import network.bisq.mobile.test.mocks.SettingsRepositoryMock
import network.bisq.mobile.test.presentation.compose.PlatformPresentationKoinComposeTestBase
import org.junit.Test
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The dashboard and the resources screen show the same release in mutually exclusive states: the
 * card until the version is ignored, the resources line only afterwards.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReleaseUpdateEntryPointsUiTest : PlatformPresentationKoinComposeTestBase() {
    private val settingsRepository = SettingsRepositoryMock()

    override fun additionalModules(): List<Module> =
        listOf(
            module {
                single {
                    ReleaseUpdateService(
                        FakeReleaseNotificationsServiceFacade(MutableStateFlow(listOf(notification("0.15.0")))),
                        FakeTradeRestrictingAlertServiceFacade(),
                        settingsRepository,
                        FixedVersionProvider("0.14.2"),
                        testDispatcher,
                    )
                }
                factory {
                    ReleaseUpdatePresenter(
                        MainPresenterTestFactory.create(),
                        get(),
                        UnsupportedInAppUpdateProvider(),
                        FakeAppUpdateLinker(),
                        isNodeApp = false,
                    )
                }
            },
        )

    private fun showBothEntryPoints() {
        setTestContent {
            Column {
                DashboardReleaseUpdate()
                ResourcesReleaseUpdateEntry()
            }
        }
    }

    @Test
    fun `the dashboard shows the card and the resources screen nothing while the version is not ignored`() {
        showBothEntryPoints()

        composeTestRule.onNodeWithTag(ReleaseUpdateTestTags.CARD).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReleaseUpdateTestTags.RESOURCES_ENTRY).assertDoesNotExist()
    }

    @Test
    fun `an ignored version leaves the dashboard and reopens from the resources screen`() {
        settingsRepository.mutableData.value = Settings(ignoredReleaseVersions = setOf("0.15.0"))

        showBothEntryPoints()

        composeTestRule.onNodeWithTag(ReleaseUpdateTestTags.CARD).assertDoesNotExist()
        composeTestRule.onNodeWithTag(ReleaseUpdateTestTags.RESOURCES_ENTRY).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("updater.releaseNotesHeadline".i18n("0.15.0")).assertIsDisplayed()
    }

    private fun notification(version: String) =
        ReleaseNotification(
            id = "id-$version",
            version = requireNotNull(ReleaseVersion.parse(version)),
            releaseNotes = "notes $version",
            isPreRelease = false,
            date = 0,
        )

    private class FakeReleaseNotificationsServiceFacade(
        override val releaseNotifications: StateFlow<List<ReleaseNotification>>,
    ) : ReleaseNotificationsServiceFacade()

    private class FakeTradeRestrictingAlertServiceFacade : TradeRestrictingAlertServiceFacade() {
        override val alert: StateFlow<AuthorizedAlertData?> = MutableStateFlow(null)
        override val minVersionRequiredForTrading: StateFlow<String?> = MutableStateFlow(null)
    }

    private class FixedVersionProvider(
        private val appVersion: String,
    ) : VersionProvider {
        override fun getVersionInfo(
            isDemo: Boolean,
            isIOS: Boolean,
        ) = appVersion

        override fun getAppNameAndVersion(
            isDemo: Boolean,
            isIOS: Boolean,
        ) = appVersion

        override fun getAppVersion() = appVersion
    }
}
