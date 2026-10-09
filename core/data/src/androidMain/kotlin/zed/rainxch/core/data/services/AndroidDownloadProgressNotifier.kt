package zed.rainxch.core.data.services

import android.Manifest
import android.annotation.SuppressLint
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
    private val deleteLabel: String = "Delete",
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

        // Pause and resume are the same button in two states, so only one of them is attached. The
        // action and the data URI must both differ: with FLAG_UPDATE_CURRENT a shared identity
        // collapses the PendingIntents into one and every button would do the same thing. Pause
        // keeps the partial, resume continues it, delete erases it.
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

        val progressText = formatProgressText(versionTag, bytesDownloaded, totalBytes)
        val indeterminate = percent == null

        val builder =
            NotificationCompat
                .Builder(context, DOWNLOADS_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(appName)
                .setContentText(progressText)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, percent ?: 0, indeterminate)
                .addAction(
                    NotificationCompat.Action.Builder(
                        primaryIcon,
                        primaryLabel,
                        primaryPendingIntent,
                    ).build(),
                )
                .addAction(
                    NotificationCompat.Action.Builder(
                        android.R.drawable.ic_menu_delete,
                        deleteLabel,
                        discardPendingIntent,
                    ).build(),
                )

        NotificationManagerCompat
            .from(context)
            .notify(notificationIdFor(packageName), builder.build())
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
        NotificationManagerCompat
            .from(context)
            .cancel(notificationIdFor(packageName))
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun notificationIdFor(packageName: String): Int =
        NOTIFICATION_ID_BASE + (packageName.hashCode() and 0x00FFFFFF)

    private fun formatProgressText(
        versionTag: String,
        bytesDownloaded: Long,
        totalBytes: Long?,
    ): String {
        val downloaded = formatBytes(bytesDownloaded)
        val total = totalBytes?.let { formatBytes(it) }
        return if (total != null) {
            "$versionTag · $downloaded / $total"
        } else {
            "$versionTag · $downloaded"
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        val gb = mb / 1024.0
        return "%.2f GB".format(gb)
    }

    private companion object {
        const val DOWNLOADS_CHANNEL_ID = "app_downloads"

        const val NOTIFICATION_ID_BASE = 3000
    }
}
