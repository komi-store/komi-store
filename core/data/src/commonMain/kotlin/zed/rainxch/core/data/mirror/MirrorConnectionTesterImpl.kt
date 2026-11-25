package zed.rainxch.core.data.mirror

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.withTimeoutOrNull
import zed.rainxch.core.data.network.MirrorRewriter
import zed.rainxch.core.data.network.recoverHttpFailure
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.network.MirrorConnectionTester
import zed.rainxch.core.domain.network.MirrorTestOutcome
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class MirrorConnectionTesterImpl(
    private val client: HttpClient,
    private val logger: KomiStoreLogger,
) : MirrorConnectionTester {
    override suspend fun test(urlTemplate: String?): MirrorTestOutcome {
        val targetUrl = MirrorRewriter.probeUrl(urlTemplate)
        return withTimeoutOrNull(TEST_TIMEOUT) { probe(targetUrl) } ?: MirrorTestOutcome.Timeout
    }

    private suspend fun probe(url: String): MirrorTestOutcome =
        recoverHttpFailure({ e ->
            logger.error("MirrorConnectionTester: mirror test failed for $url", e)
            MirrorTestOutcome.Failed
        }) {
            try {
                val mark = TimeSource.Monotonic.markNow()
                val status = client.get(url).status
                val elapsedMs = mark.elapsedNow().inWholeMilliseconds
                if (status.isSuccess()) MirrorTestOutcome.Success(elapsedMs) else MirrorTestOutcome.HttpError(status.value)
            } catch (_: UnresolvedAddressException) {
                MirrorTestOutcome.DnsFailure
            }
        }

    private companion object {
        val TEST_TIMEOUT = 5.seconds
    }
}
