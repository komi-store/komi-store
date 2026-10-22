package zed.rainxch.core.domain.model.announcement

data class AnnouncementsFeedSnapshot(
    val items: List<Announcement>,
    val dismissedIds: Set<String>,
    val acknowledgedIds: Set<String>,
    val mutedCategories: Set<AnnouncementCategory>,
    val lastFetchedAtMillis: Long,
    val lastRefreshFailed: Boolean,
) {
    val visibleItems: List<Announcement>
        get() =
            items
                .asSequence()
                .filter { it.id !in dismissedIds }
                .filter { it.category !in mutedCategories || !it.category.isMutable }
                .toList()

    val unreadCount: Int
        get() = visibleItems.count { it.id !in acknowledgedIds }

    val pendingCriticalAcknowledgment: Announcement?
        get() =
            visibleItems.firstOrNull {
                it.severity == AnnouncementSeverity.CRITICAL &&
                    it.requiresAcknowledgment &&
                    it.id !in acknowledgedIds
            }
}
