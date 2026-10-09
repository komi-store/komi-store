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
    // The "to" version the row's line points at, when there is one (the store's latest, or a
    // pending install's parked target). Null means the line shows only the installed version.
    val versionTargetVersion: String? = null,
    // A pending install whose parked target is older than the installed version: the row must
    // flag the backwards move. Always false for rows that are not waiting to install.
    val isVersionDowngrade: Boolean = false,
)
