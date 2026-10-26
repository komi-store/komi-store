package zed.rainxch.core.data.network

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.plugins.*
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.serialization.json.Json
import zed.rainxch.core.data.datasource.TokenStore
import zed.rainxch.core.data.network.interceptor.HostTokenInterceptor
import zed.rainxch.core.data.network.interceptor.RateLimitInterceptor
import zed.rainxch.core.data.network.interceptor.UnauthorizedInterceptor
import zed.rainxch.core.domain.model.settings.ProxyConfig
import zed.rainxch.core.domain.repository.HostTokenRepository
import zed.rainxch.core.domain.repository.RateLimitRepository
import zed.rainxch.core.domain.repository.UserSessionRepository
import java.io.IOException

expect fun createPlatformHttpClient(proxyConfig: ProxyConfig): HttpClient

// Shared with parseStarredPage, so a page decodes the same way in both places.
internal val gitHubJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

fun createGitHubHttpClient(
    tokenStore: TokenStore,
    rateLimitRepository: RateLimitRepository,
    userSessionRepository: UserSessionRepository? = null,
    hostTokenRepository: HostTokenRepository? = null,
    proxyConfig: ProxyConfig = ProxyConfig.System,
): HttpClient =
    createPlatformHttpClient(proxyConfig)
        .config {
            install(RateLimitInterceptor) {
                this.rateLimitRepository = rateLimitRepository
            }

            if (userSessionRepository != null) {
                install(UnauthorizedInterceptor) {
                    this.userSessionRepository = userSessionRepository
                }
            }

            install(ContentNegotiation) {
                json(gitHubJson)
            }

            install(HttpTimeout) {
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            }

            installGitHubRetry()

            install(HttpRedirect) {
                checkHttpMethod = false
            }

            if (hostTokenRepository != null) {
                install(HostTokenInterceptor) {
                    repository = hostTokenRepository
                }
            }

            expectSuccess = false

            defaultRequest {
                url("https://api.github.com")
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
                header(HttpHeaders.UserAgent, "GithubStore/1.0 (KMP)")

                val token =
                    tokenStore
                        .blockingCurrentToken()
                        ?.accessToken
                        ?.trim()
                        .orEmpty()
                if (token.isNotEmpty()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }
        }.also { it.installMirrorRewrite() }

private const val CONNECT_TIMEOUT_MS = 30_000L
private const val MAX_RETRIES = 3
private val SERVER_ERROR_CODES = 500..<600

private fun HttpClientConfig<*>.installGitHubRetry() {
    install(HttpRequestRetry) {
        maxRetries = MAX_RETRIES
        retryIf { _, response ->
            if (response.status == HttpStatusCode.Forbidden) {
                val remaining = response.headers["X-RateLimit-Remaining"]?.toIntOrNull()
                if (remaining == 0) return@retryIf false
            }
            response.status.value in SERVER_ERROR_CODES
        }
        retryOnExceptionIf { _, cause ->
            cause is HttpRequestTimeoutException ||
                cause is UnresolvedAddressException ||
                cause is IOException
        }
        exponentialDelay()
    }
}

suspend inline fun <reified T> HttpClient.executeRequest(crossinline block: suspend HttpClient.() -> HttpResponse): Result<T> =
    recoverHttpFailure({ Result.failure(it) }) {
        val response = block()

        if (response.status.isSuccess()) {
            Result.success(response.body<T>())
        } else {
            Result.failure(
                Exception("HTTP ${response.status.value}: ${response.status.description}"),
            )
        }
    }
