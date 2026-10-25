package zed.rainxch.apps.presentation.model

data class AppItem(
    val installedApp: InstalledAppUi,
    val updateState: UpdateState = UpdateState.Idle,
    val downloadProgress: Int? = null,
    val error: String? = null,
    val isBusy: Boolean = false,
    val hasFilter: Boolean = false,
    val hasPin: Boolean = false,
    val canSkipVersion: Boolean = false,
    val versionLabel: String = "",
    val idleVersionLabel: String = "",
    val versionTargetVersion: String? = null,
    val isVersionDowngrade: Boolean = false,
    val ratingAverage: Double? = null,
    val ratingCount: Int = 0,
)
