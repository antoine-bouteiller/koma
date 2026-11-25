package app.koma

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.koma.auth.CustomTabBrowser
import app.koma.auth.KeystoreSecretStore
import app.koma.data.KomaDatabase
import app.koma.data.httpClient
import app.koma.data.mangabaka.MangaBakaBackend
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import java.time.Clock
import java.time.ZoneId

class KomaApplication : Application() {
    val browser = CustomTabBrowser()

    val container: AppContainer by lazy {
        val http = httpClient()
        val clock = Clock.systemUTC()
        AppContainer(
            context = this,
            http = http,
            images =
                ImageLoader.Builder(this)
                    .serviceLoaderEnabled(false)
                    .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
                    .build(),
            driver = AndroidSqliteDriver(KomaDatabase.Schema, this, "koma.db"),
            backend = MangaBakaBackend(http, KeystoreSecretStore(this), browser, clock),
            preferences = SharedPreferenceStore(this),
            clock = clock,
            zone = { ZoneId.systemDefault() },
            installedVersion = BuildConfig.VERSION_NAME,
            installApk = { apk ->
                val uri = FileProvider.getUriForFile(this, "$packageName.update", apk)
                startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                        )
                )
            },
        )
    }
}
