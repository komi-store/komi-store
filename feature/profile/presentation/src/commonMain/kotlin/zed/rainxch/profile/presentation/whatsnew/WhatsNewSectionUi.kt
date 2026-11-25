package zed.rainxch.profile.presentation.whatsnew

import kotlinx.collections.immutable.ImmutableList
import zed.rainxch.core.domain.model.announcement.WhatsNewSectionType

data class WhatsNewSectionUi(
    val type: WhatsNewSectionType,
    val bullets: ImmutableList<String>,
)
