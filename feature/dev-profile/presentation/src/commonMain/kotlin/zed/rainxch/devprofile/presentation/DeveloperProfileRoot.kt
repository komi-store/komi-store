package zed.rainxch.devprofile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfaceElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.presentation.components.ScrollbarContainer
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.bars.KomiTopBarSize
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.locals.LocalScrollbarEnabled
import zed.rainxch.core.presentation.utils.arrowKeyScroll
import zed.rainxch.devprofile.domain.model.RepoFilterType
import zed.rainxch.devprofile.presentation.components.ContributionCalendarCard
import zed.rainxch.devprofile.presentation.components.DeveloperRepoItem
import zed.rainxch.devprofile.presentation.components.FilterSortControls
import zed.rainxch.devprofile.presentation.components.IdentityRailCard
import zed.rainxch.devprofile.presentation.components.ProfileInfoCard
import zed.rainxch.githubstore.core.presentation.res.*

@Composable
fun DeveloperProfileRoot(
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (repoId: Long) -> Unit,
    onNavigateToUser: (username: String) -> Unit,
    viewModel: DeveloperProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    DeveloperProfileScreen(
        state = state,
        onAction = { action ->
            when (action) {
                DeveloperProfileAction.OnNavigateBackClick -> {
                    onNavigateBack()
                }

                is DeveloperProfileAction.OnRepositoryClick -> {
                    onNavigateToDetails(action.repoId)
                }

                is DeveloperProfileAction.OnOpenLink -> {
                    val url = action.url.trim()
                    val allowed = url.startsWith("https://") || url.startsWith("http://")
                    if (allowed) uriHandler.openUri(url)
                }

                is DeveloperProfileAction.OnNavigateToUser -> {
                    val username = action.username.trim().removePrefix("@")
                    if (username.isNotBlank() && username != state.username) {
                        onNavigateToUser(username)
                    }
                }

                else -> {
                    viewModel.onAction(action)
                }
            }
        },
    )
}

@Composable
fun DeveloperProfileScreen(
    state: DeveloperProfileState,
    onAction: (DeveloperProfileAction) -> Unit,
) {
    KomiScaffold(
        topBar = {
            DevProfileTopbar(
                state = state,
                onAction = onAction,
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            when {
                state.isLoading -> {
                    KomiCircularProgress(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                state.errorMessage != null && state.profile == null -> {
                    ErrorContent(
                        message = state.errorMessage,
                        onRetry = { onAction(DeveloperProfileAction.OnRetry) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                state.profile != null -> {
                    val listState = rememberLazyListState()
                    val isScrollbarEnabled = LocalScrollbarEnabled.current
                    ScrollbarContainer(
                        listState = listState,
                        enabled = isScrollbarEnabled,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().arrowKeyScroll(listState, autoFocus = true),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                        item {
                            ProfileInfoCard(
                                profile = state.profile,
                                onAction = onAction,
                            )
                        }

                        if (!state.profile.isOrganization) {
                            item {
                                ContributionCalendarCard(
                                    contributions = state.contributions,
                                    isLoading = state.isLoadingContributions,
                                )
                            }
                        }

                        item {
                            IdentityRailCard(
                                profile = state.profile,
                                onAction = onAction,
                            )
                        }

                        item {
                            FilterSortControls(
                                currentFilter = state.currentFilter,
                                currentSort = state.currentSort,
                                searchQuery = state.searchQuery,
                                repoCount = state.filteredRepositories.size,
                                totalCount = state.repositories.size,
                                onAction = onAction,
                            )
                        }

                        if (state.isLoadingRepos) {
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    KomiCircularProgress()
                                }
                            }
                        } else if (state.filteredRepositories.isEmpty()) {
                            item {
                                EmptyReposContent(
                                    filter = state.currentFilter,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                )
                            }
                        } else {
                            items(
                                items = state.filteredRepositories,
                                key = { it.id },
                            ) { repo ->
                                DeveloperRepoItem(
                                    repository = repo,
                                    onItemClick = {
                                        onAction(DeveloperProfileAction.OnRepositoryClick(repo.id))
                                    },
                                    onToggleFavorite = {
                                        onAction(DeveloperProfileAction.OnToggleFavorite(repo))
                                    },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                    }
                }
            }

            if (state.errorMessage != null && state.profile != null) {
                val colors = LocalPersonality.current.colors
                KomiSurface(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .fillMaxWidth(),
                    elevation = KomiSurfaceElevation.Raised,
                    contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KomiText(
                            text = state.errorMessage,
                            role = KomiTextRole.Body,
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f),
                        )

                        KomiButton(
                            onClick = { onAction(DeveloperProfileAction.OnRetry) },
                            label = stringResource(Res.string.retry),
                            variant = KomiButtonVariant.Text,
                            size = KomiButtonSize.Sm,
                        )

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { onAction(DeveloperProfileAction.OnDismissError) },
                            contentAlignment = Alignment.Center,
                        ) {
                            KomiIcon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Res.string.dismiss),
                                tint = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyReposContent(
    filter: RepoFilterType,
    modifier: Modifier = Modifier,
) {
    val message =
        when (filter) {
            RepoFilterType.WITH_RELEASES -> stringResource(Res.string.no_repos_with_releases)
            RepoFilterType.WITH_INSTALLABLE -> stringResource(Res.string.no_repos_with_installable)
            RepoFilterType.INSTALLED -> stringResource(Res.string.no_installed_repos)
            RepoFilterType.FAVORITES -> stringResource(Res.string.no_favorite_repos)
        }

    val colors = LocalPersonality.current.colors
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KomiIcon(
            imageVector = Icons.Default.FolderOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = colors.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        KomiText(
            text = message,
            maxLines = 2,
            role = KomiTextRole.Body,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun DevProfileTopbar(
    state: DeveloperProfileState,
    onAction: (DeveloperProfileAction) -> Unit,
) {
    val profileUrl = state.profile?.htmlUrl
    KomiTopBar(
        title = state.username,
        size = KomiTopBarSize.Compact,
        onBack = { onAction(DeveloperProfileAction.OnNavigateBackClick) },
        actions = if (profileUrl != null) {
            {
                KomiIconButton(
                    icon = Icons.Rounded.OpenInBrowser,
                    contentDescription = stringResource(Res.string.open_repository),
                    onClick = { onAction(DeveloperProfileAction.OnOpenLink(profileUrl)) },
                    variant = KomiButtonVariant.Tonal,
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KomiIcon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = colors.error,
        )

        Spacer(modifier = Modifier.height(16.dp))

        KomiText(
            text = stringResource(Res.string.error_generic, message),
            maxLines = 3,
            role = KomiTextRole.CardTitle,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        KomiButton(
            label = stringResource(Res.string.retry),
            onClick = {
                onRetry()
            },
        )
    }
}
