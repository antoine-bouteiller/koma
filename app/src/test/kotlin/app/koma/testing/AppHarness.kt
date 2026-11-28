package app.koma.testing

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.koma.AppContainer
import app.koma.data.Backend
import app.koma.data.ChapterUpdate
import app.koma.data.KomaDatabase
import app.koma.data.httpClient
import app.koma.data.mangabaka.MangaBakaBackend
import app.koma.source.CatalogSource
import app.koma.ui.KomaApp
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.serviceLoaderEnabled
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import okhttp3.Dispatcher
import org.junit.Assert.assertEquals
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment

private const val RELEASES = "https://api.github.com/repos/antoine-bouteiller/koma/releases/latest"
private const val ISSUER = "https://mangabaka.org/auth"

/**
 * Runs the whole app on the JVM against [backend], else MangaBaka over [network], [secrets] and
 * [browser]. Work runs on the main looper, so Compose's idling waits for it. A test that makes a
 * request no stub answers fails.
 */
class AppHarness : TestRule {
    val compose: ComposeContentTestRule = createComposeRule()
    private val folder = TemporaryFolder()

    val network = FakeNetwork()
    val secrets = MemorySecretStore()
    val preferences = MemoryPreferenceStore()
    val browser = FakeBrowser()
    /** The APKs handed to Android's installer. */
    val installed = mutableListOf<File>()
    /** What each background library update announced, in order. */
    val libraryUpdates = CopyOnWriteArrayList<ChapterUpdate>()
    /** The image URLs the app asked for. */
    val images = CopyOnWriteArrayList<String>()
    var image: suspend (ImageRequest) -> ImageResult = { request ->
        ErrorResult(null, request, IOException("No images in tests."))
    }

    var epoch: Instant = Instant.parse("2026-01-01T12:00:00Z")
    var zone: ZoneId = ZoneOffset.UTC
    /** Replaces MangaBaka when set before [launch]; kept across [reload]. */
    var backend: Backend? = null
    /** The sources each container gets; none unless a test says otherwise. */
    var sources: () -> List<CatalogSource> = { emptyList() }

    private var running by mutableStateOf<AppContainer?>(null)
    private var backDispatcher: OnBackPressedDispatcher? = null

    /** The running app's dependencies. */
    val container: AppContainer
        get() = checkNotNull(running) { "The app is not running." }

    /** The database file, which a test may write before [launch]. */
    val database: File
        get() = folder.root.resolve("koma.db")

    override fun apply(base: Statement, description: Description): Statement =
        RuleChain.outerRule(folder)
            .around(compose)
            .apply(
                object : Statement() {
                    override fun evaluate() {
                        // GitHub has no release unless a test says otherwise.
                        network.answer(RELEASES, 404, """{"message":"Not Found"}""")
                        // Nor has MangaBaka any read.
                        network.answer(EVENTS, 200, events(50, "end", false))
                        base.evaluate()
                        assertEquals("unstubbed requests", emptyList<String>(), network.unstubbed)
                    }
                },
                description,
            )

    private fun newContainer(): AppContainer {
        val context = RuntimeEnvironment.getApplication()
        val http =
            httpClient().newBuilder().dispatcher(Dispatcher(Inline)).addInterceptor(network).build()
        val clock = Clock.fixed(epoch, ZoneOffset.UTC)
        lateinit var built: AppContainer
        var update: Job? = null
        built =
            AppContainer(
                context = context,
                http = http,
                images =
                    ImageLoader.Builder(context)
                        .serviceLoaderEnabled(false)
                        .components {
                            add(
                                object : Interceptor {
                                    override suspend fun intercept(
                                        chain: Interceptor.Chain
                                    ): ImageResult {
                                        images += chain.request.data.toString()
                                        return image(chain.request)
                                    }
                                }
                            )
                        }
                        .build(),
                driver =
                    AndroidSqliteDriver(
                        KomaDatabase.Schema,
                        context,
                        database.absolutePath,
                    ),
                backend = backend ?: MangaBakaBackend(http, secrets, browser, clock),
                sources = sources(),
                preferences = preferences,
                clock = clock,
                zone = { zone },
                installedVersion = "0.1.0",
                installApk = { installed += it },
                startLibraryUpdate = {
                    if (update?.isActive != true) {
                        update =
                            MainScope().launch { built.updater.updateChapters(libraryUpdates::add) }
                    }
                },
                work = Dispatchers.Main,
            )
        return built
    }

