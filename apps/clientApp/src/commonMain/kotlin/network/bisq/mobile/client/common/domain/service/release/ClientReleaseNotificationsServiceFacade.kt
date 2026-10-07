package network.bisq.mobile.client.common.domain.service.release

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import network.bisq.mobile.client.common.data.mapping.release.toDomainOrNull
import network.bisq.mobile.client.common.data.model.release.ReleaseNotificationDto
import network.bisq.mobile.client.common.domain.websocket.subscription.collectPayloads
import network.bisq.mobile.data.service.release.ReleaseNotificationsServiceFacade
import network.bisq.mobile.domain.model.release.ReleaseNotification
import network.bisq.mobile.domain.service.capabilities.BackendCapabilitiesService
import network.bisq.mobile.domain.service.capabilities.Feature
import network.bisq.mobile.domain.utils.redactedSummary
import network.bisq.mobile.domain.utils.resultCatching

/**
 * Release notifications relayed by the trusted node. Connect does not verify the release manager's
 * signature itself; it trusts its paired node, as it does for security-manager alerts. A node that
 * lies can only mislabel the update card, never redirect it, since the update destination is
 * resolved app-side.
 *
 * Gated on [Feature.RELEASE_NOTIFICATIONS]: an older node has no such topic and would never answer
 * the subscription, so nothing is subscribed until the node advertises it.
 */
class ClientReleaseNotificationsServiceFacade(
    private val apiGateway: ReleaseNotificationsApiGateway,
    private val backendCapabilitiesService: BackendCapabilitiesService,
    private val json: Json,
    // Injectable so tests can drive the subscription collector on their virtual-time dispatcher.
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ReleaseNotificationsServiceFacade() {
    private val _releaseNotifications = MutableStateFlow<List<ReleaseNotification>>(emptyList())
    override val releaseNotifications: StateFlow<List<ReleaseNotification>> = _releaseNotifications.asStateFlow()

    override suspend fun activate() {
        super.activate()

        serviceScope.launch(defaultDispatcher) {
            // Awaited rather than read as a snapshot: this facade is activated before
            // ConfigServiceFacade fetches the capability manifest. On a node that never advertises
            // the feature this never resumes, and serviceScope is cancelled on deactivate.
            backendCapabilitiesService.capabilities.first { it.isSupported(Feature.RELEASE_NOTIFICATIONS) }

            resultCatching {
                subscribeReleaseNotifications()
            }.onFailure {
                log.w { "Failed to subscribe to release notifications: ${it.redactedSummary()}" }
            }
        }
    }

    override suspend fun deactivate() {
        // Cancelled before clearing, so a late event cannot repopulate the cleared list.
        super.deactivate()
        _releaseNotifications.value = emptyList()
    }

    private suspend fun subscribeReleaseNotifications() {
        val observer = apiGateway.subscribeReleaseNotifications()
        observer.collectPayloads<List<ReleaseNotificationDto>>(json) { payload, _ ->
            _releaseNotifications.value = payload.mapNotNull(ReleaseNotificationDto::toDomainOrNull)
        }
    }
}
