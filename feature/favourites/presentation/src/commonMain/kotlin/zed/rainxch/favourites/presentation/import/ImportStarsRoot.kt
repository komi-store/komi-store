package zed.rainxch.favourites.presentation.import

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import zed.rainxch.core.presentation.components.GitHubStoreImage
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.bars.KomiTopBarSize
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.import_stars_add
import zed.rainxch.githubstore.core.presentation.res.import_stars_add_all
import zed.rainxch.githubstore.core.presentation.res.import_stars_added
import zed.rainxch.githubstore.core.presentation.res.import_stars_button
import zed.rainxch.githubstore.core.presentation.res.import_stars_empty
import zed.rainxch.githubstore.core.presentation.res.import_stars_header_count
import zed.rainxch.githubstore.core.presentation.res.import_stars_hint
import zed.rainxch.githubstore.core.presentation.res.import_stars_no_match
import zed.rainxch.githubstore.core.presentation.res.import_stars_search_hint
import zed.rainxch.githubstore.core.presentation.res.import_stars_subtitle
import zed.rainxch.githubstore.core.presentation.res.import_stars_title
import zed.rainxch.githubstore.core.presentation.res.import_stars_try_another

@Composable
fun ImportStarsRoot(
    viewModel: ImportStarsViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (repoId: Long, owner: String, repo: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ImportStarsEvent.NavigateBack -> onNavigateBack()
            is ImportStarsEvent.NavigateToDetails -> {
                onNavigateToDetails(event.repoId, event.owner, event.repo)
            }
        }
    }

    ImportStarsScreen(state = state, onAction = viewModel::onAction)
}

@Composable
private fun ImportStarsScreen(
    state: ImportStarsState,
    onAction: (ImportStarsAction) -> Unit,
) {
    KomiScaffold(
        topBar = {
            KomiTopBar(
                title = stringResource(Res.string.import_stars_title),
                size = KomiTopBarSize.Compact,
                onBack = { onAction(ImportStarsAction.OnNavigateBack) },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (state.phase) {
                ImportStarsState.Phase.UsernameInput -> UsernameInputPanel(
                    state = state,
                    onAction = onAction,
                )

                ImportStarsState.Phase.Results -> ResultsPanel(
                    state = state,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun UsernameInputPanel(
    state: ImportStarsState,
    onAction: (ImportStarsAction) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KomiText(
            text = stringResource(Res.string.import_stars_subtitle),
            role = KomiTextRole.Body,
            color = colors.onSurfaceVariant,
        )

        KomiTextField(
            value = state.usernameQuery,
            onValueChange = { onAction(ImportStarsAction.OnUsernameQueryChange(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(Res.string.import_stars_hint),
        )

        if (state.errorMessage != null) {
            KomiText(
                text = state.errorMessage,
                role = KomiTextRole.Body,
                color = colors.error,
            )
        }

        KomiButton(
            onClick = { onAction(ImportStarsAction.OnImportClick) },
            label = stringResource(Res.string.import_stars_button),
            variant = KomiButtonVariant.Primary,
            size = KomiButtonSize.Md,
            modifier = Modifier.fillMaxWidth(),
            loading = state.isImporting,
            enabled = state.usernameQuery.trim().isNotEmpty() && !state.isImporting,
        )
    }
}

@Composable
private fun ResultsPanel(
    state: ImportStarsState,
    onAction: (ImportStarsAction) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KomiText(
                text = stringResource(
                    resource = Res.string.import_stars_header_count,
                    state.importedUsername ?: "",
                    state.candidates.size,
                ),
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            KomiButton(
                onClick = { onAction(ImportStarsAction.OnResetImport) },
                label = stringResource(Res.string.import_stars_try_another),
                variant = KomiButtonVariant.Text,
                size = KomiButtonSize.Sm,
            )
        }

        Spacer(Modifier.height(8.dp))
        KomiTextField(
            value = state.searchQuery,
            onValueChange = { onAction(ImportStarsAction.OnSearchChange(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(Res.string.import_stars_search_hint),
            leadingIcon = Icons.Rounded.Search,
            onClear = { onAction(ImportStarsAction.OnClearSearch) },
        )

        if (state.pendingCount > 0) {
            Spacer(Modifier.height(12.dp))
            KomiButton(
                onClick = { onAction(ImportStarsAction.OnAddAll) },
                label = stringResource(Res.string.import_stars_add_all, state.pendingCount),
                variant = KomiButtonVariant.Tonal,
                size = KomiButtonSize.Md,
                modifier = Modifier.fillMaxWidth(),
                loading = state.isBulkAdding,
                enabled = !state.isBulkAdding,
            )
        }

        Spacer(Modifier.height(8.dp))
        if (state.filteredCandidates.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                KomiText(
                    text = if (state.candidates.isEmpty()) {
                        stringResource(
                            resource = Res.string.import_stars_empty,
                            state.importedUsername ?: ""
                        )
                    } else {
                        stringResource(Res.string.import_stars_no_match)
                    },
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = state.filteredCandidates, key = { it.repoId }) { candidate ->
                    CandidateRow(
                        candidate = candidate,
                        onClick = { onAction(ImportStarsAction.OnCandidateClick(candidate)) },
                        onToggle = { onAction(ImportStarsAction.OnToggleFavourite(candidate)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: ImportCandidateUi,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GitHubStoreImage(
            imageModel = { candidate.ownerAvatarUrl },
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(LocalPersonality.current.shape.cornerSmall)),
        )

        Column(modifier = Modifier.weight(1f)) {
            KomiText(
                text = "${candidate.owner}/${candidate.name}",
                role = KomiTextRole.CardTitle,
                color = colors.onBackground,
            )

            if (!candidate.description.isNullOrBlank()) {
                KomiText(
                    text = candidate.description,
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }

        KomiIcon(
            imageVector = if (candidate.isAlreadyFavourited) {
                Icons.Filled.Favorite
            } else {
                Icons.Outlined.FavoriteBorder
            },
            contentDescription = stringResource(
                if (candidate.isAlreadyFavourited) {
                    Res.string.import_stars_added
                } else {
                    Res.string.import_stars_add
                },
            ),
            tint = if (candidate.isAlreadyFavourited) {
                colors.primary
            } else {
                colors.onSurfaceVariant
            },
            modifier = Modifier.clickable(onClick = onToggle),
        )
    }
}
