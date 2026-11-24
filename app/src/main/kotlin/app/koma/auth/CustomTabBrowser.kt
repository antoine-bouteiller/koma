package app.koma.auth

import android.app.Activity
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import app.koma.AuthBrowser
import java.lang.ref.WeakReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opens sign-in in a Custom Tab. MainActivity reports its redirects, pauses and resumes. */
class CustomTabBrowser : AuthBrowser {
    private var host = WeakReference<Activity>(null)
    private var waiting: CompletableDeferred<Uri?>? = null
    private var left = false

    fun attach(activity: Activity) {
        host = WeakReference(activity)
    }

    override suspend fun open(url: String, redirect: String): Uri? =
        withContext(Dispatchers.Main) {
            waiting?.complete(null)
            val activity = host.get() ?: return@withContext null
            val reply = CompletableDeferred<Uri?>()
            waiting = reply
            left = false
            try {
                CustomTabsIntent.Builder().build().launchUrl(activity, url.toUri())
            } catch (_: ActivityNotFoundException) {
                reply.complete(null)
            }
            try {
                reply.await()
            } finally {
                if (waiting === reply) waiting = null
            }
        }

    fun onRedirect(uri: Uri) {
        waiting?.complete(uri)
    }

    fun onPause() {
        if (waiting != null) left = true
    }

    /** A return to the app with no redirect means the Custom Tab was closed. */
    fun onResume() {
        if (left) waiting?.complete(null)
        left = false
    }
}
