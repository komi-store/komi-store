package zed.rainxch.core.data.network

import zed.rainxch.core.data.dto.ExternalMatchRequest
import zed.rainxch.core.data.dto.ExternalMatchResponse

class MockExternalMatchApi : ExternalMatchApi {
    override suspend fun match(request: ExternalMatchRequest): Result<ExternalMatchResponse> =
        Result.success(
            ExternalMatchResponse(
                matches =
                    request.candidates.map {
                        ExternalMatchResponse.MatchEntry(
                            packageName = it.packageName,
                            candidates = emptyList(),
                        )
                    },
            ),
        )
}
