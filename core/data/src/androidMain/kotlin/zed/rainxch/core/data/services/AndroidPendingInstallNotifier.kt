package zed.rainxch.core.data.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import zed.rainxch.core.domain.system.PendingInstallNotifier

class AndroidPendingInstallNotifier(
    private val context: Context,
) : PendingInstallNotifier {
    @SuppressLint("MissingPermission")
    override fun notifyPending(
        packageName: String,
        repoOwner: String,
        repoName: String,
        appName: String,
        versionTag: String,
    ) {
        if (!hasNotificationPermission()) return

        val safeOwner = sanitizeForUri(repoOwner)
        val safeRepo = sanitizeForUri(repoName)
        val uri =
            if (safeOwner.isNotEmpty() && safeRepo.isNotEmpty()) {
                "githubstore://repo/$safeOwner/$safeRepo"
            } else {
                FALLBACK_URI
            }
        val deepLinkIntent =
            context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.apply {
                    action = Intent.ACTION_VIEW
                    data = uri.toUri()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                } ?: return
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                packageName.hashCode(),
                deepLinkIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat
                .Builder(context, UPDATES_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(appName)
                .setContentText(versionTag)
                .setSubText(SUBTEXT)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        NotificationManagerCompat
            .from(context)
            .notify(notificationIdFor(packageName), notification)
    }

    override fun clearPending(packageName: String) {
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

    private fun notificationIdFor(packageName: String): Int = NOTIFICATION_ID_BASE + (packageName.hashCode() and 0x00FFFFFF)

    private fun sanitizeForUri(input: String): String {
        if (input.isBlank()) return ""
        return input.filter { ch ->
            ch.isLetterOrDigit() || ch == '-' || ch == '.' || ch == '_'
        }
    }

    private companion object {
        const val UPDATES_CHANNEL_ID = "app_updates"
        const val FALLBACK_URI = "githubstore://apps"
        const val SUBTEXT = "Ready to install"

        const val NOTIFICATION_ID_BASE = 2000
    }
}
