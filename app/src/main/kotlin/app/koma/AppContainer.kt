package app.koma

import android.app.Application
import android.content.Context
import android.net.Uri
import app.cash.sqldelight.db.SqlDriver
import app.koma.data.AccountRepository
import app.koma.data.Backend
import app.koma.data.HistoryRepository
import app.koma.data.KomaDatabase
import app.koma.data.LibraryRepository
import app.koma.data.LibraryUpdater
import app.koma.data.MediaRepository
import app.koma.data.SourceRepository
import app.koma.data.Store
import app.koma.source.CatalogSource
import app.koma.ui.theme.ThemePreferences
import coil3.ImageLoader
import eu.kanade.tachiyomi.network.NetworkHelper
import java.io.File
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addFactory

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
    backend: Backend,
    /** The sources chapters come from, in the order binding tries them. */
    sources: List<CatalogSource>,
    val preferences: PreferenceStore,
    val clock: Clock,
    val zone: () -> ZoneId,
    val installedVersion: String,
    /** Hands a downloaded APK to Android's installer, which asks the user to install it. */
    val installApk: (File) -> Unit,
    /**
     * Runs [LibraryUpdater.updateChapters] in the background, showing its progress and then the
     * titles no source bound, unless it already runs.
     */
    val startLibraryUpdate: () -> Unit,
    /**
     * Runs the blocking network and database work. Tests pass the main dispatcher, which Compose's
     * idling waits for.
     */
    val work: CoroutineDispatcher = Dispatchers.IO,
) {
    private val store = Store(KomaDatabase(driver), work)
    val media = MediaRepository(store, backend)
    val library = LibraryRepository(store, backend, media, clock)
    val history = HistoryRepository(store, backend, media)
    val account = AccountRepository(store, backend)
    val sources = SourceRepository(store, sources)
    val updater = LibraryUpdater(store, library, media, this.sources)
    val theme = ThemePreferences(preferences)

    init {
        // What Mihon's app provides its extensions. Factories, so the latest container's win.
        val application = context.applicationContext as Application
        val network = NetworkHelper(http)
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
        Injekt.addFactory<Application> { application }
        Injekt.addFactory { network }
        Injekt.addFactory { json }
    }
}
