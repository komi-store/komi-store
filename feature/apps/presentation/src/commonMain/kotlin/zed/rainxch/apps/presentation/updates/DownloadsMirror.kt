package zed.rainxch.apps.presentation.updates

import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.apps.presentation.AppsStateHolder
import zed.rainxch.apps.presentation.hiddenRowPackages
import zed.rainxch.apps.presentation.mappers.toUi
import zed.rainxch.apps.presentation.mirroredUpdateState
import zed.rainxch.apps.presentation.model.UpdateState
import zed.rainxch.apps.presentation.transientDownloads
import zed.rainxch.apps.presentation.withDownloadProgress
import zed.rainxch.apps.presentation.withUpdateState
import zed.rainxch.core.domain.system.DownloadOrchestrator
import zed.rainxch.core.domain.system.DownloadStage
import zed.rainxch.core.domain.system.DownloadStagePhase
import zed.rainxch.core.domain.system.OrchestratedDownload
import zed.rainxch.core.domain.system.phase
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.download_failed

// Mirrors every orchestrator download, Details' included, onto the Library rows and the in-progress cards.
internal class DownloadsMirror(
    private val downloadOrchestrator: DownloadOrchestrator,
    private val isUpdating: (String) -> Boolean,
) {
    private val stages = mutableMapOf<String, DownloadStage>()
    private var snapshot: Map<String, OrchestratedDownload> = emptyMap()

    fun observe(holder: AppsStateHolder) {
        holder.scope.launch {
            downloadOrchestrator.downloads.collect { latest ->
                snapshot = latest
                mirror(holder, latest, getString(Res.string.download_failed))
            }
        }
    }

    suspend fun reapply(holder: AppsStateHolder) {
        stages.clear()
        mirror(holder, snapshot, getString(Res.string.download_failed))
    }

    private fun mirror(
        holder: AppsStateHolder,
        entries: Map<String, OrchestratedDownload>,
        failedFallback: String,
    ) {
        val knownPackages = holder.current.apps.mapTo(mutableSetOf()) { it.installedApp.packageName }

        (stages.keys - entries.keys).toList().forEach { packageName ->
            stages.remove(packageName)
            if (!isUpdating(packageName)) {
                holder.update { it.withUpdateState(packageName, UpdateState.Idle) }
            }
        }

        entries.forEach { (packageName, entry) ->
            if (packageName in knownPackages && !isUpdating(packageName)) {
                applyEntry(holder, packageName, entry, failedFallback)
            }
        }

        val transient = transientDownloads(entries, knownPackages).map { it.toUi() }.toImmutableList()
        val hidden = hiddenRowPackages(entries, knownPackages).toImmutableSet()
        holder.update { current ->
            if (current.inProgressDownloads == transient && current.hiddenRowPackages == hidden) {
                current
            } else {
                current.copy(inProgressDownloads = transient, hiddenRowPackages = hidden)
            }
        }
    }

    private fun applyEntry(
        holder: AppsStateHolder,
        packageName: String,
        entry: OrchestratedDownload,
        failedFallback: String,
    ) {
        val stageChanged = stages.put(packageName, entry.stage) != entry.stage
        if (stageChanged) {
            holder.update { it.withUpdateState(packageName, mirroredUpdateState(entry.stage, entry.errorMessage, failedFallback)) }
        }
        if (entry.stage.phase == DownloadStagePhase.Live) {
            holder.update { it.withDownloadProgress(packageName, entry.progressPercent) }
        }
    }
}
