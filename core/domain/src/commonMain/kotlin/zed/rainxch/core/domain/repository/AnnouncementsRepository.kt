package zed.rainxch.core.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.announcement.AnnouncementCategory
import zed.rainxch.core.domain.model.announcement.AnnouncementsFeedSnapshot

interface AnnouncementsRepository {
    fun observeFeed(): Flow<AnnouncementsFeedSnapshot>

    suspend fun refresh(): Result<Unit>

    suspend fun dismiss(id: String)

    suspend fun acknowledge(id: String)

    suspend fun setMuted(
        category: AnnouncementCategory,
        muted: Boolean,
    )
}
