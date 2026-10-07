package network.bisq.mobile.domain.utils

interface VersionProvider {
    fun getVersionInfo(
        isDemo: Boolean,
        isIOS: Boolean,
    ): String

    fun getAppNameAndVersion(
        isDemo: Boolean,
        isIOS: Boolean,
    ): String

    /** The plain version of the running app, e.g. "0.14.2", as release notifications state it. */
    fun getAppVersion(): String
}
