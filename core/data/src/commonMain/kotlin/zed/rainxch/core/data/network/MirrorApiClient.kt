package zed.rainxch.core.data.network

import zed.rainxch.core.data.dto.MirrorListResponse

class MirrorApiClient(
    private val backendApiClient: BackendApiClient,
) {
    suspend fun fetchList(): Result<MirrorListResponse> = backendApiClient.getMirrorList()
}
