package zed.rainxch.devprofile.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import zed.rainxch.devprofile.domain.model.RepoFilterType
import zed.rainxch.devprofile.domain.model.RepoSortType
import zed.rainxch.devprofile.presentation.model.ContributionCalendarUi
import zed.rainxch.devprofile.presentation.model.DeveloperProfileUi
import zed.rainxch.devprofile.presentation.model.DeveloperRepositoryUi

data class DeveloperProfileState(
    val username: String = "",
    val profile: DeveloperProfileUi? = null,
    val repositories: ImmutableList<DeveloperRepositoryUi> = persistentListOf(),
    val filteredRepositories: ImmutableList<DeveloperRepositoryUi> = persistentListOf(),
    val isLoading: Boolean = false,
    val isLoadingRepos: Boolean = false,
    val errorMessage: String? = null,
    val currentFilter: RepoFilterType = RepoFilterType.WITH_RELEASES,
    val currentSort: RepoSortType = RepoSortType.UPDATED,
    val searchQuery: String = "",
    val contributions: ContributionCalendarUi? = null,
    val isLoadingContributions: Boolean = false,
)
