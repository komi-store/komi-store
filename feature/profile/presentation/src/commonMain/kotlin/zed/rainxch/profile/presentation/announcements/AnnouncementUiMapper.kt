package zed.rainxch.profile.presentation.announcements

import zed.rainxch.core.domain.model.announcement.Announcement

internal fun Announcement.toAnnouncementUi(): AnnouncementUi =
    AnnouncementUi(
        id = id,
        severity = severity,
        category = category,
        iconHint = iconHint,
        title = title,
        body = body,
        ctaUrl = ctaUrl,
        ctaLabel = ctaLabel,
        dismissible = dismissible,
        requiresAcknowledgment = requiresAcknowledgment,
    )
