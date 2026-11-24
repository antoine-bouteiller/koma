package app.koma.auth

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun SignOutButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.testTag("sign-out")) { Text("Sign Out") }
}
