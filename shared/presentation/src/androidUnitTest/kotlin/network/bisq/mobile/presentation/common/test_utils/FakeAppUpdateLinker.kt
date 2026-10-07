package network.bisq.mobile.presentation.common.test_utils

import network.bisq.mobile.data.utils.AppUpdateChannel
import network.bisq.mobile.data.utils.AppUpdateLinker

/**
 * Records [openUpdatePage] calls instead of opening anything.
 *
 * @param opens what [openUpdatePage] reports, false to exercise the cannot-open feedback.
 */
class FakeAppUpdateLinker(
    private val updateChannel: AppUpdateChannel = AppUpdateChannel.GITHUB_RELEASES,
    private val opens: Boolean = true,
) : AppUpdateLinker {
    var openUpdatePageCount = 0
        private set

    override fun getUpdateChannel(): AppUpdateChannel = updateChannel

    override suspend fun openUpdatePage(): Boolean {
        openUpdatePageCount++
        return opens
    }
}
