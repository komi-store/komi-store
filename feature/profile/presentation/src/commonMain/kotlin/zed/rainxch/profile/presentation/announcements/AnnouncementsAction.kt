package zed.rainxch.profile.presentation.announcements

import zed.rainxch.core.domain.model.announcement.Announcement
import zed.rainxch.core.domain.model.announcement.AnnouncementCategory

sealed interface AnnouncementsAction {
    data object OnRefresh : AnnouncementsAction

    data object OnOpenMuteSheet : AnnouncementsAction

    data object OnDismissMuteSheet : AnnouncementsAction

    data object OnEnterScreen : AnnouncementsAction

    data class OnCtaClick(
        val announcement: Announcement,
    ) : AnnouncementsAction

    data class OnDismissClick(
        val announcement: Announcement,
    ) : AnnouncementsAction

    data class OnAcknowledgeClick(
        val announcement: Announcement,
    ) : AnnouncementsAction

    data class OnToggleExpand(
        val announcementId: String,
    ) : AnnouncementsAction

    data class OnToggleMute(
        val category: AnnouncementCategory,
        val muted: Boolean,
    ) : AnnouncementsAction
}
