package app.koma

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import app.koma.ui.KomaApp

class MainActivity : ComponentActivity() {
    private val app
        get() = application as KomaApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        app.browser.attach(this)
        setContent { KomaApp(app.container) }
        if (savedInstanceState == null) askToNotify()
    }

    /** Android 13 hides the library update's notification unless allowed. */
    private fun askToNotify() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(permission)
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
