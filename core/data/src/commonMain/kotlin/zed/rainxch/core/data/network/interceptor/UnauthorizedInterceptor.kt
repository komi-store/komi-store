package zed.rainxch.core.data.network.interceptor

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.http.HttpHeaders
import io.ktor.util.AttributeKey
import zed.rainxch.core.domain.repository.UserSessionRepository

class UnauthorizedInterceptor(
    private val userSessionRepository: UserSessionRepository,
) {
    class Config {
        var userSessionRepository: UserSessionRepository? = null
    }

    companion object Plugin : HttpClientPlugin<Config, UnauthorizedInterceptor> {
        override val key: AttributeKey<UnauthorizedInterceptor> =
            AttributeKey("UnauthorizedInterceptor")

        override fun prepare(block: Config.() -> Unit): UnauthorizedInterceptor {
            val config = Config().apply(block)
            return UnauthorizedInterceptor(
                userSessionRepository =
                    requireNotNull(config.userSessionRepository) {
                        "AuthenticationState must be provided"
                    },
            )
        }

        override fun install(
            plugin: UnauthorizedInterceptor,
            scope: HttpClient,
        ) {
            scope.receivePipeline.intercept(HttpReceivePipeline.After) {
                val tokenKey = extractBearerToken(subject.call.request.headers[HttpHeaders.Authorization])
                if (subject.status.value == 401) {
                    plugin.userSessionRepository.notifySessionExpired(tokenKey)
                } else {
                    plugin.userSessionRepository.notifyRequestSucceeded(tokenKey)
                }
                proceedWith(subject)
            }
        }

        private fun extractBearerToken(headerValue: String?): String? {
            if (headerValue.isNullOrEmpty()) return null
            val trimmed = headerValue.trim()
            val withoutScheme =
                when {
                    trimmed.startsWith("Bearer ", ignoreCase = true) -> {
                        trimmed.substring("Bearer ".length)
                    }

                    trimmed.startsWith("token ", ignoreCase = true) -> {
                        trimmed.substring("token ".length)
                    }

                    else -> {
                        trimmed
                    }
                }
            return withoutScheme.trim().takeIf { it.isNotEmpty() }
        }
    }
}
