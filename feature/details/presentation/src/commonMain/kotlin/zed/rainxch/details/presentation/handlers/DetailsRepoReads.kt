package zed.rainxch.details.presentation.handlers

import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.account.github.GithubUserProfile
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.NetworkException
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.repository.UserSessionRepository
import zed.rainxch.details.domain.model.CachedReleases
import zed.rainxch.details.domain.model.RepoStats
import zed.rainxch.details.domain.repository.DetailsRepository
import zed.rainxch.details.presentation.DetailsArgs
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.rate_limit_exceeded
import zed.rainxch.githubstore.core.presentation.res.rate_limit_exceeded_retry_in
import zed.rainxch.githubstore.core.presentation.res.rate_limit_exceeded_signin_hint

// The page's reads from the host: the repository, its releases, stats, README and owner. A rate limit marks the session.
internal class DetailsRepoReads(
    private val detailsRepository: DetailsRepository,
    private val userSessionRepository: UserSessionRepository,
    private val logger: KomiStoreLogger,
) {
    // Null for a foreign-source page opened without owner and repo, which has nothing to read.
    internal suspend fun repository(args: DetailsArgs): GithubRepoSummary? =
        when {
            args.sourceHost != null && (args.owner.isBlank() || args.repo.isBlank()) -> {
                null
            }

            args.sourceHost != null -> {
                detailsRepository.getRepositoryByOwnerAndName(args.owner, args.repo, args.sourceHost)
            }

            args.owner.isNotEmpty() && args.repo.isNotEmpty() -> {
                detailsRepository.getRepositoryByOwnerAndName(args.owner, args.repo, sourceHost = null)
            }

            else -> {
                detailsRepository.getRepositoryById(args.repositoryId)
            }
        }

    internal suspend fun refreshedRepository(
        repo: GithubRepoSummary,
        sourceHost: String?,
    ): GithubRepoSummary =
        if (sourceHost != null) {
            detailsRepository.getRepositoryByOwnerAndName(repo.owner.login, repo.name, sourceHost)
        } else {
            detailsRepository.refreshRepository(repo.owner.login, repo.name)
        }

    internal suspend fun cachedReleases(
        repo: GithubRepoSummary,
        sourceHost: String?,
    ): CachedReleases? = detailsRepository.getCachedReleases(repo.owner.login, repo.name, sourceHost)

    internal suspend fun releases(
        repo: GithubRepoSummary,
        sourceHost: String?,
        bypassCache: Boolean = false,
    ): List<GithubRelease> =
        detailsRepository.getAllReleases(
            owner = repo.owner.login,
            repo = repo.name,
            defaultBranch = repo.defaultBranch,
            sourceHost = sourceHost,
            bypassCache = bypassCache,
        )

    // A user-asked refresh reads the host first and reports a failure instead of serving the stored copy.
    internal suspend fun refreshedReleases(
        repo: GithubRepoSummary,
        defaultBranch: String,
        sourceHost: String?,
    ): List<GithubRelease> =
        detailsRepository.getAllReleases(
            owner = repo.owner.login,
            repo = repo.name,
            defaultBranch = defaultBranch,
            sourceHost = sourceHost,
            bypassCache = true,
            allowStale = false,
            preferDirectSource = true,
        )

    internal suspend fun stats(
        repo: GithubRepoSummary,
        sourceHost: String?,
    ): RepoStats = detailsRepository.getRepoStats(owner = repo.owner.login, repo = repo.name, sourceHost = sourceHost)

    internal suspend fun statsOrNull(
        session: DetailsSession,
        repo: GithubRepoSummary,
    ): RepoStats? =
        try {
            stats(repo, session.args.sourceHost)
        } catch (_: RateLimitException) {
            session.rateLimited.set(true)
            null
        } catch (e: NetworkException) {
            logger.warn("Failed to load repo stats: ${e.message}")
            null
        } catch (e: LocalStoreException) {
            logger.warn("Failed to load repo stats: ${e.message}")
            null
        }

    internal suspend fun readmeOrNull(
        session: DetailsSession,
        repo: GithubRepoSummary,
    ): Pair<String, String?>? =
        try {
            detailsRepository
                .getReadme(
                    owner = repo.owner.login,
                    repo = repo.name,
                    defaultBranch = repo.defaultBranch,
                    sourceHost = session.args.sourceHost,
                )?.let { (content, language, _) -> content to language }
        } catch (_: RateLimitException) {
            session.rateLimited.set(true)
            null
        } catch (e: LocalStoreException) {
            logger.warn("Failed to load readme: ${e.message}")
            null
        }

    // Owner profiles come from GitHub only.
    internal suspend fun ownerProfileOrNull(
        session: DetailsSession,
        owner: String,
    ): GithubUserProfile? =
        if (session.args.sourceHost != null) {
            null
        } else {
            try {
                detailsRepository.getUserProfile(owner)
            } catch (_: RateLimitException) {
                session.rateLimited.set(true)
                null
            } catch (e: NetworkException) {
                logger.warn("Failed to load user profile: ${e.message}")
                null
            } catch (e: LocalStoreException) {
                logger.warn("Failed to load user profile: ${e.message}")
                null
            }
        }

    internal suspend fun rateLimitMessage(e: RateLimitException): String {
        val seconds = e.rateLimitInfo.timeUntilReset().inWholeSeconds
        val base =
            if (seconds > 0L) {
                getString(Res.string.rate_limit_exceeded_retry_in, seconds.toInt())
            } else {
                getString(Res.string.rate_limit_exceeded)
            }
        return if (userSessionRepository.isCurrentlyUserLoggedIn()) {
            base
        } else {
            base + " " + getString(Res.string.rate_limit_exceeded_signin_hint)
        }
    }
}
