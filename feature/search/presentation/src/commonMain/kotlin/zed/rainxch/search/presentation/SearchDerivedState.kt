package zed.rainxch.search.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.model.DiscoveryRepositoryUi
import zed.rainxch.search.presentation.model.ProgrammingLanguageUi
import zed.rainxch.search.presentation.model.SearchSourceUi
import zed.rainxch.search.presentation.model.SortByUi

internal fun SearchState.withDerived(): SearchState {
    val visible = visibleRepos()
    val isIdle = !isLoading && !isLoadingMore
    return copy(
        visibleRepos = visible,
        activeFilterCount = activeFilterCount(),
        canLoadMore = isIdle && hasMorePages,
        areResultsHiddenBySeen = repositories.isNotEmpty() && visible.isEmpty() && isHideSeenEnabled,
        isHistoryVisible = query.isBlank() && repositories.isEmpty() && recentSearches.isNotEmpty() && !isLoading,
        isNoResultsVisible = isIdle && !hasMorePages && errorMessage == null && repositories.isEmpty() && query.isNotBlank(),
    )
}

private fun SearchState.visibleRepos(): ImmutableList<DiscoveryRepositoryUi> {
    val needsHideSeenFilter = isHideSeenEnabled && seenRepoIds.isNotEmpty()
    if (hiddenRepoIds.isEmpty() && !needsHideSeenFilter) return repositories
    return repositories
        .filter { repo ->
            repo.repository.id !in hiddenRepoIds &&
                (!needsHideSeenFilter || repo.repository.id !in seenRepoIds)
        }.toImmutableList()
}

private fun SearchState.activeFilterCount(): Int =
    listOf(
        selectedSource != SearchSourceUi.GitHub,
        selectedSearchPlatform != DiscoveryPlatform.All,
        selectedLanguage != ProgrammingLanguageUi.All,
        selectedSortBy != SortByUi.BestMatch,
    ).count { it }
