package zed.rainxch.core.data.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import zed.rainxch.core.data.dto.ExternalMatchRequest
import zed.rainxch.core.data.dto.ExternalMatchResponse
import zed.rainxch.core.domain.repository.TweaksRepository

class ExternalMatchApiSelector(
    private val real: BackendExternalMatchApi,
    private val mock: MockExternalMatchApi,
    tweaks: TweaksRepository,
    scope: CoroutineScope,
) : ExternalMatchApi {
    private val flagState =
        tweaks
            .getExternalMatchSearchEnabled()
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )

    override suspend fun match(request: ExternalMatchRequest): Result<ExternalMatchResponse> =
        if (flagState.value) {
            real.match(request)
        } else {
            mock.match(request)
        }
}
