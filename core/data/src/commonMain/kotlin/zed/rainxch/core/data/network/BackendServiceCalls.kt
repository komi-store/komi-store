package zed.rainxch.core.data.network

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import zed.rainxch.core.data.dto.AnnouncementsResponseDto
import zed.rainxch.core.data.dto.ExternalMatchRequest
import zed.rainxch.core.data.dto.ExternalMatchResponse
import zed.rainxch.core.data.dto.MirrorListResponse
import zed.rainxch.core.data.dto.SigningFingerprintSeedResponse

suspend fun BackendApiClient.postExternalMatch(body: ExternalMatchRequest): Result<ExternalMatchResponse> =
    request(authed = false) {
        post("external-match") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.bodyOrBareRateLimit()
    }

suspend fun BackendApiClient.getSigningSeeds(
    since: Long? = null,
    cursor: String? = null,
    platform: String = "android",
): Result<SigningFingerprintSeedResponse> =
    request(authed = false) {
        get("signing-seeds") {
            parameter("platform", platform)
            if (since != null) parameter("since", since)
            if (cursor != null) parameter("cursor", cursor)
        }.bodyOrBareRateLimit()
    }

suspend fun BackendApiClient.getMirrorList(): Result<MirrorListResponse> =
    request(authed = false) {
        get("mirrors/list").backendBodyOrFailure()
    }

suspend fun BackendApiClient.getAnnouncements(): Result<AnnouncementsResponseDto> =
    request(authed = false) {
        get("announcements").backendBodyOrFailure()
    }

private suspend inline fun <reified T> HttpResponse.bodyOrBareRateLimit(): Result<T> =
    when {
        status.isSuccess() -> Result.success(body())
        status == HttpStatusCode.TooManyRequests -> Result.failure(RateLimitedException())
        else -> Result.failure(BackendException(status.value))
    }