    /** Opens the app. */
    fun launch() {
        running = newContainer()
        compose.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            // Saved state outlives a reload, as Android restores it after the process is gone.
            val saved = rememberSaveableStateHolder()
            val current = running ?: return@setContent
            // No key(current): rememberSaveable keys its state by position, and a reload empties
            // the
            // content in between, so the app is still composed from scratch.
            val owner =
                remember(current) {
                    object : ViewModelStoreOwner {
                        override val viewModelStore = ViewModelStore()
                    }
                }
            DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                saved.SaveableStateProvider("app") { KomaApp(current) }
            }
        }
        awaitIdle()
    }

    /** Quits and opens the app again, over the same database file and secrets. */
    fun reload() {
        val old = container
        // Disposed first, so the saved state is in before the new app reads it.
        running = null
        awaitIdle()
        old.driver.close()
        running = newContainer()
        awaitIdle()
    }

    /** Stores an unexpired MangaBaka session. */
    fun signedIn(
        accessToken: String = "access",
        refreshToken: String = "refresh",
        expiresAt: Long = epoch.toEpochMilli() + 7 * 86_400_000L,
    ) {
        secrets.set(
            "mangabaka.session",
            """{"accessToken":"$accessToken","refreshToken":"$refreshToken","expiresAt":$expiresAt}""",
        )
        // Asked at every launch.
        network.answer(PROFILE, 200, profile("reader"))
    }

    /** Stubs MangaBaka's discovery, token and revocation endpoints. */
    fun stubAuthServer(
        token: String =
            """{"access_token":"access","refresh_token":"refresh","token_type":"Bearer","expires_in":604800}"""
    ) {
        network.answer(
            "$ISSUER/.well-known/openid-configuration",
            200,
            """{"issuer":"$ISSUER","authorization_endpoint":"$ISSUER/oauth2/authorize","token_endpoint":"$ISSUER/oauth2/token","revocation_endpoint":"$ISSUER/oauth2/revoke"}""",
        )
        network.answer("$ISSUER/oauth2/token", 200, token)
        network.answer("$ISSUER/oauth2/revoke", 200, "{}")
    }

    /** Waits until no request is in flight and Compose is idle. */
    fun awaitIdle() {
        do compose.waitForIdle() while (!network.idle)
    }

    /** Whether Android's back would close the app. */
    val backClosesApp: Boolean
        get() = compose.runOnIdle { !checkNotNull(backDispatcher).hasEnabledCallbacks() }

    /** Presses Android's back, which must stay in the app. */
    fun back() {
        check(!backClosesApp) { "Back would close the app." }
        compose.runOnIdle { checkNotNull(backDispatcher).onBackPressed() }
        awaitIdle()
    }

    fun node(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag)

    fun tap(tag: String) {
        node(tag).performClick()
        awaitIdle()
    }

    fun text(tag: String, expected: String) {
        node(tag).assertTextEquals(expected)
    }

    fun has(tag: String) {
        node(tag).assertExists()
    }

    fun missing(tag: String) {
        node(tag).assertDoesNotExist()
    }

    fun selected(tag: String) {
        node(tag).assertIsSelected()
    }

    /** The test tags of [tag]'s children, in order. */
    fun childTags(tag: String): List<String> {
        val children = node(tag).onChildren().fetchSemanticsNodes()
        return children.mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
    }
}

/**
 * Runs an enqueued call on the thread that enqueues it, so [AppHarness.awaitIdle] never sees a call
 * that has left the main looper but not yet reached [FakeNetwork].
 */
private object Inline : AbstractExecutorService() {
    override fun execute(command: Runnable) = command.run()

    override fun shutdown() = Unit

    override fun shutdownNow(): List<Runnable> = emptyList()

    override fun isShutdown() = false

    override fun isTerminated() = false

    override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
}
