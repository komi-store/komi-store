package zed.rainxch.core.data.network

import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// A route that waits on one GitHub call through the backend.
internal const val UPSTREAM_TIMEOUT_MS = 15_000L

// A route the backend builds from many GitHub calls: the feed and explore search.
internal const val AGGREGATE_TIMEOUT_MS = 30_000L

internal fun HttpRequestBuilder.allowSlowResponse(millis: Long) {
    timeout {
        requestTimeoutMillis = millis
        socketTimeoutMillis = millis
    }
}

internal fun HttpRequestBuilder.userToken(token: String?) {
    if (token != null) header(X_GITHUB_TOKEN_HEADER, token)
}

internal suspend inline fun <reified T> HttpResponse.backendBodyOrFailure(): Result<T> =
    when {
        status.isSuccess() -> Result.success(body())
        status == HttpStatusCode.TooManyRequests -> Result.failure(toRateLimitedException())
        else -> Result.failure(BackendException(status.value))
    }

internal suspend fun HttpResponse.toRateLimitedException(): RateLimitedException {
    BackendRateLimitTracker.record(this)
    val headerRetryAfter = headers["Retry-After"]?.toLongOrNull()
    return RateLimitedException(
        retryAfterSeconds = retryAfterFromBody() ?: headerRetryAfter,
        resetEpochSeconds = headers["X-RateLimit-Reset"]?.toLongOrNull(),
        limit = headers["X-RateLimit-Limit"]?.toIntOrNull(),
    )
}

private suspend fun HttpResponse.retryAfterFromBody(): Long? =
    if ((contentLength() ?: 0L) == 0L) null else errorBodyOrNull()?.longField("retry_after")

// The backend's error body, or null when it is empty, not a JSON object, or can't be read.
internal suspend fun HttpResponse.errorBodyOrNull(): JsonObject? {
    val text = recoverHttpFailure({ null }) { bodyAsText() }?.takeIf { it.isNotBlank() } ?: return null
    return try {
        Json.parseToJsonElement(text) as? JsonObject
    } catch (_: SerializationException) {
        null
    }
}

internal fun JsonObject.stringField(name: String): String? = (get(name) as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.longField(name: String): Long? = stringField(name)?.toLongOrNull()
