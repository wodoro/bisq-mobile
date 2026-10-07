package network.bisq.mobile.play_update

import android.app.Activity
import android.os.Looper
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import network.bisq.mobile.domain.service.app_update.InAppUpdateState
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PlayInAppUpdateProviderTest {
    private val application = RuntimeEnvironment.getApplication()
    private lateinit var fakeAppUpdateManager: FakeAppUpdateManager
    private var installSplashVisibleWhenPrepared: Boolean? = null

    private lateinit var provider: PlayInAppUpdateProvider

    @Before
    fun setUp() {
        fakeAppUpdateManager = FakeAppUpdateManager(application)
        provider =
            PlayInAppUpdateProvider(
                application = application,
                prepareForRestart = { installSplashVisibleWhenPrepared = fakeAppUpdateManager.isInstallSplashScreenVisible },
                appUpdateManager = fakeAppUpdateManager,
            )
    }

    // Play's tasks and the provider's scope both run on the main looper. The fake resolves
    // startUpdateFlow as soon as its dialog shows, where Play waits for the user's answer.
    private fun settle() = shadowOf(Looper.getMainLooper()).idle()

    private fun resumeActivity() {
        Robolectric.buildActivity(Activity::class.java).setup()
    }

    private fun startAcceptedUpdate() {
        fakeAppUpdateManager.setUpdateAvailable(NEWER_VERSION_CODE)
        resumeActivity()
        provider.startUpdate()
        settle()
        assertTrue(fakeAppUpdateManager.isConfirmationDialogVisible)
        fakeAppUpdateManager.userAcceptsUpdate()
        settle()
    }

    @Test
    fun `refresh reports an update Play can install in place`() {
        fakeAppUpdateManager.setUpdateAvailable(NEWER_VERSION_CODE)

        provider.refresh()
        settle()

        assertEquals(InAppUpdateState.Available, provider.state.value)
    }

    @Test
    fun `refresh reports nothing while Play has no update`() {
        fakeAppUpdateManager.setUpdateNotAvailable()

        provider.refresh()
        settle()

        assertEquals(InAppUpdateState.Unavailable, provider.state.value)
    }

    @Test
    fun `the flexible flow downloads in the background and becomes ready to install`() {
        startAcceptedUpdate()
        assertEquals(InAppUpdateState.Downloading(progress = 0f), provider.state.value)

        fakeAppUpdateManager.downloadStarts()
        fakeAppUpdateManager.setTotalBytesToDownload(TOTAL_BYTES)
        fakeAppUpdateManager.setBytesDownloaded(TOTAL_BYTES / 2)
        settle()
        assertEquals(InAppUpdateState.Downloading(progress = 0.5f), provider.state.value)

        fakeAppUpdateManager.downloadCompletes()
        settle()
        assertEquals(InAppUpdateState.ReadyToInstall, provider.state.value)
    }

    @Test
    fun `a download cancelled before installing leaves the update available`() {
        startAcceptedUpdate()
        fakeAppUpdateManager.downloadStarts()

        fakeAppUpdateManager.userCancelsDownload()
        settle()

        assertEquals(InAppUpdateState.Available, provider.state.value)
    }

    @Test
    fun `a failed download is reported`() {
        startAcceptedUpdate()
        fakeAppUpdateManager.downloadStarts()

        fakeAppUpdateManager.downloadFails()
        settle()

        assertEquals(InAppUpdateState.Failed, provider.state.value)
    }

    @Test
    fun `completing prepares for the restart before Play installs`() {
        startAcceptedUpdate()
        fakeAppUpdateManager.downloadStarts()
        fakeAppUpdateManager.downloadCompletes()
        settle()

        provider.completeUpdate()
        settle()

        assertEquals(false, installSplashVisibleWhenPrepared)
        assertTrue(fakeAppUpdateManager.isInstallSplashScreenVisible)
    }

    @Test
    fun `nothing starts without a visible activity to confirm over`() {
        fakeAppUpdateManager.setUpdateAvailable(NEWER_VERSION_CODE)

        provider.startUpdate()
        settle()

        assertFalse(fakeAppUpdateManager.isConfirmationDialogVisible)
    }

    private companion object {
        // Any version code above the running app's, which Robolectric reports as 0.
        const val NEWER_VERSION_CODE = 2
        const val TOTAL_BYTES = 1_000L
    }
}
