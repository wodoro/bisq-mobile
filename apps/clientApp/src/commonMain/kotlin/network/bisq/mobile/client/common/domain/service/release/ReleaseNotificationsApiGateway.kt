package network.bisq.mobile.client.common.domain.service.release

import network.bisq.mobile.client.common.domain.APP_TYPE
import network.bisq.mobile.client.common.domain.websocket.WebSocketClientService
import network.bisq.mobile.client.common.domain.websocket.subscription.Topic
import network.bisq.mobile.client.common.domain.websocket.subscription.WebSocketEventObserver

class ReleaseNotificationsApiGateway(
    private val webSocketClientService: WebSocketClientService,
) {
    // The node matches the app type strictly, so only Bisq Connect releases come back.
    suspend fun subscribeReleaseNotifications(): WebSocketEventObserver = webSocketClientService.subscribe(Topic.RELEASE_NOTIFICATIONS, APP_TYPE)
}
