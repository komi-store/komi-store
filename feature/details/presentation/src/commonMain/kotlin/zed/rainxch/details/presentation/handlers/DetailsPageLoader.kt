package zed.rainxch.details.presentation.handlers

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.account.github.GithubUserProfile
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.NetworkException
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.utils.VersionMath
import zed.rainxch.details.domain.model.ReleaseCategory
import zed.rainxch.details.domain.model.RepoStats
import zed.rainxch.details.presentation.RawDetailsState
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.failed_to_load_details
import kotlin.time.Clock

// Opens the page: the repository and everything around it in parallel, then the release the user most likely wants.
internal class DetailsPageLoader(
    private val reads: DetailsRepoReads,
    private val extras: DetailsRepoExtras,
    private val installedApps: DetailsInstalledApps,
    private val selection: ReleaseSelection,
    private val content: DetailsContentHandler,
    private val logger: KomiStoreLogger,
) {
    private class Releases(
        val releases: List<GithubRelease>,
        val failed: Boolean,
        val fromCache: Boolean,
    )

    private class PageReads(
        val releases: Releases,
        val stats: RepoStats?,
        val readme: Pair<String, String?>?,
        val ownerProfile: GithubUserProfile?,
        val installedApps: List<InstalledApp>,
    )

    internal fun retry(session: DetailsSession) {
        session.hasLoadedInitialData = false
        loadInitial(session)
    }

    internal fun loadInitial(session: DetailsSession) {
        session.launch {
            try {
                session.rateLimited.set(false)
                session.update { it.copy(isLoading = true, errorMessage = null) }
                installedApps.sync()
                val repo = reads.repository(session.args)
                if (repo == null) {
                    logger.error("Foreign-source Details opened without owner/repo for host=${session.args.sourceHost}")
                    showLoadFailure(session)
                } else {
                    launch { extras.markSeen(repo) }
                    loadPage(session, repo)
                }
            } catch (e: RateLimitException) {
                logger.error("Rate limited: ${e.message}")
                val message = reads.rateLimitMessage(e)
                session.update { it.copy(isLoading = false, errorMessage = message) }
            } catch (e: NetworkException) {
                logger.error("Details load failed: ${e.message}")
                showLoadFailure(session)
            } catch (e: LocalStoreException) {
                logger.error("Details load failed: ${e.message}")
                showLoadFailure(session)
            }
        }
    }

    private suspend fun showLoadFailure(session: DetailsSession) {
        val message = getString(Res.string.failed_to_load_details)
        session.update { it.copy(isLoading = false, errorMessage = message) }
    }

    private suspend fun loadPage(
        session: DetailsSession,
        repo: GithubRepoSummary,
    ) {
        val flags =
            coroutineScope {
                val favourite = async { extras.isFavourite(repo.id) }
                val starred = async { extras.isStarred(repo.id) }
                favourite.await() to starred.await()
            }
        session.update { it.copy(repository = repo, isFavourite = flags.first, isStarred = flags.second) }
        val page = readPage(session, repo)
        if (session.rateLimited.get()) {
            session.update { it.copy(isLoading = false, errorMessage = null, releasesLoadFailed = true) }
            return
        }
        val selected = showPage(session, repo, page)
        if (page.releases.fromCache) revalidateReleases(session)
        installedApps.observe(session, repo.id)
        extras.loadReviewsPreview(session, repo)
        content.maybeAutoTranslate(session, readmeBody = page.readme?.first, releaseDescription = selected?.description)
    }

    private suspend fun readPage(
        session: DetailsSession,
        repo: GithubRepoSummary,
    ): PageReads =
        coroutineScope {
            val releases = async { initialReleases(session, repo) }
            val stats = async { reads.statsOrNull(session, repo) }
            val readme = async { reads.readmeOrNull(session, repo) }
            val ownerProfile = async { reads.ownerProfileOrNull(session, repo.owner.login) }
            val apps = async { installedApps.appsOf(repo.id) }
            PageReads(releases.await(), stats.await(), readme.await(), ownerProfile.await(), apps.await())
        }

    // The stored copy when there is one, so the page opens at once; revalidateReleases re-reads it later.
    private suspend fun initialReleases(
        session: DetailsSession,
        repo: GithubRepoSummary,
    ): Releases {
        val cached = reads.cachedReleases(repo, session.args.sourceHost)
        return if (cached != null) {
            Releases(cached.releases, failed = false, fromCache = true)
        } else {
            try {
                Releases(reads.releases(repo, session.args.sourceHost), failed = false, fromCache = false)
            } catch (_: RateLimitException) {
                session.rateLimited.set(true)
                Releases(emptyList(), failed = true, fromCache = false)
            } catch (e: NetworkException) {
                logger.warn("Failed to load releases: ${e.message}")
                Releases(emptyList(), failed = true, fromCache = false)
            } catch (e: LocalStoreException) {
                logger.warn("Failed to load releases: ${e.message}")
                Releases(emptyList(), failed = true, fromCache = false)
            }
        }
    }

    private fun showPage(
        session: DetailsSession,
        repo: GithubRepoSummary,
        page: PageReads,
    ): GithubRelease? {
        val allReleases = page.releases.releases
        val installedApp =
            session.args.packageName
                ?.takeIf { it.isNotBlank() }
                ?.let { pkg -> page.installedApps.firstOrNull { it.packageName == pkg } }
                ?: selection.primaryInstalledApp(page.installedApps, null, emptyList(), allReleases)
        val installedAssetName = installedApp?.let { it.installedAssetName ?: it.latestAssetName ?: it.pendingInstallAssetName }
        // Ownership is judged against the releases and apps just read, which the state doesn't hold yet.
        val provisional = session.current.copy(allReleases = allReleases, installedApps = page.installedApps)
        val selected = openingRelease(provisional, installedApp, installedAssetName)
        val assets = selection.assetsFor(session.current, selected, installedApp, installedAssetName)
        val environment = installedApps.environment()
        logger.debug("Loaded repo: ${repo.name}, installedApp: ${installedApp?.packageName}")
        val insights = selection.insights(allReleases, installedApp)
        val deviceLanguageCode = content.deviceLanguageCode()
        session.update {
            it
                .withReleases(allReleases, page.releases.failed, selected, assets)
                .withEnvironment(environment, session, deviceLanguageCode)
                .copy(
                    isLoading = false,
                    errorMessage = null,
                    repository = repo,
                    stats = page.stats,
                    readmeMarkdown = page.readme?.first,
                    readmeLanguage = page.readme?.second,
                    userProfile = page.ownerProfile,
                    installedApp = installedApp,
                ).withInsights(insights)
        }
        return selected
    }

    private fun RawDetailsState.withReleases(
        allReleases: List<GithubRelease>,
        failed: Boolean,
        selected: GithubRelease?,
        assets: ReleaseSelection.Assets,
    ): RawDetailsState =
        copy(
            allReleases = allReleases,
            releasePlatforms = selection.platformsByRelease(allReleases),
            deviceBuildReleaseIds = selection.deviceBuildReleaseIds(allReleases),
            releaseLines = selection.releaseLines(allReleases),
            releasesLoadFailed = failed,
            isRetryingReleases = false,
            selectedRelease = selected,
            selectedReleaseCategory = selected?.category() ?: ReleaseCategory.STABLE,
            installableAssets = assets.installable,
            primaryAsset = assets.primary,
        )

    private fun RawDetailsState.withEnvironment(
        environment: DetailsInstalledApps.Environment,
        session: DetailsSession,
        deviceLanguageCode: String,
    ): RawDetailsState {
        val isAndroid = session.platform == Platform.ANDROID
        return copy(
            systemArchitecture = environment.systemArchitecture,
            isObtainiumAvailable = environment.isObtainiumAvailable,
            isObtainiumEnabled = isAndroid,
            isAppManagerAvailable = environment.isAppManagerAvailable,
            isAppManagerEnabled = isAndroid,
            deviceLanguageCode = deviceLanguageCode,
            isComingFromUpdate = session.args.isComingFromUpdate,
        )
    }

    // The app's update target, else the newest release in its channel that it owns, else the channel's newest device build.
    private fun openingRelease(
        provisional: RawDetailsState,
        installedApp: InstalledApp?,
        installedAssetName: String?,
    ): GithubRelease? {
        val allReleases = provisional.allReleases
        val installedVersion = installedApp?.installedVersion
        val installedRelease =
            allReleases.firstOrNull { VersionMath.isExactSameVersion(it.tagName, installedVersion) }
                ?: allReleases.firstOrNull { VersionMath.isSameVersion(it.tagName, installedVersion) }
        val channel = installedRelease?.category() ?: ReleaseCategory.STABLE
        val deviceBuildIds = selection.deviceBuildReleaseIds(allReleases)
        return selection.updateTargetOf(allReleases, installedApp)
            ?: installedApp?.let { selection.firstOwnedBy(provisional, it, channel, installedAssetName) }
            ?: allReleases.filter { it.id in deviceBuildIds }.firstInCategory(channel)
            ?: allReleases.firstInCategory(channel)
            ?: allReleases.firstInCategory(ReleaseCategory.ALL)
    }

    // The backend serves a copy up to an hour old, so re-reading a younger list can't return anything newer.
    private fun revalidateReleases(session: DetailsSession) {
        val repo = session.current.repository ?: return
        session.launch {
            try {
                val cached = reads.cachedReleases(repo, session.args.sourceHost) ?: return@launch
                val ageMs = Clock.System.now().toEpochMilliseconds() - cached.cachedAtEpochMs
                if (ageMs < RELEASES_REVALIDATE_MIN_AGE_MS) return@launch
                val fresh = reads.releases(repo, session.args.sourceHost, bypassCache = true)
                if (fresh.isEmpty()) return@launch
                val previousSelectedId = session.current.selectedRelease?.id
                session.update { selection.withFreshReleases(it, fresh) }
                if (session.current.selectedRelease?.id != previousSelectedId) {
                    session.whatsNewTranslationJob?.cancel()
                    content.maybeAutoTranslate(session, null, session.current.selectedRelease?.description)
                }
            } catch (e: NetworkException) {
                logger.debug("Release re-read failed for ${repo.name}: ${e.message}")
            } catch (e: LocalStoreException) {
                logger.debug("Release re-read failed for ${repo.name}: ${e.message}")
            } catch (e: RateLimitException) {
                logger.debug("Release re-read failed for ${repo.name}: ${e.message}")
            }
        }
    }

    private companion object {
        const val RELEASES_REVALIDATE_MIN_AGE_MS = 60L * 60L * 1000L
    }
}
