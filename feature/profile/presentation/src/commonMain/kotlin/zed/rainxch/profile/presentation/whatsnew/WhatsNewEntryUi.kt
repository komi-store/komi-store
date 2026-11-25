package zed.rainxch.profile.presentation.whatsnew

import kotlinx.collections.immutable.ImmutableList

data class WhatsNewEntryUi(
    val versionCode: Int,
    val versionName: String,
    val releaseDate: String,
    val sections: ImmutableList<WhatsNewSectionUi>,
)
