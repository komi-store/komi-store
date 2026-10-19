package zed.rainxch.favourites.presentation.model

enum class FavouritesSortRule {
    RecentlyAdded,
    NameAsc,
    ;

    companion object {
        fun fromName(name: String?): FavouritesSortRule = entries.find { it.name == name } ?: RecentlyAdded
    }
}
