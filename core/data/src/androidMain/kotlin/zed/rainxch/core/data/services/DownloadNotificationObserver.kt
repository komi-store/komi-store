package zed.rainxch.core.data.services

import android.os.SystemClock
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import zed.rainxch.core.data.download.DownloadNotificationAction
import zed.rainxch.core.data.download.notificationActionsFor
import zed.rainxch.core.domain.system.DownloadOrchestrator
import zed.rainxch.core.domain.system.DownloadProgressNotifier
import zed.rainxch.core.domain.system.DownloadStage
import zed.rainxch.core.domain.system.OrchestratedDownload

class DownloadNotificationObserver(
    private val orchestrator: DownloadOrchestrator,
    private val notifier: DownloadProgressNotifier,
) {
    @Volatile
    private var job: Job? = null

    private val lastStages = mutableMapOf<String, DownloadStage>()
    private val lastNotifiedAt = mutableMapOf<String, Long>()

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                orchestrator.downloads.collect { snapshot ->
                    try {
                        reconcile(snapshot)
                    } catch (t: Throwable) {

                        Logger.w(t) { "DownloadNotificationObserver: reconcile failed, continuing" }
                    }
                }
            } finally {

                job = null
            }
        }
    }

    private fun reconcile(snapshot: Map<String, OrchestratedDownload>) {

        val removed = lastStages.keys - snapshot.keys
        for (pkg in removed) {
            clearProgressSafely(pkg)
            lastStages.remove(pkg)
            lastNotifiedAt.remove(pkg)
        }

        for ((pkg, entry) in snapshot) {
            val previous = lastStages[pkg]
            val stageChanged = previous != entry.stage
            val actions = notificationActionsFor(entry.stage)
            when {
                DownloadNotificationAction.PAUSE in actions -> {
                    val now = SystemClock.uptimeMillis()
                    val last = lastNotifiedAt[pkg] ?: 0L
                    val shouldPost =
                        stageChanged ||
                            entry.progressPercent == 100 ||
                            (now - last) >= PROGRESS_UPDATE_INTERVAL_MS
                    if (shouldPost) {
                        postProgress(pkg, entry, paused = false)
                        lastNotifiedAt[pkg] = now
                    }
                }

                DownloadNotificationAction.RESUME in actions -> {
                    // The bar and the bytes are held across the pause, so one post at the transition
                    // is enough; the ongoing notification then sits with Resume/Delete until it moves.
                    if (stageChanged) {
                        postProgress(pkg, entry, paused = true)
                        lastNotifiedAt[pkg] = SystemClock.uptimeMillis()
                    }
                }

                else -> {
                    // Any stage that no longer carries a notification clears the one that was up;
                    // terminal-to-terminal transitions have nothing to take down.
                    if (previous != null && notificationActionsFor(previous).isNotEmpty()) {
                        clearProgressSafely(pkg)
                        lastNotifiedAt.remove(pkg)
                    }
                }
            }
            lastStages[pkg] = entry.stage
        }
    }

    private fun postProgress(
        pkg: String,
        entry: OrchestratedDownload,
        paused: Boolean,
    ) {
        try {
            notifier.notifyProgress(
                packageName = pkg,
                appName = entry.displayAppName,
                versionTag = entry.releaseTag.ifBlank { entry.assetName },
                percent = entry.progressPercent,
                bytesDownloaded = entry.bytesDownloaded,
                totalBytes = entry.totalBytes,
                paused = paused,
            )
        } catch (t: Throwable) {
            Logger.w(t) { "DownloadNotificationObserver: notifyProgress failed for $pkg" }
        }
    }

    private fun clearProgressSafely(pkg: String) {
        try {
            notifier.clearProgress(pkg)
        } catch (t: Throwable) {
            Logger.w(t) { "DownloadNotificationObserver: clearProgress failed for $pkg" }
        }
    }

    private companion object {

        const val PROGRESS_UPDATE_INTERVAL_MS = 400L
    }
}
