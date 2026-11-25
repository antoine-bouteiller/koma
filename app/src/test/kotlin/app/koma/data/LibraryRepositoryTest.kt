package app.koma.data

import app.koma.testing.FakeBackend
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LibraryRepositoryTest {
    private val backend = FakeBackend()
    private val store = memoryStore()
    private val media = MediaRepository(store, backend)
    private val library =
        LibraryRepository(
            store,
            backend,
            media,
            Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC),
        )

    private suspend fun stored() = library.library.first().items.map { it.media.ref to it.status }

    @Test
    fun `a refused cursor replaces the stored library with one full read`() = runTest {
        val alpha = backend.add("α-1", "Alpha")
        val beta = backend.add("β-2", "Beta")
        backend.file(alpha, ListStatus.Reading)
        library.sync()
        backend.refuseCursors = true
        backend.drop(alpha)
        backend.file(beta, ListStatus.Dropped)
        backend.calls.clear()
        library.sync()
        assertEquals(listOf("changes(c1)", "changes(null)"), backend.calls)
        assertEquals(listOf(beta to ListStatus.Dropped), stored())
    }

    @Test
    fun `a later sync applies only what changed`() = runTest {
        val alpha = backend.add("α-1", "Alpha")
        val beta = backend.add("β-2", "Beta")
        backend.file(alpha, ListStatus.Reading)
        library.sync()
        backend.drop(alpha)
        backend.file(beta, ListStatus.Paused)
        library.sync()
        assertEquals(listOf("changes(null)", "changes(c1)"), backend.calls)
        assertEquals(listOf(beta to ListStatus.Paused), stored())
    }

    @Test
    fun `media the backend no longer answers is left out`() = runTest {
        val alpha = backend.add("α-1", "Alpha")
        backend.file(alpha, ListStatus.Reading)
        backend.file(MediaRef(BackendId.MangaBaka, "gone"), ListStatus.Reading)
        library.sync()
        assertEquals(true, library.library.first().synced)
        assertEquals(listOf(alpha to ListStatus.Reading), stored())
    }

    @Test
    fun `a status is added when the stored library lacks the media, else updated`() = runTest {
        val alpha = backend.add("α-1", "Alpha")
        val beta = backend.add("β-2", "Beta")
        backend.file(alpha, ListStatus.Reading)
        library.sync()
        library.setStatus(alpha, ListStatus.Completed)
        library.setStatus(beta, ListStatus.Planning)
        assertEquals(
            listOf("updateEntry(α-1, Completed)", "addEntry(β-2, Planning)"),
            backend.calls.drop(1),
        )
        assertEquals(ListStatus.Completed, library.status(alpha).first())
        assertEquals(ListStatus.Planning, library.status(beta).first())
    }

    @Test
    fun `a sync queued behind a sign-out keeps nothing`() = runTest {
        backend.file(backend.add("α-1", "Alpha"), ListStatus.Reading)
        val answer = backend.hold("changes")
        val first = launch { library.sync() }
        runCurrent()
        val queued = launch { library.sync() }
        runCurrent()
        store.clearUserData()
        answer.complete(Unit)
        joinAll(first, queued)
        assertEquals(listOf("changes(null)"), backend.calls)
        assertEquals(Library(false, emptyList()), library.library.first())
    }

    @Test
    fun `a status whose media cannot be looked up is not sent`() = runTest {
        backend.failMedia = true
        val delta = MediaRef(BackendId.MangaBaka, "δ-4")
        val refused = runCatching { library.setStatus(delta, ListStatus.Planning) }
        assertTrue(refused.exceptionOrNull() is IOException)
        assertEquals(emptyList<String>(), backend.calls)
    }

    @Test
    fun `a refused status syncs the library and is not stored`() = runTest {
        val gamma = backend.add("γ-3", "Gamma")
        library.sync()
        backend.file(gamma, ListStatus.Paused)
        val refused = runCatching { library.setStatus(gamma, ListStatus.Completed) }
        assertTrue(refused.exceptionOrNull() is IOException)
        assertEquals(
            listOf("changes(null)", "addEntry(γ-3, Completed)", "changes(c1)"),
            backend.calls,
        )
        assertEquals(ListStatus.Paused, library.status(gamma).first())
    }
}
