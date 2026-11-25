package app.koma.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.koma.AppContainer
import app.koma.R
import app.koma.auth.SessionViewModel
import app.koma.auth.SignInPrompt
import app.koma.data.BackendId
import app.koma.data.MediaRef
import app.koma.history.HistoryView
import app.koma.library.LibraryView
import app.koma.settings.Account
import app.koma.settings.SettingsPage
import app.koma.settings.SettingsView
import app.koma.settings.title
import app.koma.title.TitleView
import app.koma.ui.theme.KomaTheme
import app.koma.update.UpdateDialog
import app.koma.update.UpdatePill
import app.koma.update.UpdateViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class Tab(
    val route: String,
    override val title: String,
    override val icon: Int,
    override val selectedIcon: Int,
    override val motion: IconMotion,
    val emptyTitle: String = "",
    val emptyMessage: String = "",
) : TabItem {
    Library(
        "library",
        "Library",
        R.drawable.ic_book,
        R.drawable.avd_book,
        IconMotion.Fill,
    ),
    Updates(
        "updates",
        "Updates",
        R.drawable.ic_notifications,
        R.drawable.ic_notifications_filled,
        IconMotion.Ring,
        "No new chapters",
        "New chapters of the titles you follow show up here.",
    ),
    History(
        "history",
        "History",
        R.drawable.ic_history,
        R.drawable.ic_history,
        IconMotion.Rewind,
        "Nothing read yet",
        "The chapters you read show up here.",
    ),
    Discover(
        "discover",
        "Discover",
        R.drawable.ic_auto_awesome,
        R.drawable.avd_auto_awesome,
        IconMotion.Fill,
        "Nothing to discover yet",
        "Trending, top-rated and popular titles show up here.",
    ),
    Settings(
        "settings",
        "Settings",
        R.drawable.ic_settings,
        R.drawable.ic_settings_filled,
        IconMotion.Turn,
    );

    override val tag: String
        get() = "tab-$route"
}

/** What the app shows: a tab, and the Settings page or the Library title opened over it. */
private data class Screen(val tab: Tab, val page: SettingsPage?, val series: MediaRef?) {
    val route: String
        get() = page?.route ?: series?.let { "series" } ?: tab.route

    // A title's own screen names it, under the header.
    fun title(backend: String): String =
        page?.title(backend) ?: if (series != null) "" else tab.title

    val nested: Boolean
        get() = page != null || series != null

    /** Keys the screen's saved state and its header accessory. */
    val key: String
        get() =
            page?.let { "settings-${it.route}" }
                ?: series?.let { "series-${it.backend}-${it.id}" }
                ?: tab.route
}

/**
 * Tabs fade through one another. A page opened over a tab slides in along the horizontal axis, and
 * slides back out the way it came.
 */
private fun AnimatedContentTransitionScope<Screen>.screenChange(
    motion: MotionScheme
): ContentTransform {
    val spatial = motion.defaultSpatialSpec<IntOffset>()
    val effects = motion.defaultEffectsSpec<Float>()
    return when {
        initialState.tab != targetState.tab ->
            fadeIn(effects) +
                scaleIn(motion.defaultSpatialSpec(), initialScale = 0.94f) togetherWith
                fadeOut(motion.fastEffectsSpec())
        targetState.nested ->
            slideInHorizontally(spatial) { it / 4 } + fadeIn(effects) togetherWith
                slideOutHorizontally(spatial) { -it / 4 } + fadeOut(effects)
        else ->
            slideInHorizontally(spatial) { -it / 4 } + fadeIn(effects) togetherWith
                slideOutHorizontally(spatial) { it / 4 } + fadeOut(effects)
    } using SizeTransform(clip = false)
}

private val MediaRefSaver =
    Saver<MediaRef?, List<String>>(
        save = { ref -> ref?.let { listOf(it.backend.name, it.id) } },
        restore = { (backend, id) -> MediaRef(BackendId.valueOf(backend), id) },
    )

