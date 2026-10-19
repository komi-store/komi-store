package zed.rainxch.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.serialization.json.Json
import zed.rainxch.core.data.dto.ForgejoRepoNetworkModel
import zed.rainxch.core.data.dto.ForgejoSearchResponse
import zed.rainxch.core.data.dto.GithubReadmeResponseDto
import zed.rainxch.core.data.dto.ReleaseNetwork
import zed.rainxch.core.data.network.interceptor.HostTokenInterceptor
import zed.rainxch.core.domain.model.settings.ProxyConfig
import zed.rainxch.core.domain.repository.HostTokenRepository
import java.io.IOException

class ForgejoApiClient(
    host: String,
    proxyConfig: ProxyConfig = ProxyConfig.System,
    hostTokenRepository: HostTokenRepository,
) {
    private val baseUrl: String = "https://$host/api/v1"

    private val client: HttpClient =
        createPlatformHttpClient(proxyConfig).config {
            install(ContentNegotiation) {
                json(JSON)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            install(HttpRequestRetry) {
                maxRetries = 3
                retryIf { _, response -> response.status.value in 500..<600 }
                retryOnExceptionIf { _, cause ->
                    cause is HttpRequestTimeoutException ||
                        cause is UnresolvedAddressException ||
                        cause is IOException
                }
                exponentialDelay()
            }
            install(HttpRedirect) { checkHttpMethod = false }
            install(HostTokenInterceptor) { repository = hostTokenRepository }
            expectSuccess = false
            defaultRequest {
                url(baseUrl)
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.UserAgent, "GithubStore/1.0 (KMP)")
            }
        }

    suspend fun getRepository(
        owner: String,
        repo: String,
    ): Result<ForgejoRepoNetworkModel> =
        client.executeRequest {
            get("$baseUrl/repos/$owner/$repo")
        }

    suspend fun getReleases(
        owner: String,
        repo: String,
        perPage: Int = 50,
        page: Int = 1,
    ): Result<List<ReleaseNetwork>> =
        client.executeRequest {
            get("$baseUrl/repos/$owner/$repo/releases") {
                parameter("limit", perPage)
                parameter("page", page)
            }
        }

    suspend fun getLatestRelease(
        owner: String,
        repo: String,
    ): Result<ReleaseNetwork> =
        client.executeRequest {
            get("$baseUrl/repos/$owner/$repo/releases/latest")
        }

    suspend fun searchRepositories(
        query: String,
        page: Int = 1,
        limit: Int = 30,
    ): Result<ForgejoSearchResponse> =
        client.executeRequest {
            get("$baseUrl/repos/search") {
                parameter("q", query)
                parameter("page", page)
                parameter("limit", limit)
            }
        }

    suspend fun getContentsFile(
        owner: String,
        repo: String,
        filepath: String,
        ref: String,
    ): Result<GithubReadmeResponseDto> =
        client.executeRequest {
            get("$baseUrl/repos/$owner/$repo/contents/$filepath") {
                parameter("ref", ref)
            }
        }

    suspend fun listContentsRoot(
        owner: String,
        repo: String,
        ref: String,
    ): Result<List<GithubReadmeResponseDto>> =
        client.executeRequest {
            get("$baseUrl/repos/$owner/$repo/contents") {
                parameter("ref", ref)
            }
        }

    fun close() {
        runCatching { client.close() }
    }

    companion object {
        private val JSON =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
    }
}
