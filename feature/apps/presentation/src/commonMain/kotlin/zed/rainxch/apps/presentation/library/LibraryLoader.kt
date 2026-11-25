package zed.rainxch.apps.presentation.library

import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import zed.rainxch.apps.presentation.AppsAction
import zed.rainxch.apps.presentation.AppsEvent
import zed.rainxch.apps.presentation.AppsStateHolder
import zed.rainxch.apps.presentation.appComparator
import zed.rainxch.apps.presentation.mappers.toAppItem
import zed.rainxch.apps.presentation.mappers.toUi
import zed.rainxch.apps.presentation.model.AppSortRule
import zed.rainxch.apps.presentation.model.UpdateState
import zed.rainxch.apps.presentation.withFilteredApps
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.repository.ExternalImportRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.ReviewsRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.usecases.SyncInstalledAppsUseCase
import zed.rainxch.core.presentation.utils.TimeZoneChangeSignal

// The tracked apps list: loading, sorting, searching, update checks, review stars and the external import banner.
class LibraryLoader(
    private val installedAppsRepository: InstalledAppsRepository,
    private val syncInstalledAppsUseCase: SyncInstalledAppsUseCase,
    private val tweaksRepository: TweaksRepository,
    externalImportRepository: ExternalImportRepository,
    reviewsRepository: ReviewsRepository,
    private val logger: KomiStoreLogger,
) {
    private val ratings = LibraryRatings(reviewsRepository)
    private val importProposal = ExternalImportProposal(externalImportRepository, tweaksRepository)
    private var lastAutoCheckTimestamp: Long = 0L

    fun load(
        holder: AppsStateHolder,
        onAppsChanged: suspend () -> Unit,
    ) {
        importProposal.observe(holder)
        holder.scope.launch {
            holder.update { it.copy(isLoading = true) }
            try {
                val syncResult = syncInstalledAppsUseCase()
                if (syncResult.isFailure) {
                    logger.error("Sync had issues but continuing: ${syncResult.exceptionOrNull()?.message}")
                }
                combine(
                    installedAppsRepository.getAllInstalledApps(),
                    tweaksRepository.getAppsSortRule(),
                    TimeZoneChangeSignal.revision,
                ) { apps, sortStored, _ ->
                    apps to AppSortRule.fromName(sortStored)
                }.collect { (apps, sortRule) ->
                    showApps(holder, apps, sortRule)
                    onAppsChanged()
                    holder.update { it.withFilteredApps() }
                    ratings.refresh(holder, apps)
                }
            } catch (e: LocalStoreException) {
                logger.error("Failed to load apps: ${e.message}")
                holder.update { it.copy(isLoading = false) }
            }
            autoCheckForUpdatesIfNeeded(holder)
        }
    }

    fun onAction(
        holder: AppsStateHolder,
        action: AppsAction.Library,
    ) {
        when (action) {
            is AppsAction.OnSearchChange -> {
                holder.update { it.copy(searchQuery = action.query).withFilteredApps() }
            }

            is AppsAction.OnSortRuleSelected -> {
                holder.update { it.copy(sortRule = action.sortRule).withFilteredApps() }
                holder.scope.launch { tweaksRepository.setAppsSortRule(action.sortRule.name) }
            }

            AppsAction.OnCheckAllForUpdates -> {
                checkAllForUpdates(holder)
            }

            AppsAction.OnRefresh -> {
                refresh(holder)
            }

            AppsAction.OnLifecycleResume -> {
                autoCheckForUpdatesIfNeeded(holder)
            }

            AppsAction.OnToggleUpToDateSection -> {
                holder.update { it.copy(isUpToDateSectionExpanded = !it.isUpToDateSectionExpanded) }
            }

            AppsAction.OnToggleUpdatesSection -> {
                holder.update { it.copy(isUpdatesSectionExpanded = !it.isUpdatesSectionExpanded) }
            }

            is AppsAction.OnNavigateToRepo -> {
                holder.scope.launch {
                    holder.send(
                        AppsEvent.NavigateToRepo(
                            repoId = action.repoId,
                            sourceHost = action.sourceHost,
                            owner = action.owner,
                            repo = action.repo,
                            packageName = action.packageName,
                        ),
                    )
                }
            }
        }
    }

    fun onAction(
        holder: AppsStateHolder,
        action: AppsAction.ImportProposal,
    ) {
        when (action) {
            AppsAction.OnImportProposalReview -> importProposal.review(holder)
            AppsAction.OnImportProposalDismiss -> importProposal.dismissBanner(holder)
            AppsAction.OnRescanForGithubApps -> importProposal.rescan(holder)
        }
    }

    // Keeps each row's in-flight update state across database emissions.
    private suspend fun showApps(
        holder: AppsStateHolder,
        apps: List<InstalledApp>,
        sortRule: AppSortRule,
    ) {
        val appItems =
            apps
                .map { domainApp ->
                    val app = domainApp.toUi()
                    val existing = holder.current.apps.find { it.installedApp.packageName == app.packageName }
                    app.toAppItem(
                        updateState = existing?.updateState ?: UpdateState.Idle,
                        downloadProgress = existing?.downloadProgress,
                        error = existing?.error,
                    )
                }.sortedWith(appComparator(sortRule))
        holder.update {
            it.copy(
                apps = appItems.map(ratings::withRating).toImmutableList(),
                sortRule = sortRule,
                isLoading = false,
                updateAllButtonEnabled = appItems.any { item -> item.installedApp.isUpdateAvailable },
            )
        }
    }

    private fun autoCheckForUpdatesIfNeeded(holder: AppsStateHolder) {
        val now = System.currentTimeMillis()
        val lastCheck = maxOf(lastAutoCheckTimestamp, oldestTrackedCheck(holder))
        if (now - lastCheck < UPDATE_CHECK_COOLDOWN_MS) {
            logger.debug("Skipping auto-check: last check was ${(now - lastCheck) / MILLIS_PER_SECOND}s ago")
            return
        }
        checkAllForUpdates(holder)
    }

    private fun oldestTrackedCheck(holder: AppsStateHolder): Long =
        holder.current.apps
            .map { it.installedApp }
            .filter { it.updateCheckEnabled }
            .minOfOrNull { it.lastCheckedAt } ?: 0L

    private fun checkAllForUpdates(holder: AppsStateHolder) {
        holder.scope.launch {
            lastAutoCheckTimestamp = System.currentTimeMillis()
            holder.update { it.copy(isCheckingForUpdates = true) }
            try {
                syncInstalledAppsUseCase()
                installedAppsRepository.checkAllForUpdates()
                holder.update { it.copy(lastCheckedTimestamp = System.currentTimeMillis()) }
            } catch (e: LocalStoreException) {
                logger.error("Check all for updates failed: ${e.message}")
            } finally {
                holder.update { it.copy(isCheckingForUpdates = false) }
            }
        }
    }

    private fun refresh(holder: AppsStateHolder) {
        holder.scope.launch {
            holder.update { it.copy(isRefreshing = true) }
            try {
                syncInstalledAppsUseCase()
                installedAppsRepository.checkAllForUpdates()
                val now = System.currentTimeMillis()
                lastAutoCheckTimestamp = now
                holder.update { it.copy(lastCheckedTimestamp = now) }
            } catch (e: LocalStoreException) {
                logger.error("Refresh failed: ${e.message}")
            } finally {
                holder.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private companion object {
        const val UPDATE_CHECK_COOLDOWN_MS = 30 * 60 * 1000L
        const val MILLIS_PER_SECOND = 1000
    }
}
