package network.bisq.mobile.client.common.data.mapping.release

import network.bisq.mobile.client.common.data.model.release.ReleaseNotificationDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseNotificationDtoMappingTest {
    @Test
    fun `maps every field the app uses`() {
        val dto = ReleaseNotificationDto(id = "a", date = 42L, isPreRelease = true, releaseNotes = "Notes", versionString = "0.11.0")

        val mapped = assertNotNull(dto.toDomainOrNull())

        assertEquals("a", mapped.id)
        assertEquals("0.11.0", mapped.version.toString())
        assertEquals("Notes", mapped.releaseNotes)
        assertTrue(mapped.isPreRelease)
        assertEquals(42L, mapped.date)
    }

    @Test
    fun `drops a notification whose version does not parse`() {
        assertNull(ReleaseNotificationDto(id = "a", date = 42L, versionString = "latest").toDomainOrNull())
    }
}
