@file:OptIn(ExperimentalTime::class)

package zed.rainxch.apps.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.apps.presentation.components.AdvancedAppSettingsBottomSheet
import zed.rainxch.apps.presentation.components.AppItemCard
import zed.rainxch.apps.presentation.components.AppsSectionHeader
import zed.rainxch.apps.presentation.components.AppsTopbar
import zed.rainxch.apps.presentation.components.CompactAppRow
import zed.rainxch.apps.presentation.components.ImportSummarySheet
import zed.rainxch.apps.presentation.components.KaoBanner
import zed.rainxch.apps.presentation.components.LinkAppBottomSheet
import zed.rainxch.apps.presentation.components.PendingDiscardSheet
import zed.rainxch.apps.presentation.components.PendingUninstallSheet
import zed.rainxch.apps.presentation.components.UpdatesBanner
import zed.rainxch.apps.presentation.components.VariantPickerDialog
import zed.rainxch.apps.presentation.import.components.ImportProposalBanner
import zed.rainxch.apps.presentation.model.InstalledAppUi
import zed.rainxch.core.presentation.components.ScrollbarContainer
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiFab
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement
import zed.rainxch.core.presentation.components.overlays.KomiToastState
import zed.rainxch.core.presentation.components.overlays.rememberKomiToastState
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.refresh.KomiPullToRefresh
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.locals.LocalScrollbarEnabled
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.core.presentation.utils.arrowKeyScroll
import zed.rainxch.core.presentation.utils.constrainedContentWidth
import zed.rainxch.core.presentation.utils.formatLastChecked
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.add_by_link
import zed.rainxch.githubstore.core.presentation.res.apps_section_pending_installs
import zed.rainxch.githubstore.core.presentation.res.apps_section_up_to_date
import zed.rainxch.githubstore.core.presentation.res.cancel
import zed.rainxch.githubstore.core.presentation.res.checking_for_updates
import zed.rainxch.githubstore.core.presentation.res.confirm_discard_pending_message
import zed.rainxch.githubstore.core.presentation.res.confirm_discard_pending_title
import zed.rainxch.githubstore.core.presentation.res.confirm_uninstall_message
import zed.rainxch.githubstore.core.presentation.res.confirm_uninstall_title
import zed.rainxch.githubstore.core.presentation.res.discard_pending_install
import zed.rainxch.githubstore.core.presentation.res.last_checked
import zed.rainxch.githubstore.core.presentation.res.no_apps_found
import zed.rainxch.githubstore.core.presentation.res.search_your_apps
import zed.rainxch.githubstore.core.presentation.res.uninstall
import kotlin.time.ExperimentalTime

@Composable
fun AppsRoot(
    onNavigateBack: () -> Unit,
    onNavigateToRepo: (repoId: Long, sourceHost: String?, owner: String?, repo: String?, initialAssetName: String?, packageName: String?) -> Unit,
    onNavigateToExternalImport: () -> Unit,
    onNavigateToStarredPicker: () -> Unit,
    viewModel: AppsViewModel = koinViewModel(),
    state: AppsState,
) {
    val toastState = rememberKomiToastState()
    val coroutineScope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.onAction(AppsAction.OnLifecycleResume)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is AppsEvent.NavigateToRepo -> {
                onNavigateToRepo(event.repoId, event.sourceHost, event.owner, event.repo, event.initialAssetName, event.packageName)
            }

            is AppsEvent.ShowError -> {
                coroutineScope.launch {
                    toastState.danger(event.message)
                }
            }

            is AppsEvent.ShowSuccess -> {
                coroutineScope.launch {
                    toastState.success(event.message)
                }
            }

            AppsEvent.NavigateToExternalImport -> {
                onNavigateToExternalImport()
            }
        }
    }

    AppsScreen(
        state = state,
        onAction = { action ->
            when (action) {
                AppsAction.OnNavigateBackClick -> {
                    onNavigateBack()
                }

                AppsAction.OnAddFromStarredClick -> {
                    onNavigateToStarredPicker()
                }

                else -> {
                    viewModel.onAction(action)
                }
            }
        },
        toastState = toastState,
    )

    if (state.showLinkSheet) {
        LinkAppBottomSheet(
            state = state,
            onAction = viewModel::onAction,
        )
    }

    if (state.advancedSettingsApp != null) {
        AdvancedAppSettingsBottomSheet(
            state = state,
            onAction = viewModel::onAction,
        )
    }

    if (state.variantPickerApp != null) {
        VariantPickerDialog(
            state = state,
            onAction = viewModel::onAction,
        )
    }

    state.importSummary?.let { summary ->
        ImportSummarySheet(
            summary = summary,
            expandedBuckets = state.expandedImportBuckets,
            onToggleBucket = { viewModel.onAction(AppsAction.OnToggleImportSummaryBucket(it)) },
            onDismiss = {
                viewModel.onAction(AppsAction.OnDismissImportSummary)
            },
        )
    }

    state.appPendingUninstall?.let { app ->
        PendingUninstallSheet(
            app = app,
            onAction = viewModel::onAction,
        )
    }

    state.appPendingDiscard?.let { app ->
        PendingDiscardSheet(
            app = app,
            onAction = viewModel::onAction
        )
    }
}


