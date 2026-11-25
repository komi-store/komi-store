package zed.rainxch.core.data.network

import io.ktor.client.request.get
import io.ktor.client.request.parameter
import zed.rainxch.core.data.dto.BackendExploreResponse
import zed.rainxch.core.data.dto.BackendFeedResponse
import zed.rainxch.core.data.dto.BackendRepoResponse
import zed.rainxch.core.data.dto.BackendSearchResponse

suspend fun BackendApiClient.getCategory(
    category: String,
    platform: String,
): Result<List<BackendRepoResponse>> =
    request(authed = false) {
        get("categories/$category/$platform").backendBodyOrFailure()
    }

suspend fun BackendApiClient.getTopic(
    bucket: String,
    platform: String,
): Result<List<BackendRepoResponse>> =
    request(authed = false) {
        get("topics/$bucket/$platform").backendBodyOrFailure()
    }

suspend fun BackendApiClient.search(
    query: String,
    platform: String? = null,
    sort: String? = null,
    limit: Int = 20,
    offset: Int = 0,
): Result<BackendSearchResponse> =
    request(authed = true) { token ->
        get("search") {
            parameter("q", query)
            if (platform != null) parameter("platform", platform)
            if (sort != null) parameter("sort", sort)
            parameter("limit", limit)
            parameter("offset", offset)
            userToken(token)
        }.backendBodyOrFailure()
    }

suspend fun BackendApiClient.getFeed(
    platform: String?,
    page: Int = 1,
    limit: Int = 20,
): Result<BackendFeedResponse> =
    request(authed = false) {
        get("feed") {
            if (platform != null) parameter("platform", platform)
            parameter("page", page)
            parameter("limit", limit)
            allowSlowResponse(AGGREGATE_TIMEOUT_MS)
        }.backendBodyOrFailure()
    }

suspend fun BackendApiClient.searchExplore(
    query: String,
    platform: String? = null,
    page: Int = 1,
): Result<BackendExploreResponse> =
    request(authed = true) { token ->
        get("search/explore") {
            parameter("q", query)
            if (platform != null) parameter("platform", platform)
            parameter("page", page)
            allowSlowResponse(AGGREGATE_TIMEOUT_MS)
            userToken(token)
        }.backendBodyOrFailure()
    }
