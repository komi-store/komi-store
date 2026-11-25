package zed.rainxch.apps.data.repository

import zed.rainxch.apps.domain.repository.AppLinking
import zed.rainxch.apps.domain.repository.AppsBackup
import zed.rainxch.apps.domain.repository.AppsRepository
import zed.rainxch.apps.domain.repository.RepoLookup
import zed.rainxch.core.domain.helpers.AppLauncher
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.installation.InstalledApp

class AppsRepositoryImpl internal constructor(
    private val appLauncher: AppLauncher,
    private val logger: KomiStoreLogger,
    lookup: RepoLookup,
    linking: AppLinking,
    backup: AppsBackup,
) : AppsRepository,
    RepoLookup by lookup,
    AppLinking by linking,
    AppsBackup by backup {
    override suspend fun openApp(
        installedApp: InstalledApp,
        onCantLaunchApp: () -> Unit,
    ) {
        val canLaunch = appLauncher.canLaunchApp(installedApp)

        if (canLaunch) {
            appLauncher
                .launchApp(installedApp)
                .onFailure { error ->
                    logger.error("Failed to launch app: ${error.message}")
                    onCantLaunchApp()
                }
        } else {
            onCantLaunchApp()
        }
    }
}
