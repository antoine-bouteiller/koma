package app.koma.testing

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
import app.koma.data.KomaDatabase
import app.koma.data.httpClient
import app.koma.ui.KomaApp
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.serviceLoaderEnabled
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
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
 * Runs the whole app on the JVM against [network], [secrets] and [browser]. Work runs on the main
 * looper, so Compose's idling waits for it. A test that makes a request no stub answers fails.
 */
class AppHarness : TestRule {
    val compose: ComposeContentTestRule = createComposeRule()
    private val folder = TemporaryFolder()

    val network = FakeNetwork()
    val secrets = MemorySecretStore()
    val preferences = MemoryPreferenceStore()
    val browser = FakeBrowser()
    val opened = mutableListOf<String>()
    /** The image URLs the app asked for; no image is ever loaded. */
    val images = CopyOnWriteArrayList<String>()

    var epoch: Instant = Instant.parse("2026-01-01T12:00:00Z")
    var zone: ZoneId = ZoneOffset.UTC

    private var container by mutableStateOf<AppContainer?>(null)

    override fun apply(base: Statement, description: Description): Statement =
        RuleChain.outerRule(folder)
            .around(compose)
            .apply(
                object : Statement() {
                    override fun evaluate() {
                        // GitHub has no release unless a test says otherwise.
                        network.answer(RELEASES, 404, """{"message":"Not Found"}""")
                        base.evaluate()
                        assertEquals("unstubbed requests", emptyList<String>(), network.unstubbed)
                    }
                },
                description,
            )

    private fun newContainer(): AppContainer {
        val context = RuntimeEnvironment.getApplication()
        return AppContainer(
            context = context,
            http = httpClient().newBuilder().addInterceptor(network).build(),
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
                                    return ErrorResult(
                                        null,
                                        chain.request,
                                        IOException("No images in tests."),
                                    )
                                }
                            }
                        )
                    }
                    .build(),
            driver =
                AndroidSqliteDriver(
                    KomaDatabase.Schema,
                    context,
                    folder.root.resolve("koma.db").absolutePath,
                ),
            secrets = secrets,
            preferences = preferences,
            browser = browser,
            clock = Clock.fixed(epoch, ZoneOffset.UTC),
            zone = { zone },
            installedVersion = "0.1.0",
            openUrl = { opened += it },
            work = Dispatchers.Main,
        )
    }

    /** Opens the app. */
    fun launch() {
        container = newContainer()
        compose.setContent {
            // Saved state outlives a reload, as Android restores it after the process is gone.
            val saved = rememberSaveableStateHolder()
            val current = container ?: return@setContent
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
        val old = checkNotNull(container)
        // Disposed first, so the saved state is in before the new app reads it.
        container = null
        awaitIdle()
        old.driver.close()
        container = newContainer()
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
