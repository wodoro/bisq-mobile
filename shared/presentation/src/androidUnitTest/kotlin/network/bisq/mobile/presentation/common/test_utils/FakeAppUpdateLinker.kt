package network.bisq.mobile.presentation.common.test_utils

import network.bisq.mobile.data.utils.AppUpdateChannel
import network.bisq.mobile.data.utils.AppUpdateLinker

/** Sentinel URL for presenter tests — must not match production release destinations. */
const val TEST_APP_UPDATE_URL = "https://example.test/bisq-app-update"

class FakeAppUpdateLinker(
    private val updateUrl: String = TEST_APP_UPDATE_URL,
    private val updateChannel: AppUpdateChannel = AppUpdateChannel.GITHUB_RELEASES,
) : AppUpdateLinker {
    override fun getUpdateChannel(): AppUpdateChannel = updateChannel

    override fun getUpdateUrl(): String = updateUrl
}
