package app.koma.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.koma.AppContainer
import app.koma.R
import app.koma.ui.Chevron
import app.koma.ui.SettingsGroup
import app.koma.ui.SettingsRow
import app.koma.ui.SettingsSection
import app.koma.ui.TrackHeaderBackdrop

/** The Settings tab: its list of pages, or the [page] opened from it. */
@Composable
fun SettingsView(
    container: AppContainer,
    page: SettingsPage?,
    onOpen: (SettingsPage) -> Unit,
    account: Account,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onLoadUserName: () -> Unit,
    contentPadding: PaddingValues,
) {
    LaunchedEffect(account.signedIn) { if (account.signedIn) onLoadUserName() }
    SettingsColumn(contentPadding, page?.route ?: "settings") {
        when (page) {
            null -> SettingsRoot(account, onOpen)
            SettingsPage.Theme -> ThemeView(container)
            SettingsPage.MangaBaka -> MangaBakaSettings(account, onSignIn, onSignOut)
        }
    }
}

@Composable
private fun SettingsRoot(account: Account, onOpen: (SettingsPage) -> Unit) {
    SettingsSection("General", R.drawable.ic_settings) {
        SettingsGroup(
            listOf(
                { shape ->
                    SettingsRow(
                        title = "Theme",
                        icon = R.drawable.ic_dark_mode,
                        shape = shape,
                        tag = "settings-theme",
                        subtitle = "Dark mode, true black and colors",
                        onClick = { onOpen(SettingsPage.Theme) },
                    )
                },
                { shape ->
                    SettingsRow(
                        title = "MangaBaka",
                        icon = R.drawable.ic_person,
                        shape = shape,
                        tag = "settings-mangabaka",
                        subtitle = accountSummary(account),
                        onClick = { onOpen(SettingsPage.MangaBaka) },
                    )
                },
            )
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MangaBakaSettings(account: Account, onSignIn: () -> Unit, onSignOut: () -> Unit) {
    if (account.signedIn) {
        SettingsGroup(
            listOf(
                { shape ->
                    SettingsRow(
                        title = account.userName?.ifEmpty { null } ?: "MangaBaka account",
                        icon = R.drawable.ic_person,
                        shape = shape,
                        tag = "account",
                        subtitle = "Signed in to MangaBaka",
                    )
                },
                { shape ->
                    SettingsRow(
                        title = "Sign out",
                        icon = R.drawable.ic_logout,
                        shape = shape,
                        tag = "sign-out",
                        onClick = onSignOut,
                        color = MaterialTheme.colorScheme.error,
                        trailing = {},
                    )
                },
            )
        )
    } else {
        SettingsGroup(
            listOf { shape ->
                SettingsRow(
                    title = "Sign in",
                    icon = R.drawable.ic_login,
                    shape = shape,
                    tag = "mangabaka-sign-in",
                    subtitle = "Koma shows the titles you track and the chapters you read.",
                    onClick = onSignIn,
                    enabled = !account.busy,
                    trailing = {
                        if (account.busy) {
                            LoadingIndicator(Modifier.size(32.dp).testTag("sign-in-busy"))
                        } else {
                            Chevron()
                        }
                    },
                )
            }
        )
        if (account.error.isNotEmpty()) {
            Text(
                account.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp).testTag("sign-in-error"),
            )
        }
    }
}

@Composable
private fun SettingsColumn(
    contentPadding: PaddingValues,
    tag: String,
    content: @Composable () -> Unit,
) {
    val scroll = rememberScrollState()
    TrackHeaderBackdrop(scroll)
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(scroll)
            .padding(contentPadding)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
    }
}
