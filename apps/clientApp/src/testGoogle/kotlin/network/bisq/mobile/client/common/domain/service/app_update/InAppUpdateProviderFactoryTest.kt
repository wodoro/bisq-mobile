package network.bisq.mobile.client.common.domain.service.app_update

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import network.bisq.mobile.data.utils.AppUpdateChannel
import network.bisq.mobile.domain.service.app_update.UnsupportedInAppUpdateProvider
import network.bisq.mobile.play_update.PlayInAppUpdateProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * The google half of the flavor seam. Its counterpart lives in `src/testFdroid`; nothing but the
 * flavor decides which factory compiles, so each side is pinned where it is built.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class InAppUpdateProviderFactoryTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `a Play install gets the Play provider`() {
        assertTrue(createInAppUpdateProvider(application, AppUpdateChannel.PLAY_STORE) is PlayInAppUpdateProvider)
    }

    @Test
    fun `a sideloaded APK of this flavor never calls Play`() {
        assertTrue(createInAppUpdateProvider(application, AppUpdateChannel.GITHUB_RELEASES) is UnsupportedInAppUpdateProvider)
    }
}
