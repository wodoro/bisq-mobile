package network.bisq.mobile.presentation.common.ui.release_update

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import network.bisq.mobile.data.model.Settings
import network.bisq.mobile.data.service.alert.TradeRestrictingAlertServiceFacade
import network.bisq.mobile.data.service.release.ReleaseNotificationsServiceFacade
import network.bisq.mobile.data.utils.AppUpdateChannel
import network.bisq.mobile.data.utils.UrlLauncher
import network.bisq.mobile.domain.model.alert.AuthorizedAlertData
import network.bisq.mobile.domain.model.release.ReleaseNotification
import network.bisq.mobile.domain.model.release.ReleaseVersion
import network.bisq.mobile.domain.service.app_update.InAppUpdateProvider
import network.bisq.mobile.domain.service.app_update.InAppUpdateState
import network.bisq.mobile.domain.service.app_update.ReleaseUpdateService
import network.bisq.mobile.domain.utils.VersionProvider
import network.bisq.mobile.presentation.common.test_utils.FakeAppUpdateLinker
import network.bisq.mobile.presentation.common.test_utils.MainPresenterTestFactory
import network.bisq.mobile.presentation.common.test_utils.TEST_APP_UPDATE_URL
import network.bisq.mobile.presentation.common.test_utils.probeStateFlow
import network.bisq.mobile.test.mocks.SettingsRepositoryMock
import network.bisq.mobile.test.presentation.coroutines.PlatformPresentationKoinTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReleaseUpdatePresenterTest : PlatformPresentationKoinTestBase() {
    private val notifications = MutableStateFlow<List<ReleaseNotification>>(emptyList())
    private val minVersionRequiredForTrading = MutableStateFlow<String?>(null)
    private val settingsRepository = SettingsRepositoryMock()
    private val inAppUpdateProvider = FakeInAppUpdateProvider()
    private val urlLauncher = mockk<UrlLauncher>(relaxed = true)

    private fun createPresenter(
        updateChannel: AppUpdateChannel = AppUpdateChannel.GITHUB_RELEASES,
        isNodeApp: Boolean = false,
    ): ReleaseUpdatePresenter {
        coEvery { urlLauncher.openUrl(any()) } returns true
        val releaseUpdateService =
            ReleaseUpdateService(
                FakeReleaseNotificationsServiceFacade(notifications),
                FakeTradeRestrictingAlertServiceFacade(minVersionRequiredForTrading),
                settingsRepository,
                FixedVersionProvider(RUNNING_VERSION),
                testDispatcher,
            )
        return ReleaseUpdatePresenter(
            MainPresenterTestFactory.create(urlLauncher = urlLauncher),
            releaseUpdateService,
            inAppUpdateProvider,
            FakeAppUpdateLinker(updateChannel = updateChannel),
            isNodeApp,
        )
    }

    @Test
    fun `shows nothing while no newer release exists`() =
        runTest {
            notifications.value = listOf(notification(RUNNING_VERSION))
            val probe = probeStateFlow(createPresenter().uiState)
            advanceUntilIdle()

            assertEquals(ReleaseUpdateVisibility.HIDDEN, probe.latest().visibility)
            probe.cancel()
        }

    @Test
    fun `an install the store cannot update links to the page for its channel`() =
        runTest {
            notifications.value = listOf(notification("0.15.0"))
            val probe = probeStateFlow(createPresenter(updateChannel = AppUpdateChannel.GITHUB_RELEASES).uiState)
            advanceUntilIdle()

            val state = probe.latest()
            assertEquals(ReleaseUpdateVisibility.CARD, state.visibility)
            assertEquals("0.15.0", state.versionString)
            assertEquals("notes 0.15.0", state.releaseNotes)
            assertEquals(PlayUpdatePhase.NOT_APPLICABLE, state.playPhase)
            assertEquals(AppUpdateChannel.GITHUB_RELEASES, state.externalLinkChannel)
            assertTrue(state.isDismissVisible)
            probe.cancel()
        }

    @Test
    fun `a Play install falls back to the store listing while Play offers no in-app update`() =
        runTest {
            notifications.value = listOf(notification("0.15.0"))
            val probe = probeStateFlow(createPresenter(updateChannel = AppUpdateChannel.PLAY_STORE).uiState)
            advanceUntilIdle()

            assertEquals(PlayUpdatePhase.NOT_APPLICABLE, probe.latest().playPhase)
            assertEquals(AppUpdateChannel.PLAY_STORE, probe.latest().externalLinkChannel)
            probe.cancel()
        }

    @Test
    fun `follows the in-app update through download and restart`() =
        runTest {
            notifications.value = listOf(notification("0.15.0"))
            val presenter = createPresenter(updateChannel = AppUpdateChannel.PLAY_STORE)
            val probe = probeStateFlow(presenter.uiState)

            inAppUpdateProvider.state.value = InAppUpdateState.Available
            advanceUntilIdle()
            assertEquals(PlayUpdatePhase.AVAILABLE, probe.latest().playPhase)
            presenter.onAction(ReleaseUpdateUiAction.OnStartInAppUpdate)
            assertEquals(1, inAppUpdateProvider.startCount)

            inAppUpdateProvider.state.value = InAppUpdateState.Downloading(0.62f)
            advanceUntilIdle()
            assertEquals(PlayUpdatePhase.DOWNLOADING, probe.latest().playPhase)
            assertEquals(0.62f, probe.latest().downloadProgress)
            assertFalse(probe.latest().isDismissVisible)

            inAppUpdateProvider.state.value = InAppUpdateState.ReadyToInstall
            advanceUntilIdle()
            assertEquals(PlayUpdatePhase.READY_TO_RESTART, probe.latest().playPhase)
            assertFalse(probe.latest().isDismissVisible)
            presenter.onAction(ReleaseUpdateUiAction.OnRestartToUpdate)
            assertEquals(1, inAppUpdateProvider.completeCount)
            probe.cancel()
        }

    @Test
    fun `a failed download offers dismiss again and retries through the same action`() =
        runTest {
            notifications.value = listOf(notification("0.15.0"))
            val presenter = createPresenter(updateChannel = AppUpdateChannel.PLAY_STORE)
            val probe = probeStateFlow(presenter.uiState)
            inAppUpdateProvider.state.value = InAppUpdateState.Failed
            advanceUntilIdle()

            assertEquals(PlayUpdatePhase.FAILED, probe.latest().playPhase)
            assertTrue(probe.latest().isDismissVisible)
            presenter.onAction(ReleaseUpdateUiAction.OnStartInAppUpdate)
            assertEquals(1, inAppUpdateProvider.startCount)
            probe.cancel()
        }

    @Test
    fun `dismissing moves the version to the resources entry and persists it`() =
        runTest {
            notifications.value = listOf(notification("0.15.0"))
            val presenter = createPresenter()
            val probe = probeStateFlow(presenter.uiState)
            advanceUntilIdle()
            presenter.onAction(ReleaseUpdateUiAction.OnOpenReleaseNotes)

            presenter.onAction(ReleaseUpdateUiAction.OnDismissVersion("0.15.0"))
            advanceUntilIdle()

            assertEquals(ReleaseUpdateVisibility.DISMISSED, probe.latest().visibility)
            assertFalse(probe.latest().isReleaseNotesSheetVisible)
            assertFalse(probe.latest().isDismissVisible)
            assertEquals(setOf("0.15.0"), settingsRepository.data.value.ignoredReleaseVersions)
            probe.cancel()
        }

    @Test
    fun `a download in progress keeps the card even for an ignored version`() =
        runTest {
            settingsRepository.mutableData.value = Settings(ignoredReleaseVersions = setOf("0.15.0"))
            notifications.value = listOf(notification("0.15.0"))
            val probe = probeStateFlow(createPresenter(updateChannel = AppUpdateChannel.PLAY_STORE).uiState)

            inAppUpdateProvider.state.value = InAppUpdateState.Downloading(0.1f)
            advanceUntilIdle()
            assertEquals(ReleaseUpdateVisibility.CARD, probe.latest().visibility)

            inAppUpdateProvider.state.value = InAppUpdateState.ReadyToInstall
            advanceUntilIdle()
            assertEquals(ReleaseUpdateVisibility.CARD, probe.latest().visibility)
            probe.cancel()
        }

    @Test
    fun `a mandatory update cannot be dismissed and overrides an earlier dismiss`() =
        runTest {
            settingsRepository.mutableData.value = Settings(ignoredReleaseVersions = setOf("0.15.0"))
            notifications.value = listOf(notification("0.15.0"))
            minVersionRequiredForTrading.value = "2.1.14"
            val probe = probeStateFlow(createPresenter().uiState)
            advanceUntilIdle()

            val state = probe.latest()
            assertEquals(ReleaseUpdateVisibility.CARD, state.visibility)
            assertTrue(state.isMandatory)
            assertEquals("2.1.14", state.minVersionString)
            assertFalse(state.isDismissVisible)
            probe.cancel()
        }

    @Test
    fun `the external link opens the destination the linker resolves`() =
        runTest {
            notifications.value = listOf(notification("0.15.0"))
            val presenter = createPresenter()

            presenter.onAction(ReleaseUpdateUiAction.OnOpenExternalUpdateLink)
            advanceUntilIdle()

            coVerify(exactly = 1) { urlLauncher.openUrl(TEST_APP_UPDATE_URL) }
        }

    @Test
    fun `asks the store only once a newer release is known`() =
        runTest {
            val presenter = createPresenter(updateChannel = AppUpdateChannel.PLAY_STORE)
            presenter.onViewAttached()
            advanceUntilIdle()
            assertEquals(0, inAppUpdateProvider.refreshCount)

            notifications.value = listOf(notification("0.15.0"))
            advanceUntilIdle()
            assertEquals(1, inAppUpdateProvider.refreshCount)

            notifications.value = listOf(notification("0.15.0"), notification("0.16.0"))
            advanceUntilIdle()
            assertEquals(2, inAppUpdateProvider.refreshCount)
            presenter.onViewUnattaching()
        }

    @Test
    fun `the release notes sheet opens and closes`() =
        runTest {
            notifications.value = listOf(notification("0.15.0"))
            val presenter = createPresenter(isNodeApp = true)
            val probe = probeStateFlow(presenter.uiState)
            advanceUntilIdle()
            assertTrue(probe.latest().isNodeApp)

            presenter.onAction(ReleaseUpdateUiAction.OnOpenReleaseNotes)
            advanceUntilIdle()
            assertTrue(probe.latest().isReleaseNotesSheetVisible)

            presenter.onAction(ReleaseUpdateUiAction.OnCloseReleaseNotes)
            advanceUntilIdle()
            assertFalse(probe.latest().isReleaseNotesSheetVisible)
            probe.cancel()
        }

    private fun notification(version: String) =
        ReleaseNotification(
            id = "id-$version",
            version = requireNotNull(ReleaseVersion.parse(version)),
            releaseNotes = "notes $version",
            isPreRelease = false,
            date = 0,
        )

    private class FakeInAppUpdateProvider : InAppUpdateProvider {
        override val state = MutableStateFlow<InAppUpdateState>(InAppUpdateState.Unavailable)
        var refreshCount = 0
        var startCount = 0
        var completeCount = 0

        override fun refresh() {
            refreshCount++
        }

        override fun startUpdate() {
            startCount++
        }

        override fun completeUpdate() {
            completeCount++
        }
    }

    private class FakeReleaseNotificationsServiceFacade(
        override val releaseNotifications: StateFlow<List<ReleaseNotification>>,
    ) : ReleaseNotificationsServiceFacade()

    private class FakeTradeRestrictingAlertServiceFacade(
        override val minVersionRequiredForTrading: StateFlow<String?>,
    ) : TradeRestrictingAlertServiceFacade() {
        override val alert: StateFlow<AuthorizedAlertData?> = MutableStateFlow(null)
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

    private companion object {
        const val RUNNING_VERSION = "0.14.2"
    }
}
