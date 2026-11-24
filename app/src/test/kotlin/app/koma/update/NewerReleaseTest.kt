package app.koma.update

import app.koma.data.HttpException
import app.koma.data.httpClient
import app.koma.testing.FakeNetwork
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

private const val LATEST = "https://api.github.com/repos/antoine-bouteiller/koma/releases/latest"

class NewerReleaseTest {
    private val network = FakeNetwork()
    private val http = httpClient().newBuilder().addInterceptor(network).build()

    private fun latest(tag: String, asset: String = "koma.apk") =
        network.answer(
            LATEST,
            200,
            """{"tag_name":"$tag","assets":[{"name":"$asset","browser_download_url":"https://example.com/$asset"}]}""",
        )

    @Test
    fun `a newer stable release is offered`() {
        latest("v0.2.0")
        assertEquals(Release("0.2.0", "https://example.com/koma.apk"), newerRelease(http, "0.1.0"))
    }

    @Test
    fun `the installed release or an older one is not offered`() {
        latest("v0.1.0")
        assertNull(newerRelease(http, "0.1.0"))
        assertNull(newerRelease(http, "0.2.0-beta.1"))
    }

    @Test
    fun `no release yet offers nothing`() {
        network.answer(LATEST, 404, """{"message":"Not Found"}""")
        assertNull(newerRelease(http, "0.1.0"))
    }

    @Test
    fun `GitHub is asked as koma`() {
        network.answer(LATEST, 404, "{}")
        newerRelease(http, "0.1.0")
        assertEquals("koma", network.requests.single().headers["User-Agent"])
    }

    @Test
    fun `a release without koma apk or a failing GitHub is an error`() {
        latest("v9.0.0", asset = "other.apk")
        assertThrows(IOException::class.java) { newerRelease(http, "0.1.0") }
        network.answer(LATEST, 500, "{}")
        assertEquals(
            500,
            assertThrows(HttpException::class.java) { newerRelease(http, "0.1.0") }.status,
        )
    }
}
