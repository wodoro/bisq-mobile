package network.bisq.mobile.node.common.domain.service.app_update

import androidx.core.util.Supplier
import bisq.identity.IdentityService
import bisq.persistence.PersistableStore
import bisq.persistence.Persistence
import bisq.trade.TradeService
import bisq.trade.bisq_easy.BisqEasyTradeService
import bisq.user.UserService
import bisq.user.identity.UserIdentityService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import network.bisq.mobile.node.common.domain.service.AndroidApplicationService
import java.io.IOException
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertFailsWith

class UpdateRestartPersistenceTest {
    // Relaxed so each store's clone is a mock of its own type.
    private val identityService = mockk<IdentityService>(relaxed = true)
    private val userIdentityService = mockk<UserIdentityService>(relaxed = true)
    private val bisqEasyTradeService = mockk<BisqEasyTradeService>(relaxed = true)

    // bisq2 keeps UserIdentityStore package-private. Any typed matcher on its Persistence would
    // name that class and fail with IllegalAccessError, so that one is stubbed by method name.
    private val userIdentityPersistence = mockk<Persistence<*>>()

    private val persistence =
        UpdateRestartPersistence(
            AndroidApplicationService.Provider().apply {
                identityService = Supplier { this@UpdateRestartPersistenceTest.identityService }
                userService = Supplier { mockk<UserService> { every { userIdentityService } returns this@UpdateRestartPersistenceTest.userIdentityService } }
                tradeService = Supplier { mockk<TradeService> { every { bisqEasyTradeService } returns this@UpdateRestartPersistenceTest.bisqEasyTradeService } }
            },
        )

    private fun succeedWrites() {
        every { identityService.persistence.persistAsync(any()) } returns CompletableFuture.completedFuture(null)
        every { userIdentityService["getPersistence"]() } returns userIdentityPersistence
        every { userIdentityPersistence["persistAsync"](any<PersistableStore<*>>()) } returns CompletableFuture.completedFuture(null)
        every { bisqEasyTradeService.persistence.persistAsync(any()) } returns CompletableFuture.completedFuture(null)
    }

    @Test
    fun `writes a snapshot of each store directly instead of through the rate-limited persist`() =
        runTest {
            succeedWrites()

            persistence.persistCriticalStores()

            verify(exactly = 1) { identityService.persistence.persistAsync(identityService.persistableStore.clone) }
            verify(exactly = 1) { userIdentityPersistence["persistAsync"](any<PersistableStore<*>>()) }
            verify(exactly = 1) { bisqEasyTradeService.persistence.persistAsync(bisqEasyTradeService.persistableStore.clone) }
            // persist() is the path RateLimitedPersistenceClient may drop.
            verify(exactly = 0) { identityService.persist() }
            verify(exactly = 0) { userIdentityService.persist() }
            verify(exactly = 0) { bisqEasyTradeService.persist() }
        }

    @Test
    fun `one failed write does not stop the others and is reported`() =
        runTest {
            succeedWrites()
            every { identityService.persistence.persistAsync(any()) } returns CompletableFuture.failedFuture(IOException("disk full"))

            assertFailsWith<IOException> { persistence.persistCriticalStores() }

            verify(exactly = 1) { userIdentityPersistence["persistAsync"](any<PersistableStore<*>>()) }
            verify(exactly = 1) { bisqEasyTradeService.persistence.persistAsync(any()) }
        }
}
