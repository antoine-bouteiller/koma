package app.koma.source

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.koma.AppContainer
import app.koma.data.KomaDatabase
import app.koma.data.httpClient
import app.koma.testing.AppHarness
import app.koma.testing.FakeBackend
import app.koma.testing.FakeNetwork
import app.koma.testing.FakePermissions
import app.koma.testing.MemoryPreferenceStore
import app.koma.testing.TEST_SOURCE
import app.koma.testing.TestSource
import app.koma.testing.mangas
import coil3.ImageLoader
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.USER_AGENT
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

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
    fun `a source built for a later container requests through that container's client`() {
        val first = FakeNetwork()
        val second = FakeNetwork()
        first.answer("$TEST_SOURCE/search", 200, mangas("p" to "Pluto"))
        second.answer("$TEST_SOURCE/search", 200, mangas("m" to "Monster"))

        val before = container(first).sources.source(77)!!
        runBlocking { before.getSearchManga(1, "Pluto", FilterList()) }
        val after = container(second).sources.source(77)!!
        val found = runBlocking { after.getSearchManga(1, "Monster", FilterList()) }

        assertEquals(listOf("Monster"), found.mangas.map { it.title })
        assertEquals(1, first.requests.size)
        assertEquals(1, second.requests.size)
    }

    private fun container(network: FakeNetwork): AppContainer {
        val context = RuntimeEnvironment.getApplication()
        return AppContainer(
            context = context,
            http = httpClient().newBuilder().addInterceptor(network).build(),
            images = ImageLoader(context),
            driver = AndroidSqliteDriver(KomaDatabase.Schema, context, null),
            backend = FakeBackend(),
            sources = listOf(CatalogSource(TestSource())),
            preferences = MemoryPreferenceStore(),
            permissions = FakePermissions(),
            clock = Clock.systemUTC(),
            zone = { ZoneOffset.UTC },
            installedVersion = "0.1.0",
            installApk = {},
            startLibraryUpdate = {},
            work = Dispatchers.Unconfined,
        )
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
