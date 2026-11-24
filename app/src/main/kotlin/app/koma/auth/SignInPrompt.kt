package app.koma.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.koma.R
import app.koma.ui.EmptyState

/** Asks to sign in to MangaBaka; [errorShown] also shows why the last sign-in failed. */
@Composable
fun SignInPrompt(
    view: SignInView,
    message: String,
    tag: String,
    buttonTag: String,
    errorShown: Boolean,
    contentPadding: PaddingValues,
    onSignIn: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(contentPadding).testTag(tag)) {
        EmptyState(
            icon = R.drawable.ic_person,
            title = "Sign in to MangaBaka",
            message = message,
            modifier = Modifier.weight(1f),
        ) {
            Button(
                onClick = onSignIn,
                enabled = !view.busy,
                modifier = Modifier.testTag(buttonTag),
            ) {
                Text("Sign In")
            }
        }
        if (errorShown && view.error.isNotEmpty()) {
            Text(
                view.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        .testTag("sign-in-error"),
            )
        }
    }
}