@Composable
fun KomaApp(container: AppContainer) {
    val sessionModel = viewModel { SessionViewModel(container) }
    val updateModel = viewModel { UpdateViewModel(container) }
    val session by sessionModel.session.collectAsStateWithLifecycle()
    val signIn by sessionModel.signInView.collectAsStateWithLifecycle()
    val userName by sessionModel.userName.collectAsStateWithLifecycle()
    val theme by container.theme.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Library) }
    var settingsPage by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    var openSeries by
        rememberSaveable(stateSaver = MediaRefSaver) { mutableStateOf<MediaRef?>(null) }
    val page = settingsPage.takeIf { tab == Tab.Settings }
    val series = openSeries.takeIf { tab == Tab.Library && session.signedIn }
    val screen = Screen(tab, page, series)
    val back = {
        when (tab) {
            Tab.Settings -> settingsPage = null
            Tab.Library -> openSeries = null
            else -> {}
        }
    }
    val openSignIn = {
        tab = Tab.Settings
        settingsPage = SettingsPage.Account
    }
    // Keeps each screen's shelf and scroll position while another one shows.
    val saved = rememberSaveableStateHolder()
    val backdrop = remember { HeaderBackdrop() }
    // The header, without its accessory, and the bar float over the content, which leaves them this
    // much room at its ends.
    var headerHeight by remember { mutableStateOf(0.dp) }
    var barHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    val screens = remember { SeekableTransitionState(screen) }
    LaunchedEffect(screen) { screens.animateTo(screen) }
    val scope = rememberCoroutineScope()
    // The back gesture drags the page back over its tab, which it opens once released.
    PredictiveBackHandler(enabled = screen.nested) { progress ->
        try {
            progress.collect { screens.seekTo(it.progress, Screen(tab, null, null)) }
            back()
        } catch (e: CancellationException) {
            scope.launch { screens.animateTo(screens.currentState) }
            throw e
        }
    }
    val transition = rememberTransition(screens, label = "screen")

    KomaTheme(theme) {
        val motion = MaterialTheme.motionScheme
        val bars =
            remember(density, motion) {
                HidingBars(with(density) { BarsDistance.toPx() }, motion.defaultEffectsSpec())
            }
        LaunchedEffect(screen) { bars.show() }
        // A scroll coming into view may sit at its top, where it could never bring the bars back.
        LaunchedEffect(backdrop, bars) { snapshotFlow { backdrop.tracked }.collect { bars.show() } }
        Surface(Modifier.fillMaxSize().testTag("root")) {
            Box(Modifier.fillMaxSize()) {
                val contentPadding = PaddingValues(top = headerHeight, bottom = barHeight)
                Box(
                    Modifier.fillMaxSize()
                        .nestedScroll(bars)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                        )
                ) {
                    transition.AnimatedContent(transitionSpec = { screenChange(motion) }) { shown ->
                        val entering = this.transition.targetState == EnterExitState.Visible
                        CompositionLocalProvider(
                            LocalHeaderBackdrop provides backdrop.takeIf { entering },
                            LocalHeaderScreen provides
                                remember(shown.key) { HeaderScreen(backdrop, shown.key) },
                        ) {
                            saved.SaveableStateProvider(shown.key) {
                                when {
                                    shown.tab == Tab.Settings ->
                                        SettingsView(
                                            container = container,
                                            page = shown.page,
                                            onOpen = { settingsPage = it },
                                            account =
                                                Account(
                                                    backend = sessionModel.backendName,
                                                    signedIn = session.signedIn,
                                                    userName = userName,
                                                    busy = signIn.busy,
                                                    error = signIn.error,
                                                ),
                                            onSignIn = sessionModel::signIn,
                                            onSignOut = sessionModel::signOut,
                                            onLoadUserName = sessionModel::loadUserName,
                                            contentPadding = contentPadding,
                                        )
                                    shown.series != null ->
                                        TitleView(container, shown.series, contentPadding)
                                    shown.tab == Tab.Library && session.signedIn ->
                                        LibraryView(container, contentPadding) {
                                            openSeries = it
                                        }
                                    shown.tab == Tab.Library ->
                                        SignInPrompt(
                                            backend = sessionModel.backendName,
                                            message =
                                                "Koma shows the titles you track on ${sessionModel.backendName}.",
                                            tag = "signed-out",
                                            buttonTag = "sign-in",
                                            contentPadding = contentPadding,
                                            onSignIn = openSignIn,
                                        )
                                    shown.tab == Tab.History && session.signedIn ->
                                        HistoryView(container, contentPadding)
                                    shown.tab == Tab.History ->
                                        SignInPrompt(
                                            backend = sessionModel.backendName,
                                            message =
                                                "Koma shows the chapters you read on ${sessionModel.backendName}.",
                                            tag = "history-signed-out",
                                            buttonTag = "history-sign-in",
                                            contentPadding = contentPadding,
                                            onSignIn = openSignIn,
                                        )
                                    else ->
                                        EmptyState(
                                            shown.tab.icon,
                                            shown.tab.emptyTitle,
                                            shown.tab.emptyMessage,
                                            Modifier.padding(contentPadding)
                                                .testTag("empty-${shown.tab.route}"),
                                        )
                                }
                            }
                        }
                    }
                }
                FloatingHeader(
                    backdrop,
                    hidden = { bars.hidden },
                    onHeight = { headerHeight = with(density) { it.toDp() } },
                    accessory = {
                        transition.AnimatedContent(transitionSpec = { screenChange(motion) }) {
                            // An entering screen hands its accessory over a frame late, and a
                            // slide sized on that empty frame would not move.
                            Box(Modifier.fillMaxWidth()) { backdrop.accessory(it.key)?.invoke() }
                        }
                    },
                ) {
                    ScreenHeader(actions = { UpdatePill(updateModel) }) {
                        transition.AnimatedContent(
                            transitionSpec = { screenChange(motion) },
                            contentAlignment = Alignment.CenterStart,
                        ) { shown ->
                            ScreenTitle(
                                title = shown.title(sessionModel.backendName),
                                titleTag = "title-${shown.route}",
                                onBack = back.takeIf { shown.nested },
                            )
                        }
                    }
                }
                NavigationBar(
                    tabs = Tab.entries,
                    selected = tab,
                    onSelect = {
                        // Tapping a tab again leads back to its root.
                        if (it == tab) back()
                        tab = it
                    },
                    hidden = { bars.hidden },
                    modifier =
                        Modifier.align(Alignment.BottomCenter).onSizeChanged {
                            barHeight = with(density) { it.height.toDp() }
                        },
                )
                UpdateDialog(updateModel)
            }
        }
    }
}

// How far the content scrolls to slide the bars all the way out.
private val BarsDistance = 72.dp
