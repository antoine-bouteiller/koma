package app.koma.version

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VersionCodeTest {
    @Test
    fun `a stable release outranks its betas`() {
        assertEquals(40099, versionCode("0.4.0"))
        assertEquals(40003, versionCode("0.4.0-beta.3"))
        assertEquals(1_020_399, versionCode("1.2.3"))
    }

    @Test
    fun `the largest parts still fit Android's cap`() {
        assertEquals(2_099_999_999, versionCode("2099.99.99"))
    }

    @Test
    fun `parts past 99 or a major past 2099 have no versionCode`() {
        assertThrows(IllegalArgumentException::class.java) { versionCode("0.100.0") }
        assertThrows(IllegalArgumentException::class.java) { versionCode("0.1.100") }
        assertThrows(IllegalArgumentException::class.java) { versionCode("0.1.0-beta.100") }
        assertThrows(IllegalArgumentException::class.java) { versionCode("2100.0.0") }
    }

    @Test
    fun `a version that is not a release is refused`() {
        for (version in listOf("", "1.2", "v1.2.3", "1.2.3-rc.1", "1.2.3-beta")) {
            assertThrows(IllegalArgumentException::class.java) { versionCode(version) }
        }
    }
}
