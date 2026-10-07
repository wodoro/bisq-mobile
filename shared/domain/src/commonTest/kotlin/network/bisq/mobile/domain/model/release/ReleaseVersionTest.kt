package network.bisq.mobile.domain.model.release

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseVersionTest {
    private fun version(value: String) = requireNotNull(ReleaseVersion.parse(value)) { "$value should parse" }

    @Test
    fun `parts compare as numbers not as text`() {
        assertTrue(version("0.10.0") > version("0.9.9"))
        assertTrue(version("1.0") > version("0.99.99"))
        assertTrue(version("0.14.2") < version("0.14.10"))
    }

    @Test
    fun `missing trailing parts count as zero`() {
        assertEquals(version("0.15"), version("0.15.0"))
        assertEquals(version("0.15").hashCode(), version("0.15.0").hashCode())
        assertTrue(version("0.15.1") > version("0.15"))
    }

    @Test
    fun `keeps the original text`() {
        assertEquals("0.15", version("0.15").toString())
    }

    @Test
    fun `rejects anything bisq2 would not accept as a version`() {
        listOf("", "v0.15.0", "0.15.0-rc1", "0..1", ".1", "1.", "0.15 ", "99999999999.0").forEach {
            assertNull(ReleaseVersion.parse(it), "'$it' must not parse")
        }
    }

    @Test
    fun `different versions are not equal`() {
        assertNotEquals(version("0.15.0"), version("0.15.1"))
    }
}
