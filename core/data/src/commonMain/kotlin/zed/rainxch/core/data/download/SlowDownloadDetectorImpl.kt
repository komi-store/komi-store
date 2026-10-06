package zed.rainxch.core.data.download

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import zed.rainxch.core.data.network.ProxyManager
import zed.rainxch.core.domain.model.installation.DownloadProgress
import zed.rainxch.core.domain.model.mirror.TrafficKind
import zed.rainxch.core.domain.network.SlowDownloadDetector
import zed.rainxch.core.data.secure.safeGet

class SlowDownloadDetectorImpl(
    private val ksafe: KSafe,
    private val appScope: CoroutineScope,
) : SlowDownloadDetector {
    private val windowMs = 10L * 60 * 1000
    private val sustainedMs = 30L * 1000
    private val thresholdBytesPerSec = 100L * 1024
    private val triggerCount = 3

    private val mutex = Mutex()
    private val samples: ArrayDeque<Pair<Long, Long>> = ArrayDeque()
    private val recentSlowEvents: ArrayDeque<Long> = ArrayDeque()
    private var lastEpisodeAtMs: Long? = null

    private val _suggestMirror = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val suggestMirror: Flow<Unit> = _suggestMirror.asSharedFlow()

    override suspend fun reset() {
        mutex.withLock {
            samples.clear()
            recentSlowEvents.clear()
            lastEpisodeAtMs = null
        }
    }

    override suspend fun onProgress(progress: DownloadProgress) {
        mutex.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            samples.addLast(now to progress.bytesDownloaded)
            while (samples.isNotEmpty() && samples.first().first < now - sustainedMs) {
                samples.removeFirst()
            }
            if (samples.size >= 2) {
                val first = samples.first()
                val last = samples.last()
                val elapsedSec = (last.first - first.first).coerceAtLeast(1L) / 1000.0
                val deltaBytes = (last.second - first.second).coerceAtLeast(0L)
                val bytesPerSec = (deltaBytes / elapsedSec).toLong()
                val windowFull = (last.first - first.first) >= sustainedMs - 500
                if (windowFull && bytesPerSec < thresholdBytesPerSec) {
                    recordSlowEvent(now)
                }
            }
        }
    }

    private suspend fun recordSlowEvent(timestampMs: Long) {
        // The rolling sample window stays full for as long as a slow download keeps
        // emitting progress, so onProgress reports the same slow stretch on every
        // single sample. Count at most one episode per sustainedMs window, otherwise
        // triggerCount is reached within milliseconds and the debounce does nothing.
        val previous = lastEpisodeAtMs
        if (previous != null && timestampMs - previous < sustainedMs) return
        lastEpisodeAtMs = timestampMs

        recentSlowEvents.addLast(timestampMs)
        while (recentSlowEvents.isNotEmpty() && recentSlowEvents.first() < timestampMs - windowMs) {
            recentSlowEvents.removeFirst()
        }
        if (recentSlowEvents.size < triggerCount) return

        val active = ProxyManager.currentMirror()
        if (active != null && TrafficKind.RELEASE_ASSET in active.trafficKinds) return

        val migrationDone = runCatching { ksafe.safeGet(MIRROR_MIGRATION_MARKER, false) }.getOrDefault(false)
        if (!migrationDone) return

        val dismissed = runCatching { ksafe.safeGet(K_SUGGEST_DISMISSED, false) }.getOrDefault(false)
        if (dismissed) return
        val snoozeUntil = runCatching { ksafe.safeGet(K_SUGGEST_SNOOZE, 0L) }.getOrDefault(0L)
        if (snoozeUntil > timestampMs) return

        recentSlowEvents.clear()
        _suggestMirror.tryEmit(Unit)
    }

    private companion object {
        const val K_SUGGEST_DISMISSED = "mirror_auto_suggest_dismissed"
        const val K_SUGGEST_SNOOZE = "mirror_auto_suggest_snooze_until"
        const val MIRROR_MIGRATION_MARKER = "__migrated_mirror_v1__"
    }
}
