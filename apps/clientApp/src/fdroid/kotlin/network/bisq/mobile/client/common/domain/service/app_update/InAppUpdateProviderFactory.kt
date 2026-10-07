package network.bisq.mobile.client.common.domain.service.app_update

import android.app.Application
import network.bisq.mobile.data.utils.AppUpdateChannel
import network.bisq.mobile.domain.service.app_update.InAppUpdateProvider
import network.bisq.mobile.domain.service.app_update.UnsupportedInAppUpdateProvider

/**
 * Flavor seam for the in-app update. See the `google` flavor's copy. This flavor carries no Play
 * library, so it never updates in place and the card links to the release page.
 */
@Suppress("UNUSED_PARAMETER")
internal fun createInAppUpdateProvider(
    application: Application,
    updateChannel: AppUpdateChannel,
): InAppUpdateProvider = UnsupportedInAppUpdateProvider()
