package zed.rainxch.details.presentation.handlers

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.account.github.isEffectivelyPreRelease
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.NetworkException
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.error.RefreshError
import zed.rainxch.core.domain.model.error.RefreshException
import zed.rainxch.details.domain.model.ReleaseCategory
import zed.rainxch.details.domain.model.RepoStats
import zed.rainxch.details.presentation.DetailsEvent
import zed.rainxch.details.presentation.RawDetailsState
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.releases_unavailable_temporarily
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

// Re-reads an open page: the user's refresh and the retry after a failed release read.
internal class DetailsRefresher(
    private val reads: DetailsRepoReads,
    private val extras: DetailsRepoExtras,
    private val installedApps: DetailsInstalledApps,
    private val selection: ReleaseSelection,
    private val content: DetailsContentHandler,
    private val logger: KomiStoreLogger,
) {
    internal fun retryReleases(session: DetailsSession) {
        val repo = session.current.repository ?: return
        if (session.current.isRetryingReleases) return
        session.launch {
            val previousCategory = session.current.selectedReleaseCategory
            session.update { it.copy(isRetryingReleases = true, releasesLoadFailed = false) }
            try {
                val releases = reads.releases(repo, session.args.sourceHost)
                val selected = showRetriedReleases(session, releases, previousCategory)
                content.maybeAutoTranslate(session, readmeBody = null, releaseDescription = selected?.description)
            } catch (_: RateLimitException) {
                session.update { it.copy(isRetryingReleases = false, releasesLoadFailed = true) }
            } catch (e: NetworkException) {
                onRetryFailed(session, e)
            } catch (e: LocalStoreException) {
                onRetryFailed(session, e)
            }
        }
    }

    private fun onRetryFailed(
        session: DetailsSession,
        error: Exception,
    ) {
        logger.warn("Retry failed to load releases: ${error.message}")
        session.launch { session.message(getString(Res.string.releases_unavailable_temporarily)) }
        session.update { it.copy(isRetryingReleases = false, releasesLoadFailed = true) }
    }

    private fun showRetriedReleases(
        session: DetailsSession,
        releases: List<GithubRelease>,
        previousCategory: ReleaseCategory,
    ): GithubRelease? {
        val deviceBuildIds = selection.deviceBuildReleaseIds(releases)
        val inPreviousCategory =
            releases.filter { it.id in deviceBuildIds }.firstInCategory(previousCategory)
                ?: releases.firstInCategory(previousCategory)
        val selected = inPreviousCategory ?: releases.firstOrNull { !it.isEffectivelyPreRelease() } ?: releases.firstOrNull()
        val category = if (inPreviousCategory != null) previousCategory else selected?.category() ?: ReleaseCategory.STABLE
        val current = session.current
        val assets = selection.assetsFor(current, selected, current.installedApp)
        val installedApp =
            selection.primaryInstalledApp(current.installedApps, assets.primary?.name, assets.installable, releases, selected?.tagName)
        val insights = selection.insights(releases, installedApp)
        session.update {
            it
                .copy(
                    installedApp = installedApp,
                    allReleases = releases,
                    releasePlatforms = selection.platformsByRelease(releases),
                    deviceBuildReleaseIds = deviceBuildIds,
                    releaseLines = selection.releaseLines(releases),
                    releasesLoadFailed = false,
                    isRetryingReleases = false,
                    selectedRelease = selected,
                    selectedReleaseCategory = category,
                    installableAssets = assets.installable,
                    primaryAsset = assets.primary,
                ).withInsights(insights)
        }
        return selected
    }

    internal fun refresh(session: DetailsSession) {
        if (session.current.isRefreshing) return
        val repo = session.current.repository ?: return
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val cooledDown = session.current.refreshCooldownUntilEpochMs?.let { it > nowMs } == true
        session.update { it.copy(isRefreshing = true) }
        session.launch {
            try {
                val refreshed = refreshedRepository(session, repo, cooledDown, nowMs)
                val (freshReleases, freshStats) = freshReads(session, repo, refreshed.defaultBranch)
                if (freshReleases == null) session.send(DetailsEvent.OnRefreshError(kind = RefreshError.UPSTREAM))
                showRefreshed(session, refreshed, freshReleases, freshStats)
                freshReleases?.takeIf { it.isNotEmpty() }?.let { installedApps.syncLibraryUpdateState(session, it) }
            } catch (e: RefreshException) {
                logger.warn("Refresh failed (${e.kind}): ${e.message}")
                val nowMs = Clock.System.now().toEpochMilliseconds()
                session.update { it.copy(isRefreshing = false, refreshCooldownUntilEpochMs = it.cooldownAfter(e, nowMs)) }
                session.send(DetailsEvent.OnRefreshError(kind = e.kind, retryAfterSeconds = e.retryAfterSeconds))
            } catch (e: NetworkException) {
                onRefreshFailed(session, e)
            } catch (e: LocalStoreException) {
                onRefreshFailed(session, e)
            }
        }
    }

    private suspend fun onRefreshFailed(
        session: DetailsSession,
        error: Exception,
    ) {
        logger.error("Refresh failed: ${error.message}")
        session.update { it.copy(isRefreshing = false) }
        session.send(DetailsEvent.OnRefreshError(kind = RefreshError.GENERIC))
    }

    // A refused re-poll keeps the copy already shown and, for a cooldown or an empty budget, waits it out.
    private suspend fun refreshedRepository(
        session: DetailsSession,
        repo: GithubRepoSummary,
        cooledDown: Boolean,
        nowMs: Long,
    ): GithubRepoSummary {
        val sourceHost = session.args.sourceHost
        if (sourceHost == null && cooledDown) return repo
        return try {
            reads.refreshedRepository(repo, sourceHost)
        } catch (e: RefreshException) {
            logger.warn("Refresh: repository re-poll failed (${e.kind}): ${e.message}")
            session.update { it.copy(refreshCooldownUntilEpochMs = it.cooldownAfter(e, nowMs)) }
            repo
        }
    }

    // Read by the names the page opened with; only the default branch comes from the re-polled repository.
    private suspend fun freshReads(
        session: DetailsSession,
        repo: GithubRepoSummary,
        defaultBranch: String,
    ): Pair<List<GithubRelease>?, RepoStats?> =
        coroutineScope {
            val releases =
                async {
                    try {
                        reads.refreshedReleases(repo, defaultBranch, session.args.sourceHost)
                    } catch (e: NetworkException) {
                        logger.warn("Refresh: getAllReleases failed: ${e.message}")
                        null
                    } catch (e: LocalStoreException) {
                        logger.warn("Refresh: getAllReleases failed: ${e.message}")
                        null
                    } catch (e: RateLimitException) {
                        logger.warn("Refresh: getAllReleases failed: ${e.message}")
                        null
                    }
                }
            val stats =
                async {
                    try {
                        reads.stats(repo, session.args.sourceHost)
                    } catch (e: NetworkException) {
                        logger.warn("Refresh: getRepoStats failed: ${e.message}")
                        null
                    } catch (e: LocalStoreException) {
                        logger.warn("Refresh: getRepoStats failed: ${e.message}")
                        null
                    } catch (e: RateLimitException) {
                        logger.warn("Refresh: getRepoStats failed: ${e.message}")
                        null
                    }
                }
            releases.await() to stats.await()
        }

    private fun showRefreshed(
        session: DetailsSession,
        refreshed: GithubRepoSummary,
        freshReleases: List<GithubRelease>?,
        freshStats: RepoStats?,
    ) {
        val previousSelectedId = session.current.selectedRelease?.id
        session.update { current ->
            (freshReleases?.let { selection.withFreshReleases(current, it) } ?: current).copy(
                isRefreshing = false,
                repository = refreshed,
                releasesLoadFailed = freshReleases == null && current.releasesLoadFailed,
                stats = freshStats ?: current.stats,
            )
        }
        extras.loadReviewsPreview(session, refreshed)
        if (session.current.selectedRelease?.id != previousSelectedId) {
            session.whatsNewTranslationJob?.cancel()
            content.maybeAutoTranslate(session, null, session.current.selectedRelease?.description)
        }
    }

    private fun RawDetailsState.cooldownAfter(
        e: RefreshException,
        nowMs: Long,
    ): Long? {
        val waitsOut = e.kind == RefreshError.COOLDOWN || e.kind == RefreshError.BUDGET_EXHAUSTED
        val until = e.retryAfterSeconds?.let { nowMs + it.seconds.inWholeMilliseconds }
        return if (waitsOut) until ?: refreshCooldownUntilEpochMs else refreshCooldownUntilEpochMs
    }
}
