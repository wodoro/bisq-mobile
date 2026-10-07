package network.bisq.mobile.domain.service.app_update

import network.bisq.mobile.domain.model.release.ReleaseNotification
import network.bisq.mobile.domain.model.release.ReleaseVersion

/**
 * The newer release the app should offer.
 *
 * @param minVersionRequiredForTrading the bisq2 version a security-manager alert requires while
 * this app's trades are blocked. Non-null makes the update mandatory.
 * @param isDismissed the user ignored this version. Never true for a mandatory update.
 */
data class ReleaseUpdate(
    val version: String,
    val releaseNotes: String,
    val minVersionRequiredForTrading: String?,
    val isDismissed: Boolean,
) {
    val isMandatory: Boolean get() = minVersionRequiredForTrading != null
}

/**
 * Picks the release to offer the way desktop's `UpdaterService` does: only versions strictly above
 * the running one, never a pre-release (mobile publishes none), and the highest version rather than
 * the most recently received one. Delivery order over the P2P network is no ordering at all, and a
 * replayed notification for an older release must not win.
 */
fun selectReleaseUpdate(
    notifications: List<ReleaseNotification>,
    runningVersion: ReleaseVersion,
): ReleaseNotification? =
    notifications
        .filter { !it.isPreRelease && it.version > runningVersion }
        .maxByOrNull { it.version }
