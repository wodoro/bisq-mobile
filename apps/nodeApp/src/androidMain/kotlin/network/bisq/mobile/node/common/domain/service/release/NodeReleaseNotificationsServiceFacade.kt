package network.bisq.mobile.node.common.domain.service.release

import bisq.bonded_roles.release.AppType
import bisq.common.observable.Pin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import network.bisq.mobile.data.service.release.ReleaseNotificationsServiceFacade
import network.bisq.mobile.domain.model.release.ReleaseNotification
import network.bisq.mobile.node.common.domain.mapping.release.toDomainOrNull
import network.bisq.mobile.node.common.domain.service.AndroidApplicationService

/**
 * Reads the release notifications the embedded bisq2 core has received and verified as signed by
 * the release manager.
 */
class NodeReleaseNotificationsServiceFacade(
    private val provider: AndroidApplicationService.Provider,
) : ReleaseNotificationsServiceFacade() {
    private val releaseNotificationsService by lazy { provider.bondedRolesService.get().releaseNotificationsService }

    private val _releaseNotifications = MutableStateFlow<List<ReleaseNotification>>(emptyList())
    override val releaseNotifications: StateFlow<List<ReleaseNotification>> = _releaseNotifications.asStateFlow()

    private var releaseNotificationsPin: Pin? = null

    override suspend fun activate() {
        super.activate()

        // The Runnable observer fires once at subscription, covering the initial refresh
        releaseNotificationsPin =
            releaseNotificationsService.releaseNotifications.addObserver(Runnable { refreshReleaseNotifications() })
    }

    override suspend fun deactivate() {
        releaseNotificationsPin?.unbind()
        releaseNotificationsPin = null
        _releaseNotifications.value = emptyList()
        super.deactivate()
    }

    private fun refreshReleaseNotifications() {
        // Strict match, as desktop's UpdaterService does: a notification without an app type, or
        // for another app, announces no release of this one.
        _releaseNotifications.value =
            releaseNotificationsService.releaseNotifications
                .filter { it.appType == AppType.MOBILE_NODE }
                .mapNotNull { it.toDomainOrNull() }
    }
}
