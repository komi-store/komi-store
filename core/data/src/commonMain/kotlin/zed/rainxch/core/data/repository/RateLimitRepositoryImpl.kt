package zed.rainxch.core.data.repository

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import zed.rainxch.core.domain.model.error.RateLimitInfo
import zed.rainxch.core.domain.repository.RateLimitRepository
import kotlin.time.Clock

class RateLimitRepositoryImpl : RateLimitRepository {
    private val _rateLimitState = MutableStateFlow<RateLimitInfo?>(null)
    override val rateLimitState: StateFlow<RateLimitInfo?> = _rateLimitState.asStateFlow()

    private val _rateLimitExhaustedEvent = MutableSharedFlow<RateLimitInfo>(extraBufferCapacity = 1)
    override val rateLimitExhaustedEvent: SharedFlow<RateLimitInfo> = _rateLimitExhaustedEvent

    override fun updateRateLimit(
        rateLimitInfo: RateLimitInfo?,
        notifyExhausted: Boolean,
    ) {
        _rateLimitState.value = rateLimitInfo
        if (notifyExhausted && rateLimitInfo != null) {
            _rateLimitExhaustedEvent.tryEmit(rateLimitInfo)
        }
    }

    override fun getCurrentRateLimit(): RateLimitInfo? = _rateLimitState.value

    override fun isCurrentlyLimited(): Boolean {
        val info = getCurrentRateLimit() ?: return false
        return info.isCurrentlyLimited()
    }

    override fun clear() {
        _rateLimitState.value = null
    }
}
