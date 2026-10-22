package zed.rainxch.core.domain.network

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.installation.DownloadProgress

data class AssetIdentity(
    val assetId: Long = 0L,
    val digest: String? = null,
    val size: Long = -1L,
)

interface Downloader {
    fun download(
        url: String,
        suggestedFileName: String? = null,
        bypassMirror: Boolean = false,
        identity: AssetIdentity? = null,
    ): Flow<DownloadProgress>

    suspend fun saveToFile(
        url: String,
        suggestedFileName: String? = null,
    ): String

    suspend fun getDownloadedFilePath(fileName: String): String?

    suspend fun cancelDownload(fileName: String): Boolean

    suspend fun discardPartial(fileName: String): Boolean

    suspend fun reclaimOrphanedPartials(claimedNames: Set<String>): Int
}
