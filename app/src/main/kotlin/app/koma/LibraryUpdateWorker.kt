package app.koma

import android.app.PendingIntent
import android.content.Context
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

private const val WORK = "library-update"
private const val CHANNEL = "library-update"
private const val NOTIFICATION = 1

/** Updates the library's chapters in a foreground service whose notification names each title. */
class LibraryUpdateWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        NotificationManagerCompat.from(applicationContext)
            .createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName("Library updates")
                    .build()
            )
        (applicationContext as KomaApplication).container.updater.updateChapters(::show)
        return Result.success()
    }

    private suspend fun show(update: ChapterUpdate) {
        val open =
            applicationContext.packageManager
                .getLaunchIntentForPackage(applicationContext.packageName)
                ?.let {
                    PendingIntent.getActivity(
                        applicationContext,
                        0,
                        it,
                        PendingIntent.FLAG_IMMUTABLE,
                    )
                }
        val notification =
            NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_refresh)
                .setContentTitle("Refreshing library (${update.position}/${update.total})")
                .setContentText(update.title)
                .setProgress(update.total, update.position - 1, false)
                .setContentIntent(open)
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
