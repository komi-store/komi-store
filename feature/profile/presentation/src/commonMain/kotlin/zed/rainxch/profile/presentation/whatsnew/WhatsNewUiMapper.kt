package zed.rainxch.profile.presentation.whatsnew

import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.core.domain.model.announcement.WhatsNewEntry
import zed.rainxch.core.domain.model.announcement.WhatsNewSection

internal fun WhatsNewEntry.toWhatsNewEntryUi(): WhatsNewEntryUi =
    WhatsNewEntryUi(
        versionCode = versionCode,
        versionName = versionName,
        releaseDate = releaseDate,
        sections = sections.map { it.toWhatsNewSectionUi() }.toImmutableList(),
    )

private fun WhatsNewSection.toWhatsNewSectionUi(): WhatsNewSectionUi =
    WhatsNewSectionUi(
        type = type,
        bullets = bullets.toImmutableList(),
    )
