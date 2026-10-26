package zed.rainxch.core.data.repository

import zed.rainxch.core.data.local.db.AppDatabase
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.dao.UpdateHistoryDao
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.installedapps.AppTrackingPreferences
import zed.rainxch.core.domain.repository.installedapps.InstalledAppUpdateChecks
import zed.rainxch.core.domain.repository.installedapps.InstalledAppsReader
import zed.rainxch.core.domain.repository.installedapps.InstalledAppsWriter
import zed.rainxch.core.domain.repository.installedapps.PendingInstallStore

class InstalledAppsRepositoryImpl internal constructor(
    database: AppDatabase,
    installedAppsDao: InstalledAppDao,
    historyDao: UpdateHistoryDao,
    updateChecker: InstalledAppUpdateChecker,
) : InstalledAppsRepository,
    InstalledAppsReader by InstalledAppsReaderImpl(installedAppsDao),
    InstalledAppsWriter by InstalledAppsWriterImpl(database, installedAppsDao, historyDao),
    InstalledAppUpdateChecks by updateChecker,
    PendingInstallStore by PendingInstallStoreImpl(installedAppsDao),
    AppTrackingPreferences by AppTrackingPreferencesImpl(installedAppsDao, updateChecker)
