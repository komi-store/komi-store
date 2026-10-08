package zed.rainxch.core.data.services

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Locale

object DownloadNotificationFactory {
    const val DOWNLOADS_CHANNEL_ID = "app_downloads"

    const val FOREGROUND_NOTIFICATION_ID = 1006

    private const val NOTIFICATION_ID_BASE = 3000
    private const val PAUSE_LABEL = "Pause"
    private const val DELETE_LABEL = "Delete"
    private const val PROGRESS_MAX = 100

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun notificationIdFor(packageName: String): Int =
        NOTIFICATION_ID_BASE + (packageName.hashCode() and 0x00FFFFFF)

    fun buildNotification(
        context: Context,
        title: String,
        text: String,
        percent: Int?,
        cancelPackageName: String?,
    ): Notification {
        val builder =
            NotificationCompat
                .Builder(context, DOWNLOADS_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(PROGRESS_MAX, percent ?: 0, percent == null)

        if (cancelPackageName != null) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    PAUSE_LABEL,
                    actionPendingIntent(
                        context = context,
                        packageName = cancelPackageName,
                        action = DownloadCancelReceiver.ACTION_CANCEL,
                        uriScheme = DownloadCancelReceiver.URI_SCHEME_PAUSE,
                    ),
                ).build(),
            )
            builder.addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_delete,
                    DELETE_LABEL,
                    actionPendingIntent(
                        context = context,
                        packageName = cancelPackageName,
                        action = DownloadCancelReceiver.ACTION_DISCARD,
                        uriScheme = DownloadCancelReceiver.URI_SCHEME_DISCARD,
                    ),
                ).build(),
            )
        }

        return builder.build()
    }

    fun formatProgressText(
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

    // Pause keeps the partial so it can be resumed, delete erases it. The two must differ in
    // both the action and the data URI: with FLAG_UPDATE_CURRENT a shared identity collapses
    // them into one PendingIntent and both buttons fire the same broadcast.
    private fun actionPendingIntent(
        context: Context,
        packageName: String,
        action: String,
        uriScheme: String,
    ): PendingIntent {
        val intent =
            Intent(context, DownloadCancelReceiver::class.java).apply {
                this.action = action
                data = Uri.parse("$uriScheme://$packageName")
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

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(Locale.ROOT, kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(Locale.ROOT, mb)
        val gb = mb / 1024.0
        return "%.2f GB".format(Locale.ROOT, gb)
    }
}
