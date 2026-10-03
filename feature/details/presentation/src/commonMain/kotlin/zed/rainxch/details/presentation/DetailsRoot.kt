package zed.rainxch.details.presentation

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import zed.rainxch.core.presentation.components.InstalledAppIcon
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.domain.isDesktop
import zed.rainxch.core.domain.model.error.RefreshError
import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.presentation.components.ScrollbarContainer
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.buttons.KomiActionRow
import zed.rainxch.core.presentation.components.buttons.KomiActionRowItem
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.overlays.KomiDialog
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
import zed.rainxch.core.presentation.utils.contentWidthCap
import zed.rainxch.core.presentation.utils.isPullToRefreshSupported
import zed.rainxch.details.presentation.components.ApkInspectSheet
import zed.rainxch.details.presentation.components.PlatformHandoffSheet
import zed.rainxch.details.presentation.components.sections.about
import zed.rainxch.details.presentation.components.sections.header
import zed.rainxch.details.presentation.components.sections.logs
import zed.rainxch.details.presentation.components.sections.releaseChannel
import zed.rainxch.details.presentation.components.sections.stats
import zed.rainxch.details.presentation.components.sections.whatsNew
import zed.rainxch.details.presentation.components.states.ErrorState
import zed.rainxch.githubstore.core.presentation.res.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@Composable
fun DetailsRoot(
    onNavigateBack: () -> Unit,
    onNavigateToDeveloperProfile: (username: String) -> Unit,
    onOpenRepositoryInApp: (repoId: Long) -> Unit,
    onNavigateToAbout: (repoId: Long, owner: String, repo: String, sourceHost: String?, translateTo: String?) -> Unit,
    onNavigateToWhatsNew: (repoId: Long, owner: String, repo: String, sourceHost: String?) -> Unit,
    onNavigateToIssues: (owner: String, repo: String) -> Unit,
    onNavigateToSecurity: (owner: String, repo: String) -> Unit,
    onNavigateToMarkdownViewer: (url: String) -> Unit,
    viewModel: DetailsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberKomiToastState()
    val coroutineScope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is DetailsEvent.OnOpenRepositoryInApp -> {
                onOpenRepositoryInApp(event.repositoryId)
            }

            is DetailsEvent.OnMessage -> {
                coroutineScope.launch {
                    toastState.show(event.message)
                }
            }

            is DetailsEvent.OnRefreshError -> {
                coroutineScope.launch {
                    val seconds = event.retryAfterSeconds?.toInt() ?: 0
                    val text = when (event.kind) {
                        RefreshError.COOLDOWN -> getString(
                            Res.string.details_refresh_snackbar_cooldown,
                            seconds.coerceAtLeast(1),
                        )

                        RefreshError.BUDGET_EXHAUSTED -> getString(
                            Res.string.details_refresh_snackbar_budget_exhausted,
                            seconds.coerceAtLeast(1),
                        )

                        RefreshError.ARCHIVED -> getString(Res.string.details_refresh_snackbar_archived)
                        RefreshError.NOT_FOUND -> getString(Res.string.details_refresh_snackbar_not_found)
                        RefreshError.UPSTREAM -> getString(Res.string.details_refresh_snackbar_upstream)
                        RefreshError.GENERIC -> getString(Res.string.details_refresh_snackbar_generic)
                    }
                    toastState.show(text)
                }
            }
        }
    }

    DetailsScreen(
        state = state,
        toastState = toastState,
        onAction = { action ->
            when (action) {
                DetailsAction.OnNavigateBackClick -> {
                    onNavigateBack()
                }

                is DetailsAction.OpenDeveloperProfile -> {
                    onNavigateToDeveloperProfile(action.username)
                }

                is DetailsAction.OnMessage -> {
                    coroutineScope.launch {
                        toastState.show(getString(action.messageText))
                    }
                }

                else -> {
                    viewModel.onAction(action)
                }
            }
        },
        onTranslateLanguage = state.repository?.let { repo ->
            { code ->
                onNavigateToAbout(
                    repo.id,
                    repo.owner.login,
                    repo.name,
                    repo.sourceHost,
                    code,
                )
            }
        },
        onReadMoreWhatsNew = state.repository?.let { repo ->
            {
                onNavigateToWhatsNew(
                    repo.id,
                    repo.owner.login,
                    repo.name,
                    repo.sourceHost,
                )
            }
        },
        onOpenIssues = state.repository?.takeIf { it.sourceHost == null }?.let { repo ->
            { onNavigateToIssues(repo.owner.login, repo.name) }
        },
        onOpenSecurity = state.repository?.takeIf { it.sourceHost == null }?.let { repo ->
            { onNavigateToSecurity(repo.owner.login, repo.name) }
        },
    )

    state.handoff?.let { handoff ->
        PlatformHandoffSheet(handoff = handoff, onAction = viewModel::onAction)
    }

    state.downgradeWarning?.let { warning ->
        KomiDialog(
            onDismissRequest = {
                viewModel.onAction(DetailsAction.OnDismissDowngradeWarning)
            },
            title = {
                KomiText(
                    text = stringResource(Res.string.downgrade_warning_title),
                    role = KomiTextRole.Title,
                    fontWeight = FontWeight.SemiBold,
                    uppercase = false,
                )
            },
            text = {
                val personality = LocalPersonality.current
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val targetName =
                        state.installedApps.firstOrNull { it.packageName == warning.packageName }
                            ?.appName
                            ?: warning.packageName
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        InstalledAppIcon(
                            packageName = warning.packageName,
                            appName = targetName,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(personality.shape.cornerSmall)),
                        )
                        Column {
                            KomiText(
                                text = targetName,
                                role = KomiTextRole.Title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                uppercase = false,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            KomiText(
                                text = warning.packageName,
                                role = KomiTextRole.Body,
                                fontSize = 12.sp,
                                color = personality.colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    KomiText(
                        text =
                            stringResource(
                                Res.string.downgrade_warning_message,
                                warning.targetVersion,
                                warning.currentVersion,
                            ),
                        role = KomiTextRole.Body,
                    )
                    KomiText(
                        text =
                            stringResource(
                                Res.string.downgrade_warning_uninstall_note,
                                warning.currentVersion,
                            ),
                        role = KomiTextRole.Body,
                        color = personality.colors.error,
                    )
                }
            },
            confirmButton = {
                val personality = LocalPersonality.current
                val dialogSurfaceIsDark = personality.colors.surfaceContainerHigh.luminance() < 0.5f
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    KomiButton(
                        onClick = {
                            viewModel.onAction(DetailsAction.OnDismissDowngradeWarning)
                        },
                        label = stringResource(Res.string.cancel),
                        variant = KomiButtonVariant.Text,
                        size = KomiButtonSize.Sm,
                        containerColor = Color.Transparent,
                        contentColor =
                            if (dialogSurfaceIsDark) {
                                Color(0xFFBCBCBC)
                            } else {
                                Color(0xFF747474)
                            },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KomiButton(
                            onClick = {
                                viewModel.onAction(DetailsAction.OnConfirmDowngradeUninstall)
                            },
                            label = stringResource(Res.string.uninstall_first),
                            variant = KomiButtonVariant.Text,
                            size = KomiButtonSize.Sm,
                            containerColor = Color.Transparent,
                            contentColor = personality.colors.error,
                        )
                        KomiButton(
                            onClick = {
                                viewModel.onAction(DetailsAction.OnConfirmDowngradeInstall)
                            },
                            label = stringResource(Res.string.install_anyway),
                            variant = KomiButtonVariant.Text,
                            size = KomiButtonSize.Sm,
                            containerColor = Color.Transparent,
                            contentColor =
                                if (dialogSurfaceIsDark) {
                                    Color(0xFFB6C4FF)
                                } else {
                                    Color(0xFF3B5BDB)
                                },
                        )
                    }
                }
            },
        )
    }

    state.signingKeyWarning?.let { warning ->
        KomiDialog(
            onDismissRequest = {
                viewModel.onAction(DetailsAction.OnDismissSigningKeyWarning)
            },
            title = {
                KomiText(
                    text = stringResource(Res.string.signing_key_changed_title),
                    role = KomiTextRole.Title,
                    fontWeight = FontWeight.SemiBold,
                    uppercase = false,
                )
            },
            text = {
                KomiText(
                    text =
                        stringResource(
                            Res.string.signing_key_changed_message,
                            warning.expectedFingerprint.take(19),
                            warning.actualFingerprint.take(19),
                        ),
                    role = KomiTextRole.Body,
                )
            },
            confirmButton = {
                KomiButton(
                    onClick = {
                        viewModel.onAction(DetailsAction.OnOverrideSigningKeyWarning)
                    },
                    label = stringResource(Res.string.install_anyway),
                    variant = KomiButtonVariant.Text,
                    size = KomiButtonSize.Sm,
                )
            },
            dismissButton = {
                KomiButton(
                    onClick = {
                        viewModel.onAction(DetailsAction.OnDismissSigningKeyWarning)
                    },
                    label = stringResource(Res.string.cancel),
                    variant = KomiButtonVariant.Text,
                    size = KomiButtonSize.Sm,
                )
            },
        )
    }

    if (state.showUninstallConfirmation) {
        val appName = state.installedApp?.appName ?: ""
        KomiDialog(
            onDismissRequest = {
                viewModel.onAction(DetailsAction.OnDismissUninstallConfirmation)
            },
            title = {
                KomiText(
                    text = stringResource(Res.string.confirm_uninstall_title),
                    role = KomiTextRole.Title,
                    fontWeight = FontWeight.SemiBold,
                    uppercase = false,
                )
            },
            text = {
                KomiText(
                    text = stringResource(Res.string.confirm_uninstall_message, appName),
                    role = KomiTextRole.Body,
                )
            },
            confirmButton = {
                KomiButton(
                    onClick = {
                        viewModel.onAction(DetailsAction.OnConfirmUninstall)
                    },
                    label = stringResource(Res.string.uninstall),
                    variant = KomiButtonVariant.Text,
                    size = KomiButtonSize.Sm,
                )
            },
            dismissButton = {
                KomiButton(
                    onClick = {
                        viewModel.onAction(DetailsAction.OnDismissUninstallConfirmation)
                    },
                    label = stringResource(Res.string.cancel),
                    variant = KomiButtonVariant.Text,
                    size = KomiButtonSize.Sm,
                )
            },
        )
    }

    if (state.showUnlinkConfirmation) {
        val appName = state.installedApp?.appName ?: ""
        KomiDialog(
            onDismissRequest = {
                viewModel.onAction(DetailsAction.OnDismissUnlinkConfirmation)
            },
            title = {
                KomiText(
                    text = stringResource(Res.string.details_unlink_external_app_dialog_title),
                    role = KomiTextRole.Title,
                    fontWeight = FontWeight.SemiBold,
                    uppercase = false,
                )
            },
            text = {
                KomiText(
                    text = stringResource(
                        Res.string.details_unlink_external_app_dialog_body,
                        appName
                    ),
                    role = KomiTextRole.Body,
                )
            },
            confirmButton = {
                KomiButton(
                    onClick = {
                        viewModel.onAction(DetailsAction.OnConfirmUnlinkExternalApp)
                    },
                    label = stringResource(Res.string.details_unlink_external_app_dialog_confirm),
                    variant = KomiButtonVariant.Text,
                    size = KomiButtonSize.Sm,
                )
            },
            dismissButton = {
                KomiButton(
                    onClick = {
                        viewModel.onAction(DetailsAction.OnDismissUnlinkConfirmation)
                    },
                    label = stringResource(Res.string.cancel),
                    variant = KomiButtonVariant.Text,
                    size = KomiButtonSize.Sm,
                )
            },
        )
    }

    if (state.showExternalInstallerPrompt) {
        KomiDialog(
            onDismissRequest = {
                viewModel.onAction(DetailsAction.DismissExternalInstallerPrompt)
            },
            title = {
                KomiText(
                    text = stringResource(Res.string.install_permission_unavailable),
                    role = KomiTextRole.Title,
                    fontWeight = FontWeight.SemiBold,
                    uppercase = false,
                )
            },
            text = {
                KomiText(
                    text = stringResource(Res.string.install_permission_blocked_message),
                    role = KomiTextRole.Body,
                )
            },
            confirmButton = {
                KomiButton(
                    onClick = {
                        viewModel.onAction(DetailsAction.OpenWithExternalInstaller)
                    },
                    label = stringResource(Res.string.open_with_external_installer),
                    variant = KomiButtonVariant.Text,
                    size = KomiButtonSize.Sm,
                )
            },
            dismissButton = {
                KomiButton(
                    onClick = {
                        viewModel.onAction(DetailsAction.DismissExternalInstallerPrompt)
                    },
                    label = stringResource(Res.string.dismiss),
                    variant = KomiButtonVariant.Text,
                    size = KomiButtonSize.Sm,
                )
            },
        )
    }

    if (state.isApkInspectSheetVisible) {
        ApkInspectSheet(
            inspection = state.apkInspection,
            isLoading = state.isApkInspectLoading,
            onDismiss = { viewModel.onAction(DetailsAction.OnDismissApkInspect) },
        )
    }
}

