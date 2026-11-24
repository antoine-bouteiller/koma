package app.koma

import android.content.Context
import android.net.Uri
import app.cash.sqldelight.db.SqlDriver
import app.koma.auth.MangaBakaAuth
import app.koma.data.KomaDatabase
import app.koma.data.MangaBakaApi
import app.koma.data.Store
import app.koma.ui.theme.ThemePreferences
import coil3.ImageLoader
import java.io.File
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient

/** [set] and [delete] throw an IOException when the change did not reach disk. */
interface SecretStore {
    fun get(key: String): String?

    fun set(key: String, value: String)

    fun delete(key: String)
}

/** Plain values that outlive the process and the signed-in session. */
interface PreferenceStore {
    fun get(key: String): String?

    fun set(key: String, value: String)
}

fun interface AuthBrowser {
    /** Opens [url] and answers the [redirect] it comes back to, or null when cancelled. */
    suspend fun open(url: String, redirect: String): Uri?
}

class AppContainer(
    val context: Context,
    val http: OkHttpClient,
    val images: ImageLoader,
    val driver: SqlDriver,
    val secrets: SecretStore,
    val preferences: PreferenceStore,
    val browser: AuthBrowser,
    val clock: Clock,
    val zone: () -> ZoneId,
    val installedVersion: String,
    /** Hands a downloaded APK to Android's installer, which asks the user to install it. */
    val installApk: (File) -> Unit,
    /**
     * Runs the blocking network and database work. Tests pass the main dispatcher, which Compose's
     * idling waits for.
     */
    val work: CoroutineDispatcher = Dispatchers.IO,
) {
    val auth = MangaBakaAuth(http, secrets, browser, clock)
    val api = MangaBakaApi(http, auth)
    val store = Store(KomaDatabase(driver), api, work)
    val theme = ThemePreferences(preferences)
}
