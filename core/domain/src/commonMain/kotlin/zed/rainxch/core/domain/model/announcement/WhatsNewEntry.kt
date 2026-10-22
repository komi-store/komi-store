package zed.rainxch.core.domain.model.announcement

data class WhatsNewEntry(
    val versionCode: Int,
    val versionName: String,
    val releaseDate: String,
    val sections: List<WhatsNewSection>,
    val showAsSheet: Boolean = true,
)
