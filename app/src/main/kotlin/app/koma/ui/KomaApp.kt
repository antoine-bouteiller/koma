package app.koma.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.koma.AppContainer
import app.koma.R
import app.koma.auth.SessionViewModel
import app.koma.auth.SignInPrompt
import app.koma.history.HistoryView
import app.koma.library.LibraryView
import app.koma.settings.Account
import app.koma.settings.SettingsPage
import app.koma.settings.SettingsView
import app.koma.ui.theme.KomaTheme
import app.koma.update.UpdateDialog
import app.koma.update.UpdatePill
import app.koma.update.UpdateViewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

enum class Tab(
    val route: String,
    override val title: String,
    override val icon: Int,
    val emptyTitle: String = "",
    val emptyMessage: String = "",
) : TabItem {
    Library(
        "library",
        "Library",
        R.drawable.ic_book,
        "Your library is empty",
        "The titles you track on MangaBaka show up here.",
    ),
    Updates(
        "updates",
        "Updates",
        R.drawable.ic_notifications,
        "No new chapters",
        "New chapters of the titles you follow show up here.",
    ),
    History(
        "history",
        "History",
        R.drawable.ic_history,
        "Nothing read yet",
        "The chapters you read show up here.",
    ),
    Discover(
        "discover",
        "Discover",
        R.drawable.ic_auto_awesome,
        "Nothing to discover yet",
        "Trending, top-rated and popular titles show up here.",
    ),
    Settings("settings", "Settings", R.drawable.ic_settings);

    override val tag: String
        get() = "tab-$route"
}

// Room under the content for the floating tab bar, which the content scrolls under.
private val TabBarSpace = 104.dp

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
    val page = settingsPage.takeIf { tab == Tab.Settings }
    val openSignIn = {
        tab = Tab.Settings
        settingsPage = SettingsPage.MangaBaka
    }
    // Keeps each tab's shelf and scroll position while another tab shows.
    val tabs = rememberSaveableStateHolder()
    val haze = rememberHazeState()
    val backdrop = remember { HeaderBackdrop() }
    // The header floats over the content, which leaves it this much room at its start.
    var headerHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    BackHandler(enabled = page != null) { settingsPage = null }

    KomaTheme(theme) {
        Surface(Modifier.fillMaxSize().testTag("root")) {
            Box(Modifier.fillMaxSize()) {
                val contentPadding = PaddingValues(top = headerHeight, bottom = TabBarSpace)
                Box(
                    Modifier.fillMaxSize()
                        .hazeSource(haze)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                        )
                ) {
                    CompositionLocalProvider(LocalHeaderBackdrop provides backdrop) {
                        tabs.SaveableStateProvider(
                            page?.let { "settings-${it.route}" } ?: tab.route
                        ) {
                            when {
                                tab == Tab.Settings ->
                                    SettingsView(
                                        container = container,
                                        page = page,
                                        onOpen = { settingsPage = it },
                                        account =
                                            Account(
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
                                tab == Tab.Library && session.signedIn ->
                                    LibraryView(container, contentPadding)
                                tab == Tab.Library ->
                                    SignInPrompt(
                                        message = "Koma shows the titles you track on MangaBaka.",
                                        tag = "signed-out",
                                        buttonTag = "sign-in",
                                        contentPadding = contentPadding,
                                        onSignIn = openSignIn,
                                    )
                                tab == Tab.History && session.signedIn ->
                                    HistoryView(container, contentPadding)
                                tab == Tab.History ->
                                    SignInPrompt(
                                        message = "Koma shows the chapters you read on MangaBaka.",
                                        tag = "history-signed-out",
                                        buttonTag = "history-sign-in",
                                        contentPadding = contentPadding,
                                        onSignIn = openSignIn,
                                    )
                                else ->
                                    EmptyState(
                                        tab.icon,
                                        tab.emptyTitle,
                                        tab.emptyMessage,
                                        Modifier.padding(contentPadding)
                                            .testTag("empty-${tab.route}"),
                                    )
                            }
                        }
                    }
                }
                GlassHeader(
                    haze,
                    backdrop,
                    Modifier.onSizeChanged { headerHeight = with(density) { it.height.toDp() } },
                ) {
                    ScreenHeader(
                        title = page?.title ?: tab.title,
                        titleTag = "title-${page?.route ?: tab.route}",
                        onBack = page?.let { { settingsPage = null } },
                    ) {
                        UpdatePill(updateModel)
                    }
                }
                FloatingTabBar(
                    tabs = Tab.entries,
                    selected = tab,
                    onSelect = {
                        // Tapping Settings again leads back to its list.
                        if (it == tab) settingsPage = null
                        tab = it
                    },
                    haze = haze,
                    modifier =
                        Modifier.align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
                UpdateDialog(updateModel)
            }
        }
    }
}
