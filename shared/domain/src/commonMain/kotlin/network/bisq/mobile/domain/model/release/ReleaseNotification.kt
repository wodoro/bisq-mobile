package network.bisq.mobile.domain.model.release

/**
 * A release announcement signed by bisq2's release-manager bonded role, already scoped to this
 * app's `AppType`. It deliberately has no URL: where an update is fetched from is always resolved
 * by the app itself (see `AppUpdateLinker`), so the release manager cannot redirect it.
 */
data class ReleaseNotification(
    val id: String,
    val version: ReleaseVersion,
    val releaseNotes: String,
    val isPreRelease: Boolean,
    val date: Long,
)
