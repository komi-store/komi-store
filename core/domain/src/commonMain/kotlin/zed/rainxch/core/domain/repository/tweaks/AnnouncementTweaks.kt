package zed.rainxch.core.domain.repository.tweaks

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.announcement.AnnouncementCategory

interface AnnouncementTweaks {
    fun getAnnouncementsDismissedIds(): Flow<Set<String>>

    suspend fun addAnnouncementDismissedId(id: String)

    fun getAnnouncementsAcknowledgedIds(): Flow<Set<String>>

    suspend fun addAnnouncementAcknowledgedId(id: String)

    fun getAnnouncementsMutedCategories(): Flow<Set<AnnouncementCategory>>

    suspend fun setAnnouncementCategoryMuted(
        category: AnnouncementCategory,
        muted: Boolean,
    )

    fun getAnnouncementsLastFetchedAt(): Flow<Long>

    suspend fun setAnnouncementsLastFetchedAt(epochMillis: Long)
}
