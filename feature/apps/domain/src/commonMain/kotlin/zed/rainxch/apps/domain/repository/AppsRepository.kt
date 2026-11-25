package zed.rainxch.apps.domain.repository

import zed.rainxch.core.domain.model.installation.InstalledApp

interface AppsRepository :
    RepoLookup,
    AppLinking,
    AppsBackup {
    suspend fun openApp(
        installedApp: InstalledApp,
        onCantLaunchApp: () -> Unit = { },
    )
}
