package network.bisq.mobile.domain.service.app_update

import network.bisq.mobile.domain.model.release.ReleaseNotification
import network.bisq.mobile.domain.model.release.ReleaseVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReleaseUpdateSelectionTest {
    private val running = requireNotNull(ReleaseVersion.parse("0.14.2"))

    @Test
    fun `offers only a version strictly above the running one`() {
        assertNull(selectReleaseUpdate(listOf(notification("0.14.1")), running))
        assertNull(selectReleaseUpdate(listOf(notification("0.14.2")), running))
        assertNull(selectReleaseUpdate(listOf(notification("0.14.2.0")), running))
        assertEquals("0.14.3", selectReleaseUpdate(listOf(notification("0.14.3")), running)?.version.toString())
    }

    @Test
    fun `the highest version wins over the most recently received one`() {
        val notifications =
            listOf(
                notification("0.16.0", date = 1),
                // A replayed, older release that happens to arrive last.
                notification("0.15.0", date = 2),
            )

        assertEquals("0.16.0", selectReleaseUpdate(notifications, running)?.version.toString())
    }

    @Test
    fun `a pre-release is never offered even when it is the highest version`() {
        val notifications =
            listOf(
                notification("0.15.0"),
                notification("0.16.0", isPreRelease = true),
            )

        assertEquals("0.15.0", selectReleaseUpdate(notifications, running)?.version.toString())
        assertNull(selectReleaseUpdate(listOf(notification("0.16.0", isPreRelease = true)), running))
    }

    @Test
    fun `nothing is offered without notifications`() {
        assertNull(selectReleaseUpdate(emptyList(), running))
    }

    private fun notification(
        version: String,
        isPreRelease: Boolean = false,
        date: Long = 0,
    ) = ReleaseNotification(
        id = "id-$version",
        version = requireNotNull(ReleaseVersion.parse(version)),
        releaseNotes = "notes $version",
        isPreRelease = isPreRelease,
        date = date,
    )
}
