package zed.rainxch.profile.presentation.announcements

import zed.rainxch.core.domain.model.announcement.AnnouncementCategory
import zed.rainxch.core.domain.model.announcement.AnnouncementIconHint
import zed.rainxch.core.domain.model.announcement.AnnouncementSeverity

data class AnnouncementUi(
    val id: String,
    val severity: AnnouncementSeverity,
    val category: AnnouncementCategory,
    val iconHint: AnnouncementIconHint?,
    val title: String,
    val body: String,
    val ctaUrl: String?,
    val ctaLabel: String?,
    val dismissible: Boolean,
    val requiresAcknowledgment: Boolean,
)
