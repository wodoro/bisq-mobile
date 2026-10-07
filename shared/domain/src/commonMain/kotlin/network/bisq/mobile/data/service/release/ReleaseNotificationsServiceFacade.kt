package network.bisq.mobile.data.service.release

import kotlinx.coroutines.flow.StateFlow
import network.bisq.mobile.data.service.ServiceFacade
import network.bisq.mobile.domain.model.release.ReleaseNotification

abstract class ReleaseNotificationsServiceFacade : ServiceFacade() {
    /**
     * Every release notification currently published for this app's `AppType`, in no particular
     * order and not yet filtered by version or pre-release flag.
     */
    abstract val releaseNotifications: StateFlow<List<ReleaseNotification>>
}
