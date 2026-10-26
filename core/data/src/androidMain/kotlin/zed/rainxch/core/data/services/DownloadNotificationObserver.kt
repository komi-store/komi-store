package zed.rainxch.core.data.services

import android.os.SystemClock
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import zed.rainxch.core.data.download.DownloadNotificationAction
import zed.rainxch.core.data.download.notificationActionsFor
import zed.rainxch.core.domain.model.installation.DownloadProgress
import zed.rainxch.core.domain.system.DownloadOrchestrator
import zed.rainxch.core.domain.system.DownloadProgressNotifier
import zed.rainxch.core.domain.system.DownloadStage
import zed.rainxch.core.domain.system.OrchestratedDownload
import kotlin.coroutines.cancellation.CancellationException

class DownloadNotificationObserver(
    private val orchestrator: DownloadOrchestrator,
    private val notifier: DownloadProgressNotifier,
    private val foregroundController: DownloadForegroundController,
) {
    @Volatile
    private var job: Job? = null

    private val lastStages = mutableMapOf<String, DownloadStage>()
    private val lastNotifiedAt = mutableMapOf<String, Long>()

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job =
            scope.launch {
                try {
                    orchestrator.downloads.collect { snapshot -> reconcileSafely(snapshot) }
                } finally {
                    job = null
                }
            }
    }

    private fun reconcileSafely(snapshot: Map<String, OrchestratedDownload>) {
        try {
            reconcile(snapshot)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            logReconcileFailure(e)
        } catch (e: IllegalArgumentException) {
            logReconcileFailure(e)
        } catch (e: SecurityException) {
            logReconcileFailure(e)
        }
    }

    private fun logReconcileFailure(e: RuntimeException) {
        Logger.w(e) { "DownloadNotificationObserver: reconcile failed, continuing" }
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
                            entry.progressPercent == COMPLETE_PERCENT ||
                            (now - last) >= PROGRESS_UPDATE_INTERVAL_MS
                    if (shouldPost) {
                        postProgress(pkg, entry, paused = false)
                        lastNotifiedAt[pkg] = now
                    }
                }

                DownloadNotificationAction.RESUME in actions -> {
                    if (stageChanged) {
                        postProgress(pkg, entry, paused = true)
                        lastNotifiedAt[pkg] = SystemClock.uptimeMillis()
                    }
                }

                else -> {
                    if (previous != null && notificationActionsFor(previous).isNotEmpty()) {
                        clearProgressSafely(pkg)
                        lastNotifiedAt.remove(pkg)
                    }
                }
            }
            lastStages[pkg] = entry.stage
        }

        updateForeground(snapshot)
    }

    private fun updateForeground(snapshot: Map<String, OrchestratedDownload>) {
        val active =
            snapshot.values.filter {
                it.stage == DownloadStage.Queued || it.stage == DownloadStage.Downloading
            }
        if (active.isEmpty()) {
            foregroundController.stop()
        } else {
            foregroundController.ensureRunning(active)
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
                progress =
                    DownloadProgress(
                        bytesDownloaded = entry.bytesDownloaded,
                        totalBytes = entry.totalBytes,
                        percent = entry.progressPercent,
                    ),
                paused = paused,
            )
        } catch (e: SecurityException) {
            Logger.w(e) { "DownloadNotificationObserver: notifyProgress failed for $pkg" }
        } catch (e: IllegalArgumentException) {
            Logger.w(e) { "DownloadNotificationObserver: notifyProgress failed for $pkg" }
        }
    }

    private fun clearProgressSafely(pkg: String) {
        try {
            notifier.clearProgress(pkg)
        } catch (e: SecurityException) {
            Logger.w(e) { "DownloadNotificationObserver: clearProgress failed for $pkg" }
        }
    }

    private companion object {
        const val PROGRESS_UPDATE_INTERVAL_MS = 400L
        const val COMPLETE_PERCENT = 100
    }
}
