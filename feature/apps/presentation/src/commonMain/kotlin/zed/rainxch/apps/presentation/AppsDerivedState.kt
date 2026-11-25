package zed.rainxch.apps.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.apps.presentation.mappers.groupedByRepo
import zed.rainxch.apps.presentation.model.DeviceAppUi
import zed.rainxch.apps.presentation.model.GithubAssetUi

internal fun AppsState.withDerived(): AppsState {
    val visibleApps = filteredApps.filterNot { it.installedApp.packageName in hiddenRowPackages }
    val pending =
        visibleApps
            .filter { it.installedApp.isPendingInstall && it.installedApp.pendingInstallFilePath != null }
            .toImmutableList()
    val updates =
        visibleApps
            .filter {
                it.installedApp.isUpdateAvailable &&
                    it.installedApp.updateCheckEnabled &&
                    !it.installedApp.isPendingInstall
            }.toImmutableList()
    val idle =
        visibleApps
            .filter {
                (!it.installedApp.isUpdateAvailable || !it.installedApp.updateCheckEnabled) &&
                    !it.installedApp.isPendingInstall
            }.toImmutableList()

    return copy(
        filteredDeviceApps = sortedDeviceApps(deviceApps, deviceAppSearchQuery),
        filteredLinkAssets = filteredLinkAssets(linkInstallableAssets, linkAssetFilter),
        pendingApps = pending,
        updateApps = updates,
        idleApps = idle,
        pendingGroups = pending.groupedByRepo(),
        updateGroups = updates.groupedByRepo(),
        idleGroups = idle.groupedByRepo(),
    )
}

private fun sortedDeviceApps(
    deviceApps: ImmutableList<DeviceAppUi>,
    query: String,
): ImmutableList<DeviceAppUi> {
    val searched =
        if (query.isBlank()) {
            deviceApps
        } else {
            deviceApps.filter {
                it.appName.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
            }
        }
    return searched
        .sortedWith(
            compareBy<DeviceAppUi> { it.installerCategory.sortPriority }
                .thenBy { it.appName.lowercase() }
                .thenBy { it.packageName },
        ).toImmutableList()
}

// A filter that doesn't compile yet shows every asset rather than none.
private fun filteredLinkAssets(
    assets: ImmutableList<GithubAssetUi>,
    filter: String,
): ImmutableList<GithubAssetUi> {
    val raw = filter.trim()
    val regex = if (raw.isEmpty()) null else runCatching { Regex(raw, RegexOption.IGNORE_CASE) }.getOrNull()
    return if (regex == null) assets else assets.filter { regex.containsMatchIn(it.name) }.toImmutableList()
}
