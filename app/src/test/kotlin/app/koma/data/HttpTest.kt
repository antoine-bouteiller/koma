package app.koma.data

import app.koma.testing.FakeNetwork
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpTest {
    @Test
    fun `the app's client names itself only on a request that names no client`() {
        val network = FakeNetwork()
        network.answer("https://example.test/", 200, "{}")
        val http = httpClient().newBuilder().addInterceptor(network).build()

        http.call(Request.Builder().url("https://example.test/a").build()) {}
        http.call(
            Request.Builder().url("https://example.test/b").header("User-Agent", "site").build()
        ) {}

        assertEquals(listOf("koma", "site"), network.requests.map { it.headers["User-Agent"] })
    }
}
