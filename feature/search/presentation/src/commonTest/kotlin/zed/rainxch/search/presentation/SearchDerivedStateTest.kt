package zed.rainxch.search.presentation

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.model.DiscoveryRepositoryUi
import zed.rainxch.core.presentation.model.GithubRepoSummaryUi
import zed.rainxch.core.presentation.model.GithubUserUi
import zed.rainxch.search.presentation.model.ProgrammingLanguageUi
import zed.rainxch.search.presentation.model.SortByUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchDerivedStateTest {
    @Test
    fun hidden_repos_and_seen_repos_under_hide_seen_leave_the_visible_list() {
        val state =
            SearchState(
                repositories = persistentListOf(row(1), row(2), row(3)),
                hiddenRepoIds = persistentSetOf(1),
                seenRepoIds = persistentSetOf(2),
                isHideSeenEnabled = true,
            ).withDerived()

        assertEquals(listOf(3L), state.visibleRepos.map { it.repository.id })
    }

    @Test
    fun seen_repos_stay_visible_while_hide_seen_is_off() {
        val state =
            SearchState(
                repositories = persistentListOf(row(1), row(2)),
                seenRepoIds = persistentSetOf(2),
            ).withDerived()

        assertEquals(listOf(1L, 2L), state.visibleRepos.map { it.repository.id })
    }

    @Test
    fun every_filter_off_its_default_counts_once() {
        val state =
            SearchState(
                selectedSearchPlatform = DiscoveryPlatform.Android,
                selectedLanguage = ProgrammingLanguageUi.Kotlin,
                selectedSortBy = SortByUi.MostStars,
            ).withDerived()

        assertEquals(3, state.activeFilterCount)
    }

    @Test
    fun a_finished_search_without_results_shows_no_results() {
        val state = SearchState(query = "komi", hasMorePages = false).withDerived()

        assertTrue(state.isNoResultsVisible)
        assertFalse(state.canLoadMore)
    }

    @Test
    fun results_all_hidden_by_seen_are_flagged() {
        val state =
            SearchState(
                repositories = persistentListOf(row(1)),
                seenRepoIds = persistentSetOf(1),
                isHideSeenEnabled = true,
            ).withDerived()

        assertTrue(state.areResultsHiddenBySeen)
        assertTrue(state.canLoadMore)
    }

    @Test
    fun recent_searches_show_only_before_a_query() {
        val idle = SearchState(recentSearches = persistentListOf("komi")).withDerived()
        val typing = SearchState(query = "ko", recentSearches = persistentListOf("komi")).withDerived()

        assertTrue(idle.isHistoryVisible)
        assertFalse(typing.isHistoryVisible)
    }

    private fun row(id: Long): DiscoveryRepositoryUi =
        DiscoveryRepositoryUi(
            isInstalled = false,
            isUpdateAvailable = false,
            isFavourite = false,
            isStarred = false,
            repository =
                GithubRepoSummaryUi(
                    id = id,
                    name = "repo$id",
                    fullName = "owner/repo$id",
                    owner = GithubUserUi(id = 1, login = "owner", avatarUrl = "", htmlUrl = ""),
                    description = null,
                    defaultBranch = "main",
                    htmlUrl = "",
                    stargazersCount = 0,
                    forksCount = 0,
                    language = null,
                    topics = null,
                    releasesUrl = "",
                    updatedAt = "",
                ),
        )
}
