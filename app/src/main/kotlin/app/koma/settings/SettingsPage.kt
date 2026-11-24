package app.koma.settings

/** A page opened from the Settings tab. */
enum class SettingsPage(val title: String, val route: String) {
    Theme("Theme", "theme"),
    MangaBaka("MangaBaka", "mangabaka"),
}

/** What the MangaBaka page shows of the session. */
data class Account(
    val signedIn: Boolean,
    /** Null until MangaBaka answered; empty when the person has none. */
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
