package network.bisq.mobile.node.common.domain.mapping.release

import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import bisq.bonded_roles.release.ReleaseNotification as ReleaseNotificationBisq2

class ReleaseNotificationBisq2MappingTest {
    @Test
    fun `maps every field the app uses`() {
        val notification = releaseNotification(versionString = "0.15.0")

        val mapped = assertNotNull(notification.toDomainOrNull())

        assertEquals("release-1", mapped.id)
        assertEquals("0.15.0", mapped.version.toString())
        assertEquals("Faster sync", mapped.releaseNotes)
        assertTrue(mapped.isPreRelease)
        assertEquals(42L, mapped.date)
    }

    @Test
    fun `drops a notification whose version does not parse`() {
        assertNull(releaseNotification(versionString = "v0.15.0").toDomainOrNull())
    }

    private fun releaseNotification(versionString: String) =
        mockk<ReleaseNotificationBisq2> {
            every { id } returns "release-1"
            every { this@mockk.versionString } returns versionString
            every { releaseNotes } returns "Faster sync"
            every { isPreRelease } returns true
            every { date } returns 42L
        }
}
