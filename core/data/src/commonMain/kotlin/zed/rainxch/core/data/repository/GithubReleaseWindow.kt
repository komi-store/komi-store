@file:Suppress("TooGenericExceptionCaught")

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
import zed.rainxch.core.domain.utils.VersionMath
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun fetchGithubReleaseWindow(
    client: HttpClient,
    owner: String,
    repo: String,
    includePreReleases: Boolean,
    perPage: Int,
): List<GithubRelease> {
    return try {
        val releases =
            client
                .executeRequest<List<ReleaseNetwork>> {
                    get("/repos/$owner/$repo/releases") {
                        header(HttpHeaders.Accept, "application/vnd.github+json")
                        parameter("per_page", perPage)
                    }
                }.getOrNull() ?: return emptyList()

        releases
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
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.e { "Failed to fetch releases for $owner/$repo: ${e.message}" }
        emptyList()
    }
}
