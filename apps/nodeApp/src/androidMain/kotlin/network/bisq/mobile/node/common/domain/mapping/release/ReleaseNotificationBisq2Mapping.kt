package network.bisq.mobile.node.common.domain.mapping.release

import network.bisq.mobile.domain.model.release.ReleaseNotification
import network.bisq.mobile.domain.model.release.ReleaseVersion
import bisq.bonded_roles.release.ReleaseNotification as ReleaseNotificationBisq2

fun ReleaseNotificationBisq2.toDomainOrNull(): ReleaseNotification? {
    // bisq2 already rejects malformed versions on the network; this only keeps the domain total.
    val version = ReleaseVersion.parse(versionString) ?: return null
    return ReleaseNotification(
        id = id,
        version = version,
        releaseNotes = releaseNotes,
        isPreRelease = isPreRelease,
        date = date,
    )
}
