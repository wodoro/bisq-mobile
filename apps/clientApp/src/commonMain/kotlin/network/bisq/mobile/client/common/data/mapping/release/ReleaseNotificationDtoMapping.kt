package network.bisq.mobile.client.common.data.mapping.release

import network.bisq.mobile.client.common.data.model.release.ReleaseNotificationDto
import network.bisq.mobile.domain.model.release.ReleaseNotification
import network.bisq.mobile.domain.model.release.ReleaseVersion

fun ReleaseNotificationDto.toDomainOrNull(): ReleaseNotification? {
    val version = ReleaseVersion.parse(versionString) ?: return null
    return ReleaseNotification(
        id = id,
        version = version,
        releaseNotes = releaseNotes,
        isPreRelease = isPreRelease,
        date = date,
    )
}
