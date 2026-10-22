package zed.rainxch.auth.data.network

import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import zed.rainxch.core.data.network.BACKEND_ORIGIN
import zed.rainxch.core.data.network.WEB_ORIGIN

class WebAuthApi(
    private val gitHubAuthApi: GitHubAuthApi,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    suspend fun register(
        state: String,
        codeChallenge: String,
        codeVerifier: String,
    ): String {
        val res =
            gitHubAuthApi.http.post("$WEB_ORIGIN/auth/register") {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                headers.append(HttpHeaders.UserAgent, "GithubStore/1.0 (WebAuth)")
                setBody(
                    RegisterRequest(
                        state = state,
                        codeChallenge = codeChallenge,
                        codeVerifier = codeVerifier,
                    ),
                )
            }
        val text = res.bodyAsText()
        if (!res.status.isSuccess()) {
            val errorBody =
                runCatching {
                    json.decodeFromString(ErrorResponse.serializer(), text).error
                }.getOrNull() ?: text.take(200)
            throw BackendHttpException(
                statusCode = res.status.value,
                message = "auth/register HTTP ${res.status.value}: $errorBody",
            )
        }
        return json.decodeFromString(RegisterResponse.serializer(), text).authUrl
    }

    suspend fun consumeHandoff(handoffId: String): String {
        val res =
            gitHubAuthApi.http.post("$BACKEND_ORIGIN/v1/oauth/handoff/$handoffId") {
                accept(ContentType.Application.Json)
                headers.append(HttpHeaders.UserAgent, "GithubStore/1.0 (WebAuth)")
            }
        val text = res.bodyAsText()
        if (!res.status.isSuccess()) {
            val errorBody =
                runCatching {
                    json.decodeFromString(ErrorResponse.serializer(), text).error
                }.getOrNull() ?: text.take(200)
            throw BackendHttpException(
                statusCode = res.status.value,
                message = "oauth/handoff HTTP ${res.status.value}: $errorBody",
            )
        }
        return json.decodeFromString(HandoffResponse.serializer(), text).accessToken
    }

    private fun io.ktor.http.HttpStatusCode.isSuccess(): Boolean = value in 200..299
}

@Serializable
private data class RegisterRequest(
    @SerialName("state") val state: String,
    @SerialName("code_challenge") val codeChallenge: String,
    @SerialName("code_verifier") val codeVerifier: String,
)

@Serializable
private data class RegisterResponse(
    @SerialName("auth_url") val authUrl: String,
)

@Serializable
private data class HandoffResponse(
    @SerialName("access_token") val accessToken: String,
)

@Serializable
private data class ErrorResponse(
    @SerialName("error") val error: String,
)