@Composable
fun AppsScreen(
    state: AppsState,
    onAction: (AppsAction) -> Unit,
    toastState: KomiToastState,
) {
    val colors = LocalPersonality.current.colors
    KomiScaffold(
        topBar = {
            AppsTopbar(
                onAction = onAction,
                state = state
            )
        },
        floatingActionButton = {
            KomiFab(
                icon = Icons.Default.Add,
                contentDescription = stringResource(Res.string.add_by_link),
                onClick = {
                    onAction(AppsAction.OnAddByLinkClick)
                },
            )
        },
        toastState = toastState,
    ) { innerPadding ->
        KomiPullToRefresh(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(AppsAction.OnRefresh) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier.constrainedContentWidth().fillMaxHeight(),
                ) {
                    KomiTextField(
                        value = state.searchQuery,
                        onValueChange = { onAction(AppsAction.OnSearchChange(it)) },
                        leadingIcon = Icons.Default.Search,
                        placeholder = stringResource(Res.string.search_your_apps),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    if (state.isCheckingForUpdates) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            KomiCircularProgress(
                                modifier = Modifier.size(14.dp),
                            )

                            KomiText(
                                text = stringResource(Res.string.checking_for_updates),
                                role = KomiTextRole.Body,
                                fontSize = 13.sp,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    } else if (state.lastCheckedTimestamp != null) {
                        KomiText(
                            text =
                                stringResource(
                                    Res.string.last_checked,
                                    formatLastChecked(state.lastCheckedTimestamp),
                                ),
                            role = KomiTextRole.Body,
                            fontSize = 13.sp,
                            uppercase = false,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    when {
                        state.isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                KomiCircularProgress()
                            }
                        }

                        state.filteredApps.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                KomiText(
                                    text = stringResource(Res.string.no_apps_found),
                                    role = KomiTextRole.Title,
                                    color = colors.onBackground,
                                )
                            }
                        }

                        else -> {
                            val listState = rememberLazyListState()
                            val isScrollbarEnabled = LocalScrollbarEnabled.current

                            val onRowSelect: (InstalledAppUi) -> Unit =
                                { app ->
                                    onAction(
                                        AppsAction.OnNavigateToRepo(
                                            repoId = app.repoId,
                                            sourceHost = app.sourceHost,
                                            owner = app.repoOwner,
                                            repo = app.repoName,
                                            initialAssetName = app.installedAssetName ?: app.latestAssetName ?: app.pendingInstallAssetName,
                                            packageName = app.packageName,
                                        ),
                                    )
                                }

                            ScrollbarContainer(
                                listState = listState,
                                enabled = isScrollbarEnabled,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize().arrowKeyScroll(listState),

                                    contentPadding = PaddingValues(
                                        start = 0.dp,
                                        end = 0.dp,
                                        top = 8.dp,
                                        bottom = 88.dp,
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (state.showImportProposalBanner) {
                                        item(key = "external-import-banner") {
                                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                                ImportProposalBanner(
                                                    pendingCount = state.pendingExternalImportCount,
                                                    onReview = { onAction(AppsAction.OnImportProposalReview) },
                                                    onDismiss = { onAction(AppsAction.OnImportProposalDismiss) },
                                                )
                                            }
                                        }
                                    }

                                    if (state.showKaoBanner) {
                                        item(key = "kao-banner") {
                                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                                KaoBanner(
                                                    onLearnMore = { onAction(AppsAction.OnKaoLearnMore) },
                                                    onDismiss = { onAction(AppsAction.OnDismissKaoBanner) },
                                                )
                                            }
                                        }
                                    }

                                    if (state.pendingApps.isNotEmpty()) {
                                        item(key = "header-pending-installs") {
                                            AppsSectionHeader(
                                                title = stringResource(Res.string.apps_section_pending_installs),
                                                count = state.pendingApps.size,
                                                isExpanded = true,
                                                collapsible = false,
                                                onToggle = {},
                                            )
                                        }

                                        items(
                                            items = state.pendingApps,
                                            key = { "pending-${it.installedApp.packageName}" },
                                        ) { appItem ->
                                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                                AppItemCard(
                                                    appItem = appItem,
                                                    onOpenClick = {
                                                        onAction(
                                                            AppsAction.OnOpenApp(
                                                                appItem.installedApp
                                                            )
                                                        )
                                                    },
                                                    onUpdateClick = {
                                                        onAction(
                                                            AppsAction.OnUpdateApp(
                                                                appItem.installedApp
                                                            )
                                                        )
                                                    },
                                                    onCancelClick = {
                                                        onAction(
                                                            AppsAction.OnCancelUpdate(
                                                                appItem.installedApp.packageName
                                                            )
                                                        )
                                                    },
                                                    onUninstallClick = {
                                                        onAction(
                                                            AppsAction.OnUninstallApp(
                                                                appItem.installedApp
                                                            )
                                                        )
                                                    },
                                                    onRepoClick = { onRowSelect(appItem.installedApp) },
                                                    onTogglePreReleases = { enabled ->
                                                        onAction(
                                                            AppsAction.OnTogglePreReleases(
                                                                appItem.installedApp.packageName,
                                                                enabled
                                                            )
                                                        )
                                                    },
                                                    onToggleUpdateCheck = { enabled ->
                                                        onAction(
                                                            AppsAction.OnToggleUpdateCheck(
                                                                appItem.installedApp.packageName,
                                                                enabled
                                                            )
                                                        )
                                                    },
                                                    onAdvancedSettingsClick = {
                                                        onAction(
                                                            AppsAction.OnOpenAdvancedSettings(
                                                                appItem.installedApp
                                                            )
                                                        )
                                                    },
                                                    onPickVariantClick = {
                                                        onAction(
                                                            AppsAction.OnOpenVariantPicker(
                                                                app = appItem.installedApp,
                                                                resumeUpdateAfterPick = false,
                                                            ),
                                                        )
                                                    },
                                                    onInstallPendingClick = {
                                                        onAction(
                                                            AppsAction.OnInstallPendingApp(
                                                                appItem.installedApp
                                                            )
                                                        )
                                                    },
                                                    onDiscardPendingClick = {
                                                        onAction(
                                                            AppsAction.OnDiscardPendingInstall(
                                                                appItem.installedApp
                                                            )
                                                        )
                                                    },
                                                    onSkipVersionClick = {
                                                        val tag =
                                                            appItem.installedApp.latestVersion
                                                                ?: appItem.installedApp.latestVersionName
                                                        if (!tag.isNullOrBlank()) {
                                                            onAction(
                                                                AppsAction.OnSkipReleaseTag(
                                                                    appItem.installedApp.packageName,
                                                                    tag,
                                                                ),
                                                            )
                                                        }
                                                    },
                                                    onUnskipVersionClick = {
                                                        onAction(
                                                            AppsAction.OnUnskipReleaseTag(
                                                                appItem.installedApp.packageName
                                                            )
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    if (state.updateApps.isNotEmpty() || state.isUpdatingAll) {
                                        item(key = "updates-banner") {
                                            Box(
                                                modifier = Modifier.padding(
                                                    horizontal = 16.dp,
                                                    vertical = 4.dp
                                                )
                                            ) {
                                                UpdatesBanner(
                                                    count = state.updateApps.size,
                                                    isExpanded = state.isUpdatesSectionExpanded,
                                                    isUpdatingAll = state.isUpdatingAll,
                                                    updateAllProgress = state.updateAllProgress,
                                                    updateAllEnabled = state.updateAllButtonEnabled,
                                                    onUpdateAll = { onAction(AppsAction.OnUpdateAll) },
                                                    onCancelUpdateAll = { onAction(AppsAction.OnCancelUpdateAll) },
                                                    onToggleExpanded = { onAction(AppsAction.OnToggleUpdatesSection) },
                                                )
                                            }
                                        }
                                    }

                                    if (state.updateApps.isNotEmpty() && state.isUpdatesSectionExpanded) {
                                        items(
                                            items = state.updateApps,
                                            key = { "rich-${it.installedApp.packageName}" },
                                        ) { appItem ->
                                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                                AppItemCard(
                                                    appItem = appItem,
                                                    onOpenClick = {
                                                        onAction(
                                                            AppsAction.OnOpenApp(
                                                                appItem.installedApp
                                                            )
                                                        )
                                                    },
                                                    onUpdateClick = {
                                                        onAction(
                                                            AppsAction.OnUpdateApp(
                                                                appItem.installedApp
                                                            )
                                                        )
                                                    },
                                                    onCancelClick = {
                                                        onAction(
                                                            AppsAction.OnCancelUpdate(
                                                                appItem.installedApp.packageName
                                                            )
                                                        )
                                                    },
                                                    onUninstallClick = {
                                                        onAction(
                                                            AppsAction.OnUninstallApp(
                                                                appItem.installedApp
                                                            )
                                                        )
                                                    },
                                                    onRepoClick = { onRowSelect(appItem.installedApp) },
                                                    onTogglePreReleases = { enabled ->
                                                        onAction(
                                                            AppsAction.OnTogglePreReleases(
                                                                appItem.installedApp.packageName,
                                                                enabled
                                                            )
                                                        )
                                                    },
                                                    onToggleUpdateCheck = { enabled ->
                                                        onAction(
                                                            AppsAction.OnToggleUpdateCheck(
                                                                appItem.installedApp.packageName,
                                                                enabled
                                                            )
                                                        )
                                                    },
                                                    onAdvancedSettingsClick = {
                                                        onAction(
                                                            AppsAction.OnOpenAdvancedSettings(
                                                                appItem.installedApp
                                                            )
                                                        )
                                                    },
                                                    onPickVariantClick = {
                                                        onAction(
                                                            AppsAction.OnOpenVariantPicker(
                                                                app = appItem.installedApp,
                                                                resumeUpdateAfterPick = false,
                                                            ),
                                                        )
                                                    },
                                                    onInstallPendingClick = {
                                                        onAction(
                                                            AppsAction.OnInstallPendingApp(
                                                                appItem.installedApp
                                                            )
                                                        )
                                                    },
                                                    onDiscardPendingClick = {
                                                        onAction(
                                                            AppsAction.OnDiscardPendingInstall(
                                                                appItem.installedApp
                                                            )
                                                        )
                                                    },
                                                    onSkipVersionClick = {
                                                        val tag =
                                                            appItem.installedApp.latestVersion
                                                                ?: appItem.installedApp.latestVersionName
                                                        if (!tag.isNullOrBlank()) {
                                                            onAction(
                                                                AppsAction.OnSkipReleaseTag(
                                                                    appItem.installedApp.packageName,
                                                                    tag,
                                                                ),
                                                            )
                                                        }
                                                    },
                                                    onUnskipVersionClick = {
                                                        onAction(
                                                            AppsAction.OnUnskipReleaseTag(
                                                                appItem.installedApp.packageName
                                                            )
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    if (state.idleApps.isNotEmpty()) {
                                        item(key = "header-up-to-date") {
                                            AppsSectionHeader(
                                                title = stringResource(Res.string.apps_section_up_to_date),
                                                count = state.idleApps.size,
                                                isExpanded = state.isUpToDateSectionExpanded,
                                                collapsible = true,
                                                onToggle = {
                                                    onAction(AppsAction.OnToggleUpToDateSection)
                                                },
                                            )
                                        }

                                        if (state.isUpToDateSectionExpanded) {
                                            items(
                                                items = state.idleApps,
                                                key = { "compact-${it.installedApp.packageName}" },
                                            ) { appItem ->
                                                Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                                                    CompactAppRow(
                                                        appItem = appItem,
                                                        onOpenClick = {
                                                            onAction(
                                                                AppsAction.OnOpenApp(
                                                                    appItem.installedApp
                                                                )
                                                            )
                                                        },
                                                        onInstallPendingClick = {
                                                            onAction(
                                                                AppsAction.OnInstallPendingApp(
                                                                    appItem.installedApp
                                                                )
                                                            )
                                                        },
                                                        onDiscardPendingClick = {
                                                            onAction(
                                                                AppsAction.OnDiscardPendingInstall(
                                                                    appItem.installedApp
                                                                )
                                                            )
                                                        },
                                                        onAdvancedSettingsClick = {
                                                            onAction(
                                                                AppsAction.OnOpenAdvancedSettings(
                                                                    appItem.installedApp
                                                                )
                                                            )
                                                        },
                                                        onPickVariantClick = {
                                                            onAction(
                                                                AppsAction.OnOpenVariantPicker(
                                                                    app = appItem.installedApp,
                                                                    resumeUpdateAfterPick = false,
                                                                ),
                                                            )
                                                        },
                                                        onUninstallClick = {
                                                            onAction(
                                                                AppsAction.OnUninstallApp(
                                                                    appItem.installedApp
                                                                )
                                                            )
                                                        },
                                                        onTogglePreReleases = { enabled ->
                                                            onAction(
                                                                AppsAction.OnTogglePreReleases(
                                                                    appItem.installedApp.packageName,
                                                                    enabled,
                                                                ),
                                                            )
                                                        },
                                                        onToggleUpdateCheck = { enabled ->
                                                            onAction(
                                                                AppsAction.OnToggleUpdateCheck(
                                                                    appItem.installedApp.packageName,
                                                                    enabled,
                                                                ),
                                                            )
                                                        },
                                                        onUnskipVersionClick = {
                                                            onAction(
                                                                AppsAction.OnUnskipReleaseTag(
                                                                    appItem.installedApp.packageName,
                                                                ),
                                                            )
                                                        },
                                                        onRowClick = { onRowSelect(appItem.installedApp) },
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    item {
                                        Spacer(Modifier.height(32.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun Preview() {
    PersonalityPreview {
        AppsScreen(
            state = AppsState(),
            onAction = {},
            toastState = rememberKomiToastState(),
        )
    }
}
