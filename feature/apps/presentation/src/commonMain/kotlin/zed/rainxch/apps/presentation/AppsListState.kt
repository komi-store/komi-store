package zed.rainxch.apps.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.apps.presentation.mappers.computeIsBusy
import zed.rainxch.apps.presentation.model.AppItem
import zed.rainxch.apps.presentation.model.AppSortRule
import zed.rainxch.apps.presentation.model.UpdateState

internal fun AppsState.withFilteredApps(): AppsState = copy(filteredApps = filterAndSort(apps, searchQuery, sortRule))

internal fun AppsState.withUpdateState(
    packageName: String,
    updateState: UpdateState,
): AppsState =
    copy(
        apps =
            apps.mapApp(packageName) { appItem ->
                appItem.copy(
                    updateState = updateState,
                    downloadProgress = if (updateState is UpdateState.Downloading) appItem.downloadProgress else null,
                    error = if (updateState is UpdateState.Error) updateState.message else null,
                    isBusy = computeIsBusy(appItem.installedApp.isPendingInstall, updateState),
                )
            },
    ).withFilteredApps()

internal fun AppsState.withDownloadProgress(
    packageName: String,
    progress: Int?,
): AppsState =
    copy(
        apps = apps.mapApp(packageName) { it.copy(downloadProgress = progress) },
        filteredApps = filteredApps.mapApp(packageName) { it.copy(downloadProgress = progress) },
    )

internal fun appComparator(sortRule: AppSortRule): Comparator<AppItem> =
    when (sortRule) {
        AppSortRule.UpdatesFirst -> {
            compareByDescending<AppItem> { it.installedApp.isUpdateAvailable }
                .thenByDescending { it.installedApp.latestReleasePublishedAt ?: "" }
                .thenBy { it.installedApp.appName.lowercase() }
        }

        AppSortRule.RecentlyUpdated -> {
            compareByDescending<AppItem> { it.installedApp.lastUpdatedAt }
                .thenByDescending { it.installedApp.isUpdateAvailable }
                .thenBy { it.installedApp.appName.lowercase() }
        }

        AppSortRule.Name -> {
            compareBy<AppItem> { it.installedApp.appName.lowercase() }
                .thenByDescending { it.installedApp.isUpdateAvailable }
        }
    }

private fun filterAndSort(
    apps: ImmutableList<AppItem>,
    query: String,
    sortRule: AppSortRule,
): ImmutableList<AppItem> {
    val matching =
        if (query.isBlank()) {
            apps
        } else {
            apps.filter { appItem ->
                appItem.installedApp.appName.contains(query, ignoreCase = true) ||
                    appItem.installedApp.repoOwner.contains(query, ignoreCase = true)
            }
        }
    return matching.sortedWith(appComparator(sortRule)).toImmutableList()
}

private inline fun ImmutableList<AppItem>.mapApp(
    packageName: String,
    transform: (AppItem) -> AppItem,
): ImmutableList<AppItem> = map { if (it.installedApp.packageName == packageName) transform(it) else it }.toImmutableList()
