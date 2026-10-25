package zed.rainxch.apps.presentation.model

// A version line: the version as it stands, and the version an update or install brings, if any.
data class VersionLabel(
    val text: String,
    val target: String? = null,
)
