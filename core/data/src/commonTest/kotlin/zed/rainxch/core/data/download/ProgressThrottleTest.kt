package zed.rainxch.core.data.download

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import zed.rainxch.core.domain.model.installation.DownloadProgress

class ProgressThrottleTest {
    @Test
    fun emitsFirstProgressRegardlessOfClock() {
        val throttle = ProgressThrottle(minInterval = 10_000.milliseconds)

        assertTrue(throttle.shouldEmit(progress(percent = 0)))
    }

    @Test
    fun emitsWhenPercentChanges() {
        val throttle = ProgressThrottle(minInterval = 10_000.milliseconds)
        throttle.shouldEmit(progress(percent = 1))

        assertTrue(throttle.shouldEmit(progress(percent = 2)))
    }

    @Test
    fun suppressesSamePercentInsideInterval() {
        val clock = FakeTimeSource()
        val throttle = ProgressThrottle(minInterval = 100.milliseconds, timeSource = clock)
        throttle.shouldEmit(progress(percent = 5))

        clock.advance(50.milliseconds)

        assertFalse(throttle.shouldEmit(progress(percent = 5)))
    }

    @Test
    fun emitsSamePercentOnceIntervalElapsed() {
        val clock = FakeTimeSource()
        val throttle = ProgressThrottle(minInterval = 100.milliseconds, timeSource = clock)
        throttle.shouldEmit(progress(percent = 5))

        clock.advance(100.milliseconds)

        assertTrue(throttle.shouldEmit(progress(percent = 5)))
    }

    @Test
    fun emitsEveryEventWhenIntervalIsZero() {
        val throttle = ProgressThrottle(minInterval = 0.milliseconds)
        throttle.shouldEmit(progress(percent = 5))

        repeat(50) {
            assertTrue(throttle.shouldEmit(progress(percent = 5)))
        }
    }

    @Test
    fun treatsUnknownTotalAsSinglePercentValue() {
        val clock = FakeTimeSource()
        val throttle = ProgressThrottle(minInterval = 100.milliseconds, timeSource = clock)

        assertTrue(throttle.shouldEmit(progress(percent = null)))

        clock.advance(10.milliseconds)

        assertFalse(throttle.shouldEmit(progress(percent = null)))
    }

    private fun progress(percent: Int?): DownloadProgress =
        DownloadProgress(
            bytesDownloaded = (percent ?: 1) * 1_000L,
            totalBytes = 100_000L,
            percent = percent,
        )
}

private class FakeTimeSource : TimeSource {
    private var elapsed = Duration.ZERO

    override fun markNow(): TimeMark = FakeMark(elapsed)

    fun advance(by: Duration) {
        elapsed += by
    }

    private inner class FakeMark(
        private val value: Duration,
    ) : TimeMark {
        override fun elapsedNow(): Duration = elapsed - value
    }
}