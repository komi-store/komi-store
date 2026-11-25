package zed.rainxch.auth.data.network

import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import zed.rainxch.core.data.network.BACKEND_ORIGIN
import zed.rainxch.core.data.network.WEB_ORIGIN
import zed.rainxch.core.domain.model.error.NetworkException

private const val ERROR_BODY_PREVIEW_CHARS = 200

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
        val reply =
            gitHubAuthApi.http.fetchReply {
                post("$WEB_ORIGIN/auth/register") {
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
            }
        if (!reply.status.isSuccess()) throw backendFailure("auth/register", reply)
        return decodeAnswer(RegisterResponse.serializer(), reply).authUrl
    }

    suspend fun consumeHandoff(handoffId: String): String {
        val reply =
            gitHubAuthApi.http.fetchReply {
                post("$BACKEND_ORIGIN/v1/oauth/handoff/$handoffId") {
                    accept(ContentType.Application.Json)
                    headers.append(HttpHeaders.UserAgent, "GithubStore/1.0 (WebAuth)")
                }
            }
        if (!reply.status.isSuccess()) throw backendFailure("oauth/handoff", reply)
        return decodeAnswer(HandoffResponse.serializer(), reply).accessToken
    }

    private fun backendFailure(
        endpoint: String,
        reply: AuthReply,
    ): BackendHttpException {
        val errorBody =
            json.decodeOrNull(ErrorResponse.serializer(), reply.body)?.error
                ?: reply.body.take(ERROR_BODY_PREVIEW_CHARS)
        return BackendHttpException(
            statusCode = reply.status.value,
            message = "$endpoint HTTP ${reply.status.value}: $errorBody",
        )
    }

    private fun <T> decodeAnswer(
        deserializer: DeserializationStrategy<T>,
        reply: AuthReply,
    ): T =
        try {
            json.decodeFromString(deserializer, reply.body)
        } catch (e: IllegalArgumentException) {
            throw NetworkException(e)
        }
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
