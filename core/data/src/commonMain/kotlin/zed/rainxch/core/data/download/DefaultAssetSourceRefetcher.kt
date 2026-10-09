package zed.rainxch.core.data.download

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlin.coroutines.cancellation.CancellationException
import zed.rainxch.core.data.dto.ReleaseNetwork
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.data.network.GithubAssetAuth
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.system.DownloadSpec

class DefaultAssetSourceRefetcher(
    private val clientProvider: GitHubClientProvider,
    private val logger: KomiStoreLogger,
) : AssetSourceRefetcher {

    // Reads straight from the repository host: the reason a download lands here is a stale
    // resolution, and every cache that can hand out a stale URL — the backend's release copy in
    // particular — is exactly what the question "what does the repository have now" must step
    // around. GitHub-host assets only: a Forgejo spec was resolved straight from its host to
    // begin with, so there is no cache in between for a refetch to step around.
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
                    // executeRequest folds ordinary failures (HTTP error, IO, serialization)
                    // into the Result instead of throwing — only rate limiting and cancellation
                    // reach the catch below. Logging the failure before the elvis keeps the
                    // refetch diagnosable: this read decides whether the whole recovery
                    // engages, so a silent null here would strand stale resolutions with no
                    // trace.
                    .onFailure { e ->
                        logger.error(
                            "Asset refetch failed for ${spec.repoOwner}/${spec.repoName}: ${e.message}",
                        )
                    }
                    .getOrNull()
                    ?: return null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
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

// The window is already host-fresh; this decides whether it carries the same asset elsewhere.
// Same release and same name first — a re-upload moves the URL without touching the tag. Then,
// when the release the spec was pinned to is gone, the newest release carrying an asset of the
// same name: the name is the floor, because a different file would be a different download, not
// a retry. Prereleases are not filtered here — the spec does not say which channel built it, and
// a test build's spec is a prerelease itself. Null means nothing moved and the failure stands.
internal fun assetReplacementFor(spec: DownloadSpec, window: List<GithubRelease>): DownloadSpec? {
    val inSameRelease =
        window.firstOrNull { it.tagName == spec.releaseTag }
            ?.assets?.firstOrNull { it.name == spec.asset.name }
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
