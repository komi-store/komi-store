package zed.rainxch.core.domain.model.installation

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val percent: Int?,
)
