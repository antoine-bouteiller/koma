package app.koma.auth

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.koma.R
import app.koma.ui.EmptyState
import app.koma.ui.PillButton

/** Asks to sign in to MangaBaka; [onSignIn] leads to where the sign-in happens. */
@Composable
fun SignInPrompt(
    message: String,
    tag: String,
    buttonTag: String,
    contentPadding: PaddingValues,
    onSignIn: () -> Unit,
) {
    EmptyState(
        icon = R.drawable.ic_person,
        title = "Sign in to MangaBaka",
        message = message,
        modifier = Modifier.padding(contentPadding).testTag(tag),
    ) {
        PillButton(
            label = "Sign In",
            onClick = onSignIn,
            icon = R.drawable.ic_login,
            modifier = Modifier.fillMaxWidth(0.5f).testTag(buttonTag),
        )
    }
}
