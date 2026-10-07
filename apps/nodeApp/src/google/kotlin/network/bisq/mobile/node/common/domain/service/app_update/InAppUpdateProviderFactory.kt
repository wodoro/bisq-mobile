package network.bisq.mobile.node.common.domain.service.app_update

import android.app.Application
import network.bisq.mobile.data.utils.AppUpdateChannel
import network.bisq.mobile.domain.service.app_update.InAppUpdateProvider
import network.bisq.mobile.domain.service.app_update.UnsupportedInAppUpdateProvider
import network.bisq.mobile.play_update.PlayInAppUpdateProvider

/**
 * Flavor seam for the in-app update, as in clientApp. Play can only update an install it made, so
 * a sideloaded APK of this flavor gets the unsupported provider and the card links to GitHub.
 *
 * @param prepareForRestart runs before Play kills the node to install the update.
 */
internal fun createInAppUpdateProvider(
    application: Application,
    updateChannel: AppUpdateChannel,
    prepareForRestart: suspend () -> Unit,
): InAppUpdateProvider =
    if (updateChannel == AppUpdateChannel.PLAY_STORE) {
        PlayInAppUpdateProvider(application, prepareForRestart)
    } else {
        UnsupportedInAppUpdateProvider()
    }
