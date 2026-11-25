package app.koma.data

import app.koma.testing.FakeBackend
import java.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AccountRepositoryTest {
    private val backend = FakeBackend()
    private val store = memoryStore()
    private val account = AccountRepository(store, backend)

    @Test
    fun `signing out forgets the library before the session ends`() = runTest {
        val library =
            LibraryRepository(store, backend, MediaRepository(store, backend), Clock.systemUTC())
        backend.file(backend.add("α-1", "Alpha"), ListStatus.Reading)
        library.sync()
        account.loadUserName()
        assertEquals("shelf-reader", account.userName.value)
        account.signOut()
        assertFalse(account.sessions.value.signedIn)
        assertEquals(Library(false, emptyList()), library.library.first())
        assertNull(account.userName.value)
    }

    @Test
    fun `the user name is asked once per session`() = runTest {
        account.loadUserName()
        backend.user = "renamed"
        account.loadUserName()
        assertEquals("shelf-reader", account.userName.value)
        account.signIn()
        account.loadUserName()
        assertEquals("renamed", account.userName.value)
        assertEquals(1, account.signIns)
        assertEquals(listOf("userName()", "userName()"), backend.calls)
    }

    @Test
    fun `a user name answered while signing out is dropped`() = runTest {
        val name = backend.hold("userName")
        val ending = backend.hold("signOut")
        val loading = launch { account.loadUserName() }
        runCurrent()
        val signingOut = launch { account.signOut() }
        runCurrent()
        name.complete(Unit)
        loading.join()
        ending.complete(Unit)
        signingOut.join()
        assertNull(account.userName.value)
    }
}
