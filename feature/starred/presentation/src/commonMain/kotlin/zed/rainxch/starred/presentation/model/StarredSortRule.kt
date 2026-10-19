package zed.rainxch.starred.presentation.model

enum class StarredSortRule {
    RecentlyStarred,
    NameAsc,
    StarsDesc,
    ;

    companion object {
        fun fromName(name: String?): StarredSortRule = entries.find { it.name == name } ?: RecentlyStarred
    }
}
