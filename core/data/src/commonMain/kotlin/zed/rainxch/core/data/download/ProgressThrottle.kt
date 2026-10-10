package zed.rainxch.core.data.download

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import zed.rainxch.core.domain.model.installation.DownloadProgress

internal class ProgressThrottle(
    private val minInterval: Duration = DEFAULT_INTERVAL,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val startedAt = timeSource.markNow()
    private var lastEmittedAt: Duration? = null
    private var lastPercent: Int? = null
    private var seenAny = false

    fun shouldEmit(progress: DownloadProgress): Boolean {
        val now = startedAt.elapsedNow()
        val percentChanged = progress.percent != lastPercent
        val intervalElapsed = lastEmittedAt?.let { now - it >= minInterval } ?: true

        if (seenAny && !percentChanged && !intervalElapsed) return false

        seenAny = true
        lastEmittedAt = now
        lastPercent = progress.percent
        return true
    }

    private companion object {
        val DEFAULT_INTERVAL = 100.milliseconds
    }
}