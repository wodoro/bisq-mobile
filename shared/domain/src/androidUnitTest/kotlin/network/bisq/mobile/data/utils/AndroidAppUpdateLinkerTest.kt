package network.bisq.mobile.data.utils

import android.app.Application
import android.content.Intent
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AndroidAppUpdateLinkerTest {
    private companion object {
        private const val GOOGLE_PLAY_INSTALLER = "com.android.vending"
        private const val GOOGLE_PLAY_FEEDBACK = "com.google.android.feedback"
        private const val FDROID_INSTALLER = "org.fdroid.fdroid"
        private const val FDROID_BASIC_INSTALLER = "org.fdroid.basic"
    }

    private val application: Application = RuntimeEnvironment.getApplication()
    private val urlLauncher = RecordingUrlLauncher()

    private fun linker(installer: () -> String?) = AndroidAppUpdateLinker(application, urlLauncher) { installer() }

    private fun openUpdatePage(installer: () -> String?): Boolean = runBlocking { linker(installer).openUpdatePage() }

    @Test
    fun `reports the channel of whichever store installed the app`() {
        assertEquals(AppUpdateChannel.PLAY_STORE, linker { GOOGLE_PLAY_INSTALLER }.getUpdateChannel())
        assertEquals(AppUpdateChannel.PLAY_STORE, linker { GOOGLE_PLAY_FEEDBACK }.getUpdateChannel())
        assertEquals(AppUpdateChannel.FDROID, linker { FDROID_INSTALLER }.getUpdateChannel())
        assertEquals(AppUpdateChannel.FDROID, linker { FDROID_BASIC_INSTALLER }.getUpdateChannel())
        // A sideloaded APK of either flavor reports no installer or the system's package installer.
        assertEquals(AppUpdateChannel.GITHUB_RELEASES, linker { null }.getUpdateChannel())
        assertEquals(AppUpdateChannel.GITHUB_RELEASES, linker { "com.google.android.packageinstaller" }.getUpdateChannel())
        assertEquals(AppUpdateChannel.GITHUB_RELEASES, linker { throw RuntimeException("PackageManager lookup failed") }.getUpdateChannel())
    }

    @Test
    fun `a Play install opens its store page`() {
        assertTrue(openUpdatePage { GOOGLE_PLAY_INSTALLER })

        assertEquals(listOf(AppUpdateUrls.marketDetailsUrl(application.packageName)), urlLauncher.openedUrls)
    }

    @Test
    fun `an F-Droid install opens its page in the F-Droid app and nowhere else`() {
        assertTrue(openUpdatePage { FDROID_INSTALLER })

        val intent = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(AppUpdateUrls.marketDetailsUrl(application.packageName), intent.dataString)
        // Aimed at F-Droid: left open, Play could answer and offer a build that cannot update this one.
        assertEquals(FDROID_INSTALLER, intent.`package`)
        assertTrue(urlLauncher.openedUrls.isEmpty())
    }

    @Test
    fun `an F-Droid install falls back to the release page when F-Droid cannot show it`() {
        shadowOf(application).checkActivities(true)

        assertTrue(openUpdatePage { FDROID_INSTALLER })

        assertNull(shadowOf(application).nextStartedActivity)
        assertEquals(listOf(AppUpdateUrls.GITHUB_RELEASES), urlLauncher.openedUrls)
    }

    @Test
    fun `any other install opens the release page`() {
        assertTrue(openUpdatePage { null })

        assertEquals(listOf(AppUpdateUrls.GITHUB_RELEASES), urlLauncher.openedUrls)
    }

    @Test
    fun `reports when the page could not be opened`() {
        urlLauncher.opens = false

        assertEquals(false, openUpdatePage { null })
    }

    @Test
    fun `default installer lookup on API R+ treats a Robolectric install as sideloaded`() {
        assertEquals(AppUpdateChannel.GITHUB_RELEASES, AndroidAppUpdateLinker(application, urlLauncher).getUpdateChannel())
    }

    @Test
    @Config(sdk = [28])
    fun `default installer lookup on pre-R API treats a Robolectric install as sideloaded`() {
        assertEquals(AppUpdateChannel.GITHUB_RELEASES, AndroidAppUpdateLinker(application, urlLauncher).getUpdateChannel())
    }

    private class RecordingUrlLauncher : UrlLauncher {
        val openedUrls = mutableListOf<String>()
        var opens = true

        override suspend fun openUrl(url: String): Boolean {
            openedUrls += url
            return opens
        }
    }
}
