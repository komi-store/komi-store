package zed.rainxch.apps.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.installation.InstalledApp

interface AppsRepository :
    RepoLookup,
    AppLinking,
    AppsBackup {
    suspend fun getApps(): Flow<List<InstalledApp>>

    suspend fun openApp(
        installedApp: InstalledApp,
        onCantLaunchApp: () -> Unit = { },
    )
}
