package zed.rainxch.apps.presentation.updates

import zed.rainxch.apps.presentation.model.InstalledAppUi
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.Installer

// Starts from the release and asset the last update check stored; a fresh lookup only when none is stored or it failed.
class UpdateTargetResolver(
    private val installedAppsRepository: InstalledAppsRepository,
    private val installer: Installer,
    private val logger: KomiStoreLogger,
) {
    private val storedMatchFailures = mutableSetOf<String>()

    // Null when no release resolves, which a failed fetch's empty window also gives.
    suspend fun resolve(app: InstalledAppUi): UpdateTarget? = storedTarget(app.packageName) ?: lookUp(app)

    fun storedMatchFailed(packageName: String) {
        storedMatchFailures += packageName
    }

    private suspend fun storedTarget(packageName: String): UpdateTarget? {
        if (packageName in storedMatchFailures) return null
        val row =
            try {
                installedAppsRepository.getAppByPackage(packageName)
            } catch (e: LocalStoreException) {
                logger.warn("Could not read the stored update match for $packageName: ${e.message}")
                null
            } ?: return null
        val tag = row.latestVersion ?: return null
        val name = row.latestAssetName?.takeIf { installer.isAssetInstallable(it) } ?: return null
        val url = row.latestAssetUrl?.takeIf { it.isNotBlank() } ?: return null
        val assetId = row.latestAssetId ?: return null
        return UpdateTarget(
            tag = tag,
            releaseId = row.latestReleaseId,
            asset =
                GithubAsset(
                    id = assetId,
                    name = name,
                    contentType = "application/octet-stream",
                    size = row.latestAssetSize ?: 0L,
                    downloadUrl = url,
                    digest = row.latestAssetDigest,
                ),
            fromStoredMatch = true,
        )
    }

    private suspend fun lookUp(app: InstalledAppUi): UpdateTarget? {
        val resolution =
            try {
                installedAppsRepository.resolveTrackedRelease(app.packageName)
            } catch (e: LocalStoreException) {
                logger.error("Failed to resolve the tracked release: ${e.message}")
                throw ReleaseLookupException(e)
            }
        return resolution?.let {
            UpdateTarget(
                tag = it.release.tagName,
                releaseId = it.release.id,
                asset = it.primaryAsset,
                fromStoredMatch = false,
            )
        }
    }

    private class ReleaseLookupException(
        cause: LocalStoreException,
    ) : IllegalStateException("Failed to fetch latest release: ${cause.message}", cause)
}
