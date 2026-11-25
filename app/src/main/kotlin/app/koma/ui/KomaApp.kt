package app.koma.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import app.koma.AppContainer
import app.koma.R
import app.koma.auth.SessionViewModel
import app.koma.auth.SignInPrompt
import app.koma.data.MediaRef
import app.koma.history.HistoryView
import app.koma.library.LibraryView
import app.koma.settings.Account
import app.koma.settings.SettingsPage
import app.koma.settings.SettingsView
import app.koma.settings.title
import app.koma.title.TitleView
import app.koma.ui.theme.KomaTheme
import app.koma.ui.theme.easedColors
import app.koma.update.UpdateDialog
import app.koma.update.UpdatePill
import app.koma.update.UpdateViewModel
import java.util.UUID
import kotlinx.serialization.Serializable

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

/** A screen in a tab's back stack: the tab's own, a Settings page, or a title. */
@Serializable
internal sealed interface Route : NavKey {
    @Serializable data class Root(val tab: Tab) : Route

    @Serializable data class Page(val page: SettingsPage) : Route

    /**
     * [visit] tells apart two openings of a title. With equal keys, reopening a title as it closes
     * would revive the closing screen instead of opening a new one.
     */
    @Serializable data class Title(val ref: MediaRef, val visit: String) : Route
}

// A title's own screen names it, under the header.
private fun Route.title(backend: String): String =
    when (this) {
        is Route.Root -> tab.title
        is Route.Page -> page.title(backend)
        is Route.Title -> ""
    }

private val Route.tag: String
    get() =
        when (this) {
            is Route.Root -> tab.route
            is Route.Page -> page.route
            is Route.Title -> "series"
        }

/** The tab whose back stack holds an entry. */
private object TabKey : NavMetadataKey<Tab>

private val Scene<NavKey>.tab: Tab?
    get() = entries.last().metadata[TabKey]

private fun MutableList<NavKey>.popToRoot() {
    while (size > 1) removeAt(lastIndex)
}

/**
 * Tabs switch instantly. A page opened over a tab slides in along the horizontal axis, and slides
 * back out the way it came.
 */
private fun AnimatedContentTransitionScope<Scene<NavKey>>.screenChange(
    motion: MotionScheme,
    pop: Boolean,
    dragged: Boolean = false,
): ContentTransform {
    val spatial = motion.defaultSpatialSpec<IntOffset>()
    val effects = motion.defaultEffectsSpec<Float>()
    val from = if (pop) -1 else 1
    return when {
        initialState.tab == targetState.tab ->
            slideInHorizontally(spatial) { from * it / 4 } + fadeIn(effects) togetherWith
                slideOutHorizontally(spatial) { -from * it / 4 } + fadeOut(effects)
        // Held midway by the back gesture, an instant switch would show both tabs at once.
        dragged -> fadeIn(effects) togetherWith fadeOut(effects)
        else -> EnterTransition.None togetherWith ExitTransition.None
    }
}

