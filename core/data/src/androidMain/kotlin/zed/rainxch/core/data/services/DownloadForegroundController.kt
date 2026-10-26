package zed.rainxch.core.data.services

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import zed.rainxch.core.domain.system.OrchestratedDownload

class DownloadForegroundController(
    private val context: Context,
) {
    @Volatile
    private var running = false

    @Volatile
    private var degraded = false

    @Volatile
    private var foregroundPackage: String? = null

    fun ensureRunning(active: List<OrchestratedDownload>) {
        if (degraded) return
        if (!DownloadNotificationFactory.checkNotificationPermission(context)) return
        // Moving the service to another id makes the platform cancel its card, so it stays on the current download until that ends.
        if (running && active.any { it.packageName == foregroundPackage }) return

        val primary = active.first()
        val card =
            DownloadForegroundService.Card(
                notificationId = DownloadNotificationFactory.notificationIdFor(primary.packageName),
                title = primary.displayAppName.ifBlank { primary.packageName },
                text =
                    DownloadNotificationFactory.formatProgressText(
                        versionTag = primary.releaseTag.ifBlank { primary.assetName },
                        bytesDownloaded = primary.bytesDownloaded,
                        totalBytes = primary.totalBytes,
                    ),
                percent = primary.progressPercent,
                cancelPackageName = primary.packageName,
            )
        val intent = DownloadForegroundService.startIntent(context, card)
        val result = runCatching { ContextCompat.startForegroundService(context, intent) }
        if (result.isSuccess) {
            running = true
            foregroundPackage = primary.packageName
        } else {
            Logger.w(result.exceptionOrNull()) {
                "DownloadForegroundController: FGS start refused for ${primary.packageName}, " +
                    "degrading to plain notification"
            }
            // A running service is left without a card once the observer cancels the previous one, so tear it down.
            if (running) stop()
            degraded = true
        }
    }

    fun stop() {
        val serviceWasAlive = running
        val lastForegroundPackage = foregroundPackage
        running = false
        degraded = false
        foregroundPackage = null
        if (!serviceWasAlive && lastForegroundPackage == null) return

        runCatching {
            context.stopService(Intent(context, DownloadForegroundService::class.java))
        }
        if (!serviceWasAlive && lastForegroundPackage != null) {
            runCatching {
                NotificationManagerCompat
                    .from(context)
                    .cancel(DownloadNotificationFactory.notificationIdFor(lastForegroundPackage))
            }
        }
    }
}
