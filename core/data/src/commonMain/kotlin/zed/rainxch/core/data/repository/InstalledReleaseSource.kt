package zed.rainxch.core.data.repository

import zed.rainxch.core.data.mappers.toReleaseWindow
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.network.ForgejoClientRegistry
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.data.network.getReleases
import zed.rainxch.core.data.network.shouldFallbackToGithubOrRethrow
import zed.rainxch.core.domain.model.account.github.GithubRelease

internal const val RELEASE_WINDOW = 50

// Where a tracked app's releases come from: its Forgejo host, or the backend with GitHub behind it.
internal class InstalledReleaseSource(
    private val clientProvider: GitHubClientProvider,
    private val backendApiClient: BackendApiClient,
    private val forgejoClientRegistry: ForgejoClientRegistry,
) {
    suspend fun window(
        owner: String,
        repo: String,
        includePreReleases: Boolean,
        sourceHost: String? = null,
    ): List<GithubRelease> =
        if (sourceHost != null) {
            forgejoWindow(sourceHost, owner, repo, includePreReleases)
        } else {
            backendWindow(owner, repo, includePreReleases) ?: githubWindow(owner, repo, includePreReleases)
        }

    // Skips the backend, for when its copy of the releases is what went stale.
    suspend fun hostWindow(
        owner: String,
        repo: String,
        includePreReleases: Boolean,
        sourceHost: String?,
    ): List<GithubRelease> =
        if (sourceHost != null) {
            forgejoWindow(sourceHost, owner, repo, includePreReleases)
        } else {
            githubWindow(owner, repo, includePreReleases)
        }

    // Null when the backend can't answer and GitHub should be asked instead.
    private suspend fun backendWindow(
        owner: String,
        repo: String,
        includePreReleases: Boolean,
    ): List<GithubRelease>? =
        backendApiClient.getReleases(owner, repo, perPage = RELEASE_WINDOW).fold(
            onSuccess = { it.toReleaseWindow(includePreReleases) },
            onFailure = { error -> if (shouldFallbackToGithubOrRethrow(error)) null else emptyList() },
        )

    private suspend fun githubWindow(
        owner: String,
        repo: String,
        includePreReleases: Boolean,
    ): List<GithubRelease> = fetchGithubReleaseWindow(clientProvider.client, owner, repo, includePreReleases, RELEASE_WINDOW)

    private suspend fun forgejoWindow(
        host: String,
        owner: String,
        repo: String,
        includePreReleases: Boolean,
    ): List<GithubRelease> =
        forgejoClientRegistry
            .clientFor(host)
            .getReleases(owner, repo, perPage = RELEASE_WINDOW)
            .getOrNull()
            ?.toReleaseWindow(includePreReleases)
            .orEmpty()
}
