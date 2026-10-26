package zed.rainxch.core.data.services

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

        // A throw here kills the app: after startForegroundService, an unanswered dataSync start is fatal.
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
                ServiceCompat.startForeground(this, notificationId, notification, FOREGROUND_SERVICE_TYPE)
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

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun appLabel(): String =
        runCatching { applicationInfo.loadLabel(packageManager).toString() }
            .getOrNull()
            .orEmpty()
            .ifBlank { packageName }

    data class Card(
        val notificationId: Int,
        val title: String,
        val text: String,
        val percent: Int?,
        val cancelPackageName: String?,
    )

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
            card: Card,
        ): Intent =
            Intent(context, DownloadForegroundService::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_ID, card.notificationId)
                putExtra(EXTRA_TITLE, card.title)
                putExtra(EXTRA_TEXT, card.text)
                putExtra(EXTRA_PERCENT, card.percent ?: NO_PERCENT)
                putExtra(EXTRA_CANCEL_PACKAGE, card.cancelPackageName)
            }
    }
}
