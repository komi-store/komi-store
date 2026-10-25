package zed.rainxch.starred.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiFavouriteToggle
import zed.rainxch.core.presentation.components.cards.KomiLibraryRow
import zed.rainxch.core.presentation.utils.formatRelativeLong
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.library_starred
import zed.rainxch.starred.presentation.model.StarredRepositoryUi

// The heart keeps a starred repo in Favourites; unstarring happens in Details.
@Composable
fun StarredRepositoryItem(
    repository: StarredRepositoryUi,
    onToggleFavoriteClick: () -> Unit,
    onItemClick: () -> Unit,
    onDevProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KomiLibraryRow(
        name = repository.repoName,
        owner = repository.repoOwner,
        onClick = onItemClick,
        modifier = modifier,
        description = repository.repoDescription,
        avatarUrl = repository.repoOwnerAvatarUrl,
        installedPackageName = repository.installedPackageName,
        platforms = repository.platforms,
        version = repository.latestRelease,
        stars = repository.stargazersCount,
        status = repository.status,
        context = repository.starredAt?.let { stringResource(Res.string.library_starred, formatRelativeLong(it)) },
        contextIcon = Icons.Outlined.StarOutline,
        ownedByViewer = repository.isCurrentUserOwner,
        onOwnerClick = onDevProfileClick,
        action = { KomiFavouriteToggle(favourited = repository.isFavorite, onClick = onToggleFavoriteClick) },
    )
}
