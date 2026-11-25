package zed.rainxch.feed.presentation.marks

data class ViewingMarks(
    val seenIds: Set<Long> = emptySet(),
    val hiddenIds: Set<Long> = emptySet(),
    val isHideSeenEnabled: Boolean = false,
) {
    fun isVisible(repoId: Long): Boolean = repoId !in hiddenIds && (!isHideSeenEnabled || repoId !in seenIds)
}
