package zed.rainxch.core.domain.repository

import zed.rainxch.core.domain.repository.installedapps.AppTrackingPreferences
import zed.rainxch.core.domain.repository.installedapps.InstalledAppUpdateChecks
import zed.rainxch.core.domain.repository.installedapps.InstalledAppsReader
import zed.rainxch.core.domain.repository.installedapps.InstalledAppsWriter
import zed.rainxch.core.domain.repository.installedapps.PendingInstallStore

interface InstalledAppsRepository :
    InstalledAppsReader,
    InstalledAppsWriter,
    InstalledAppUpdateChecks,
    PendingInstallStore,
    AppTrackingPreferences
