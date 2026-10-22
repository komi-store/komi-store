package zed.rainxch.favourites.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Verified
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.GitHubStoreImage
import zed.rainxch.core.presentation.components.chips.KomiChip
import zed.rainxch.core.presentation.components.chips.KomiChipKind
import zed.rainxch.core.presentation.components.chips.KomiChipSize
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.favourites.presentation.model.FavouriteRepository
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.remove_from_favourites
import zed.rainxch.githubstore.core.presentation.res.self_owned_badge

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavouriteRepositoryItem(
    favouriteRepository: FavouriteRepository,
    onToggleFavouriteClick: () -> Unit,
    onItemClick: () -> Unit,
    onDevProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    KomiSurface(
        modifier = modifier,
        onClick = onItemClick,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(LocalPersonality.current.shape.cornerSmall))
                        .clickable(onClick = onDevProfileClick)
                        .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GitHubStoreImage(
                    imageModel = { favouriteRepository.repoOwnerAvatarUrl },
                    modifier =
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(shape.cornerSmall)),
                )

                KomiText(
                    text = favouriteRepository.repoOwner,
                    role = KomiTextRole.LabelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (favouriteRepository.isCurrentUserOwner) {
                    KomiIcon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = stringResource(Res.string.self_owned_badge),
                        modifier = Modifier.size(16.dp),
                        tint = colors.primary,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    KomiText(
                        text = favouriteRepository.repoName,
                        role = KomiTextRole.CardTitle,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    favouriteRepository.repoDescription?.let {
                        Spacer(Modifier.height(4.dp))

                        KomiText(
                            text = it,
                            role = KomiTextRole.Body,
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                FavoriteToggle(
                    favorited = true,
                    onClick = onToggleFavouriteClick,
                )
            }

            Spacer(Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                favouriteRepository.primaryLanguage?.let { language ->
                    KomiChip(
                        label = language,
                        kind = KomiChipKind.Info,
                        size = KomiChipSize.Sm,
                        leadingContent = {
                            KomiIcon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = colors.onSurfaceVariant,
                            )
                        },
                    )
                }

                favouriteRepository.latestRelease?.let { release ->
                    KomiChip(
                        label = release,
                        kind = KomiChipKind.Info,
                        size = KomiChipSize.Sm,
                        leadingContent = {
                            KomiIcon(
                                imageVector = Icons.Default.NewReleases,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = colors.primary,
                            )
                        },
                    )
                }

                KomiChip(
                    label = favouriteRepository.addedAtFormatter,
                    kind = KomiChipKind.Info,
                    size = KomiChipSize.Sm,
                    leadingContent = {
                        KomiIcon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = colors.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun FavoriteToggle(
    favorited: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val tint =
        if (favorited) {
            colors.error
        } else {
            colors.onSurfaceVariant
        }
    val container =
        if (favorited) {
            colors.error.copy(alpha = 0.45f)
        } else {
            colors.surfaceContainerHigh
        }
    Box(
        modifier =
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(shape.cornerSmall))
                .background(container)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        KomiIcon(
            imageVector = Icons.Default.Favorite,
            contentDescription = stringResource(Res.string.remove_from_favourites),
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}
