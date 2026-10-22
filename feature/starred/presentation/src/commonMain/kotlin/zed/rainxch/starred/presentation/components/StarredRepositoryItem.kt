package zed.rainxch.starred.presentation.components

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
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.utils.formatCount
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.starred.presentation.model.StarredRepositoryUi

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StarredRepositoryItem(
    repository: StarredRepositoryUi,
    onToggleFavoriteClick: () -> Unit,
    onItemClick: () -> Unit,
    onDevProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    KomiSurface(
        onClick = onItemClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GitHubStoreImage(
                    imageModel = { repository.repoOwnerAvatarUrl },
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(shape.cornerSmall))
                            .clickable(onClick = onDevProfileClick),
                )

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable(onClick = onDevProfileClick),
                ) {
                    KomiText(
                        text = repository.repoName,
                        role = KomiTextRole.CardTitle,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        KomiText(
                            text = repository.repoOwner,
                            role = KomiTextRole.Body,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )

                        if (repository.isCurrentUserOwner) {
                            KomiIcon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = stringResource(Res.string.self_owned_badge),
                                modifier = Modifier.size(16.dp),
                                tint = colors.primary,
                            )
                        }
                    }
                }

                FavoriteToggle(
                    favorited = repository.isFavorite,
                    onClick = onToggleFavoriteClick,
                )
            }

            repository.repoDescription?.let { description ->
                Spacer(Modifier.height(10.dp))
                KomiText(
                    text = description,
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatChipNeutral(
                    icon = Icons.Default.Star,
                    label = formatCount(repository.stargazersCount),
                )

                StatChipNeutral(
                    icon = Icons.AutoMirrored.Filled.CallSplit,
                    label = formatCount(repository.forksCount),
                )

                if (repository.openIssuesCount > 0) {
                    StatChipNeutral(
                        icon = Icons.Outlined.Warning,
                        label = formatCount(repository.openIssuesCount),
                    )
                }

                repository.primaryLanguage?.let { language ->
                    StatChipNeutral(
                        icon = Icons.Default.Code,
                        label = language,
                    )
                }

                if (repository.isInstalled) {
                    TonalBadge(
                        text = stringResource(Res.string.installed),
                        container = colors.primaryTint,
                        content = colors.primary,
                    )
                }

                repository.latestRelease?.let { version ->
                    TonalBadge(
                        text = version,
                        container = colors.primaryTint,
                        content = colors.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChipNeutral(
    icon: ImageVector,
    label: String,
) {
    val colors = LocalPersonality.current.colors
    KomiChip(
        label = label,
        kind = KomiChipKind.Info,
        size = KomiChipSize.Sm,
        leadingContent = {
            KomiIcon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = colors.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun TonalBadge(
    text: String,
    container: Color,
    content: Color,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(LocalPersonality.current.shape.cornerSmall))
                .background(container)
                .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        KomiText(
            text = text,
            role = KomiTextRole.LabelSmall,
            color = content,
        )
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
            imageVector =
                if (favorited) {
                    Icons.Filled.Favorite
                } else {
                    Icons.Outlined.FavoriteBorder
                },
            contentDescription =
                if (favorited) {
                    stringResource(Res.string.remove_from_favourites)
                } else {
                    stringResource(Res.string.add_to_favourites)
                },
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Preview
@Composable
private fun PreviewStarredRepoItem() {
    PersonalityPreview {
        StarredRepositoryItem(
            repository =
                StarredRepositoryUi(
                    repoId = 1,
                    repoName = "awesome-app",
                    repoOwner = "developer",
                    repoOwnerAvatarUrl = "",
                    repoDescription = "An awesome application that does amazing things",
                    primaryLanguage = "Kotlin",
                    repoUrl = "",
                    stargazersCount = 1234,
                    forksCount = 567,
                    openIssuesCount = 12,
                    isInstalled = true,
                    isFavorite = false,
                    latestRelease = "v1.2.3",
                    latestReleaseUrl = null,
                    starredAt = null,
                ),
            onToggleFavoriteClick = {},
            onItemClick = {},
            onDevProfileClick = {},
        )
    }
}
