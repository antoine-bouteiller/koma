package app.koma

import android.app.Application
import android.content.Intent
import androidx.core.net.toUri
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.koma.auth.CustomTabBrowser
import app.koma.auth.KeystoreSecretStore
import app.koma.data.KomaDatabase
import app.koma.data.httpClient
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import java.time.Clock
import java.time.ZoneId

class KomaApplication : Application() {
    val browser = CustomTabBrowser()

    val container: AppContainer by lazy {
        val http = httpClient()
        AppContainer(
            context = this,
            http = http,
            images =
                ImageLoader.Builder(this)
                    .serviceLoaderEnabled(false)
                    .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
                    .build(),
            driver = AndroidSqliteDriver(KomaDatabase.Schema, this, "koma.db"),
            secrets = KeystoreSecretStore(this),
            preferences = SharedPreferenceStore(this),
            browser = browser,
            clock = Clock.systemUTC(),
            zone = { ZoneId.systemDefault() },
            installedVersion = BuildConfig.VERSION_NAME,
            openUrl = { url ->
                startActivity(
                    Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
        )
    }
}
