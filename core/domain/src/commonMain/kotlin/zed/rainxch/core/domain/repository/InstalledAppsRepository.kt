package zed.rainxch.core.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.ParkedInstallDisposal
import zed.rainxch.core.domain.model.smart_detect.MatchingPreview

interface InstalledAppsRepository {
    fun getAllInstalledApps(): Flow<List<InstalledApp>>

    fun getAppsWithUpdates(): Flow<List<InstalledApp>>

    fun getUpdateCount(): Flow<Int>

    suspend fun getAppByPackage(packageName: String): InstalledApp?

    suspend fun getAppByRepoId(repoId: Long): InstalledApp?

    fun getAppByRepoIdAsFlow(repoId: Long): Flow<InstalledApp?>

    suspend fun getAppsByRepoId(repoId: Long): List<InstalledApp>

    fun getAppsByRepoIdAsFlow(repoId: Long): Flow<List<InstalledApp>>

    suspend fun isAppInstalled(repoId: Long): Boolean

    suspend fun saveInstalledApp(app: InstalledApp)

    suspend fun deleteInstalledApp(packageName: String)

    suspend fun checkForUpdates(packageName: String): Boolean

    suspend fun checkAllForUpdates()

    suspend fun updateAppVersion(
        packageName: String,
        newTag: String,
        newReleaseId: Long?,
        newAssetId: Long?,
        newAssetDigest: String?,
        newAssetName: String?,
        newAssetUrl: String?,
        newVersionName: String,
        newVersionCode: Long,
        signingFingerprint: String?,
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

    suspend fun updatePendingStatus(
        packageName: String,
        isPending: Boolean,
    )

    suspend fun setIncludePreReleases(
        packageName: String,
        enabled: Boolean,
    )

    suspend fun setUpdateCheckEnabled(
        packageName: String,
        enabled: Boolean,
    )

    suspend fun setAssetFilter(
        packageName: String,
        regex: String?,
        fallbackToOlderReleases: Boolean,
    )

    suspend fun setPreferredVariant(
        packageName: String,
        variant: String?,
        tokens: String? = null,
        glob: String? = null,
        pickedIndex: Int? = null,
        siblingCount: Int? = null,
    )

    suspend fun clearPreferredVariant(packageName: String)

    suspend fun setSkippedReleaseTag(
        packageName: String,
        tag: String?,
    )

    fun getAppsWithSkippedReleaseTag(): Flow<List<InstalledApp>>

    suspend fun setPendingInstallFilePath(
        packageName: String,
        path: String?,
        version: String? = null,
        assetName: String? = null,
    )

    // Both metadata parameters are required on purpose: the write binds them unconditionally, so
    // omitting one would silently erase a parked version or asset name that tag resolution reads
    // back later. Clearing is what setPendingInstallFilePath above is for.
    suspend fun markAwaitingInstall(
        packageName: String,
        path: String,
        version: String?,
        assetName: String?,
    )

    // Clears the park AND deletes the parked file from disk (best-effort: the installer may
    // already have taken it). [expectedPath] is the path the caller saw when it decided to
    // discard: only that file is deleted, and only while the row still names it, so a park
    // written after the decision (a download finishing mid-discard) survives. If the file
    // cannot be deleted and demonstrably survives, the pointer is kept so a later sync retries.
    // The result tells a caller that is dropping the row whether it may: Retained means the row
    // is still the only handle to a live park and must stay. The verdict is a point-in-time
    // read, and a park written between the verdict and the caller's row drop is accepted, like
    // the same-path reuse this contract's guard cannot tell apart. For "drop the pointer only"
    // use setPendingInstallFilePath above.
    suspend fun discardParkedInstall(
        packageName: String,
        expectedPath: String?,
    ): ParkedInstallDisposal

    suspend fun previewMatchingAssets(
        owner: String,
        repo: String,
        regex: String?,
        includePreReleases: Boolean,
        fallbackToOlderReleases: Boolean,
    ): MatchingPreview

    suspend fun <R> executeInTransaction(block: suspend () -> R): R
}