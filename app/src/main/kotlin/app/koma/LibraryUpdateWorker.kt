package app.koma

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.koma.data.AutoUpdate
import app.koma.data.ChapterUpdate
import app.koma.data.FoundChapters
import app.koma.data.UpdateInterval
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex

private const val WORK = "library-update"
private const val PERIODIC_WORK = "library-update-periodic"
private const val CHANNEL = "library-update"
private const val UNMATCHED_CHANNEL = "library-unmatched"
private const val FOUND_CHANNEL = "library-chapters"
private const val NOTIFICATION = 1
private const val UNMATCHED_NOTIFICATION = 2
private const val FOUND_NOTIFICATION = 3
private const val FLEX_MINUTES = 10L
/** Input of a run that syncs the library first, as a pull does before it starts one. */
private const val SYNC = "sync"

/**
 * Updates the library's chapters in a foreground service whose notification names each title, then
 * notifies the chapters found and the titles no source bound. A scheduled run syncs the library
 * first, as a pull does.
 */
class LibraryUpdateWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // The started and the scheduled update are distinct unique works, which may overlap.
        if (!running.tryLock()) return Result.success()
        return try {
            update()
        } finally {
            running.unlock()
        }
    }

    private suspend fun update(): Result {
        val notifications = NotificationManagerCompat.from(applicationContext)
        notifications.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Library updates")
                .build()
        )
        notifications.createNotificationChannel(
            NotificationChannelCompat.Builder(
                    UNMATCHED_CHANNEL,
                    NotificationManagerCompat.IMPORTANCE_DEFAULT,
                )
                .setName("Titles without a source")
                .build()
        )
        notifications.createNotificationChannel(
            NotificationChannelCompat.Builder(
                    FOUND_CHANNEL,
                    NotificationManagerCompat.IMPORTANCE_DEFAULT,
                )
                .setName("New chapters")
                .build()
        )
        val updater = (applicationContext as KomaApplication).container.updater
        val report =
            if (inputData.getBoolean(SYNC, false)) updater.refresh(::show)
            else updater.updateChapters(::show)
        if (report.found.isNotEmpty()) notifyFound(notifications, report.found)
        if (report.unmatched.isNotEmpty()) notifyUnmatched(notifications, report.unmatched)
        return Result.success()
    }

    private fun post(
        notifications: NotificationManagerCompat,
        id: Int,
        notification: Notification,
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notifications.notify(id, notification)
    }

    private fun open(): PendingIntent? =
        applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.let {
                PendingIntent.getActivity(applicationContext, 0, it, PendingIntent.FLAG_IMMUTABLE)
            }

    private fun notifyFound(notifications: NotificationManagerCompat, found: List<FoundChapters>) {
        val total = found.sumOf { it.chapters.size }
        val count = if (total == 1) "1 new chapter" else "$total new chapters"
        val lines = found.joinToString("\n") { "${it.title}: ${it.chapters.joinToString(", ")}" }
        val notification =
            NotificationCompat.Builder(applicationContext, FOUND_CHANNEL)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(count)
                .setContentText(found.joinToString(", ") { it.title })
                .setStyle(NotificationCompat.BigTextStyle().bigText(lines))
                .setContentIntent(open())
                .setAutoCancel(true)
                .build()
        post(notifications, FOUND_NOTIFICATION, notification)
    }

    private fun notifyUnmatched(notifications: NotificationManagerCompat, titles: List<String>) {
        val count = if (titles.size == 1) "1 title" else "${titles.size} titles"
        val notification =
            NotificationCompat.Builder(applicationContext, UNMATCHED_CHANNEL)
                .setSmallIcon(R.drawable.ic_error)
                .setContentTitle("No source found for $count")
                .setContentText(titles.joinToString(", "))
                .setStyle(NotificationCompat.BigTextStyle().bigText(titles.joinToString("\n")))
                .setContentIntent(open())
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .build()
        post(notifications, UNMATCHED_NOTIFICATION, notification)
    }

    private suspend fun show(update: ChapterUpdate) {
        val notification =
            NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_refresh)
                .setContentTitle("Refreshing library (${update.position}/${update.total})")
                .setContentText(update.title)
                .setProgress(update.total, update.position - 1, false)
                .setContentIntent(open())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .build()
        val info =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(
                    NOTIFICATION,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                ForegroundInfo(NOTIFICATION, notification)
            }
        try {
            setForeground(info)
        } catch (_: IllegalStateException) {
            // Android refuses a foreground service started from the background; the update runs on
            // without its notification.
        }
    }

    companion object {
        private val running = Mutex()

        /** Starts an update unless one runs. */
        fun start(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<LibraryUpdateWorker>().build(),
                )
        }

        /**
         * Runs an update every [AutoUpdate.interval], once the device meets its restrictions, or
         * stops when the interval is off. Changing only the restrictions keeps the next run's time.
         */
        fun schedule(context: Context, settings: AutoUpdate) {
            val work = WorkManager.getInstance(context)
            if (settings.interval == UpdateInterval.Off) {
                work.cancelUniqueWork(PERIODIC_WORK)
                return
            }
            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                    )
                    .setRequiresCharging(settings.charging)
                    .setRequiresBatteryNotLow(settings.batteryNotLow)
                    .build()
            // The flex window puts the first run an interval after turning it on, not right away.
            val request =
                PeriodicWorkRequestBuilder<LibraryUpdateWorker>(
                        settings.interval.hours,
                        TimeUnit.HOURS,
                        FLEX_MINUTES,
                        TimeUnit.MINUTES,
                    )
                    .setConstraints(constraints)
                    .setInputData(workDataOf(SYNC to true))
                    .build()
            work.enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
