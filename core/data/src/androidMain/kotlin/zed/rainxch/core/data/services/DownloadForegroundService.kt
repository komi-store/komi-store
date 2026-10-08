package zed.rainxch.core.data.services

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import co.touchlab.kermit.Logger

class DownloadForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        Logger.w {
            "DownloadForegroundService: dataSync time limit reached, stopping the guard " +
                "(the download continues with its plain notification)"
        }
        stopSelf()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notificationId =
            intent.getIntExtra(EXTRA_NOTIFICATION_ID, DownloadNotificationFactory.FOREGROUND_NOTIFICATION_ID)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { appLabel() }
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val percent = intent.getIntExtra(EXTRA_PERCENT, NO_PERCENT).takeIf { it != NO_PERCENT }
        val cancelPackageName = intent.getStringExtra(EXTRA_CANCEL_PACKAGE)

        // A throw here kills the app: the platform launched us with startForegroundService and
        // treats an unanswered dataSync timeout as fatal. The notification build is guarded too,
        // since creating PendingIntents is what is likeliest to throw.
        val started =
            runCatching {
                val notification =
                    DownloadNotificationFactory.buildNotification(
                        context = this,
                        title = title,
                        text = text,
                        percent = percent,
                        cancelPackageName = cancelPackageName,
                    )
                try {
                    promote(notificationId, notification)
                } catch (refused: RuntimeException) {
                    // Retry with the type declared on the <service> element instead.
                    Logger.w(refused) {
                        "DownloadForegroundService: retrying with the manifest's service type"
                    }
                    promote(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST)
                }
            }
        if (started.isFailure) {
            Logger.w(started.exceptionOrNull()) {
                "DownloadForegroundService: startForeground failed, stopping the guard " +
                    "(the download continues with its plain notification)"
            }
            runCatching { stopSelf() }
        }

        return START_NOT_STICKY
    }

    private fun promote(
        notificationId: Int,
        notification: Notification,
        type: Int = FOREGROUND_SERVICE_TYPE,
    ) {
        ServiceCompat.startForeground(
            this,
            notificationId,
            notification,
            type,
        )
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun appLabel(): String =
        runCatching { applicationInfo.loadLabel(packageManager).toString() }
            .getOrNull()
            .orEmpty()
            .ifBlank { packageName }

    companion object {
        private const val NO_PERCENT = -1
        private const val EXTRA_NOTIFICATION_ID = "zed.rainxch.githubstore.extra.FGS_NOTIFICATION_ID"
        private const val EXTRA_TITLE = "zed.rainxch.githubstore.extra.FGS_TITLE"
        private const val EXTRA_TEXT = "zed.rainxch.githubstore.extra.FGS_TEXT"
        private const val EXTRA_PERCENT = "zed.rainxch.githubstore.extra.FGS_PERCENT"
        private const val EXTRA_CANCEL_PACKAGE = "zed.rainxch.githubstore.extra.FGS_CANCEL_PACKAGE"

        private val FOREGROUND_SERVICE_TYPE: Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }

        fun startIntent(
            context: Context,
            notificationId: Int,
            title: String,
            text: String,
            percent: Int?,
            cancelPackageName: String?,
        ): Intent =
            Intent(context, DownloadForegroundService::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_PERCENT, percent ?: NO_PERCENT)
                putExtra(EXTRA_CANCEL_PACKAGE, cancelPackageName)
            }
    }
}
