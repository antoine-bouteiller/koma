package app.koma.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.koma.AppContainer
import app.koma.R
import app.koma.auth.SessionViewModel
import app.koma.auth.SignInPrompt
import app.koma.auth.SignOutButton
import app.koma.history.HistoryView
import app.koma.library.LibraryView
import app.koma.ui.theme.KomaTheme
import app.koma.update.UpdateBanner
import app.koma.update.UpdateViewModel

enum class Tab(
    val route: String,
    override val title: String,
    override val icon: Int,
    val emptyTitle: String,
    val emptyMessage: String,
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
    );

    override val tag: String
        get() = "tab-$route"
}

// Room under the content for the floating tab bar, which the content scrolls under.
private val TabBarSpace = PaddingValues(bottom = 104.dp)

@Composable
fun KomaApp(container: AppContainer) {
    val sessionModel = viewModel { SessionViewModel(container) }
    val updateModel = viewModel { UpdateViewModel(container) }
    val session by sessionModel.session.collectAsStateWithLifecycle()
    val signIn by sessionModel.signInView.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Library) }
    // Keeps each tab's shelf and scroll position while another tab shows.
    val tabs = rememberSaveableStateHolder()

    KomaTheme {
        Surface(Modifier.fillMaxSize().testTag("root")) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                            )
                        )
                ) {
                    ScreenHeader(tab.title, "title-${tab.route}") {
                        if (tab == Tab.Library && session.signedIn) {
                            SignOutButton(sessionModel::signOut)
                        }
                    }
                    UpdateBanner(updateModel, container.openUrl)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        tabs.SaveableStateProvider(tab.route) {
                            when {
                                tab == Tab.Library && session.signedIn ->
                                    LibraryView(container, TabBarSpace)
                                tab == Tab.Library ->
                                    SignInPrompt(
                                        view = signIn,
                                        message = "Koma shows the titles you track on MangaBaka.",
                                        tag = "signed-out",
                                        buttonTag = "sign-in",
                                        errorShown = true,
                                        contentPadding = TabBarSpace,
                                        onSignIn = sessionModel::signIn,
                                    )
                                tab == Tab.History && session.signedIn ->
                                    HistoryView(container, TabBarSpace)
                                tab == Tab.History ->
                                    SignInPrompt(
                                        view = signIn,
                                        message = "Koma shows the chapters you read on MangaBaka.",
                                        tag = "history-signed-out",
                                        buttonTag = "history-sign-in",
                                        errorShown = false,
                                        contentPadding = TabBarSpace,
                                        onSignIn = sessionModel::signIn,
                                    )
                                else ->
                                    EmptyState(
                                        tab.icon,
                                        tab.emptyTitle,
                                        tab.emptyMessage,
                                        Modifier.padding(TabBarSpace).testTag("empty-${tab.route}"),
                                    )
                            }
                        }
                    }
                }
                FloatingTabBar(
                    tabs = Tab.entries,
                    selected = tab,
                    onSelect = { tab = it },
                    modifier =
                        Modifier.align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = 16.dp),
                )
            }
        }
    }
}
