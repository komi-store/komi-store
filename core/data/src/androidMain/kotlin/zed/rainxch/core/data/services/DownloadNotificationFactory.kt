package zed.rainxch.core.data.services

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.util.Locale

object DownloadNotificationFactory {
    const val DOWNLOADS_CHANNEL_ID = "app_downloads"

    const val FOREGROUND_NOTIFICATION_ID = 1006

    internal const val PROGRESS_MAX = 100

    private const val NOTIFICATION_ID_BASE = 3000
    private const val NOTIFICATION_ID_MASK = 0x00FFFFFF
    private const val PAUSE_LABEL = "Pause"
    private const val DELETE_LABEL = "Delete task"
    private const val BYTES_PER_UNIT = 1024.0

    fun checkNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun notificationIdFor(packageName: String): Int = NOTIFICATION_ID_BASE + (packageName.hashCode() and NOTIFICATION_ID_MASK)

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
                NotificationCompat.Action
                    .Builder(
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
                NotificationCompat.Action
                    .Builder(
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

    // Pause and delete differ in action and data URI, or FLAG_UPDATE_CURRENT merges them into one PendingIntent.
    private fun actionPendingIntent(
        context: Context,
        packageName: String,
        action: String,
        uriScheme: String,
    ): PendingIntent {
        val intent =
            Intent(context, DownloadCancelReceiver::class.java).apply {
                this.action = action
                data = "$uriScheme://$packageName".toUri()
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
        val kb = bytes / BYTES_PER_UNIT
        val mb = kb / BYTES_PER_UNIT
        val gb = mb / BYTES_PER_UNIT
        return when {
            bytes < BYTES_PER_UNIT -> "$bytes B"
            kb < BYTES_PER_UNIT -> "%.1f KB".format(Locale.getDefault(), kb)
            mb < BYTES_PER_UNIT -> "%.1f MB".format(Locale.getDefault(), mb)
            else -> "%.2f GB".format(Locale.getDefault(), gb)
        }
    }
}
