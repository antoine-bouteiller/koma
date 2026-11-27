package app.koma.source

import app.koma.testing.AppHarness
import app.koma.testing.TEST_SOURCE
import app.koma.testing.TestSource
import app.koma.testing.mangas
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.USER_AGENT
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SourceRuntimeTest {
    @get:Rule val app = AppHarness()

    @Test
    fun `the source client adds the interceptors extensions check for, in Mihon's order`() {
        val http = OkHttpClient.Builder().addInterceptor { it.proceed(it.request()) }.build()
        assertEquals(
            listOf(
                "",
                "UncaughtExceptionInterceptor",
                "UserAgentInterceptor",
                "CloudflareInterceptor",
            ),
            NetworkHelper(http).client.interceptors.map { it.javaClass.simpleName },
        )
    }

    @Test
    fun `a keiyoushi source requests through the app's client with its own headers`() {
        val source = TestSource()
        app.sources = { listOf(CatalogSource(source)) }
        app.network.answer("$TEST_SOURCE/search?q=Pluto", 200, mangas("p" to "Pluto"))
        app.launch()

        val found = runBlocking { source.getSearchManga(1, "Pluto", FilterList()) }

        assertEquals(listOf("Pluto"), found.mangas.map { it.title })
        val request = app.network.requests.single { it.url.startsWith(TEST_SOURCE) }
        assertEquals(USER_AGENT, request.headers["User-Agent"])
        assertEquals("$TEST_SOURCE/", request.headers["Referer"])
    }

    @Test
    fun `a page image loads through the source and fails on a reply that is not 2xx`() {
        val source = TestSource()
        app.sources = { listOf(CatalogSource(source)) }
        app.network.answer("$TEST_SOURCE/images/1.webp", 200, "image")
        app.network.answer("$TEST_SOURCE/images/2.webp", 404, "")
        app.launch()

        val image = runBlocking {
            source.getImage(Page(0, imageUrl = "$TEST_SOURCE/images/1.webp"))
        }
        assertEquals("image", image.use { it.body.string() })
        val failure =
            assertThrows(HttpException::class.java) {
                runBlocking { source.getImage(Page(1, imageUrl = "$TEST_SOURCE/images/2.webp")) }
            }
        assertEquals(404, failure.code)
    }
}
