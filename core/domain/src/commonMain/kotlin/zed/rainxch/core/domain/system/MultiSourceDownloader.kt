package zed.rainxch.core.domain.system

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.installation.DownloadProgress
import zed.rainxch.core.domain.network.AssetIdentity

interface MultiSourceDownloader {
    fun download(
        githubUrl: String,
        suggestedFileName: String? = null,
        identity: AssetIdentity? = null,
    ): Flow<DownloadProgress>
}
