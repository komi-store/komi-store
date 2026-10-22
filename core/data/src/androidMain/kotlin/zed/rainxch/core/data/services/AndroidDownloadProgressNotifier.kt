package zed.rainxch.core.data.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import zed.rainxch.core.domain.system.DownloadProgressNotifier

class AndroidDownloadProgressNotifier(
    private val context: Context,
    private val pauseLabel: String = "Pause",
    private val resumeLabel: String = "Resume",
    private val deleteLabel: String = "Delete task",
) : DownloadProgressNotifier {
    @SuppressLint("MissingPermission")
    override fun notifyProgress(
        packageName: String,
        appName: String,
        versionTag: String,
        percent: Int?,
        bytesDownloaded: Long,
        totalBytes: Long?,
        paused: Boolean,
    ) {
        if (!hasNotificationPermission()) return

        val primaryPendingIntent =
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
        val primaryLabel = if (paused) resumeLabel else pauseLabel
        val primaryIcon =
            if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause

        val discardPendingIntent =
            broadcastIntent(
                action = DownloadCancelReceiver.ACTION_DISCARD,
                scheme = DownloadCancelReceiver.URI_SCHEME_DISCARD,
                packageName = packageName,
            )

        val progressText =
            DownloadNotificationFactory.formatProgressText(versionTag, bytesDownloaded, totalBytes)
        val indeterminate = percent == null

        val builder =
            NotificationCompat
                .Builder(context, DownloadNotificationFactory.DOWNLOADS_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(appName)
                .setContentText(progressText)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(!paused)
                .setOnlyAlertOnce(true)
                .setProgress(100, percent ?: 0, indeterminate)
                .addAction(
                    NotificationCompat.Action
                        .Builder(
                            primaryIcon,
                            primaryLabel,
                            primaryPendingIntent,
                        ).build(),
                ).addAction(
                    NotificationCompat.Action
                        .Builder(
                            android.R.drawable.ic_menu_delete,
                            deleteLabel,
                            discardPendingIntent,
                        ).build(),
                )

        post(packageName, paused, builder.build())
    }

    // DownloadForegroundService holds the untagged card under this id, and stopping or moving
    // the service removes it, so a paused card lives under its own tag.
    @SuppressLint("MissingPermission")
    private fun post(
        packageName: String,
        paused: Boolean,
        notification: Notification,
    ) {
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

    private fun broadcastIntent(
        action: String,
        scheme: String,
        packageName: String,
    ): PendingIntent {
        val intent =
            Intent(context, DownloadCancelReceiver::class.java).apply {
                this.action = action
                data = Uri.parse("$scheme://$packageName")
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

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val PAUSED_TAG = "paused"
    }
}
