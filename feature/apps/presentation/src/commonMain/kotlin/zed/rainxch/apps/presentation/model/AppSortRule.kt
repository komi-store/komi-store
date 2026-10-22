package zed.rainxch.apps.presentation.model

enum class AppSortRule {
    UpdatesFirst,
    RecentlyUpdated,
    Name,
    ;

    companion object {
        fun fromName(name: String?): AppSortRule = entries.find { it.name == name } ?: UpdatesFirst
    }
}
