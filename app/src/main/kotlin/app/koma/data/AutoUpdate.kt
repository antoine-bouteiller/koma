package app.koma.data

import app.koma.PreferenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/** How often the library updates on its own; [Off] leaves it to the user. */
enum class UpdateInterval(val hours: Long) {
    Off(0),
    Every6Hours(6),
    Every12Hours(12),
    Daily(24),
    Every2Days(48),
    Weekly(168),
}

/**
 * The automatic library update: its [interval], and what the device must offer before it runs. A
 * run due while a restriction fails waits until they all hold.
 */
data class AutoUpdate(
    val interval: UpdateInterval = UpdateInterval.Off,
    val wifiOnly: Boolean = true,
    val charging: Boolean = false,
    val batteryNotLow: Boolean = false,
)

private const val INTERVAL = "autoUpdate.interval"
private const val WIFI_ONLY = "autoUpdate.wifiOnly"
private const val CHARGING = "autoUpdate.charging"
private const val BATTERY_NOT_LOW = "autoUpdate.batteryNotLow"

/** Stores the automatic update's settings, and hands each change to [schedule]. */
class AutoUpdatePreferences(
    private val store: PreferenceStore,
    private val schedule: (AutoUpdate) -> Unit,
) {
    private val current = MutableStateFlow(read())

    val settings: StateFlow<AutoUpdate> = current.asStateFlow()

    fun update(change: (AutoUpdate) -> AutoUpdate) {
        val next = current.updateAndGet(change)
        store.set(INTERVAL, next.interval.name)
        store.set(WIFI_ONLY, next.wifiOnly.toString())
        store.set(CHARGING, next.charging.toString())
        store.set(BATTERY_NOT_LOW, next.batteryNotLow.toString())
        schedule(next)
    }

    private fun read(): AutoUpdate {
        val default = AutoUpdate()
        return AutoUpdate(
            interval =
                UpdateInterval.entries.find { it.name == store.get(INTERVAL) } ?: default.interval,
            wifiOnly = store.get(WIFI_ONLY) != "false",
            charging = store.get(CHARGING) == "true",
            batteryNotLow = store.get(BATTERY_NOT_LOW) == "true",
        )
    }
}
