package network.bisq.mobile.client.common.data.model.release

import kotlinx.serialization.Serializable

/** Mirrors bisq2's `ReleaseNotificationDto`, served on the RELEASE_NOTIFICATIONS topic. */
@Serializable
data class ReleaseNotificationDto(
    val id: String,
    val date: Long,
    val isPreRelease: Boolean = false,
    val releaseNotes: String = "",
    val versionString: String,
)
