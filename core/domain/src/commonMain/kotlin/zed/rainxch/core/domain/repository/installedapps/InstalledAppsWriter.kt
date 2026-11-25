package zed.rainxch.core.domain.repository.installedapps

import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.InstalledBuild

interface InstalledAppsWriter {
    suspend fun saveInstalledApp(app: InstalledApp)

    suspend fun deleteInstalledApp(packageName: String)

    suspend fun updateAppVersion(
        packageName: String,
        build: InstalledBuild,
        isPendingInstall: Boolean = true,
    )

    suspend fun clearInstallBinding(packageName: String)

    suspend fun updateApp(app: InstalledApp)

    suspend fun updateInstalledVersion(
        packageName: String,
        installedVersion: String,
        installedVersionName: String?,
        installedVersionCode: Long,
        isUpdateAvailable: Boolean,
    )

    suspend fun updateSigningFingerprint(
        packageName: String,
        signingFingerprint: String,
    )

    suspend fun <R> executeInTransaction(block: suspend () -> R): R
}
