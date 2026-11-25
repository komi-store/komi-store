package zed.rainxch.search.domain.model

enum class SortBy {
    MostStars,
    MostForks,
    BestMatch,
    RecentlyUpdated,
    RecentlyReleased,
    ;

    fun toGithubSortParam(): String? =
        when (this) {
            MostStars -> "stars"
            MostForks -> "forks"
            BestMatch -> null
            RecentlyUpdated -> "updated"
            RecentlyReleased -> "updated"
        }
}
