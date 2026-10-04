package zed.rainxch.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import zed.rainxch.core.data.data_source.TokenStore
import zed.rainxch.core.data.dto.AnnouncementsResponseDto
import zed.rainxch.core.data.dto.BackendExploreResponse
import zed.rainxch.core.data.dto.BackendFeedResponse
import zed.rainxch.core.data.dto.BackendRepoResponse
import zed.rainxch.core.data.dto.BackendSearchResponse
import zed.rainxch.core.data.dto.ExternalMatchRequest
import zed.rainxch.core.data.dto.ExternalMatchResponse
import zed.rainxch.core.data.dto.GithubReadmeResponseDto
import zed.rainxch.core.data.dto.GithubRepoNetworkModel
import zed.rainxch.core.data.dto.MirrorListResponse
import zed.rainxch.core.data.dto.ReleaseNetwork
import zed.rainxch.core.data.dto.SigningFingerprintSeedResponse
import zed.rainxch.core.data.dto.UserProfileNetwork
import zed.rainxch.core.domain.model.settings.ProxyConfig
import kotlin.coroutines.cancellation.CancellationException

class BackendApiClient(
    proxyConfigFlow: StateFlow<ProxyConfig>,
    private val tokenStore: TokenStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    @Volatile
    private var httpClient: HttpClient = buildClient(proxyConfigFlow.value)

    init {
        proxyConfigFlow
            .drop(1)
            .distinctUntilChanged()
            .onEach { config ->
                mutex.withLock {
                    httpClient.close()
                    httpClient = buildClient(config)
                }
            }.launchIn(scope)
    }

    private fun buildClient(proxyConfig: ProxyConfig): HttpClient =
        createPlatformHttpClient(proxyConfig).config {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 5_000
                connectTimeoutMillis = 3_000
                socketTimeoutMillis = 5_000
            }
            install(BackgroundRequestMarker)
            install(io.ktor.client.plugins.observer.ResponseObserver) {
                onResponse { response ->
                    BackendRateLimitTracker.record(response)
                }
            }
            defaultRequest {
                url(BASE_URL)
            }
            expectSuccess = false
        }

    fun close() {
        httpClient.close()
        scope.cancel()
    }

    suspend fun getCategory(category: String, platform: String): Result<List<BackendRepoResponse>> =
        safeCall {
            val response = httpClient.get("categories/$category/$platform")
            when {
                response.status.isSuccess() -> Result.success(response.body())
                response.status == HttpStatusCode.TooManyRequests -> Result.failure(buildRateLimited(response))
                else -> Result.failure(BackendException(response.status.value))
            }
        }

    suspend fun getTopic(bucket: String, platform: String): Result<List<BackendRepoResponse>> =
        safeCall {
            val response = httpClient.get("topics/$bucket/$platform")
            when {
                response.status.isSuccess() -> Result.success(response.body())
                response.status == HttpStatusCode.TooManyRequests -> Result.failure(buildRateLimited(response))
                else -> Result.failure(BackendException(response.status.value))
            }
        }

    suspend fun search(
        query: String,
        platform: String? = null,
        sort: String? = null,
        limit: Int = 20,
        offset: Int = 0,
    ): Result<BackendSearchResponse> =
        safeCall {
            val token = currentUserGithubToken()
            val response = httpClient.get("search") {
                parameter("q", query)
                if (platform != null) parameter("platform", platform)
                if (sort != null) parameter("sort", sort)
                parameter("limit", limit)
                parameter("offset", offset)
                if (token != null) header(X_GITHUB_TOKEN_HEADER, token)
            }
            when {
                response.status.isSuccess() -> Result.success(response.body())
                response.status == HttpStatusCode.TooManyRequests -> Result.failure(buildRateLimited(response))
                else -> Result.failure(BackendException(response.status.value))
            }
        }

    suspend fun getFeed(
        platform: String?,
        page: Int = 1,
        limit: Int = 20,
    ): Result<BackendFeedResponse> =
        safeCall {
            val response = httpClient.get("feed") {
                if (platform != null) parameter("platform", platform)
                parameter("page", page)
                parameter("limit", limit)
                timeout {
                    requestTimeoutMillis = 30_000
                    socketTimeoutMillis = 30_000
                }
            }
            when {
                response.status.isSuccess() -> Result.success(response.body())
                response.status == HttpStatusCode.TooManyRequests -> Result.failure(buildRateLimited(response))
                else -> Result.failure(BackendException(response.status.value))
            }
        }

    suspend fun searchExplore(
        query: String,
        platform: String? = null,
        page: Int = 1,
    ): Result<BackendExploreResponse> =
        safeCall {
            val token = currentUserGithubToken()
            val response = httpClient.get("search/explore") {
                parameter("q", query)
                if (platform != null) parameter("platform", platform)
                parameter("page", page)

                timeout {
                    requestTimeoutMillis = 30_000
                    socketTimeoutMillis = 30_000
                }

                if (token != null) header(X_GITHUB_TOKEN_HEADER, token)
            }
            when {
                response.status.isSuccess() -> Result.success(response.body())
                response.status == HttpStatusCode.TooManyRequests -> Result.failure(buildRateLimited(response))
                else -> Result.failure(BackendException(response.status.value))
            }
        }

    private suspend fun currentUserGithubToken(): String? =
        try {
            tokenStore.currentToken()?.accessToken?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    suspend fun getRepo(owner: String, name: String): Result<BackendRepoResponse> =
        safeCall {
            val token = currentUserGithubToken()
            val response = httpClient.get("repo/$owner/$name") {
                if (token != null) header(X_GITHUB_TOKEN_HEADER, token)
            }
            when {
                response.status.isSuccess() -> Result.success(response.body())
                response.status == HttpStatusCode.TooManyRequests -> Result.failure(buildRateLimited(response))
                else -> Result.failure(BackendException(response.status.value))
            }
        }

    suspend fun refreshRepo(owner: String, name: String): Result<BackendRepoResponse> =
        safeCall {
            val token = currentUserGithubToken()
            val response = httpClient.post("repo/$owner/$name/refresh") {
                if (token != null) header(X_GITHUB_TOKEN_HEADER, token)
                timeout {
                    requestTimeoutMillis = 15_000
                    socketTimeoutMillis = 15_000
                }
            }
            when (response.status.value) {
                in 200..299 -> Result.success(response.body())
                429 -> Result.failure(parseRefresh429(response))
                404 -> Result.failure(RepoNotFoundException())
                410 -> Result.failure(RepoArchivedException())
                else -> Result.failure(BackendException(response.status.value))
            }
        }

    suspend fun getReleases(
        owner: String,
        name: String,
        page: Int = 1,
        perPage: Int = 100,
    ): Result<List<ReleaseNetwork>> =
        safeCall {
            val token = currentUserGithubToken()
            val response = httpClient.get("releases/$owner/$name") {
                parameter("page", page)
                parameter("per_page", perPage)
                if (token != null) header(X_GITHUB_TOKEN_HEADER, token)
                timeout {
                    requestTimeoutMillis = 15_000
                    socketTimeoutMillis = 15_000
                }
            }
            when {
                response.status.isSuccess() -> Result.success(response.body())
                response.status == HttpStatusCode.TooManyRequests -> Result.failure(buildRateLimited(response))
                else -> Result.failure(BackendException(response.status.value))
            }
        }

    suspend fun getReadme(
        owner: String,
        name: String,
    ): Result<GithubReadmeResponseDto> =
        safeCall {
            val token = currentUserGithubToken()
            val response = httpClient.get("readme/$owner/$name") {
                if (token != null) header(X_GITHUB_TOKEN_HEADER, token)
                timeout {
                    requestTimeoutMillis = 15_000
                    socketTimeoutMillis = 15_000
                }
            }
            when {
                response.status.isSuccess() -> Result.success(response.body())
                response.status == HttpStatusCode.TooManyRequests -> Result.failure(buildRateLimited(response))
                else -> Result.failure(BackendException(response.status.value))
            }
        }

    suspend fun getUserRepos(
        username: String,
        page: Int = 1,
        perPage: Int = 30,
        sort: String? = "updated",
        type: String? = null,
    ): Result<List<GithubRepoNetworkModel>> =
        safeCall {
            val token = currentUserGithubToken()
            val response = httpClient.get("users/$username/repos") {
                parameter("page", page)
                parameter("per_page", perPage)
                if (sort != null) parameter("sort", sort)
                if (type != null) parameter("type", type)
                if (token != null) header(X_GITHUB_TOKEN_HEADER, token)
                timeout {
                    requestTimeoutMillis = 15_000
                    socketTimeoutMillis = 15_000
                }
            }
            when {
                response.status.isSuccess() -> Result.success(response.body())
                response.status == HttpStatusCode.TooManyRequests -> Result.failure(buildRateLimited(response))
                else -> Result.failure(BackendException(response.status.value))
            }
        }

    suspend fun getUser(username: String): Result<UserProfileNetwork> =
        safeCall {
            val token = currentUserGithubToken()
            val response = httpClient.get("user/$username") {
                if (token != null) header(X_GITHUB_TOKEN_HEADER, token)
                timeout {
                    requestTimeoutMillis = 15_000
                    socketTimeoutMillis = 15_000
                }
            }
            when {
                response.status.isSuccess() -> Result.success(response.body())
                response.status == HttpStatusCode.TooManyRequests -> Result.failure(buildRateLimited(response))
                else -> Result.failure(BackendException(response.status.value))
            }
        }

    suspend fun postExternalMatch(body: ExternalMatchRequest): Result<ExternalMatchResponse> =
        safeCall {
            val response = httpClient.post("external-match") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            when {
                response.status.isSuccess() ->
                    Result.success(response.body())
                response.status == HttpStatusCode.TooManyRequests ->
                    Result.failure(RateLimitedException())
                else ->
                    Result.failure(BackendException(response.status.value))
            }
        }

    suspend fun getSigningSeeds(
        since: Long? = null,
        cursor: String? = null,
        platform: String = "android",
    ): Result<SigningFingerprintSeedResponse> =
        safeCall {
            val response = httpClient.get("signing-seeds") {
                parameter("platform", platform)
                if (since != null) parameter("since", since)
                if (cursor != null) parameter("cursor", cursor)
            }
            when {
                response.status.isSuccess() ->
                    Result.success(response.body())
                response.status == HttpStatusCode.TooManyRequests ->
                    Result.failure(RateLimitedException())
                else ->
                    Result.failure(BackendException(response.status.value))
            }
        }

    suspend fun getMirrorList(): Result<MirrorListResponse> =
        safeCall {
            val response = httpClient.get("mirrors/list")
            when {
                response.status.isSuccess() -> Result.success(response.body())
                response.status == HttpStatusCode.TooManyRequests -> Result.failure(buildRateLimited(response))
                else -> Result.failure(BackendException(response.status.value))
            }
        }

    suspend fun getAnnouncements(): Result<AnnouncementsResponseDto> =
        safeCall {
            val response = httpClient.get("announcements")
            when {
                response.status.isSuccess() -> Result.success(response.body())
                response.status == HttpStatusCode.TooManyRequests -> Result.failure(buildRateLimited(response))
                else -> Result.failure(BackendException(response.status.value))
            }
        }

    private suspend fun buildRateLimited(response: io.ktor.client.statement.HttpResponse): RateLimitedException {
        BackendRateLimitTracker.record(response)
        val headerRetryAfter = response.headers["Retry-After"]?.toLongOrNull()
        val resetEpoch = response.headers["X-RateLimit-Reset"]?.toLongOrNull()
        val limit = response.headers["X-RateLimit-Limit"]?.toIntOrNull()
        val bodyRetryAfter = parseRateLimitBody(response)
        val effectiveRetryAfter = bodyRetryAfter ?: headerRetryAfter
        return RateLimitedException(
            retryAfterSeconds = effectiveRetryAfter,
            resetEpochSeconds = resetEpoch,
            limit = limit,
        )
    }

    private suspend fun parseRateLimitBody(response: io.ktor.client.statement.HttpResponse): Long? {
        val contentLength = response.contentLength() ?: 0L
        if (contentLength == 0L) return null
        return try {
            val text = response.bodyAsText()
            if (text.isBlank()) return null
            val element = Json.parseToJsonElement(text)
            val obj = element as? kotlinx.serialization.json.JsonObject ?: return null
            obj["retry_after"]?.let { node ->
                node as? kotlinx.serialization.json.JsonPrimitive
            }?.contentOrNull?.toLongOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun parseRefresh429(response: io.ktor.client.statement.HttpResponse): Exception {
        val headerRetryAfter = response.headers["Retry-After"]?.toLongOrNull()
        val (errorTag, bodyRetryAfter) = parseRefreshErrorBody(response)
        val retryAfter = bodyRetryAfter ?: headerRetryAfter ?: DEFAULT_REFRESH_COOLDOWN_SECONDS
        return when (errorTag) {
            "cooldown" -> RefreshCooldownException(retryAfter)
            "budget_exhausted" -> RefreshBudgetExhaustedException(retryAfter)
            else -> RateLimitedException(
                retryAfterSeconds = retryAfter,
                resetEpochSeconds = response.headers["X-RateLimit-Reset"]?.toLongOrNull(),
                limit = response.headers["X-RateLimit-Limit"]?.toIntOrNull(),
            )
        }
    }

    private suspend fun parseRefreshErrorBody(
        response: io.ktor.client.statement.HttpResponse,
    ): Pair<String?, Long?> =
        try {
            val text = response.bodyAsText()
            if (text.isBlank()) {
                null to null
            } else {
                val obj = Json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject
                if (obj == null) {
                    null to null
                } else {
                    val error = (obj["error"] as? kotlinx.serialization.json.JsonPrimitive)
                        ?.contentOrNull
                    val retryAfter = (obj["retry_after"] as? kotlinx.serialization.json.JsonPrimitive)
                        ?.contentOrNull
                        ?.toLongOrNull()
                    error to retryAfter
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null to null
        }

    private inline fun <T> safeCall(block: () -> Result<T>): Result<T> =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    companion object {
        private const val BASE_URL = BACKEND_BASE_URL
        private const val X_GITHUB_TOKEN_HEADER = "X-GitHub-Token"
        private const val DEFAULT_REFRESH_COOLDOWN_SECONDS = 30L
    }
}
