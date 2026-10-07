package network.bisq.mobile.data.utils

object AppUpdateUrls {
    const val GITHUB_RELEASES = "https://github.com/bisq-network/bisq-mobile/releases"

    const val BISQ_CONNECT_IOS_INSTALL_PAGE =
        "https://bisq-network.github.io/bisq-mobile/"

    /**
     * Store page of [packageName]. Store apps answer it, Play and F-Droid included, so aim the intent
     * at one when only that store can update the install.
     */
    fun marketDetailsUrl(packageName: String): String = "market://details?id=$packageName"

    /** HTTPS listing, used when no app handles [marketDetailsUrl]. */
    fun playStoreDetailsUrl(packageName: String): String = "https://play.google.com/store/apps/details?id=$packageName"
}

/** Where this install gets its updates from, detected at runtime rather than per build flavor. */
enum class AppUpdateChannel {
    /** Installed by Google Play, so Play can update it. */
    PLAY_STORE,

    /** Installed by the official F-Droid client, which updates it from its repository. */
    FDROID,

    /** Any other Android install: a GitHub download or another sideloaded APK of either flavor. */
    GITHUB_RELEASES,

    /** Bisq Connect on iOS, distributed through TestFlight and AltStore PAL. */
    IOS_INSTALL_PAGE,
}
