package network.bisq.mobile.client.common.domain.service.app_update

import android.app.Application
import io.mockk.mockk
import network.bisq.mobile.data.utils.AppUpdateChannel
import network.bisq.mobile.domain.service.app_update.UnsupportedInAppUpdateProvider
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The fdroid half of the flavor seam. See the google flavor's test. */
class InAppUpdateProviderFactoryTest {
    @Test
    fun `never updates in place whoever installed the app`() {
        AppUpdateChannel.entries.forEach { channel ->
            assertTrue(createInAppUpdateProvider(mockk<Application>(), channel) is UnsupportedInAppUpdateProvider)
        }
    }

    @Test
    fun `the Play library is not on this flavor's classpath`() {
        // F-Droid rejects proprietary dependencies outright, and Play Core is one.
        assertFailsWith<ClassNotFoundException> { Class.forName("com.google.android.play.core.appupdate.AppUpdateManager") }
    }
}
