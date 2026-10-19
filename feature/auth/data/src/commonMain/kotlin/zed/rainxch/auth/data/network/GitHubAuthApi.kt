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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
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
import zed.rainxch.core.domain.model.settings.ProxyConfig

private const val HEADER_REQUEST_ID = "X-Request-ID"
private const val REQUEST_TIMEOUT_MS = 60_000L

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
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
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
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 10_000
            }
        }

    suspend fun startDeviceFlowDirect(clientId: String): GithubDeviceStartDto =
        withRetry(maxAttempts = 3, initialDelay = 1000) {
            val res =
                http.post("https://github.com/login/device/code") {
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
            val status = res.status
            val text = res.bodyAsText()

            if (status !in HttpStatusCode.OK..HttpStatusCode.MultipleChoices) {
                error(
                    buildString {
                        append("GitHub device/code HTTP ")
                        append(status.value)
                        append(" ")
                        append(status.description)
                        append(". Body: ")
                        append(text.take(300))
                    },
                )
            }

            try {
                json.decodeFromString(GithubDeviceStartDto.serializer(), text)
            } catch (_: Throwable) {
                try {
                    val err = json.decodeFromString(GithubDeviceTokenErrorDto.serializer(), text)
                    error("${err.error}: ${err.errorDescription ?: ""}".trim())
                } catch (_: Throwable) {
                    error("Unexpected response from GitHub: $text")
                }
            }
        }

    suspend fun pollDeviceTokenDirect(
        clientId: String,
        deviceCode: String,
    ): Result<GithubDeviceTokenSuccessDto> {
        return try {
            val res =
                http.post("https://github.com/login/oauth/access_token") {
                    accept(ContentType.Application.Json)
                    headers.append(HttpHeaders.UserAgent, "GithubStore/1.0 (DeviceFlow)")
                    contentType(ContentType.Application.FormUrlEncoded)

                    timeout {
                        socketTimeoutMillis = 30_000
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
            val status = res.status
            val text = res.bodyAsText()

            if (status !in HttpStatusCode.OK..HttpStatusCode.MultipleChoices) {
                return Result.failure(
                    IllegalStateException(
                        "GitHub access_token HTTP ${status.value} ${status.description}",
                    ),
                )
            }

            try {
                val ok = json.decodeFromString(GithubDeviceTokenSuccessDto.serializer(), text)

                Result.success(ok)
            } catch (_: Throwable) {
                val err = json.decodeFromString(GithubDeviceTokenErrorDto.serializer(), text)
                val message =
                    buildString {
                        append(err.error)
                        val desc = err.errorDescription
                        if (!desc.isNullOrBlank()) {
                            append(": ")
                            append(desc)
                        }
                    }

                Result.failure(IllegalStateException(message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startDeviceFlowViaBackend(backendOrigin: String): GithubDeviceStartDto {
        val res =
            http.post("$backendOrigin/v1/auth/device/start") {
                accept(ContentType.Application.Json)
                headers.append(HttpHeaders.UserAgent, "GithubStore/1.0 (DeviceFlow)")
                timeout {
                    requestTimeoutMillis = 10_000
                    socketTimeoutMillis = 10_000
                }
            }
        val status = res.status
        val requestId = res.headers[HEADER_REQUEST_ID]
        val text = res.bodyAsText()
        if (!status.isSuccessLike()) {
            throw BackendHttpException(
                statusCode = status.value,
                message =
                    "Backend device/start HTTP ${status.value} " +
                        "${requestId.asRequestIdTag()}. Body: ${text.take(300)}",
            )
        }
        return try {
            json.decodeFromString(GithubDeviceStartDto.serializer(), text)
        } catch (_: Throwable) {
            try {
                val err = json.decodeFromString(GithubDeviceTokenErrorDto.serializer(), text)
                error(
                    buildString {
                        append(err.error)
                        err.errorDescription?.let {
                            append(": ")
                            append(it)
                        }
                        append(' ')
                        append(requestId.asRequestIdTag())
                    }.trim(),
                )
            } catch (_: Throwable) {
                error(
                    "Unexpected response from backend device/start " +
                        "${requestId.asRequestIdTag()}: ${text.take(300)}",
                )
            }
        }
    }

    suspend fun pollDeviceTokenViaBackend(
        backendOrigin: String,
        deviceCode: String,
    ): Result<GithubDeviceTokenSuccessDto> {
        return try {
            val res =
                http.post("$backendOrigin/v1/auth/device/poll") {
                    accept(ContentType.Application.Json)
                    headers.append(HttpHeaders.UserAgent, "GithubStore/1.0 (DeviceFlow)")
                    contentType(ContentType.Application.FormUrlEncoded)
                    timeout {
                        socketTimeoutMillis = 30_000
                    }
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("device_code", deviceCode)
                            },
                        ),
                    )
                }
            val status = res.status
            val requestId = res.headers[HEADER_REQUEST_ID]
            val text = res.bodyAsText()

            if (!status.isSuccessLike()) {
                return Result.failure(
                    BackendHttpException(
                        statusCode = status.value,
                        message =
                            "Backend device/poll HTTP ${status.value} " +
                                "${status.description} ${requestId.asRequestIdTag()}",
                    ),
                )
            }

            try {
                Result.success(json.decodeFromString(GithubDeviceTokenSuccessDto.serializer(), text))
            } catch (_: Throwable) {
                val err = json.decodeFromString(GithubDeviceTokenErrorDto.serializer(), text)
                val message =
                    buildString {
                        append(err.error)
                        val desc = err.errorDescription
                        if (!desc.isNullOrBlank()) {
                            append(": ")
                            append(desc)
                        }
                        append(' ')
                        append(requestId.asRequestIdTag())
                    }.trim()
                Result.failure(IllegalStateException(message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun validatePersonalAccessToken(token: String): PatValidation =
        try {
            val res =
                validationHttp.get("https://api.github.com/user") {
                    accept(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.UserAgent, "GithubStore/1.0 (PAT-validate)")
                }
            val status = res.status
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

                status.value in 400..499 -> {
                    PatValidation.Rejected(RejectedKind.Other(status.value))
                }

                else -> {
                    PatValidation.Unreachable("HTTP ${status.value}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PatValidation.Unreachable(e.message ?: "network error")
        }

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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == maxAttempts - 2) throw e
            }
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
        return block()
    }
}
