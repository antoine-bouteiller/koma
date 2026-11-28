package app.koma.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.koma.AppContainer
import app.koma.R
import app.koma.ui.Chevron
import app.koma.ui.GroupRow
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
            SettingsPage.Account -> AccountSettings(account, onSignIn, onSignOut)
            SettingsPage.Permissions -> PermissionsView(container)
        }
    }
}

@Composable
private fun SettingsRoot(account: Account, onOpen: (SettingsPage) -> Unit) {
    SettingsSection("General", R.drawable.ic_settings) {
        SettingsGroup(
            listOf(
                GroupRow { shape ->
                    SettingsRow(
                        title = "Theme",
                        icon = R.drawable.ic_dark_mode,
                        shape = shape,
                        tag = "settings-theme",
                        subtitle = "Dark mode, true black and colors",
                        onClick = { onOpen(SettingsPage.Theme) },
                    )
                },
                GroupRow { shape ->
                    SettingsRow(
                        title = account.backend,
                        icon = R.drawable.ic_person,
                        shape = shape,
                        tag = "settings-account",
                        subtitle = accountSummary(account),
                        onClick = { onOpen(SettingsPage.Account) },
                    )
                },
                GroupRow { shape ->
                    SettingsRow(
                        title = "Permissions",
                        icon = R.drawable.ic_verified_user,
                        shape = shape,
                        tag = "settings-permissions",
                        subtitle = "Notifications and battery optimization",
                        onClick = { onOpen(SettingsPage.Permissions) },
                    )
                },
            )
        )
    }
}

@Composable
private fun AccountSettings(account: Account, onSignIn: () -> Unit, onSignOut: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        account.signedIn,
        transitionSpec = {
            fadeIn(motion.defaultEffectsSpec()) togetherWith
                fadeOut(motion.fastEffectsSpec()) using
                SizeTransform { _, _ -> motion.defaultSpatialSpec() }
        },
        label = "account",
    ) { signedIn ->
        if (signedIn) SignedIn(account, onSignOut) else SignedOut(account, onSignIn)
    }
}

@Composable
private fun SignedIn(account: Account, onSignOut: () -> Unit) {
    SettingsGroup(
        listOf(
            GroupRow { shape ->
                SettingsRow(
                    title = account.userName?.ifEmpty { null } ?: "${account.backend} account",
                    icon = R.drawable.ic_person,
                    shape = shape,
                    tag = "account-name",
                    subtitle = "Signed in to ${account.backend}",
                )
            },
            GroupRow { shape ->
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
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SignedOut(account: Account, onSignIn: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsGroup(
            listOf(
                GroupRow { shape ->
                    SettingsRow(
                        title = "Sign in",
                        icon = R.drawable.ic_login,
                        shape = shape,
                        tag = "account-sign-in",
                        subtitle = "Koma shows the titles you track and the chapters you read.",
                        onClick = onSignIn,
                        enabled = !account.busy,
                        trailing = {
                            AnimatedContent(
                                account.busy,
                                transitionSpec = {
                                    fadeIn(motion.fastEffectsSpec()) +
                                        scaleIn(motion.fastSpatialSpec()) togetherWith
                                        fadeOut(motion.fastEffectsSpec()) +
                                            scaleOut(motion.fastSpatialSpec())
                                },
                                contentAlignment = Alignment.Center,
                                label = "sign-in-busy",
                            ) { busy ->
                                if (busy) {
                                    LoadingIndicator(Modifier.size(32.dp).testTag("sign-in-busy"))
                                } else {
                                    Chevron()
                                }
                            }
                        },
                    )
                }
            )
        )
        AnimatedContent(
            account.error,
            transitionSpec = {
                fadeIn(motion.defaultEffectsSpec()) togetherWith
                    fadeOut(motion.fastEffectsSpec()) using
                    SizeTransform { _, _ -> motion.defaultSpatialSpec() }
            },
            label = "sign-in-error",
        ) { error ->
            if (error.isNotEmpty()) {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag("sign-in-error"),
                )
            }
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
