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
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.koma.data.ChapterUpdate
import app.koma.data.FoundChapters

private const val WORK = "library-update"
private const val CHANNEL = "library-update"
private const val UNMATCHED_CHANNEL = "library-unmatched"
private const val FOUND_CHANNEL = "library-chapters"
private const val NOTIFICATION = 1
private const val UNMATCHED_NOTIFICATION = 2
private const val FOUND_NOTIFICATION = 3

/**
 * Updates the library's chapters in a foreground service whose notification names each title, then
 * notifies the chapters found and the titles no source bound.
 */
class LibraryUpdateWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
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
        val report =
            (applicationContext as KomaApplication).container.updater.updateChapters(::show)
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
        /** Starts an update unless one runs. */
        fun start(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<LibraryUpdateWorker>().build(),
                )
        }
    }
}
