package app.koma.settings

/** A page opened from the Settings tab. */
enum class SettingsPage(val route: String) {
    Theme("theme"),
    Account("account"),
    Permissions("permissions"),
}

/** The page's title; the account page is named after [backend]. */
fun SettingsPage.title(backend: String): String =
    when (this) {
        SettingsPage.Theme -> "Theme"
        SettingsPage.Account -> backend
        SettingsPage.Permissions -> "Permissions"
    }

/** What the account page shows of the session with [backend]. */
data class Account(
    val backend: String,
    val signedIn: Boolean,
    /** Null until the backend answered; empty when the person has none. */
    val userName: String?,
    val busy: Boolean,
    val error: String,
)

fun accountSummary(account: Account): String =
    when {
        !account.signedIn -> "Not signed in"
        account.userName.isNullOrEmpty() -> "Signed in"
        else -> "Signed in as ${account.userName}"
    }
