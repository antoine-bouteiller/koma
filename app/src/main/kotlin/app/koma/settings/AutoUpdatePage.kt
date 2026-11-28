package app.koma.settings

import app.koma.data.AutoUpdate
import app.koma.data.UpdateInterval

/** What the Library page shows. The restrictions apply only while an interval is set. */
data class AutoUpdatePage(val settings: AutoUpdate, val restrictionsEnabled: Boolean)

fun autoUpdatePage(settings: AutoUpdate): AutoUpdatePage =
    AutoUpdatePage(settings, restrictionsEnabled = settings.interval != UpdateInterval.Off)

fun intervalLabel(interval: UpdateInterval): String =
    when (interval) {
        UpdateInterval.Off -> "Off"
        UpdateInterval.Every6Hours -> "Every 6 hours"
        UpdateInterval.Every12Hours -> "Every 12 hours"
        UpdateInterval.Daily -> "Daily"
        UpdateInterval.Every2Days -> "Every 2 days"
        UpdateInterval.Weekly -> "Weekly"
    }
