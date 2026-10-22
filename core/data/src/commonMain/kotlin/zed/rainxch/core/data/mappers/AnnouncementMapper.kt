package zed.rainxch.core.data.mappers

import zed.rainxch.core.data.dto.AnnouncementDto
import zed.rainxch.core.domain.model.announcement.Announcement
import zed.rainxch.core.domain.model.announcement.AnnouncementCategory
import zed.rainxch.core.domain.model.announcement.AnnouncementIconHint
import zed.rainxch.core.domain.model.announcement.AnnouncementSeverity
import kotlin.time.Instant

fun AnnouncementDto.toDomain(
    fullLocale: String,
    primaryLocale: String,
): Announcement? {
    val publishedInstant = publishedAt.parseInstantOrNull() ?: return null
    val expiresInstant = expiresAt?.parseInstantOrNull()

    val variant = i18n[fullLocale] ?: i18n[primaryLocale]
    val resolvedTitle = variant?.title?.takeIf { it.isNotBlank() } ?: title
    val resolvedBody = variant?.body?.takeIf { it.isNotBlank() } ?: body
    val resolvedCtaUrl = variant?.ctaUrl?.takeIf { it.isNotBlank() } ?: ctaUrl
    val resolvedCtaLabel = variant?.ctaLabel?.takeIf { it.isNotBlank() } ?: ctaLabel

    return Announcement(
        id = id,
        publishedAt = publishedInstant,
        expiresAt = expiresInstant,
        severity = severity.parseSeverity(),
        category = category.parseCategory(),
        title = resolvedTitle,
        body = resolvedBody,
        ctaUrl = resolvedCtaUrl,
        ctaLabel = resolvedCtaLabel,
        dismissible = dismissible,
        requiresAcknowledgment = requiresAcknowledgment,
        minVersionCode = minVersionCode,
        maxVersionCode = maxVersionCode,
        platforms = platforms?.map { it.uppercase() }?.toSet(),
        installerTypes = installerTypes?.map { it.uppercase() }?.toSet(),
        iconHint = iconHint?.parseIconHint(),
    )
}

private fun String.parseInstantOrNull(): Instant? = runCatching { Instant.parse(this) }.getOrNull()

private fun String.parseSeverity(): AnnouncementSeverity =
    runCatching { AnnouncementSeverity.valueOf(trim().uppercase()) }
        .getOrDefault(AnnouncementSeverity.INFO)

private fun String.parseCategory(): AnnouncementCategory =
    runCatching { AnnouncementCategory.valueOf(trim().uppercase()) }
        .getOrDefault(AnnouncementCategory.NEWS)

private fun String.parseIconHint(): AnnouncementIconHint? = runCatching { AnnouncementIconHint.valueOf(trim().uppercase()) }.getOrNull()