@Composable
fun KomaApp(container: AppContainer) {
    val sessionModel = viewModel { SessionViewModel(container) }
    val updateModel = viewModel { UpdateViewModel(container) }
    val session by sessionModel.session.collectAsStateWithLifecycle()
    val signIn by sessionModel.signInView.collectAsStateWithLifecycle()
    val userName by sessionModel.userName.collectAsStateWithLifecycle()
    val theme by container.theme.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Library) }
    val stacks = Tab.entries.associateWith { rememberNavBackStack(Route.Root(it)) }
    LaunchedEffect(session.signedIn) {
        if (!session.signedIn) stacks.getValue(Tab.Library).popToRoot()
    }
    // Only the Library's root leaves the app; another tab's root leads back to the Library.
    val back: () -> Unit = {
        val stack = stacks.getValue(tab)
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else tab = Tab.Library
    }
    val openSignIn: () -> Unit = {
        tab = Tab.Settings
        stacks.getValue(Tab.Settings).run {
            popToRoot()
            add(Route.Page(SettingsPage.Account))
        }
    }
    // The header, without its accessory, and the bar float over the content, which leaves them this
    // much room at its ends.
    var headerHeight by remember { mutableStateOf(0.dp) }
    var barHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    KomaTheme(theme) {
        val motion = MaterialTheme.motionScheme
        val bars =
            remember(density, motion) {
                HidingBars(with(density) { BarsDistance.toPx() }, motion.defaultEffectsSpec())
            }
        val screen by
            rememberUpdatedState<@Composable (Tab, Route) -> Unit> { owner, route ->
                val stack = stacks.getValue(owner)
                val contentPadding = PaddingValues(top = headerHeight, bottom = barHeight)
                val root = (route as? Route.Root)?.tab
                ScreenFrame(
                    title = route.title(sessionModel.backendName),
                    titleTag = "title-${route.tag}",
                    onBack = back.takeIf { root == null },
                    bars = bars,
                    onHeaderHeight = { headerHeight = with(density) { it.toDp() } },
                    actions = { UpdatePill(updateModel) },
                ) {
                    when {
                        route is Route.Title -> TitleView(container, route.ref, contentPadding)
                        route is Route.Page || root == Tab.Settings ->
                            SettingsView(
                                container = container,
                                page = (route as? Route.Page)?.page,
                                onOpen = { stack += Route.Page(it) },
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
                        root == Tab.Library && session.signedIn ->
                            LibraryView(container, contentPadding) {
                                stack += Route.Title(it, UUID.randomUUID().toString())
                            }
                        root == Tab.Library ->
                            SignInPrompt(
                                backend = sessionModel.backendName,
                                message =
                                    "Koma shows the titles you track on ${sessionModel.backendName}.",
                                tag = "signed-out",
                                buttonTag = "sign-in",
                                contentPadding = contentPadding,
                                onSignIn = openSignIn,
                            )
                        root == Tab.History && session.signedIn ->
                            HistoryView(container, contentPadding)
                        root == Tab.History ->
                            SignInPrompt(
                                backend = sessionModel.backendName,
                                message =
                                    "Koma shows the chapters you read on ${sessionModel.backendName}.",
                                tag = "history-signed-out",
                                buttonTag = "history-sign-in",
                                contentPadding = contentPadding,
                                onSignIn = openSignIn,
                            )
                        root != null ->
                            EmptyState(
                                root.icon,
                                root.emptyTitle,
                                root.emptyMessage,
                                Modifier.padding(contentPadding).testTag("empty-${root.route}"),
                            )
                    }
                }
            }
        val decorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                rememberViewModelStoreNavEntryDecorator(),
            )
        // Each tab keeps its entries' state while another tab shows. An entry is remembered with
        // its back stack, so it draws the latest screen.
        val entries =
            Tab.entries.associateWith { owner ->
                rememberDecoratedNavEntries(stacks.getValue(owner), decorators) { key ->
                    NavEntry(key, metadata = metadata { put(TabKey, owner) }) {
                        screen(owner, it as Route)
                    }
                }
            }
        Surface(Modifier.fillMaxSize().testTag("root")) {
            Box(Modifier.fillMaxSize()) {
                NavDisplay(
                    // Another tab lies over the Library, where its root leads back.
                    entries =
                        entries.getValue(Tab.Library) +
                            if (tab == Tab.Library) emptyList() else entries.getValue(tab),
                    modifier = Modifier.fillMaxSize().nestedScroll(bars),
                    transitionSpec = { screenChange(motion, pop = false) },
                    popTransitionSpec = { screenChange(motion, pop = true) },
                    predictivePopTransitionSpec = {
                        screenChange(motion, pop = true, dragged = true)
                    },
                    onBack = back,
                )
                NavigationBar(
                    tabs = Tab.entries,
                    selected = tab,
                    onSelect = {
                        // Tapping a tab again leads back to its root.
                        if (it == tab) stacks.getValue(it).popToRoot() else tab = it
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

/** A screen under its own floating header, which shows [title] and the content's accessory. */
@Composable
private fun ScreenFrame(
    title: String,
    titleTag: String,
    onBack: (() -> Unit)?,
    bars: HidingBars,
    onHeaderHeight: (Int) -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val backdrop = remember { HeaderBackdrop() }
    // A scroll coming into view may sit at its top, where it could never bring the bars back.
    LaunchedEffect(backdrop, bars) { snapshotFlow { backdrop.tracked }.collect { bars.show() } }
    val app = MaterialTheme.colorScheme
    // Eased only on screens that pick colors, so the app's theme still switches at once.
    val colors = if (backdrop.tinted) easedColors(backdrop.colors ?: app) else app
    MaterialTheme(colorScheme = colors, motionScheme = MaterialTheme.motionScheme) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
            Box(
                Modifier.fillMaxSize()
                    .background(if (backdrop.tinted) colors.surface else Color.Transparent)
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                        )
                ) {
                    CompositionLocalProvider(
                        LocalHeaderBackdrop provides backdrop,
                        content = content,
                    )
                }
                FloatingHeader(
                    backdrop,
                    hidden = { bars.hidden },
                    onHeight = onHeaderHeight,
                    accessory = { backdrop.accessory?.invoke() },
                ) {
                    ScreenHeader(actions = actions) { ScreenTitle(title, titleTag, onBack) }
                }
            }
        }
    }
}

// How far the content scrolls to slide the bars all the way out.
private val BarsDistance = 72.dp
