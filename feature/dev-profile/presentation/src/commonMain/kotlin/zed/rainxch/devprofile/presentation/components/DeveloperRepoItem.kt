@file:OptIn(ExperimentalTime::class)

package zed.rainxch.devprofile.presentation.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.WarningAmber
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
import zed.rainxch.core.presentation.utils.formatRelativeLong
import zed.rainxch.devprofile.domain.model.DeveloperRepository
import zed.rainxch.githubstore.core.presentation.res.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeveloperRepoItem(
    repository: DeveloperRepository,
    onItemClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    KomiSurface(
        modifier = modifier.fillMaxWidth(),
        onClick = onItemClick,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    KomiText(
                        text = repository.name,
                        maxLines = 1,
                        role = KomiTextRole.CardTitle,
                        overflow = TextOverflow.Ellipsis,
                        color = colors.onSurface,
                    )

                    val releaseDate = repository.latestReleaseAt
                    val (label, dateString) =
                        if (releaseDate != null) {
                            Res.string.released_on_date to releaseDate
                        } else {
                            Res.string.updated_on_date to repository.updatedAt
                        }
                    KomiText(
                        text =
                            stringResource(label, formatRelativeLong(dateString))
                                .replaceFirstChar { it.uppercase() },
                        role = KomiTextRole.Caption,
                        overflow = TextOverflow.Ellipsis,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                Spacer(Modifier.width(8.dp))

                FavoriteToggle(
                    isFavorite = repository.isFavorite,
                    onClick = onToggleFavorite,
                )
            }

            repository.description?.takeIf { it.isNotBlank() }?.let { description ->
                Spacer(Modifier.height(8.dp))
                KomiText(
                    text = description,
                    maxLines = 2,
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(10.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (repository.stargazersCount > 0) {
                    KomiChip(
                        label = formatCount(repository.stargazersCount),
                        kind = KomiChipKind.Info,
                        size = KomiChipSize.Sm,
                        leadingContent = {
                            KomiIcon(
                                imageVector = Icons.Outlined.StarOutline,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = colors.onSurfaceVariant,
                            )
                        },
                    )
                }
                if (repository.forksCount > 0) {
                    KomiChip(
                        label = formatCount(repository.forksCount),
                        kind = KomiChipKind.Info,
                        size = KomiChipSize.Sm,
                        leadingContent = {
                            KomiIcon(
                                imageVector = Icons.AutoMirrored.Outlined.CallSplit,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = colors.onSurfaceVariant,
                            )
                        },
                    )
                }
                if (repository.openIssuesCount > 0) {
                    KomiChip(
                        label = formatCount(repository.openIssuesCount),
                        kind = KomiChipKind.Info,
                        size = KomiChipSize.Sm,
                        leadingContent = {
                            KomiIcon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = colors.onSurfaceVariant,
                            )
                        },
                    )
                }
                repository.language?.takeIf { it.isNotBlank() }?.let { language ->
                    KomiChip(
                        label = language,
                        kind = KomiChipKind.Info,
                        size = KomiChipSize.Sm,
                        leadingContent = {
                            KomiIcon(
                                imageVector = Icons.Outlined.Code,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = colors.onSurfaceVariant,
                            )
                        },
                    )
                }
            }

            val showBadges =
                repository.hasReleases ||
                    repository.hasInstallableAssets ||
                    repository.isInstalled
            if (showBadges) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (repository.hasInstallableAssets) {
                        TonalBadge(
                            text =
                                repository.latestVersion
                                    ?: stringResource(Res.string.has_release),
                            container = colors.primaryTint,
                            content = colors.primary,
                        )
                    } else if (repository.hasReleases) {
                        TonalBadge(
                            text =
                                repository.latestVersion
                                    ?: stringResource(Res.string.has_release),
                            container = colors.primaryContainer,
                            content = colors.onPrimaryContainer,
                        )
                    }
                    if (repository.isInstalled) {
                        TonalBadge(
                            text = stringResource(Res.string.installed),
                            container = colors.primaryTint,
                            content = colors.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteToggle(
    isFavorite: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val container =
        if (isFavorite) {
            colors.errorTint
        } else {
            colors.surfaceContainerHigh
        }
    val tint =
        if (isFavorite) {
            colors.error
        } else {
            colors.onSurfaceVariant
        }
    val shape = LocalPersonality.current.shape
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
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription =
                stringResource(
                    if (isFavorite) {
                        Res.string.remove_from_favourites
                    } else {
                        Res.string.add_to_favourites
                    },
                ),
            modifier = Modifier.size(18.dp),
            tint = tint,
        )
    }
}

@Composable
private fun TonalBadge(
    text: String,
    container: Color,
    content: Color,
) {
    val shape = LocalPersonality.current.shape
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(shape.cornerSmall))
                .background(container),
    ) {
        KomiText(
            text = text,
            role = KomiTextRole.LabelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Preview
@Composable
private fun PreviewDeveloperRepoItem() {
    PersonalityPreview {
        DeveloperRepoItem(
            repository =
                DeveloperRepository(
                    id = 1,
                    name = "awesome-kotlin-app",
                    fullName = "developer/awesome-kotlin-app",
                    description = "An amazing Kotlin Multiplatform application that demonstrates modern Android development",
                    htmlUrl = "",
                    stargazersCount = 2340,
                    forksCount = 456,
                    openIssuesCount = 23,
                    language = "Kotlin",
                    hasReleases = true,
                    hasInstallableAssets = true,
                    isInstalled = true,
                    isFavorite = false,
                    latestVersion = "v1.5.2",
                    updatedAt = Clock.System.now().toString(),
                ),
            onItemClick = {},
            onToggleFavorite = {},
        )
    }
}
