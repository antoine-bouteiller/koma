package app.koma.auth

import androidx.compose.ui.test.assertIsEnabled
import app.koma.testing.AppHarness
import app.koma.testing.BATCH
import app.koma.testing.CHANGES
import app.koma.testing.EVENTS
import app.koma.testing.PROFILE
import app.koma.testing.batch
import app.koma.testing.changes
import app.koma.testing.events
import app.koma.testing.profile
import app.koma.testing.series
import app.koma.testing.upsert
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SignInTest {
    @get:Rule val app = AppHarness()

    @Test
    fun `signed out, the library's Sign In opens the account settings`() {
        app.launch()
        app.missing("library")
        app.tap("sign-in")
        app.selected("tab-settings")
        app.text("title-account", "MangaBaka")
        app.has("account-sign-in")
        app.missing("sign-out")
    }

    @Test
    fun `signs in to MangaBaka, shows the library, and signs out`() {
        app.stubAuthServer()
        app.network.answer(
            CHANGES,
            200,
            """{"status":200,"pagination":{"limit":1000,"cursor":"c1","more":false},"data":[{"type":"entry","action":"upsert","revision":3397,"current":{"id":3397,"series_id":3397,"user_id":"u","state":"reading","progress_chapter":null,"created_at":null,"updated_at":"2026-01-01T00:00:00Z","list_ids":[]}}]}""",
        )
        app.network.answer(
            BATCH,
            200,
            """{"status":200,"data":[{"id":3397,"type":"manhwa","titles":[{"language":"ko","traits":["native"],"title":"나 혼자만 레벨업"},{"language":"en","traits":["official"],"title":"Solo Leveling"}],"cover":{"raw":null,"x150":null,"x250":null,"x350":"https://cdn.mangabaka.dev/solo.jpg","blurhash":null,"width":null,"height":null}}]}""",
        )
        app.network.answer(PROFILE, 200, profile("reader"))
        app.launch()
        app.tap("sign-in")
        app.tap("account-sign-in")
        app.browser.authorize()
        app.awaitIdle()
        app.text("account-name-title", "reader")
        app.tap("tab-library")
        app.text("manga-3397", "Solo Leveling")
        app.compose.waitUntil { "https://cdn.mangabaka.dev/solo.jpg" in app.images }
        app.reload()
        app.text("manga-3397", "Solo Leveling")
        app.tap("tab-settings")
        app.tap("sign-out")
        app.has("account-sign-in")
        app.tap("tab-library")
        app.has("sign-in")
        app.missing("manga-3397")
        assertNull(app.secrets.get("mangabaka.session"))
    }

    @Test
    fun `a session that cannot be saved does not sign in`() {
        app.stubAuthServer()
        app.secrets.failWrites = true
        app.launch()
        app.tap("sign-in")
        app.tap("account-sign-in")
        app.browser.authorize()
        app.awaitIdle()
        app.text("sign-in-error", "Sign-in could not be saved. Try again.")
        app.missing("sign-out")
    }

    @Test
    fun `signing in again after the session ended syncs the library`() {
        app.stubAuthServer(token = """{"error":"invalid_grant"}""")
        app.signedIn()
        app.network.answer(CHANGES, 200, changes("c1", false, upsert(1, "reading")))
        app.network.answer(BATCH, 200, batch(series(1, "Berserk"), series(7, "Pluto")))
        app.launch()
        app.text("manga-1", "Berserk")
        app.network.answer(EVENTS, 401, """{"status":401}""")
        // The history's 401 refreshes, and MangaBaka refuses the refresh token.
        app.tap("tab-history")
        app.has("history-sign-in")
        app.stubAuthServer()
        app.network.answer(EVENTS, 200, events(50, "end", false))
        app.network.answer(
            "$CHANGES?limit=1000&cursor=c1",
            200,
            changes("c2", false, upsert(7, "reading", "2026-01-02T00:00:00Z")),
        )
        app.network.answer(PROFILE, 200, profile("reader"))
        app.tap("history-sign-in")
        app.tap("account-sign-in")
        app.browser.authorize()
        app.awaitIdle()
        app.tap("tab-library")
        app.text("manga-7", "Pluto")
    }

    @Test
    fun `closing the browser cancels the sign-in and keeps the prompt enabled`() {
        app.stubAuthServer()
        app.launch()
        app.tap("sign-in")
        app.tap("account-sign-in")
        app.browser.cancel()
        app.awaitIdle()
        app.text("sign-in-error", "Sign-in was cancelled.")
        app.tap("account-sign-in")
        app.browser.cancel()
        app.awaitIdle()
        app.node("account-sign-in").assertIsEnabled()
    }
}
