package app.koma.data.mangabaka

import app.koma.data.HttpException
import app.koma.data.SignInResult
import app.koma.data.SignedOutException
import app.koma.data.httpClient
import app.koma.testing.AppHarness
import app.koma.testing.CHANGES
import app.koma.testing.FakeBrowser
import app.koma.testing.FakeNetwork
import app.koma.testing.MemorySecretStore
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val ISSUER = "https://mangabaka.org/auth"
private const val TOKEN = "$ISSUER/oauth2/token"
private const val API = "https://api.mangabaka.org/v2/my/library/changes?limit=1000"
private val NOW = Instant.parse("2026-01-01T12:00:00Z")

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MangaBakaAuthTest {
    @get:Rule val app = AppHarness()

    private val network = FakeNetwork()
    private val secrets = MemorySecretStore()
    private val browser = FakeBrowser()
    // Built at first use, after a test has stored its session.
    private val auth by lazy {
        MangaBakaAuth(
            httpClient().newBuilder().addInterceptor(network).build(),
            secrets,
            browser,
            Clock.fixed(NOW, ZoneOffset.UTC),
        )
    }

    private fun stubServer(token: String) {
        network.answer(
            "$ISSUER/.well-known/openid-configuration",
            200,
            """{"issuer":"$ISSUER","authorization_endpoint":"$ISSUER/oauth2/authorize","token_endpoint":"$TOKEN","revocation_endpoint":"$ISSUER/oauth2/revoke"}""",
        )
        network.answer(TOKEN, 200, token)
    }

    private fun session(accessToken: String, refreshToken: String, expiresAt: Long) {
        secrets.set(
            "mangabaka.session",
            """{"accessToken":"$accessToken","refreshToken":"$refreshToken","expiresAt":$expiresAt}""",
        )
    }

    private fun tokenRequests() = network.requests.filter { it.url == TOKEN }

    @Test
    fun `sign-in sends a PKCE authorization request and proves it at the token endpoint`() =
        runTest {
            stubServer(
                """{"access_token":"a1","refresh_token":"r1","token_type":"Bearer","expires_in":604800}"""
            )
            val result = async { auth.signIn() }
            runCurrent()
            val opened = checkNotNull(browser.opened).toHttpUrl()
            assertEquals(
                "$ISSUER/oauth2/authorize",
                opened.newBuilder().query(null).build().toString(),
            )
            assertEquals("lYFPQeyaYBnVaZJmcQHPiNjlprtCsVDL", opened.queryParameter("client_id"))
            assertEquals("koma://auth", opened.queryParameter("redirect_uri"))
            assertEquals("S256", opened.queryParameter("code_challenge_method"))
            assertEquals(
                "openid library.read library.write offline_access",
                opened.queryParameter("scope"),
            )
            browser.authorize()
            assertEquals(SignInResult(true, ""), result.await())
            val exchange = tokenRequests().single()
            assertEquals("authorization_code", exchange.field("grant_type"))
            assertEquals("code", exchange.field("code"))
            assertEquals(
                opened.queryParameter("code_challenge"),
                Pkce.challenge(checkNotNull(exchange.field("code_verifier"))),
            )
            assertTrue(auth.session().signedIn)
        }

    @Test
    fun `a redirect for another sign-in is refused`() = runTest {
        stubServer("""{"access_token":"a1","refresh_token":"r1"}""")
        val result = async { auth.signIn() }
        runCurrent()
        browser.redirect("koma://auth?code=code&state=another")
        assertEquals(SignInResult(false, "state_mismatch"), result.await())
        assertTrue(tokenRequests().isEmpty())
        assertFalse(auth.session().signedIn)
    }

    @Test
    fun `an expired access token refreshes before each call, sending the rotated refresh token`() =
        runTest {
            stubServer(
                """{"access_token":"a2","refresh_token":"r2","token_type":"Bearer","expires_in":0}"""
            )
            network.answer(API, 200, """{"data":[],"pagination":{"cursor":"c","more":false}}""")
            session("a1", "r1", expiresAt = NOW.toEpochMilli() - 1)
            auth.get(API, JsonObject.serializer())
            auth.get(API, JsonObject.serializer())
            assertEquals(listOf("r1", "r2"), tokenRequests().map { it.field("refresh_token") })
            assertTrue(secrets.get("mangabaka.session")!!.contains("\"refreshToken\":\"r2\""))
        }

    @Test
    fun `a 401 refreshes once and retries the request once`() = runTest {
        stubServer(
            """{"access_token":"a2","refresh_token":"r2","token_type":"Bearer","expires_in":604800}"""
        )
        network.answer(API, 401, """{"status":401}""")
        session("a1", "r1", expiresAt = NOW.toEpochMilli() + 60_000)
        val refused =
            try {
                auth.get(API, JsonObject.serializer())
                null
            } catch (e: HttpException) {
                e
            }
        assertEquals(401, refused?.status)
        assertEquals(1, tokenRequests().size)
        assertEquals(2, network.requests.count { it.url == API })
    }

    @Test
    fun `a sign-out while a refresh is out keeps the person signed out`() = runTest {
        stubServer(
            """{"access_token":"a2","refresh_token":"r2","token_type":"Bearer","expires_in":604800}"""
        )
        network.answer("$ISSUER/oauth2/revoke", 200, "{}")
        network.answer(API, 200, """{"data":[],"pagination":{"cursor":"c","more":false}}""")
        session("a1", "r1", expiresAt = NOW.toEpochMilli() - 1)
        network.whileInFlight = {
            if (it.url == TOKEN) {
                network.whileInFlight = {}
                runBlocking { auth.signOut() }
            }
        }
        val refused =
            try {
                auth.get(API, JsonObject.serializer())
                null
            } catch (e: SignedOutException) {
                e
            }
        assertTrue(refused != null)
        assertFalse(auth.session().signedIn)
        assertNull(secrets.get("mangabaka.session"))
    }

    @Test
    fun `a refresh reply without an access token still keeps its rotated refresh token`() =
        runTest {
            stubServer("""{"refresh_token":"r2","token_type":"Bearer"}""")
            network.answer(API, 200, """{"data":[],"pagination":{"cursor":"c","more":false}}""")
            session("a1", "r1", expiresAt = NOW.toEpochMilli() - 1)
            repeat(2) {
                try {
                    auth.get(API, JsonObject.serializer())
                } catch (_: IOException) {}
            }
            assertEquals(listOf("r1", "r2"), tokenRequests().map { it.field("refresh_token") })
            assertTrue(auth.session().signedIn)
        }

    @Test
    fun `a token server failure keeps the session, whatever its body says`() = runTest {
        stubServer("{}")
        network.answer(TOKEN, 503, """{"error":"invalid_grant"}""")
        session("a1", "r1", expiresAt = NOW.toEpochMilli() - 1)
        try {
            auth.get(API, JsonObject.serializer())
        } catch (_: IOException) {}
        assertEquals(1, tokenRequests().size)
        assertTrue(auth.session().signedIn)
        assertTrue(secrets.get("mangabaka.session")!!.contains("\"refreshToken\":\"r1\""))
    }

    @Test
    fun `invalid_grant on refresh ends the session and says so`() {
        app.stubAuthServer(token = """{"error":"invalid_grant"}""")
        app.network.answer(CHANGES, 200, """{"data":[],"pagination":{"cursor":"c","more":false}}""")
        app.signedIn(expiresAt = app.epoch.toEpochMilli() - 1)
        app.launch()
        app.tap("sign-in")
        app.text("sign-in-error", "Your MangaBaka session ended. Sign in again.")
        assertNull(app.secrets.get("mangabaka.session"))
    }
}
