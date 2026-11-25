package zed.rainxch.apps.presentation.updates

import zed.rainxch.core.domain.system.OrchestratedDownload

sealed interface UpdateDownloadResult {
    data class Ready(
        val filePath: String,
        val download: OrchestratedDownload,
    ) : UpdateDownloadResult

    data object AlreadyInstalled : UpdateDownloadResult

    data object Cancelled : UpdateDownloadResult

    data class Failed(
        val message: String?,
    ) : UpdateDownloadResult
}
