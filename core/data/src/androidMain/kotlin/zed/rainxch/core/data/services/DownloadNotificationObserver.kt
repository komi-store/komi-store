package zed.rainxch.core.data.services

import android.os.SystemClock
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import zed.rainxch.core.data.download.ForegroundPrimary
import zed.rainxch.core.domain.system.DownloadOrchestrator
import zed.rainxch.core.domain.system.DownloadProgressNotifier
import zed.rainxch.core.domain.system.DownloadStage
import zed.rainxch.core.domain.system.OrchestratedDownload

class DownloadNotificationObserver(
    private val orchestrator: DownloadOrchestrator,
    private val notifier: DownloadProgressNotifier,
    private val foreground: DownloadForegroundController,
) {
    @Volatile
    private var job: Job? = null

    private val lastStages = mutableMapOf<String, DownloadStage>()
    private val lastNotifiedAt = mutableMapOf<String, Long>()

    private val activeSince = mutableMapOf<String, Long>()

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
            activeSince.remove(pkg)
        }

        for ((pkg, entry) in snapshot) {
            val previous = lastStages[pkg]
            val stageChanged = previous != entry.stage
            when (entry.stage) {
                DownloadStage.Queued, DownloadStage.Downloading -> {
                    if (previous != DownloadStage.Queued && previous != DownloadStage.Downloading) {
                        activeSince[pkg] = SystemClock.uptimeMillis()
                    }
                    val now = SystemClock.uptimeMillis()
                    val last = lastNotifiedAt[pkg] ?: 0L
                    val shouldPost =
                        stageChanged ||
                            entry.progressPercent == 100 ||
                            (now - last) >= PROGRESS_UPDATE_INTERVAL_MS
                    if (shouldPost) {
                        try {
                            notifier.notifyProgress(
                                packageName = pkg,
                                appName = entry.displayAppName,
                                versionTag = entry.releaseTag.ifBlank { entry.assetName },
                                percent = entry.progressPercent,
                                bytesDownloaded = entry.bytesDownloaded,
                                totalBytes = entry.totalBytes,
                            )
                            lastNotifiedAt[pkg] = now
                        } catch (t: Throwable) {
                            Logger.w(t) { "DownloadNotificationObserver: notifyProgress failed for $pkg" }
                        }
                    }
                }

                DownloadStage.Installing,
                DownloadStage.AwaitingInstall,
                DownloadStage.Completed,
                DownloadStage.Cancelled,
                DownloadStage.Failed,
                -> {
                    if (previous == DownloadStage.Queued || previous == DownloadStage.Downloading) {
                        clearProgressSafely(pkg)
                        lastNotifiedAt.remove(pkg)
                    }
                    activeSince.remove(pkg)
                }
            }
            lastStages[pkg] = entry.stage
        }

        reconcileForeground(snapshot)
    }

    private fun reconcileForeground(snapshot: Map<String, OrchestratedDownload>) {
        val active =
            snapshot.filterValues {
                it.stage == DownloadStage.Queued || it.stage == DownloadStage.Downloading
            }
        if (active.isEmpty()) {
            foreground.stop()
            return
        }
        val primaryPackage = ForegroundPrimary.choose(active.keys, activeSince)
        foreground.ensureRunning(primary = primaryPackage?.let { active[it] })
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
