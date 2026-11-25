package zed.rainxch.apps.presentation.updates

import zed.rainxch.apps.presentation.markDownloadedUpdate
import zed.rainxch.apps.presentation.model.InstalledAppUi
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.DownloadOrchestrator
import zed.rainxch.core.domain.system.InstallOutcome
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.SystemInstallSerializer

// The install half of an update: record the downloaded release, hand the file to the installer, settle the park.
class UpdateInstalls(
    private val installer: Installer,
    private val installedAppsRepository: InstalledAppsRepository,
    private val systemInstallSerializer: SystemInstallSerializer,
    private val downloadOrchestrator: DownloadOrchestrator,
    private val logger: KomiStoreLogger,
) {
    suspend fun recordDownloaded(
        app: InstalledAppUi,
        ready: UpdateDownloadResult.Ready,
    ) {
        val apkInfo = installer.getApkInfoExtractor().extractPackageInfo(ready.filePath)
        val currentApp = installedAppsRepository.getAppByPackage(app.packageName)
        if (currentApp != null) {
            installedAppsRepository.updateApp(
                currentApp.markDownloadedUpdate(
                    download = ready.download,
                    versionName = apkInfo?.versionName,
                    versionCode = apkInfo?.versionCode,
                ),
            )
        } else {
            installedAppsRepository.updatePendingStatus(app.packageName, true)
            logger.debug("Marked ${app.packageName} as pending install")
        }
    }

    suspend fun install(
        packageName: String,
        filePath: String,
        extension: String,
        waitForInstallGate: Boolean,
    ) {
        val outcome =
            recoverUpdateFailure({ e ->
                systemInstallSerializer.markCompleted(packageName)
                installedAppsRepository.updatePendingStatus(packageName, false)
                throw e
            }) {
                systemInstallSerializer.awaitFreeAndMarkPending(
                    packageName,
                    timeoutMs = if (waitForInstallGate) SystemInstallSerializer.DEFAULT_TIMEOUT_MS else 0L,
                )
                installer.install(filePath, extension)
            }

        downloadOrchestrator.dismiss(packageName)
        // A delegated install keeps its parked file, so the row offers Install until the system proves the new version landed.
        if (outcome == InstallOutcome.COMPLETED) {
            try {
                installedAppsRepository.setPendingInstallFilePath(packageName, null)
            } catch (e: LocalStoreException) {
                logger.error("Failed to clear pending install file path: ${e.message}")
            }
        }
    }
}
