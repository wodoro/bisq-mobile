package network.bisq.mobile.domain.service.app_update

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import network.bisq.mobile.data.service.alert.TradeRestrictingAlertServiceFacade
import network.bisq.mobile.data.service.release.ReleaseNotificationsServiceFacade
import network.bisq.mobile.domain.model.release.ReleaseVersion
import network.bisq.mobile.domain.repository.SettingsRepository
import network.bisq.mobile.domain.utils.VersionProvider

/**
 * Decides which release, if any, the app offers, and remembers the versions the user ignored.
 *
 * Process-lifetime, because both the dashboard card and the resources screen read the answer.
 */
class ReleaseUpdateService(
    releaseNotificationsServiceFacade: ReleaseNotificationsServiceFacade,
    tradeRestrictingAlertServiceFacade: TradeRestrictingAlertServiceFacade,
    private val settingsRepository: SettingsRepository,
    versionProvider: VersionProvider,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    // Null only if the build's own version string were malformed. No update is offered then,
    // since nothing can be said to be newer than it.
    private val runningVersion: ReleaseVersion? = ReleaseVersion.parse(versionProvider.getAppVersion())

    val releaseUpdate: StateFlow<ReleaseUpdate?> =
        combine(
            releaseNotificationsServiceFacade.releaseNotifications,
            settingsRepository.data.map { it.ignoredReleaseVersions }.distinctUntilChanged(),
            tradeRestrictingAlertServiceFacade.minVersionRequiredForTrading,
        ) { notifications, ignoredVersions, minVersionRequiredForTrading ->
            val running = runningVersion ?: return@combine null
            val notification = selectReleaseUpdate(notifications, running) ?: return@combine null
            // Desktop skips its ignore cookie in exactly this case: a version ignored while optional
            // comes back, not dismissible, once trading requires an update.
            val isMandatory = minVersionRequiredForTrading != null
            ReleaseUpdate(
                version = notification.version.toString(),
                releaseNotes = notification.releaseNotes,
                minVersionRequiredForTrading = minVersionRequiredForTrading,
                isDismissed = !isMandatory && ignoredVersions.any { ReleaseVersion.parse(it) == notification.version },
            )
        }.stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun dismiss(version: String) {
        val running = runningVersion ?: return
        if (ReleaseVersion.parse(version) == null) return
        settingsRepository.update { settings ->
            // Versions at or below the running one can never be offered again, so they are dropped
            // here rather than kept forever.
            val stillRelevant =
                settings.ignoredReleaseVersions.filterTo(mutableSetOf()) { ignored ->
                    ReleaseVersion.parse(ignored)?.let { it > running } ?: false
                }
            settings.copy(ignoredReleaseVersions = stillRelevant + version)
        }
    }
}
