package zed.rainxch.details.presentation.model

import zed.rainxch.core.domain.model.installation.InstallSource

data class InstalledAppUi(
    val packageName: String,
    val appName: String,
    val installedVersion: String,
    val latestVersion: String?,
    val isPendingInstall: Boolean,
    val isUpdateAvailable: Boolean,
    val installSource: InstallSource,
    val includePreReleases: Boolean,
    val latestBuildDiffers: Boolean,
)
