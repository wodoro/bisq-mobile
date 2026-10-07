package network.bisq.mobile.data.utils

object AppUpdateUrls {
    const val GITHUB_RELEASES = "https://github.com/bisq-network/bisq-mobile/releases"

    const val BISQ_CONNECT_IOS_INSTALL_PAGE =
        "https://bisq-network.github.io/bisq-mobile/"

    /** Prefer this on Android so the Play Store app opens directly when present. */
    fun playStoreMarketUrl(packageName: String): String = "market://details?id=$packageName"

    /** HTTPS listing — used when [playStoreMarketUrl] cannot be handled. */
    fun playStoreDetailsUrl(packageName: String): String = "https://play.google.com/store/apps/details?id=$packageName"
}

/** Where this install gets its updates from, detected at runtime rather than per build flavor. */
enum class AppUpdateChannel {
    /** Installed by Google Play, so Play can update it. */
    PLAY_STORE,

    /** Any other Android install (GitHub download, F-Droid, sideloaded APK of either flavor). */
    GITHUB_RELEASES,

    /** Bisq Connect on iOS, distributed through TestFlight and AltStore PAL. */
    IOS_INSTALL_PAGE,
}
