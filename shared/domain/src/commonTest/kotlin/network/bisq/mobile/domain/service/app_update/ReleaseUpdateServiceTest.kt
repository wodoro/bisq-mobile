package network.bisq.mobile.domain.service.app_update

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import network.bisq.mobile.data.model.Settings
import network.bisq.mobile.data.service.alert.TradeRestrictingAlertServiceFacade
import network.bisq.mobile.data.service.release.ReleaseNotificationsServiceFacade
import network.bisq.mobile.domain.model.alert.AuthorizedAlertData
import network.bisq.mobile.domain.model.release.ReleaseNotification
import network.bisq.mobile.domain.model.release.ReleaseVersion
import network.bisq.mobile.domain.utils.VersionProvider
import network.bisq.mobile.test.mocks.SettingsRepositoryMock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReleaseUpdateServiceTest {
    private val releaseNotificationsFacade = FakeReleaseNotificationsServiceFacade()
    private val tradeRestrictingAlertFacade = FakeTradeRestrictingAlertServiceFacade()
    private val settingsRepository = SettingsRepositoryMock()

    private fun TestScope.createService(appVersion: String = RUNNING_VERSION) =
        ReleaseUpdateService(
            releaseNotificationsFacade,
            tradeRestrictingAlertFacade,
            settingsRepository,
            FixedVersionProvider(appVersion),
            UnconfinedTestDispatcher(testScheduler),
        )

    @Test
    fun `offers the newest applicable release`() =
        runTest {
            val service = createService()
            releaseNotificationsFacade.releaseNotifications.value = listOf(notification("0.15.0"), notification("0.16.0"))

            val update = assertNotNull(service.releaseUpdate.value)
            assertEquals("0.16.0", update.version)
            assertEquals("notes 0.16.0", update.releaseNotes)
            assertFalse(update.isMandatory)
            assertFalse(update.isDismissed)
        }

    @Test
    fun `offers nothing when no release is newer than the running app`() =
        runTest {
            val service = createService()
            releaseNotificationsFacade.releaseNotifications.value = listOf(notification(RUNNING_VERSION))

            assertNull(service.releaseUpdate.value)
        }

    @Test
    fun `a dismissed version stays dismissed and is persisted`() =
        runTest {
            val service = createService()
            releaseNotificationsFacade.releaseNotifications.value = listOf(notification("0.15.0"))

            service.dismiss("0.15.0")

            assertTrue(assertNotNull(service.releaseUpdate.value).isDismissed)
            assertEquals(setOf("0.15.0"), settingsRepository.data.value.ignoredReleaseVersions)
        }

    @Test
    fun `dismissing one version does not hide a newer one`() =
        runTest {
            val service = createService()
            releaseNotificationsFacade.releaseNotifications.value = listOf(notification("0.15.0"))
            service.dismiss("0.15.0")

            releaseNotificationsFacade.releaseNotifications.value = listOf(notification("0.15.0"), notification("0.16.0"))

            val update = assertNotNull(service.releaseUpdate.value)
            assertEquals("0.16.0", update.version)
            assertFalse(update.isDismissed)
        }

    @Test
    fun `an ignored version matches regardless of trailing zeros`() =
        runTest {
            settingsRepository.mutableData.value = Settings(ignoredReleaseVersions = setOf("0.15"))
            val service = createService()

            releaseNotificationsFacade.releaseNotifications.value = listOf(notification("0.15.0"))

            assertTrue(assertNotNull(service.releaseUpdate.value).isDismissed)
        }

    @Test
    fun `a trading block makes the update mandatory and overrides an earlier dismiss`() =
        runTest {
            settingsRepository.mutableData.value = Settings(ignoredReleaseVersions = setOf("0.15.0"))
            val service = createService()
            releaseNotificationsFacade.releaseNotifications.value = listOf(notification("0.15.0"))
            assertTrue(assertNotNull(service.releaseUpdate.value).isDismissed)

            tradeRestrictingAlertFacade.minVersionRequiredForTrading.value = "2.1.14"

            val update = assertNotNull(service.releaseUpdate.value)
            assertTrue(update.isMandatory)
            assertEquals("2.1.14", update.minVersionRequiredForTrading)
            assertFalse(update.isDismissed)
        }

    @Test
    fun `dismissing drops ignored versions that can no longer be offered`() =
        runTest {
            settingsRepository.mutableData.value = Settings(ignoredReleaseVersions = setOf("0.13.0", RUNNING_VERSION, "0.15.0", "not-a-version"))
            val service = createService()

            service.dismiss("0.16.0")

            assertEquals(setOf("0.15.0", "0.16.0"), settingsRepository.data.value.ignoredReleaseVersions)
        }

    @Test
    fun `a malformed version is never persisted`() =
        runTest {
            val service = createService()

            service.dismiss("v0.15.0")

            assertEquals(emptySet(), settingsRepository.data.value.ignoredReleaseVersions)
        }

    @Test
    fun `offers nothing when the running version cannot be parsed`() =
        runTest {
            val service = createService(appVersion = "unknown")
            releaseNotificationsFacade.releaseNotifications.value = listOf(notification("99.0.0"))

            assertNull(service.releaseUpdate.value)
        }

    private fun notification(version: String) =
        ReleaseNotification(
            id = "id-$version",
            version = requireNotNull(ReleaseVersion.parse(version)),
            releaseNotes = "notes $version",
            isPreRelease = false,
            date = 0,
        )

    private class FakeReleaseNotificationsServiceFacade : ReleaseNotificationsServiceFacade() {
        override val releaseNotifications = MutableStateFlow<List<ReleaseNotification>>(emptyList())
    }

    private class FakeTradeRestrictingAlertServiceFacade : TradeRestrictingAlertServiceFacade() {
        override val alert: StateFlow<AuthorizedAlertData?> = MutableStateFlow(null)
        override val minVersionRequiredForTrading = MutableStateFlow<String?>(null)
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
