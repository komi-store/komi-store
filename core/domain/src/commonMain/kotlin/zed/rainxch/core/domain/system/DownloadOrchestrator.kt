package zed.rainxch.core.domain.system

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DownloadOrchestrator {

    val downloads: StateFlow<Map<String, OrchestratedDownload>>

    fun observe(packageName: String): Flow<OrchestratedDownload?>

    suspend fun enqueue(spec: DownloadSpec): String

    suspend fun downgradeToDeferred(packageName: String)

    suspend fun cancel(packageName: String)

    suspend fun resume(packageName: String)

    suspend fun retry(packageName: String)

    suspend fun discard(packageName: String)

    suspend fun installPending(packageName: String): InstallOutcome?

    fun dismiss(packageName: String)
}
