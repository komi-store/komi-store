package zed.rainxch.core.data.network.interceptor

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.util.AttributeKey
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.error.RateLimitInfo
import zed.rainxch.core.domain.repository.RateLimitRepository

class RateLimitInterceptor(
    private val rateLimitRepository: RateLimitRepository,
) {
    class Config {
        var rateLimitRepository: RateLimitRepository? = null
    }

    companion object Plugin : HttpClientPlugin<Config, RateLimitInterceptor> {
        override val key: AttributeKey<RateLimitInterceptor> =
            AttributeKey("RateLimitInterceptor")

        override fun prepare(block: Config.() -> Unit): RateLimitInterceptor {
            val config = Config().apply(block)
            return RateLimitInterceptor(
                rateLimitRepository =
                    requireNotNull(config.rateLimitRepository) {
                        "RateLimitRepository must be provided"
                    },
            )
        }

        override fun install(
            plugin: RateLimitInterceptor,
            scope: HttpClient,
        ) {
            scope.receivePipeline.intercept(HttpReceivePipeline.State) {
                val response = subject

                parseRateLimitFromHeaders(response.headers)?.let { rateLimitInfo ->
                    val isErrorResponse =
                        response.status == HttpStatusCode.Forbidden || response.status == HttpStatusCode.TooManyRequests
                    plugin.rateLimitRepository.updateRateLimit(
                        rateLimitInfo = rateLimitInfo,
                        notifyExhausted = isErrorResponse && rateLimitInfo.isExhausted,
                    )

                    if (isErrorResponse && rateLimitInfo.isExhausted) {
                        throw RateLimitException(rateLimitInfo)
                    }
                }

                proceedWith(subject)
            }
        }

        private fun parseRateLimitFromHeaders(headers: Headers): RateLimitInfo? {
            val limit = headers.parsedOrNull("X-RateLimit-Limit", String::toIntOrNull) ?: return null
            val remaining = headers.parsedOrNull("X-RateLimit-Remaining", String::toIntOrNull) ?: return null
            val reset = headers.parsedOrNull("X-RateLimit-Reset", String::toLongOrNull) ?: return null
            val resource = headers["X-RateLimit-Resource"] ?: "core"
            return RateLimitInfo(limit, remaining, reset, resource)
        }

        private fun <T : Any> Headers.parsedOrNull(
            name: String,
            parse: (String) -> T?,
        ): T? {
            val raw = this[name]
            val value = raw?.let(parse)
            when {
                raw == null -> Logger.w { "Missing $name" }
                value == null -> Logger.w { "Malformed $name: $raw" }
            }
            return value
        }
    }
}
