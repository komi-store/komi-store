package zed.rainxch.auth.data.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import zed.rainxch.auth.domain.repository.RejectedKind
import zed.rainxch.core.data.dto.GithubDeviceStartDto
import zed.rainxch.core.data.dto.GithubDeviceTokenErrorDto
import zed.rainxch.core.data.dto.GithubDeviceTokenSuccessDto
import zed.rainxch.core.data.network.createPlatformHttpClient
import zed.rainxch.core.data.network.networkFailures
import zed.rainxch.core.domain.model.error.NetworkException
import zed.rainxch.core.domain.model.settings.ProxyConfig

private const val REQUEST_TIMEOUT_MS = 60_000L
private const val CONNECT_TIMEOUT_MS = 30_000L
private const val SOCKET_TIMEOUT_MS = 30_000L
private const val VALIDATION_TIMEOUT_MS = 10_000L
private const val BACKEND_START_TIMEOUT_MS = 10_000L
private const val ERROR_BODY_PREVIEW_CHARS = 300
private val HTTP_CLIENT_ERRORS = 400..499

class GitHubAuthApi(
    proxyConfigFlow: StateFlow<ProxyConfig>,
    appScope: CoroutineScope,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    @Volatile
    private var currentHttp: HttpClient = buildHttp(proxyConfigFlow.value)

    @Volatile
    private var currentValidationHttp: HttpClient = buildValidationHttp(proxyConfigFlow.value)

    init {
        proxyConfigFlow
            .drop(1)
            .distinctUntilChanged()
            .onEach { config ->
                val previousHttp = currentHttp
                val previousValidationHttp = currentValidationHttp
                currentHttp = buildHttp(config)
                currentValidationHttp = buildValidationHttp(config)
                previousHttp.close()
                previousValidationHttp.close()
            }.launchIn(appScope)
    }

    val http: HttpClient get() = currentHttp

    private val validationHttp: HttpClient get() = currentValidationHttp

    private fun buildHttp(proxyConfig: ProxyConfig): HttpClient =
        createPlatformHttpClient(proxyConfig).config {
            install(ContentNegotiation) { json(json) }

            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                socketTimeoutMillis = SOCKET_TIMEOUT_MS
            }

            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 2)
                retryOnException(maxRetries = 2, retryOnTimeout = true)
                exponentialDelay()
            }
        }

    private fun buildValidationHttp(proxyConfig: ProxyConfig): HttpClient =
        createPlatformHttpClient(proxyConfig).config {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                requestTimeoutMillis = VALIDATION_TIMEOUT_MS
                connectTimeoutMillis = VALIDATION_TIMEOUT_MS
                socketTimeoutMillis = VALIDATION_TIMEOUT_MS
            }
        }

    suspend fun startDeviceFlowDirect(clientId: String): GithubDeviceStartDto =
        withRetry(maxAttempts = 3, initialDelay = 1000) {
            val reply =
                http.fetchReply {
                    post("https://github.com/login/device/code") {
                        accept(ContentType.Application.Json)
                        headers.append(HttpHeaders.UserAgent, "GithubStore/1.0 (DeviceFlow)")
                        contentType(ContentType.Application.FormUrlEncoded)
                        setBody(
                            FormDataContent(
                                Parameters.build {
                                    append("client_id", clientId)
                                    append("scope", "repo")
                                },
                            ),
                        )
                    }
                }

            if (!reply.status.isSuccessLike()) {
                throw NetworkException(
                    "GitHub device/code HTTP ${reply.status.value} ${reply.status.description}. " +
                        "Body: ${reply.body.take(ERROR_BODY_PREVIEW_CHARS)}",
                    null,
                )
            }

            json.decodeOrNull(GithubDeviceStartDto.serializer(), reply.body)
                ?: throw NetworkException("Unexpected response from GitHub: ${reply.body}", null)
        }

    suspend fun pollDeviceTokenDirect(
        clientId: String,
        deviceCode: String,
    ): Result<GithubDeviceTokenSuccessDto> =
        try {
            val reply =
                http.fetchReply {
                    post("https://github.com/login/oauth/access_token") {
                        accept(ContentType.Application.Json)
                        headers.append(HttpHeaders.UserAgent, "GithubStore/1.0 (DeviceFlow)")
                        contentType(ContentType.Application.FormUrlEncoded)

                        timeout {
                            socketTimeoutMillis = SOCKET_TIMEOUT_MS
                        }

                        setBody(
                            FormDataContent(
                                Parameters.build {
                                    append("client_id", clientId)
                                    append("device_code", deviceCode)
                                    append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                                },
                            ),
                        )
                    }
                }

            if (reply.status.isSuccessLike()) {
                tokenAnswer(reply.body, requestIdTag = null)
            } else {
                Result.failure(
                    IllegalStateException(
                        "GitHub access_token HTTP ${reply.status.value} ${reply.status.description}",
                    ),
                )
            }
        } catch (e: NetworkException) {
            Result.failure(e)
        }

    suspend fun startDeviceFlowViaBackend(backendOrigin: String): GithubDeviceStartDto {
        val reply =
            http.fetchReply {
                post("$backendOrigin/v1/auth/device/start") {
                    accept(ContentType.Application.Json)
                    headers.append(HttpHeaders.UserAgent, "GithubStore/1.0 (DeviceFlow)")
                    timeout {
                        requestTimeoutMillis = BACKEND_START_TIMEOUT_MS
                        socketTimeoutMillis = BACKEND_START_TIMEOUT_MS
                    }
                }
            }
        if (!reply.status.isSuccessLike()) {
            throw BackendHttpException(
                statusCode = reply.status.value,
                message =
                    "Backend device/start HTTP ${reply.status.value} " +
                        "${reply.requestId.asRequestIdTag()}. Body: ${reply.body.take(ERROR_BODY_PREVIEW_CHARS)}",
            )
        }
        return json.decodeOrNull(GithubDeviceStartDto.serializer(), reply.body)
            ?: throw NetworkException(
                "Unexpected response from backend device/start " +
                    "${reply.requestId.asRequestIdTag()}: ${reply.body.take(ERROR_BODY_PREVIEW_CHARS)}",
                null,
            )
    }

    suspend fun pollDeviceTokenViaBackend(
        backendOrigin: String,
        deviceCode: String,
    ): Result<GithubDeviceTokenSuccessDto> =
        try {
            val reply =
                http.fetchReply {
                    post("$backendOrigin/v1/auth/device/poll") {
                        accept(ContentType.Application.Json)
                        headers.append(HttpHeaders.UserAgent, "GithubStore/1.0 (DeviceFlow)")
                        contentType(ContentType.Application.FormUrlEncoded)
                        timeout {
                            socketTimeoutMillis = SOCKET_TIMEOUT_MS
                        }
                        setBody(
                            FormDataContent(
                                Parameters.build {
                                    append("device_code", deviceCode)
                                },
                            ),
                        )
                    }
                }

            if (reply.status.isSuccessLike()) {
                tokenAnswer(reply.body, requestIdTag = reply.requestId.asRequestIdTag())
            } else {
                Result.failure(
                    BackendHttpException(
                        statusCode = reply.status.value,
                        message =
                            "Backend device/poll HTTP ${reply.status.value} " +
                                "${reply.status.description} ${reply.requestId.asRequestIdTag()}",
                    ),
                )
            }
        } catch (e: NetworkException) {
            Result.failure(e)
        }

    suspend fun validatePersonalAccessToken(token: String): PatValidation =
        try {
            val status =
                networkFailures {
                    validationHttp
                        .get("https://api.github.com/user") {
                            accept(ContentType.Application.Json)
                            header(HttpHeaders.Authorization, "Bearer $token")
                            header(HttpHeaders.UserAgent, "GithubStore/1.0 (PAT-validate)")
                        }.status
                }
            when {
                status.isSuccessLike() -> {
                    PatValidation.Valid
                }

                status == HttpStatusCode.Unauthorized -> {
                    PatValidation.Rejected(RejectedKind.BadCredentials)
                }

                status == HttpStatusCode.Forbidden -> {
                    PatValidation.Rejected(RejectedKind.InsufficientScope)
                }

                status.value in HTTP_CLIENT_ERRORS -> {
                    PatValidation.Rejected(RejectedKind.Other(status.value))
                }

                else -> {
                    PatValidation.Unreachable("HTTP ${status.value}")
                }
            }
        } catch (e: NetworkException) {
            PatValidation.Unreachable(e.message ?: "network error")
        }

    // A 200 without a token carries GitHub's error code (authorization_pending, slow_down, ...), which the repository reads.
    private fun tokenAnswer(
        body: String,
        requestIdTag: String?,
    ): Result<GithubDeviceTokenSuccessDto> =
        json.decodeOrNull(GithubDeviceTokenSuccessDto.serializer(), body)?.let { Result.success(it) }
            ?: try {
                val err = json.decodeFromString(GithubDeviceTokenErrorDto.serializer(), body)
                Result.failure(IllegalStateException(err.describe(requestIdTag)))
            } catch (e: IllegalArgumentException) {
                Result.failure(NetworkException(e))
            }

    private fun GithubDeviceTokenErrorDto.describe(requestIdTag: String?): String =
        buildString {
            append(error)
            val desc = errorDescription
            if (!desc.isNullOrBlank()) {
                append(": ")
                append(desc)
            }
            if (requestIdTag != null) {
                append(' ')
                append(requestIdTag)
            }
        }.trim()

    private fun HttpStatusCode.isSuccessLike(): Boolean = this in HttpStatusCode.OK..HttpStatusCode.MultipleChoices

    private fun String?.asRequestIdTag(): String = if (this.isNullOrBlank()) "(X-Request-ID=<none>)" else "(X-Request-ID=$this)"

    private suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelay: Long = 1000,
        maxDelay: Long = 5000,
        factor: Double = 2.0,
        block: suspend () -> T,
    ): T {
        var currentDelay = initialDelay
        repeat(maxAttempts - 1) { attempt ->
            try {
                return block()
            } catch (e: NetworkException) {
                if (attempt == maxAttempts - 2) throw e
            }
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
        return block()
    }
}
