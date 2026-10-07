package network.bisq.mobile.client.common.domain.httpclient

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import network.bisq.mobile.client.common.domain.sensitive_settings.SensitiveSettings
import network.bisq.mobile.client.common.domain.sensitive_settings.SensitiveSettingsRepository
import network.bisq.mobile.client.common.test_utils.ClientKoinIntegrationTestBase
import network.bisq.mobile.data.service.network.KmpTorService
import network.bisq.mobile.domain.utils.OperationCancelledException
import network.bisq.mobile.domain.utils.VersionProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HttpClientServiceTest : ClientKoinIntegrationTestBase() {
    private lateinit var kmpTorService: KmpTorService
    private lateinit var settingsRepository: SensitiveSettingsRepository

    override fun onSetup() {
        kmpTorService = mockk(relaxed = true)
        every { kmpTorService.state } returns MutableStateFlow(KmpTorService.TorState.Stopped())

        settingsRepository =
            object : SensitiveSettingsRepository {
                private val _data = MutableStateFlow(SensitiveSettings())
                override val data = _data

                override suspend fun fetch() = _data.value

                override suspend fun update(transform: suspend (SensitiveSettings) -> SensitiveSettings) {
                    _data.value = transform(_data.value)
                }

                override suspend fun clear() {
                    _data.value = SensitiveSettings()
                }
            }
    }

    private fun createService(): HttpClientService =
        HttpClientService(
            kmpTorService = kmpTorService,
            sensitiveSettingsRepository = settingsRepository,
            json = Json { ignoreUnknownKeys = true },
            versionProvider =
                object : VersionProvider {
                    override fun getVersionInfo(
                        isDemo: Boolean,
                        isIOS: Boolean,
                    ) = "test/1.0"

                    override fun getAppNameAndVersion(
                        isDemo: Boolean,
                        isIOS: Boolean,
                    ) = "test/1.0"

                    override fun getAppVersion() = "1.0"
                },
            defaultHost = "127.0.0.1",
            defaultPort = 8090,
        )

    // The collector runs on Dispatchers.Default; wait until it has emitted the expected settings
    private suspend fun HttpClientService.awaitSettings(predicate: (HttpClientSettings) -> Boolean) {
        withTimeout(5_000) { httpClientChangedFlow.first(predicate) }
    }

    private suspend fun HttpClientService.activateWithSession(sessionId: String) {
        activate()
        settingsRepository.update {
            it.copy(bisqApiUrl = "http://127.0.0.1:8090", clientId = "client-id", sessionId = sessionId, sessionExpiresAt = 1_000L)
        }
        awaitSettings { it.sessionId == sessionId }
    }

    @Test
    fun `keeps the http client when only the session changes`() =
        runBlocking {
            val service = createService()
            service.activateWithSession("session-1")
            val clientBefore = service.getClient()

            settingsRepository.update { it.copy(sessionId = "session-2", sessionExpiresAt = 2_000L) }
            service.awaitSettings { it.sessionId == "session-2" }

            assertSame(clientBefore, service.getClient())
            service.deactivate()
        }

    @Test
    fun `session-only change still unblocks awaitClientReady`() =
        runBlocking {
            val service = createService()
            service.activateWithSession("session-1")

            // Undispatched so the generation is read before the settings change
            val ready = async(start = CoroutineStart.UNDISPATCHED) { service.awaitClientReady(timeoutMs = 5_000) }
            settingsRepository.update { it.copy(sessionId = "session-2") }

            assertTrue(ready.await())
            service.deactivate()
        }

    @Test
    fun `rebuilds the http client when the api url changes`() =
        runBlocking {
            val service = createService()
            service.activateWithSession("session-1")
            val clientBefore = service.getClient()

            settingsRepository.update { it.copy(bisqApiUrl = "http://127.0.0.1:9090") }
            service.awaitSettings { it.bisqApiUrl == "http://127.0.0.1:9090" }

            assertNotSame(clientBefore, service.getClient())
            service.deactivate()
        }

    @Test
    fun `rebuilds the http client after a deactivate and activate`() =
        runBlocking {
            val service = createService()
            service.activateWithSession("session-1")
            val clientBefore = service.getClient()

            service.deactivate()
            service.activate()
            service.awaitSettings { it.sessionId == "session-1" }

            assertNotSame(clientBefore, service.getClient())
            service.deactivate()
        }

    @Test
    fun `deactivate drops the replayed settings`() =
        runBlocking {
            val service = createService()
            service.activateWithSession("session-1")

            service.deactivate()

            assertTrue(service.httpClientChangedFlow.replayCache.isEmpty())
        }

    @Test
    fun `getClient called between deactivate and activate waits for the new client`() =
        runBlocking {
            val service = createService()
            service.activateWithSession("session-1")
            service.deactivate()

            // On a restart the bootstrap requests the session before this service is activated.
            // Unconfined so getClient() subscribes before activate().
            val pending = async(Dispatchers.Unconfined) { runCatching { service.getClient() } }
            service.activate()

            assertTrue(withTimeout(5_000) { pending.await() }.isSuccess)
            service.deactivate()
        }

    @Test
    fun `deactivate cancels a pending getClient`() =
        runBlocking<Unit> {
            val service = createService()

            val pending = async(Dispatchers.Unconfined) { runCatching { service.getClient() } }
            service.deactivate()

            assertIs<OperationCancelledException>(pending.await().exceptionOrNull())
        }

    @Test
    fun `renewClient replaces the client without announcing a settings change`() =
        runBlocking {
            val service = createService()
            service.activateWithSession("session-1")
            val clientBefore = service.getClient()

            // Undispatched so the generation is read before the renewal
            val ready = async(start = CoroutineStart.UNDISPATCHED) { service.awaitClientReady(timeoutMs = 300) }
            val renewed = service.renewClient()

            assertNotSame(clientBefore, renewed)
            assertSame(renewed, service.getClient())
            assertFalse(ready.await())
            service.deactivate()
        }

    @Test
    fun `renewClient returns null before any settings`() =
        runBlocking {
            assertNull(createService().renewClient())
        }
}
