package zed.rainxch.apps.presentation.library

import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.apps.domain.repository.AppsRepository
import zed.rainxch.apps.presentation.AppsAction
import zed.rainxch.apps.presentation.AppsEvent
import zed.rainxch.apps.presentation.AppsStateHolder
import zed.rainxch.apps.presentation.mappers.toUi
import zed.rainxch.core.domain.helpers.ShareManager
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.export_failed
import zed.rainxch.githubstore.core.presentation.res.import_failed
import java.io.IOException

// Export to native or Obtainium JSON, and import of either with its summary sheet.
class LibraryBackup(
    private val appsRepository: AppsRepository,
    private val shareManager: ShareManager,
    private val logger: KomiStoreLogger,
) {
    fun onAction(
        holder: AppsStateHolder,
        action: AppsAction.Backup,
    ) {
        when (action) {
            AppsAction.OnExportApps -> {
                export(holder, filePrefix = "github-store-apps", logLabel = "Export") { appsRepository.exportApps() }
            }

            AppsAction.OnExportObtainium -> {
                export(holder, filePrefix = "github-store-obtainium", logLabel = "Obtainium export") { appsRepository.exportObtainium() }
            }

            AppsAction.OnImportApps -> {
                importFromFile(holder)
            }

            AppsAction.OnDismissImportSummary -> {
                holder.update { it.copy(importSummary = null, expandedImportBuckets = persistentSetOf()) }
            }

            is AppsAction.OnToggleImportSummaryBucket -> {
                holder.update {
                    val buckets = it.expandedImportBuckets
                    val next = if (action.bucket in buckets) buckets - action.bucket else buckets + action.bucket
                    it.copy(expandedImportBuckets = next.toImmutableSet())
                }
            }
        }
    }

    private fun export(
        holder: AppsStateHolder,
        filePrefix: String,
        logLabel: String,
        produce: suspend () -> String,
    ) {
        holder.scope.launch {
            holder.update { it.copy(isExporting = true) }
            try {
                val json = produce()
                shareManager.shareFile("$filePrefix-${System.currentTimeMillis()}.json", json)
            } catch (e: LocalStoreException) {
                exportFailed(holder, logLabel, e)
            } catch (e: IOException) {
                exportFailed(holder, logLabel, e)
            } catch (e: IllegalArgumentException) {
                exportFailed(holder, logLabel, e)
            } finally {
                holder.update { it.copy(isExporting = false) }
            }
        }
    }

    private suspend fun exportFailed(
        holder: AppsStateHolder,
        logLabel: String,
        cause: Exception,
    ) {
        logger.error("$logLabel failed: ${cause.message}", cause)
        holder.send(AppsEvent.ShowError(getString(Res.string.export_failed, cause.message ?: "")))
    }

    private fun importFromFile(holder: AppsStateHolder) {
        shareManager.pickFile("application/json") { content ->
            if (content != null) {
                holder.scope.launch { importApps(holder, content) }
            }
        }
    }

    private suspend fun importApps(
        holder: AppsStateHolder,
        json: String,
    ) {
        holder.update { it.copy(isImporting = true) }
        try {
            val result = appsRepository.importApps(json)
            holder.update { it.copy(importSummary = result.toUi()) }
        } catch (e: LocalStoreException) {
            importFailed(holder, e)
        } catch (e: IllegalArgumentException) {
            importFailed(holder, e)
        } finally {
            holder.update { it.copy(isImporting = false) }
        }
    }

    private suspend fun importFailed(
        holder: AppsStateHolder,
        cause: Exception,
    ) {
        logger.error("Import failed: ${cause.message}", cause)
        holder.send(AppsEvent.ShowError(getString(Res.string.import_failed, cause.message ?: "")))
    }
}
