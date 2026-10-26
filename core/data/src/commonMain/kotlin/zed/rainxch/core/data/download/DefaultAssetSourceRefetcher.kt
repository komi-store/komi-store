package zed.rainxch.core.data.download

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import zed.rainxch.core.data.dto.ReleaseNetwork
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.data.network.GithubAssetAuth
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.system.DownloadSpec
import kotlin.coroutines.cancellation.CancellationException

class DefaultAssetSourceRefetcher(
    private val clientProvider: GitHubClientProvider,
    private val logger: KomiStoreLogger,
) : AssetSourceRefetcher {
    // Reads GitHub directly, since the dead URL came from a stale copy such as the backend's; Forgejo specs already read their host.
    override suspend fun refetch(spec: DownloadSpec): DownloadSpec? {
        if (!GithubAssetAuth.isGithubHost(spec.asset.downloadUrl)) return null

        val releases =
            try {
                clientProvider.client
                    .executeRequest<List<ReleaseNetwork>> {
                        get("/repos/${spec.repoOwner}/${spec.repoName}/releases") {
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                            parameter("per_page", RELEASE_WINDOW)
                        }
                    }
                    // executeRequest returns ordinary failures in the Result; log them, as this read decides whether recovery runs.
                    .onFailure { e ->
                        logger.error("Asset refetch failed for ${spec.repoOwner}/${spec.repoName}", e)
                    }.getOrNull()
                    ?: return null
            } catch (e: CancellationException) {
                throw e
            } catch (e: java.io.IOException) {
                logger.error("Asset refetch failed for ${spec.repoOwner}/${spec.repoName}", e)
                return null
            }

        val window = releases.filter { it.draft != true }.map { it.toDomain() }
        return assetReplacementFor(spec, window)
    }

    private companion object {
        const val RELEASE_WINDOW = 10
    }
}

// Same release first, then the newest one (pre-releases count) with an asset of the same name; null means nothing moved.
internal fun assetReplacementFor(
    spec: DownloadSpec,
    window: List<GithubRelease>,
): DownloadSpec? {
    val inSameRelease =
        window
            .firstOrNull { it.tagName == spec.releaseTag }
            ?.assets
            ?.firstOrNull { it.name == spec.asset.name }
    val replacement =
        if (inSameRelease != null) {
            spec.copy(asset = inSameRelease)
        } else {
            newestReleaseCarrying(spec, window)
        }
    return replacement?.takeIf { it.asset.downloadUrl != spec.asset.downloadUrl }
}

private fun newestReleaseCarrying(
    spec: DownloadSpec,
    window: List<GithubRelease>,
): DownloadSpec? {
    val release =
        window
            .sortedByDescending { it.publishedAt }
            .firstOrNull { candidate -> candidate.assets.any { it.name == spec.asset.name } }
            ?: return null
    return spec.copy(
        asset = release.assets.first { it.name == spec.asset.name },
        releaseTag = release.tagName,
        releaseId = release.id,
    )
}
