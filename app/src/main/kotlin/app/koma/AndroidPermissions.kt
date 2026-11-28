package app.koma

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri

class AndroidPermissions(private val context: Context) : Permissions {
    override fun notificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    override fun batteryOptimizationDisabled(): Boolean =
        context
            .getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    override fun enableNotifications() {
        open(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        )
    }

    // Play restricts this request; Koma ships outside Play.
    @SuppressLint("BatteryLife")
    override fun disableBatteryOptimization() {
        try {
            open(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData("package:${context.packageName}".toUri())
            )
        } catch (_: ActivityNotFoundException) {
            open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun open(intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
