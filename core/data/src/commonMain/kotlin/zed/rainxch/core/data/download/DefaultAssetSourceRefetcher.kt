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
    // Reads the host directly, since the dead URL came from a stale cached resolution such as
    // the backend's release copy. Forgejo specs are resolved from their host already.
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
                    // executeRequest returns ordinary failures in the Result. Log them: this read
                    // decides whether the recovery runs at all.
                    .onFailure { e ->
                        logger.error(
                            "Asset refetch failed for ${spec.repoOwner}/${spec.repoName}: ${e.message}",
                        )
                    }.getOrNull()
                    ?: return null
            } catch (e: CancellationException) {
                throw e
            } catch (e: java.io.IOException) {
                logger.error(
                    "Asset refetch failed for ${spec.repoOwner}/${spec.repoName}: ${e.message}",
                )
                return null
            }

        val window = releases.filter { it.draft != true }.map { it.toDomain() }
        return assetReplacementFor(spec, window)
    }

    private companion object {
        const val RELEASE_WINDOW = 10
    }
}

// Same release first, then the newest release carrying an asset of the same name. A different
// name would be a different download, not a retry. Pre-releases count, since a test build is one.
// Null means nothing moved.
internal fun assetReplacementFor(
    spec: DownloadSpec,
    window: List<GithubRelease>,
): DownloadSpec? {
    val inSameRelease =
        window
            .firstOrNull { it.tagName == spec.releaseTag }
            ?.assets
            ?.firstOrNull { it.name == spec.asset.name }
    if (inSameRelease != null) {
        return if (inSameRelease.downloadUrl == spec.asset.downloadUrl) {
            null
        } else {
            spec.copy(asset = inSameRelease)
        }
    }

    val replacementRelease =
        window
            .sortedByDescending { it.publishedAt }
            .firstOrNull { release -> release.assets.any { it.name == spec.asset.name } }
            ?: return null
    val replacement = replacementRelease.assets.first { it.name == spec.asset.name }
    if (replacement.downloadUrl == spec.asset.downloadUrl) return null

    return spec.copy(
        asset = replacement,
        releaseTag = replacementRelease.tagName,
        releaseId = replacementRelease.id,
    )
}
