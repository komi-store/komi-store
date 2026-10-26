package zed.rainxch.core.data.repository

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import zed.rainxch.core.data.dto.ReleaseNetwork
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.isEffectivelyPreRelease
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.utils.VersionMath

internal suspend fun fetchGithubReleaseWindow(
    client: HttpClient,
    owner: String,
    repo: String,
    includePreReleases: Boolean,
    perPage: Int,
): List<GithubRelease> {
    val fetched =
        try {
            client.executeRequest<List<ReleaseNetwork>> {
                get("/repos/$owner/$repo/releases") {
                    header(HttpHeaders.Accept, "application/vnd.github+json")
                    parameter("per_page", perPage)
                }
            }
        } catch (e: RateLimitException) {
            Result.failure(e)
        }
    val releases =
        fetched.getOrElse { error ->
            Logger.e { "Failed to fetch releases for $owner/$repo: ${error.message}" }
            return emptyList()
        }
    return releases
        .asSequence()
        .filter { it.draft != true }
        .sortedByDescending { it.publishedAt ?: it.createdAt ?: "" }
        .map { it.toDomain() }
        .onEach { release ->
            val flagSays = release.isPrerelease
            val tagSays = VersionMath.isPreReleaseTag(release.tagName)
            if (flagSays != tagSays) {
                Logger.w {
                    "Pre-release flag/tag mismatch for $owner/$repo " +
                        "release '${release.tagName}' (name='${release.name}'): " +
                        "apiFlag=$flagSays, tagMarker=$tagSays — " +
                        "treating as pre-release=${true}"
                }
            }
        }.filter { includePreReleases || !it.isEffectivelyPreRelease() }
        .toList()
}
