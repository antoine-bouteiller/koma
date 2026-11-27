package app.koma.data

import android.database.sqlite.SQLiteDatabase
import app.koma.source.details
import app.koma.testing.AppHarness
import app.koma.testing.BATCH
import app.koma.testing.CHANGES
import app.koma.testing.batch
import app.koma.testing.changes
import app.koma.testing.series
import app.koma.testing.upsert
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpgradeTest {
    @get:Rule val app = AppHarness()

    /** The database as version 1 left it: keyed by MangaBaka's numeric series ids. */
    private fun versionOne() {
        SQLiteDatabase.openOrCreateDatabase(app.database, null).use { db ->
            listOf(
                    "CREATE TABLE series (id INTEGER PRIMARY KEY, title TEXT NOT NULL, cover TEXT NOT NULL, type TEXT NOT NULL)",
                    "CREATE TABLE library_entry (series_id INTEGER PRIMARY KEY, state TEXT NOT NULL, updated_at TEXT NOT NULL)",
                    "CREATE TABLE read_event (id INTEGER PRIMARY KEY, series_id INTEGER NOT NULL, at INTEGER NOT NULL, from_chapter REAL, to_chapter REAL)",
                    "CREATE INDEX read_event_at ON read_event (at DESC, id DESC)",
                    "CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
                    "INSERT INTO series VALUES (1, 'Berserk', '', 'manga')",
                    "INSERT INTO library_entry VALUES (1, 'reading', '2026-01-01T00:00:00Z')",
                    "INSERT INTO read_event VALUES (1, 1, 0, 1, 2)",
                    "INSERT INTO meta VALUES ('library_cursor', 'c1')",
                )
                .forEach(db::execSQL)
            db.version = 1
        }
    }

    @Test
    fun `an upgraded app stays signed in and reads its library again`() {
        versionOne()
        app.signedIn()
        app.network.answer(CHANGES, 200, changes("c2", false, upsert(2, "reading")))
        app.network.answer(BATCH, 200, batch(series(2, "Vagabond")))
        app.launch()
        app.text("manga-2", "Vagabond")
        app.missing("manga-1")
        assertEquals(listOf("$CHANGES?limit=1000"), app.network.requested(CHANGES))
    }

    @Test
    fun `an upgraded app keeps the source bound to a title`() {
        versionOne()
        app.launch()
        val vagabond = details("Vagabond", id = "2")
        val manga =
            SManga.create().apply {
                url = "vagabond"
                title = "Vagabond"
            }
        runBlocking {
            app.container.sources.bind(vagabond.media.ref, Candidate(7, "Test Source", manga))
        }

        app.reload()

        assertEquals(
            Binding(7, "vagabond", "Vagabond"),
            runBlocking { app.container.sources.resolve(vagabond) },
        )
    }
}
