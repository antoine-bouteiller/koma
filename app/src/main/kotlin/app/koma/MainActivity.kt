package app.koma

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.koma.ui.KomaApp

class MainActivity : ComponentActivity() {
    private val app
        get() = application as KomaApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        app.browser.attach(this)
        setContent { KomaApp(app.container) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = intent.data ?: return
        if (uri.scheme == "koma" && uri.host == "auth") app.browser.onRedirect(uri)
    }

    override fun onPause() {
        super.onPause()
        app.browser.onPause()
    }

    override fun onResume() {
        super.onResume()
        app.browser.onResume()
    }
}
