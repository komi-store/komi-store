package zed.rainxch.favourites.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiFavouriteToggle
import zed.rainxch.core.presentation.components.cards.KomiLibraryRow
import zed.rainxch.core.presentation.utils.formatRelativeLong
import zed.rainxch.favourites.presentation.model.FavouriteRepository
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.library_added

@Composable
fun FavouriteRepositoryItem(
    favouriteRepository: FavouriteRepository,
    onToggleFavouriteClick: () -> Unit,
    onItemClick: () -> Unit,
    onDevProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KomiLibraryRow(
        name = favouriteRepository.repoName,
        owner = favouriteRepository.repoOwner,
        onClick = onItemClick,
        modifier = modifier,
        description = favouriteRepository.repoDescription,
        avatarUrl = favouriteRepository.repoOwnerAvatarUrl,
        installedPackageName = favouriteRepository.installedPackageName,
        platforms = favouriteRepository.platforms,
        version = favouriteRepository.latestRelease,
        stars = favouriteRepository.stars,
        status = favouriteRepository.status,
        context = stringResource(Res.string.library_added, formatRelativeLong(favouriteRepository.addedAt)),
        contextIcon = Icons.Outlined.FavoriteBorder,
        ownedByViewer = favouriteRepository.isCurrentUserOwner,
        onOwnerClick = onDevProfileClick,
        action = { KomiFavouriteToggle(favourited = true, onClick = onToggleFavouriteClick) },
    )
}
