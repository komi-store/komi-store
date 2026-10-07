package zed.rainxch.core.data.mirror

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.TimeSource
import zed.rainxch.core.data.network.MirrorRewriter
import zed.rainxch.core.domain.model.mirror.MirrorConfig
import zed.rainxch.core.domain.network.MirrorLatencyProbe

class MirrorLatencyProbeImpl(
    private val client: HttpClient,
) : MirrorLatencyProbe {
    override suspend fun probe(mirrors: List<MirrorConfig>): Map<String, Int> {
        val targets =
            mirrors.mapNotNull { mirror ->
                mirror.urlTemplate?.let { template -> mirror.id to MirrorRewriter.probeUrl(template) }
            }
        if (targets.isEmpty()) return emptyMap()

        val gate = Semaphore(MAX_PARALLEL_PROBES)
        return coroutineScope {
            targets
                .map { (id, url) ->
                    async {
                        gate.withPermit { id to measure(url) }
                    }
                }.awaitAll()
                .mapNotNull { (id, latencyMs) -> latencyMs?.let { id to it } }
                .toMap()
        }
    }

    private suspend fun measure(url: String): Int? =
        try {
            val mark = TimeSource.Monotonic.markNow()
            val response = client.get(url)
            response.bodyAsText()
            val elapsedMs = mark.elapsedNow().inWholeMilliseconds
            if (response.status.value in 200..299) elapsedMs.toInt() else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }

    private companion object {
        private const val MAX_PARALLEL_PROBES = 6
    }
}