package zed.rainxch.core.data.repository

import co.touchlab.kermit.Logger
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.entities.InstalledAppVariantPin
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.SystemPackagesException
import zed.rainxch.core.domain.repository.installedapps.AppTrackingPreferences
import zed.rainxch.core.domain.repository.installedapps.InstalledAppUpdateChecks

internal class AppTrackingPreferencesImpl(
    private val installedAppsDao: InstalledAppDao,
    private val updateChecks: InstalledAppUpdateChecks,
) : AppTrackingPreferences {
    override suspend fun setIncludePreReleases(
        packageName: String,
        enabled: Boolean,
    ) {
        localStore { installedAppsDao.updateIncludePreReleases(packageName, enabled) }
    }

    override suspend fun setUpdateCheckEnabled(
        packageName: String,
        enabled: Boolean,
    ) {
        localStore { installedAppsDao.updateUpdateCheckEnabled(packageName, enabled) }
        if (enabled) {
            recheckAfterChange(packageName, "enabled update checks")
        } else {
            localStore { installedAppsDao.clearUpdateMetadata(packageName, System.currentTimeMillis()) }
        }
    }

    override suspend fun setAssetFilter(
        packageName: String,
        regex: String?,
        fallbackToOlderReleases: Boolean,
    ) {
        val normalized = regex?.trim()?.takeIf { it.isNotEmpty() }
        localStore {
            installedAppsDao.updateAssetFilter(
                packageName = packageName,
                regex = normalized,
                fallback = fallbackToOlderReleases,
            )
        }
        recheckAfterChange(packageName, "a new asset filter")
    }

    override suspend fun setPreferredVariant(
        packageName: String,
        variant: String?,
        tokens: String?,
        glob: String?,
        pickedIndex: Int?,
        siblingCount: Int?,
    ) {
        localStore {
            installedAppsDao.updatePreferredVariant(
                InstalledAppVariantPin(
                    packageName = packageName,
                    preferredAssetVariant = variant?.trim()?.takeIf { it.isNotEmpty() },
                    preferredAssetTokens = tokens?.trim()?.takeIf { it.isNotEmpty() },
                    assetGlobPattern = glob?.trim()?.takeIf { it.isNotEmpty() },
                    pickedAssetIndex = pickedIndex,
                    pickedAssetSiblingCount = siblingCount?.takeIf { it > 0 },
                ),
            )
        }
        recheckAfterChange(packageName, "a new variant")
    }

    override suspend fun clearPreferredVariant(packageName: String) {
        setPreferredVariant(
            packageName = packageName,
            variant = null,
            tokens = null,
            glob = null,
            pickedIndex = null,
            siblingCount = null,
        )
    }

    override suspend fun setSkippedReleaseTag(
        packageName: String,
        tag: String?,
    ) {
        localStore { installedAppsDao.setSkippedReleaseTag(packageName, tag?.takeIf { it.isNotBlank() }) }
    }

    // The preference is saved either way; a failed re-check leaves the next scheduled check to pick it up.
    private suspend fun recheckAfterChange(
        packageName: String,
        change: String,
    ) {
        try {
            updateChecks.checkForUpdates(packageName)
        } catch (e: LocalStoreException) {
            Logger.w(e) { "Saved $change for $packageName but the immediate re-check failed" }
        } catch (e: SystemPackagesException) {
            Logger.w(e) { "Saved $change for $packageName but the immediate re-check failed" }
        }
    }
}
