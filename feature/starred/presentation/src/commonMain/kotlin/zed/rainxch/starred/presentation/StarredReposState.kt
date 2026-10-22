package zed.rainxch.starred.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import zed.rainxch.starred.presentation.model.StarredRepositoryUi
import zed.rainxch.starred.presentation.model.StarredSortRule

data class StarredReposState(
    val starredRepositories: ImmutableList<StarredRepositoryUi> = persistentListOf(),
    val filteredRepositories: ImmutableList<StarredRepositoryUi> = persistentListOf(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null,
    val lastSyncTime: Long? = null,
    val lastSyncSubtitle: String? = null,
    val isAuthenticated: Boolean = false,
    val searchQuery: String = "",
    val sortRule: StarredSortRule = StarredSortRule.RecentlyStarred,
    val showAllStarred: Boolean = false,
    val notInstallableCount: Int = 0,
)
