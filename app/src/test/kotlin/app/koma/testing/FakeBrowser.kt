package app.koma.testing

import android.net.Uri
import app.koma.AuthBrowser
import kotlinx.coroutines.CompletableDeferred
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Records the authorization URL the app opens, and answers it as the test says. */
class FakeBrowser : AuthBrowser {
    var opened: String? = null
        private set

    private var waiting: CompletableDeferred<Uri?>? = null

    override suspend fun open(url: String, redirect: String): Uri? {
        opened = url
        return CompletableDeferred<Uri?>().also { waiting = it }.await()
    }

    /** The person approves: the redirect carries a code and the opened URL's state. */
    fun authorize(code: String = "code") {
        val state =
            checkNotNull(opened) { "No sign-in is open." }.toHttpUrl().queryParameter("state")
        answer(Uri.parse("koma://auth?code=$code&state=$state"))
    }

    /** The browser comes back to [uri]. */
    fun redirect(uri: String) = answer(Uri.parse(uri))

    /** The person closes the browser. */
    fun cancel() = answer(null)

    private fun answer(uri: Uri?) {
        checkNotNull(waiting) { "No sign-in is waiting." }.complete(uri)
        waiting = null
    }
}
