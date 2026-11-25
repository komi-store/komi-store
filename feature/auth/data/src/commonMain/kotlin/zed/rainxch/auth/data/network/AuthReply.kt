package zed.rainxch.auth.data.network

import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import zed.rainxch.core.data.network.networkFailures

private const val HEADER_REQUEST_ID = "X-Request-ID"

internal class AuthReply(
    val status: HttpStatusCode,
    val requestId: String?,
    val body: String,
)

// Whatever Ktor throws on the way, short of cancellation, leaves as a NetworkException.
internal suspend fun HttpClient.fetchReply(request: suspend HttpClient.() -> HttpResponse): AuthReply =
    networkFailures {
        val response = request()
        AuthReply(response.status, response.headers[HEADER_REQUEST_ID], response.bodyAsText())
    }

// kotlinx.serialization raises IllegalArgumentException, SerializationException among them, for a body of another shape.
internal fun <T> Json.decodeOrNull(
    deserializer: DeserializationStrategy<T>,
    body: String,
): T? =
    try {
        decodeFromString(deserializer, body)
    } catch (_: IllegalArgumentException) {
        null
    }
