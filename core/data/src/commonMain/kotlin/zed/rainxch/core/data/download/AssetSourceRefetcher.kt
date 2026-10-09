package zed.rainxch.core.data.download

import zed.rainxch.core.domain.system.DownloadSpec

interface AssetSourceRefetcher {
    // The spec as the retry should attempt it, or null when nothing moved (the failure stands).
    suspend fun refetch(spec: DownloadSpec): DownloadSpec?
}
