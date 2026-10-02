package zed.rainxch.githubstore.app.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface GithubStoreGraph {
    @Serializable
    data object ExploreScreen : GithubStoreGraph

    @Serializable
    data object ChartsScreen : GithubStoreGraph

    @Serializable
    data class SearchScreen(
        val initialPlatform: String? = null,
    ) : GithubStoreGraph

    @Serializable
    data object AuthenticationScreen : GithubStoreGraph

    @Serializable
    data class DetailsScreen(
        val repositoryId: Long = -1L,
        val owner: String = "",
        val repo: String = "",
        val isComingFromUpdate: Boolean = false,
        val sourceHost: String? = null,
        val initialAssetName: String? = null,
        val packageName: String? = null,
    ) : GithubStoreGraph

    @Serializable
    data class DeveloperProfileScreen(
        val username: String,
    ) : GithubStoreGraph

    @Serializable
    data object ProfileGraph : GithubStoreGraph {
        @Serializable
        data object ProfileScreen : GithubStoreGraph
    }

    @Serializable
    data object TweaksScreen : GithubStoreGraph

    @Serializable
    data object AboutScreen : GithubStoreGraph

    @Serializable
    data object LicensesScreen : GithubStoreGraph

    @Serializable
    data object FavouritesScreen : GithubStoreGraph

    @Serializable
    data object StarredReposScreen : GithubStoreGraph

    @Serializable
    data object RecentlyViewedScreen : GithubStoreGraph

    @Serializable
    data object AppsScreen : GithubStoreGraph

    @Serializable
    data object ExternalImportScreen : GithubStoreGraph

    @Serializable
    data object MirrorPickerScreen : GithubStoreGraph

    @Serializable
    data object SkippedUpdatesScreen : GithubStoreGraph

    @Serializable
    data object HiddenRepositoriesScreen : GithubStoreGraph

    @Serializable
    data object WhatsNewHistoryScreen : GithubStoreGraph

    @Serializable
    data object AnnouncementsScreen : GithubStoreGraph

    @Serializable
    data object StarredPickerScreen : GithubStoreGraph

    @Serializable
    data object ImportStarsScreen : GithubStoreGraph

    @Serializable
    data object HostTokensScreen : GithubStoreGraph

    @Serializable
    data class CategoryListScreen(
        val category: String,
    ) : GithubStoreGraph

    @Serializable
    data class DetailsAboutScreen(
        val repositoryId: Long = -1L,
        val owner: String = "",
        val repo: String = "",
        val sourceHost: String? = null,
        val translateTo: String? = null,
    ) : GithubStoreGraph

    @Serializable
    data class DetailsWhatsNewScreen(
        val repositoryId: Long = -1L,
        val owner: String = "",
        val repo: String = "",
        val sourceHost: String? = null,
    ) : GithubStoreGraph

    @Serializable
    data class RepoIssuesScreen(
        val owner: String,
        val repo: String,
    ) : GithubStoreGraph

    @Serializable
    data class RepoIssueDetailScreen(
        val owner: String,
        val repo: String,
        val issueNumber: Int,
    ) : GithubStoreGraph

    @Serializable
    data class RepoSecurityScreen(
        val owner: String,
        val repo: String,
    ) : GithubStoreGraph

    @Serializable
    data class RepoPullsScreen(
        val owner: String,
        val repo: String,
    ) : GithubStoreGraph

    @Serializable
    data class MarkdownViewerScreen(
        val url: String,
    ) : GithubStoreGraph
}
