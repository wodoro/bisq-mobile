package network.bisq.mobile.node.common.domain.service.app_update

import bisq.persistence.PersistableStore
import bisq.persistence.PersistenceClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import network.bisq.mobile.node.common.domain.service.AndroidApplicationService
import java.util.concurrent.CompletableFuture

/**
 * Writes the identity and trade stores to disk right before Play installs an update.
 *
 * Their regular writes go through bisq2's `RateLimitedPersistenceClient`, which drops a write that
 * comes within a second of the previous one and catches up only in a JVM shutdown hook. Play's
 * install kills the process without running that hook, so the latest trade step or identity change
 * could otherwise be lost. Writing through each store's `Persistence` directly bypasses the limit;
 * bisq2 runs every write on one thread, so this queues behind any write already in flight.
 */
class UpdateRestartPersistence(
    private val provider: AndroidApplicationService.Provider,
) {
    suspend fun persistCriticalStores() {
        withContext(Dispatchers.IO) {
            CompletableFuture
                .allOf(
                    provider.identityService.get().persistNow(),
                    provider.userService
                        .get()
                        .userIdentityService
                        .persistNow(),
                    provider.tradeService
                        .get()
                        .bisqEasyTradeService
                        .persistNow(),
                ).await()
        }
    }
}

private fun <T : PersistableStore<T>> PersistenceClient<T>.persistNow(): CompletableFuture<Void> = persistence.persistAsync(persistableStore.clone)
