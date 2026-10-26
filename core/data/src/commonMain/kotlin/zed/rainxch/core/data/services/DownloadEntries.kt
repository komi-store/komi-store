package zed.rainxch.core.data.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import zed.rainxch.core.domain.system.DownloadStage
import zed.rainxch.core.domain.system.OrchestratedDownload

class DownloadEntries {
    private val state = MutableStateFlow<Map<String, OrchestratedDownload>>(emptyMap())
    val downloads: StateFlow<Map<String, OrchestratedDownload>> = state.asStateFlow()

    // Also guards the orchestrator's job table, so a check and the write it decides stay atomic.
    val lock = Mutex()

    operator fun get(packageName: String): OrchestratedDownload? = state.value[packageName]

    fun mutate(transform: (Map<String, OrchestratedDownload>) -> Map<String, OrchestratedDownload>) = state.update(transform)

    suspend fun update(
        packageName: String,
        onlyId: String? = null,
        transform: (OrchestratedDownload) -> OrchestratedDownload,
    ) {
        lock.withLock {
            state.update { current ->
                val entry = current[packageName] ?: return@update current
                if (onlyId != null && entry.id != onlyId) return@update current
                current + (packageName to transform(entry))
            }
        }
    }

    suspend fun markFailed(
        packageName: String,
        message: String?,
        onlyId: String? = null,
    ) = update(packageName, onlyId) { it.copy(stage = DownloadStage.Failed, errorMessage = message) }
}
