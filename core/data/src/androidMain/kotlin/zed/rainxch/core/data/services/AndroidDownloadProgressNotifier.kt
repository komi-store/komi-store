package zed.rainxch.core.data.services

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import zed.rainxch.core.domain.model.installation.DownloadProgress
import zed.rainxch.core.domain.system.DownloadProgressNotifier

class AndroidDownloadProgressNotifier(
    private val context: Context,
    private val pauseLabel: String = "Pause",
    private val resumeLabel: String = "Resume",
    private val deleteLabel: String = "Delete task",
) : DownloadProgressNotifier {
    override fun notifyProgress(
        packageName: String,
        appName: String,
        versionTag: String,
        progress: DownloadProgress,
        paused: Boolean,
    ) {
        if (!DownloadNotificationFactory.checkNotificationPermission(context)) return
        val (bytesDownloaded, totalBytes, percent) = progress

        val progressText =
            DownloadNotificationFactory.formatProgressText(versionTag, bytesDownloaded, totalBytes)
        val indeterminate = percent == null

        val notification =
            NotificationCompat
                .Builder(context, DownloadNotificationFactory.DOWNLOADS_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(appName)
                .setContentText(progressText)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(!paused)
                .setOnlyAlertOnce(true)
                .setProgress(DownloadNotificationFactory.PROGRESS_MAX, percent ?: 0, indeterminate)
                .addAction(primaryAction(packageName, paused))
                .addAction(
                    NotificationCompat.Action
                        .Builder(
                            android.R.drawable.ic_menu_delete,
                            deleteLabel,
                            broadcastIntent(
                                action = DownloadCancelReceiver.ACTION_DISCARD,
                                scheme = DownloadCancelReceiver.URI_SCHEME_DISCARD,
                                packageName = packageName,
                            ),
                        ).build(),
                ).build()

        // A paused card has its own tag: the foreground service owns the untagged card and removes it when it stops.
        val id = DownloadNotificationFactory.notificationIdFor(packageName)
        val manager = NotificationManagerCompat.from(context)
        if (paused) {
            manager.cancel(id)
            manager.notify(PAUSED_TAG, id, notification)
        } else {
            manager.cancel(PAUSED_TAG, id)
            manager.notify(id, notification)
        }
    }

    private fun primaryAction(
        packageName: String,
        paused: Boolean,
    ): NotificationCompat.Action {
        val pendingIntent =
            if (paused) {
                broadcastIntent(
                    action = DownloadCancelReceiver.ACTION_RESUME,
                    scheme = DownloadCancelReceiver.URI_SCHEME_RESUME,
                    packageName = packageName,
                )
            } else {
                broadcastIntent(
                    action = DownloadCancelReceiver.ACTION_CANCEL,
                    scheme = DownloadCancelReceiver.URI_SCHEME_PAUSE,
                    packageName = packageName,
                )
            }
        val label = if (paused) resumeLabel else pauseLabel
        val icon = if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        return NotificationCompat.Action.Builder(icon, label, pendingIntent).build()
    }

    private fun broadcastIntent(
        action: String,
        scheme: String,
        packageName: String,
    ): PendingIntent {
        val intent =
            Intent(context, DownloadCancelReceiver::class.java).apply {
                this.action = action
                data = "$scheme://$packageName".toUri()
                setPackage(context.packageName)
                putExtra(DownloadCancelReceiver.EXTRA_PACKAGE_NAME, packageName)
            }
        return PendingIntent.getBroadcast(
            context,
            packageName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun clearProgress(packageName: String) {
        val id = DownloadNotificationFactory.notificationIdFor(packageName)
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(id)
        manager.cancel(PAUSED_TAG, id)
    }

    private companion object {
        const val PAUSED_TAG = "paused"
    }
}
