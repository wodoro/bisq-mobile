package network.bisq.mobile.client.common.domain.service.release

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import network.bisq.mobile.client.common.di.clientJson
import network.bisq.mobile.client.common.domain.websocket.messages.WebSocketEvent
import network.bisq.mobile.client.common.domain.websocket.subscription.ModificationType
import network.bisq.mobile.client.common.domain.websocket.subscription.Topic
import network.bisq.mobile.client.common.domain.websocket.subscription.WebSocketEventObserver
import network.bisq.mobile.client.common.test_utils.ClientKoinIntegrationTestBase
import network.bisq.mobile.domain.service.capabilities.BackendCapabilities
import network.bisq.mobile.domain.service.capabilities.BackendCapabilitiesService
import network.bisq.mobile.domain.service.capabilities.Feature
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ClientReleaseNotificationsServiceFacadeTest : ClientKoinIntegrationTestBase() {
    private val apiGateway: ReleaseNotificationsApiGateway = mockk()
    private val capabilities = MutableStateFlow(BackendCapabilities(setOf(Feature.RELEASE_NOTIFICATIONS.key)))
    private val backendCapabilitiesService: BackendCapabilitiesService =
        mockk { every { this@mockk.capabilities } returns this@ClientReleaseNotificationsServiceFacadeTest.capabilities }
    private val observer = WebSocketEventObserver()
    private var sequence = 0

    private lateinit var facade: ClientReleaseNotificationsServiceFacade

    override fun onSetup() {
        coEvery { apiGateway.subscribeReleaseNotifications() } returns observer
        facade = ClientReleaseNotificationsServiceFacade(apiGateway, backendCapabilitiesService, clientJson, testDispatcher)
    }

    @Test
    fun `subscribes to nothing when the node does not advertise the feature`() =
        runTest {
            capabilities.value = BackendCapabilities(setOf(Feature.CONTACTS.key))

            facade.activate()
            advanceUntilIdle()

            coVerify(exactly = 0) { apiGateway.subscribeReleaseNotifications() }
            assertTrue(facade.releaseNotifications.value.isEmpty())
        }

    @Test
    fun `subscribes once the node advertises the feature after activation`() =
        runTest {
            capabilities.value = BackendCapabilities(emptySet())
            facade.activate()
            advanceUntilIdle()

            capabilities.value = BackendCapabilities(setOf(Feature.RELEASE_NOTIFICATIONS.key))
            advanceUntilIdle()

            coVerify(exactly = 1) { apiGateway.subscribeReleaseNotifications() }
        }

    @Test
    fun `maps the payload and drops entries whose version does not parse`() =
        runTest {
            facade.activate()
            advanceUntilIdle()

            emit(
                """
                [
                  {"id":"a","date":1,"isPreRelease":false,"releaseNotes":"Notes","versionString":"0.11.0","appType":"MOBILE_CLIENT"},
                  {"id":"b","date":2,"isPreRelease":true,"releaseNotes":"","versionString":"0.12.0-rc1","appType":"MOBILE_CLIENT"}
                ]
                """.trimIndent(),
            )
            advanceUntilIdle()

            val notification = facade.releaseNotifications.value.single()
            assertEquals("a", notification.id)
            assertEquals("0.11.0", notification.version.toString())
            assertEquals("Notes", notification.releaseNotes)
        }

    @Test
    fun `every push replaces the previous list`() =
        runTest {
            facade.activate()
            advanceUntilIdle()
            emit("""[{"id":"a","date":1,"isPreRelease":false,"releaseNotes":"","versionString":"0.11.0"}]""")
            advanceUntilIdle()

            emit("[]")
            advanceUntilIdle()

            assertTrue(facade.releaseNotifications.value.isEmpty())
        }

    private suspend fun emit(payload: String) {
        observer.setEvent(
            WebSocketEvent(
                topic = Topic.RELEASE_NOTIFICATIONS,
                subscriberId = "release-notifications",
                deferredPayload = payload,
                modificationType = ModificationType.REPLACE,
                sequenceNumber = sequence++,
            ),
        )
    }
}