@Composable
fun DetailsScreen(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
    toastState: KomiToastState,
    onTranslateLanguage: ((String) -> Unit)? = null,
    onReadMoreWhatsNew: (() -> Unit)? = null,
    onOpenIssues: (() -> Unit)? = null,
    onOpenSecurity: (() -> Unit)? = null,
) {
    KomiScaffold(
        topBar = {
            KomiTopBar(
                title = "",
                leading = {
                    KomiIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.navigate_back),
                        onClick = { onAction(DetailsAction.OnNavigateBackClick) },
                    )
                },
                actions = {
                    if (state.repository != null) {
                        DetailsActions(state = state, onAction = onAction)
                    }
                }
            )
        },
        toastState = toastState
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                KomiCircularProgress()
            }

            return@KomiScaffold
        }

        if (state.errorMessage != null) {
            ErrorState(state.errorMessage, onAction)

            return@KomiScaffold
        }

        val density = LocalDensity.current
        var containerHeightDp by remember { mutableStateOf(0.dp) }
        val collapsedSectionHeight = containerHeightDp * 0.4f
        val listState = rememberLazyListState()
        val isScrollbarEnabled = LocalScrollbarEnabled.current
        val contentWidthDp = contentWidthCap()
        val pullEnabled = remember { isPullToRefreshSupported() }

        val isDesktop = remember { isDesktop() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scrollable(
                    state = listState,
                    orientation = Orientation.Vertical,

                    reverseDirection = true,
                    enabled = isDesktop,
                )
                .onSizeChanged { size ->

                    val newHeight = with(density) { size.height.toDp() }
                    if (newHeight != containerHeightDp) containerHeightDp = newHeight
                },
            contentAlignment = Alignment.Center,
        ) {
            ScrollbarContainer(
                listState = listState,
                enabled = isScrollbarEnabled,
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .widthIn(max = contentWidthDp)
                        .fillMaxWidth(),
            ) {
                val listModifier =
                    Modifier
                        .fillMaxHeight()
                        .widthIn(max = contentWidthDp)
                        .fillMaxWidth()
                        .arrowKeyScroll(listState, autoFocus = true)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                (event.isMetaPressed || event.isCtrlPressed) &&
                                event.key == Key.R
                            ) {
                                onAction(DetailsAction.Refresh)
                                true
                            } else {
                                false
                            }
                        }.padding(innerPadding)

                PullToRefreshHost(
                    enabled = pullEnabled,
                    isRefreshing = state.isRefreshing,
                    onRefresh = { onAction(DetailsAction.Refresh) },
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = listModifier,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        header(
                            state = state,
                            onAction = onAction,
                        )

                        state.stats?.let { stats ->
                            stats(
                                repoStats = stats,
                            )
                        }

                        releaseChannel(
                            state = state,
                            onAction = onAction,
                        )

                        if (onOpenIssues != null || onOpenSecurity != null) {
                            item(key = "repo_pages_actions") {
                                RepoPagesActionRow(
                                    onOpenIssues = onOpenIssues,
                                    onOpenSecurity = onOpenSecurity,
                                )
                            }
                        }

                        state.selectedRelease?.let { release ->
                            whatsNew(
                                release = release,
                                isExpanded = state.isWhatsNewExpanded,
                                onToggleExpanded = { onAction(DetailsAction.ToggleWhatsNewExpanded) },
                                collapsedHeight = collapsedSectionHeight,
                                measuredHeightPx = state.whatsNewMeasuredHeightPx,
                                onMeasured = { onAction(DetailsAction.OnWhatsNewMeasured(it)) },
                                onReadMore = onReadMoreWhatsNew,
                            )
                        }

                        state.readmeMarkdown?.let {
                            about(
                                readmeMarkdown = state.readmeMarkdown,
                                readmeLanguage = state.readmeLanguage,
                                onTranslateLanguage = onTranslateLanguage,
                            )
                        }


                        if (state.installLogs.isNotEmpty()) {
                            logs(state)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PullToRefreshHost(
    enabled: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (enabled) {
        KomiPullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            content()
        }
    } else {
        content()
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun DetailsActions(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
) {
    val cooldownUntilMs = state.refreshCooldownUntilEpochMs
    var nowMs by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }

    LaunchedEffect(cooldownUntilMs) {
        if (cooldownUntilMs == null) return@LaunchedEffect
        while (Clock.System.now().toEpochMilliseconds() < cooldownUntilMs) {
            nowMs = Clock.System.now().toEpochMilliseconds()
            delay(500L.milliseconds)
        }
        nowMs = Clock.System.now().toEpochMilliseconds()
    }

    val cooldownSeconds = cooldownUntilMs?.let { until ->
        ((until - nowMs + 999) / 1000).coerceAtLeast(0L).toInt()
    } ?: 0
    val cooldownActive = cooldownSeconds > 0
    val refreshDisabled = cooldownActive || state.isRefreshing

    val openLabel = stringResource(Res.string.open_repository)
    val starLabel = stringResource(
        if (state.isStarred) Res.string.repository_starred else Res.string.repository_not_starred,
    )
    val favouriteLabel = stringResource(
        if (state.isFavourite) Res.string.remove_from_favourites else Res.string.add_to_favourites,
    )
    val shareLabel = stringResource(Res.string.share_repository)
    val refreshLabel = if (cooldownActive) {
        stringResource(Res.string.details_refresh_cooldown, cooldownSeconds)
    } else {
        stringResource(Res.string.details_refresh)
    }

    val items = buildList {
        add(
            KomiActionRowItem(
                icon = Icons.Default.OpenInBrowser,
                title = openLabel,
                onClick = {
                    onAction(DetailsAction.OpenRepoInBrowser)
                },
            ),
        )
        add(
            KomiActionRowItem(
                icon = if (state.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                title = starLabel,
                onClick = {
                    onAction(DetailsAction.OnToggleStar)
                },
            ),
        )
        add(
            KomiActionRowItem(
                icon = if (state.isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                title = favouriteLabel,
                onClick = {
                    onAction(DetailsAction.OnToggleFavorite)
                },
            ),
        )
        if (state.repository?.htmlUrl != null) {
            add(
                KomiActionRowItem(
                    icon = Icons.Default.Share,
                    title = shareLabel,
                    onClick = {
                        onAction(DetailsAction.OnShareClick)
                    },
                ),
            )
        }
        add(
            KomiActionRowItem(
                icon = Icons.Default.Refresh,
                title = refreshLabel,
                onClick = {
                    onAction(DetailsAction.Refresh)
                },
                enabled = !refreshDisabled,
            ),
        )
        if (state.installedApp?.installSource == InstallSource.MANUAL) {
            add(
                KomiActionRowItem(
                    icon = Icons.Default.LinkOff,
                    title = stringResource(Res.string.details_unlink_external_app_menu),
                    onClick = {
                        onAction(DetailsAction.OnUnlinkExternalApp)
                    },
                ),
            )
        }
    }.toImmutableList()

    KomiActionRow(items = items, maxVisible = 1)
}

@Preview
@Composable
private fun Preview() {
    PersonalityPreview {
        DetailsScreen(
            state =
                DetailsState(
                    isLoading = false,
                ),
            onAction = {},
            toastState = KomiToastState(),
        )
    }
}

@Composable
private fun RepoPagesActionRow(
    onOpenIssues: (() -> Unit)?,
    onOpenSecurity: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        onOpenIssues?.let { open ->
            KomiButton(
                onClick = open,
                label = stringResource(Res.string.repo_pages_details_issues_button),
                variant = KomiButtonVariant.Tonal,
                size = KomiButtonSize.Sm,
                modifier = Modifier.weight(1f),
            )
        }
        onOpenSecurity?.let { open ->
            KomiButton(
                onClick = open,
                label = stringResource(Res.string.repo_pages_details_security_button),
                variant = KomiButtonVariant.Tonal,
                size = KomiButtonSize.Sm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
