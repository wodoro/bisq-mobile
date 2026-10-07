package network.bisq.mobile.client.common.data.model.release

import kotlinx.serialization.Serializable

/** Mirrors bisq2's `ReleaseNotificationDto`, served on the RELEASE_NOTIFICATIONS topic. */
@Serializable
data class ReleaseNotificationDto(
    val id: String,
    val date: Long,
    // No default: a payload without the flag must not pass as a final release.
    val isPreRelease: Boolean,
    val releaseNotes: String = "",
    val versionString: String,
)
