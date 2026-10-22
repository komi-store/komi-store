package zed.rainxch.core.data.mirror

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import zed.rainxch.core.data.network.MirrorRewriter
import zed.rainxch.core.domain.model.mirror.MirrorConfig
import zed.rainxch.core.domain.network.MirrorLatencyProbe
import kotlin.time.TimeSource

class MirrorLatencyProbeImpl(
    private val client: HttpClient,
) : MirrorLatencyProbe {
    override suspend fun probe(mirrors: List<MirrorConfig>): Map<String, Int> {
        val targets = probeTargets(mirrors)
        if (targets.isEmpty()) return emptyMap()

        val gate = Semaphore(MAX_PARALLEL_PROBES)
        return coroutineScope {
            targets
                .map { (id, url) ->
                    async {
                        gate.withPermit { id to bestOfTwo(url) }
                    }
                }.awaitAll()
                .mapNotNull { (id, latencyMs) -> latencyMs?.let { id to it } }
                .toMap()
        }
    }

    private suspend fun bestOfTwo(url: String): Int? {
        val cold = measure(url, COLD_TIMEOUT_MS) ?: return null
        val warm = measure(url, WARM_TIMEOUT_MS) ?: return cold
        return minOf(cold, warm)
    }

    private suspend fun measure(
        url: String,
        timeoutMs: Long,
    ): Int? =
        try {
            val mark = TimeSource.Monotonic.markNow()
            val response =
                client.get(url) {
                    timeout {
                        requestTimeoutMillis = timeoutMs
                        connectTimeoutMillis = timeoutMs
                        socketTimeoutMillis = timeoutMs
                    }
                }
            response.bodyAsText()
            val elapsedMs = mark.elapsedNow().inWholeMilliseconds
            if (response.status.isSuccess()) elapsedMs.toInt() else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }

    private companion object {
        const val MAX_PARALLEL_PROBES = 6
        const val COLD_TIMEOUT_MS = 12_000L
        const val WARM_TIMEOUT_MS = 5_000L
    }
}

internal fun probeTargets(mirrors: List<MirrorConfig>): List<Pair<String, String>> =
    mirrors.mapNotNull { mirror ->
        MirrorRewriter.mirrorProbeUrl(mirror.urlTemplate)?.let { mirror.id to it }
    }
