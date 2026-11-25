package zed.rainxch.apps.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.components.AppsSectionHeader
import zed.rainxch.apps.presentation.components.LibraryAppRow
import zed.rainxch.apps.presentation.components.LibraryDownloadRow
import zed.rainxch.apps.presentation.components.LibraryGroup
import zed.rainxch.apps.presentation.components.LibraryGroupDivider
import zed.rainxch.apps.presentation.components.UpdatesBanner
import zed.rainxch.apps.presentation.import.components.ImportProposalBanner
import zed.rainxch.apps.presentation.model.AppGroup
import zed.rainxch.apps.presentation.model.InstalledAppUi
import zed.rainxch.apps.presentation.model.LibraryDownloadUi
import zed.rainxch.core.presentation.components.ScrollbarContainer
import zed.rainxch.core.presentation.layout.CardGridSpec
import zed.rainxch.core.presentation.layout.rememberWidthCappedStaggeredCells
import zed.rainxch.core.presentation.locals.LocalScrollbarEnabled
import zed.rainxch.core.presentation.utils.arrowKeyScroll
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.apps_section_in_progress
import zed.rainxch.githubstore.core.presentation.res.apps_section_pending_installs
import zed.rainxch.githubstore.core.presentation.res.apps_section_up_to_date

@Composable
internal fun AppsGrid(
    state: AppsState,
    onAction: (AppsAction) -> Unit,
    listState: LazyStaggeredGridState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val installedVersions =
        remember(state.apps) {
            state.apps.associate { it.installedApp.packageName to it.installedApp.installedVersion }.toImmutableMap()
        }

    ScrollbarContainer(
        gridState = listState,
        enabled = LocalScrollbarEnabled.current,
        modifier = modifier,
    ) {
        LazyVerticalStaggeredGrid(
            columns = rememberWidthCappedStaggeredCells(contentPadding = contentPadding),
            state = listState,
            modifier = Modifier.fillMaxSize().arrowKeyScroll(listState),
            contentPadding = contentPadding,
            verticalItemSpacing = CardGridSpec.GridItemSpacing,
            horizontalArrangement = CardGridSpec.GridArrangement,
        ) {
            inProgressSection(state.inProgressDownloads, installedVersions, onAction)
            importProposalItem(state, onAction)
            pendingSection(state.pendingApps.size, state.pendingGroups, onAction)
            updatesSection(state, onAction)
            upToDateSection(state, onAction)
            item(key = "trailing-spacer", span = StaggeredGridItemSpan.FullLine) {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

private fun LazyStaggeredGridScope.inProgressSection(
    downloads: ImmutableList<LibraryDownloadUi>,
    installedVersions: ImmutableMap<String, String>,
    onAction: (AppsAction) -> Unit,
) {
    if (downloads.isEmpty()) return
    item(key = "header-in-progress", span = StaggeredGridItemSpan.FullLine) {
        AppsSectionHeader(
            title = stringResource(Res.string.apps_section_in_progress),
            count = downloads.size,
            isExpanded = true,
            collapsible = false,
            onToggle = {},
        )
    }
    items(downloads, key = { download -> "transient-${download.id}" }) { download ->
        InProgressDownloadItem(
            download = download,
            installedVersion = installedVersions[download.packageName],
            onAction = onAction,
        )
    }
}

private fun LazyStaggeredGridScope.importProposalItem(
    state: AppsState,
    onAction: (AppsAction) -> Unit,
) {
    if (!state.showImportProposalBanner) return
    item(key = "external-import-banner", span = StaggeredGridItemSpan.FullLine) {
        ImportProposalBanner(
            pendingCount = state.pendingExternalImportCount,
            onReview = { onAction(AppsAction.OnImportProposalReview) },
            onDismiss = { onAction(AppsAction.OnImportProposalDismiss) },
        )
    }
}

private fun LazyStaggeredGridScope.pendingSection(
    pendingCount: Int,
    groups: ImmutableList<AppGroup>,
    onAction: (AppsAction) -> Unit,
) {
    if (pendingCount == 0) return
    item(key = "header-pending-installs", span = StaggeredGridItemSpan.FullLine) {
        AppsSectionHeader(
            title = stringResource(Res.string.apps_section_pending_installs),
            count = pendingCount,
            isExpanded = true,
            collapsible = false,
            onToggle = {},
        )
    }
    items(groups, key = { group -> "pending-${group.key}" }) { group ->
        LibraryGroupItem(group = group, onAction = onAction)
    }
}

private fun LazyStaggeredGridScope.updatesSection(
    state: AppsState,
    onAction: (AppsAction) -> Unit,
) {
    if (state.updateApps.isNotEmpty() || state.isUpdatingAll) {
        item(key = "updates-banner", span = StaggeredGridItemSpan.FullLine) {
            UpdatesBanner(
                count = state.updateApps.size,
                isExpanded = state.isUpdatesSectionExpanded,
                isUpdatingAll = state.isUpdatingAll,
                updateAllProgress = state.updateAllProgress,
                updateAllEnabled = state.updateAllButtonEnabled,
                onUpdateAll = { onAction(AppsAction.OnUpdateAll) },
                onCancelUpdateAll = { onAction(AppsAction.OnCancelUpdateAll) },
                onToggleExpand = { onAction(AppsAction.OnToggleUpdatesSection) },
            )
        }
    }
    if (state.updateApps.isNotEmpty() && state.isUpdatesSectionExpanded) {
        items(state.updateGroups, key = { group -> "rich-${group.key}" }) { group ->
            LibraryGroupItem(group = group, onAction = onAction)
        }
    }
}

private fun LazyStaggeredGridScope.upToDateSection(
    state: AppsState,
    onAction: (AppsAction) -> Unit,
) {
    if (state.idleApps.isEmpty()) return
    item(key = "header-up-to-date", span = StaggeredGridItemSpan.FullLine) {
        AppsSectionHeader(
            title = stringResource(Res.string.apps_section_up_to_date),
            count = state.idleApps.size,
            isExpanded = state.isUpToDateSectionExpanded,
            collapsible = true,
            onToggle = { onAction(AppsAction.OnToggleUpToDateSection) },
        )
    }
    if (state.isUpToDateSectionExpanded) {
        items(state.idleGroups, key = { group -> "compact-${group.key}" }) { group ->
            LibraryGroupItem(group = group, onAction = onAction)
        }
    }
}

@Composable
private fun InProgressDownloadItem(
    download: LibraryDownloadUi,
    installedVersion: String?,
    onAction: (AppsAction) -> Unit,
) {
    val key = download.packageName
    val openRepo =
        download.repoId?.let { repoId ->
            {
                onAction(
                    AppsAction.OnNavigateToRepo(
                        repoId = repoId,
                        sourceHost = download.sourceHost,
                        owner = download.repoOwner,
                        repo = download.repoName,
                        packageName = download.packageName,
                    ),
                )
            }
        }
    LibraryDownloadRow(
        download = download,
        installedVersion = installedVersion,
        onOpenRepo = openRepo,
        onPause = { onAction(AppsAction.OnCancelInProgressDownload(key)) },
        onResume = { onAction(AppsAction.OnResumeInProgressDownload(key)) },
        onRetry = { onAction(AppsAction.OnRetryInProgressDownload(key)) },
        onDiscard = { onAction(AppsAction.OnDiscardInProgressDownload(key)) },
        onInstall = { onAction(AppsAction.OnInstallInProgressDownload(key)) },
        onDismiss = { onAction(AppsAction.OnDismissInProgressDownload(key)) },
    )
}

// An app on its own card, or several apps from one repo on one card.
@Composable
private fun LibraryGroupItem(
    group: AppGroup,
    onAction: (AppsAction) -> Unit,
) {
    val single = group.items.singleOrNull()
    if (single != null) {
        LibraryAppRow(appItem = single, onAction = onAction, onOpenRepo = { onAction(single.installedApp.toNavigateAction()) })
        return
    }
    val first = group.items.first()
    LibraryGroup(header = first.installedApp, ratingAverage = first.ratingAverage, ratingCount = first.ratingCount) {
        group.items.forEachIndexed { index, appItem ->
            if (index > 0) LibraryGroupDivider()
            LibraryAppRow(
                appItem = appItem,
                onAction = onAction,
                onOpenRepo = { onAction(appItem.installedApp.toNavigateAction()) },
                framed = false,
            )
        }
    }
}

private fun InstalledAppUi.toNavigateAction(): AppsAction =
    AppsAction.OnNavigateToRepo(
        repoId = repoId,
        sourceHost = sourceHost,
        owner = repoOwner,
        repo = repoName,
        packageName = packageName,
    )
