package zed.rainxch.githubstore.app.components

import zed.rainxch.core.domain.model.error.RateLimitInfo
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

data class RateLimitUi(
    val limit: Int,
    val resetEpochSeconds: Long,
) {
    // Read when the dialog shows, so a limit hit while the app was away counts down from now.
    fun minutesUntilReset(): Int =
        (Instant.fromEpochSeconds(resetEpochSeconds) - Clock.System.now())
            .coerceAtLeast(Duration.ZERO)
            .inWholeMinutes
            .toInt()
}

fun RateLimitInfo.toUi(): RateLimitUi = RateLimitUi(limit = limit, resetEpochSeconds = resetTimestamp)
