package zed.rainxch.apps.presentation.mappers

import org.jetbrains.compose.resources.getString
import zed.rainxch.apps.presentation.model.AppItem
import zed.rainxch.apps.presentation.model.InstalledAppUi
import zed.rainxch.apps.presentation.model.UpdateState
import zed.rainxch.apps.presentation.resolveVersionLine
import zed.rainxch.core.presentation.utils.formatEpochDate
import zed.rainxch.core.presentation.utils.formatIsoDateOrRaw
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.apps_version_dated
import zed.rainxch.githubstore.core.presentation.res.apps_version_update

fun computeIsBusy(isPendingInstall: Boolean, updateState: UpdateState): Boolean =
    isPendingInstall ||
        updateState is UpdateState.Downloading ||
        updateState is UpdateState.Installing ||
        updateState is UpdateState.CheckingUpdate

suspend fun InstalledAppUi.toAppItem(
    updateState: UpdateState = UpdateState.Idle,
    downloadProgress: Int? = null,
    error: String? = null,
): AppItem {
    val canSkipVersion = isUpdateAvailable &&
        !(latestVersion ?: latestVersionName).isNullOrBlank()

    // A pending install's "to" is the parked version, not the store's latest: the two can differ
    // when the download is a rollback, and the line has to show which way it goes.
    val versionLine =
        resolveVersionLine(
            installedVersion = installedVersion,
            isPendingInstall = isPendingInstall,
            pendingInstallVersion = pendingInstallVersion,
            latestVersion = latestVersion,
        )
    val idleLine =
        resolveVersionLine(
            installedVersion = installedVersion,
            isPendingInstall = isPendingInstall,
            pendingInstallVersion = pendingInstallVersion,
            latestVersion = null,
        )

    return AppItem(
        installedApp = this,
        updateState = updateState,
        downloadProgress = downloadProgress,
        error = error,
        isBusy = computeIsBusy(isPendingInstall, updateState),
        hasFilter = !assetFilterRegex.isNullOrBlank() || fallbackToOlderReleases,
        hasPin = !preferredAssetVariant.isNullOrBlank(),
        canSkipVersion = canSkipVersion,
        versionLabel = buildVersionLabel(
            installedVersion = installedVersion,
            targetVersion = versionLine.targetVersion,
            // The store's latest carries a publish date; a parked target has none of its own.
            targetPublishedAt = if (isPendingInstall) null else latestReleasePublishedAt,
            lastUpdatedAt = lastUpdatedAt,
        ),
        idleVersionLabel = buildVersionLabel(
            installedVersion = installedVersion,
            targetVersion = idleLine.targetVersion,
            targetPublishedAt = null,
            lastUpdatedAt = lastUpdatedAt,
        ),
        versionTargetVersion = versionLine.targetVersion,
        isVersionDowngrade = versionLine.isDowngrade,
    )
}

private suspend fun buildVersionLabel(
    installedVersion: String,
    targetVersion: String?,
    targetPublishedAt: String?,
    lastUpdatedAt: Long,
): String {
    val displayDate = if (targetVersion != null) {
        // Release publish times can arrive as a bare date. Formatting one through the local
        // timezone is what shifted it a day back west of UTC, so this goes through the raw-aware
        // helper like the details and security screens do.
        targetPublishedAt?.let { formatIsoDateOrRaw(it) }
    } else {
        formatEpochDate(lastUpdatedAt)
    }

    val base = if (targetVersion != null) {
        getString(Res.string.apps_version_update, installedVersion, targetVersion)
    } else {
        installedVersion
    }

    return if (displayDate != null) {
        getString(Res.string.apps_version_dated, base, displayDate)
    } else {
        base
    }
}
