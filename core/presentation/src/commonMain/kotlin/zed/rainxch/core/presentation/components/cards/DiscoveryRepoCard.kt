package zed.rainxch.core.presentation.components.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.model.DiscoveryRepositoryUi
import zed.rainxch.core.presentation.utils.daysSinceIso
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.hide_repository
import zed.rainxch.githubstore.core.presentation.res.mark_as_unviewed
import zed.rainxch.githubstore.core.presentation.res.mark_as_viewed
import zed.rainxch.githubstore.core.presentation.res.open_on_github
import zed.rainxch.githubstore.core.presentation.res.share_repository

@Composable
fun DiscoveryRepoCard(
    discoveryRepositoryUi: DiscoveryRepositoryUi,
    onClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
    onHideClick: (() -> Unit)? = null,
    onToggleSeen: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    compact: Boolean = false,
    feed: KomiRepoCardFeed = KomiRepoCardFeed.Plain,
    rank: Int = 1,
    index: Int = 0,
) {
    val repo = discoveryRepositoryUi.repository
    var showActions by remember { mutableStateOf(false) }
    val platforms =
        remember(repo.availablePlatforms) {
            repo.availablePlatforms.filter { it != DiscoveryPlatform.All }.toImmutableList()
        }
    val longPress =
        onLongPress ?: if (onHideClick != null) ({ showActions = true }) else null

    KomiRepoCard(
        name = repo.name,
        owner = repo.owner.login,
        imageUrl = repo.owner.avatarUrl,
        language = repo.language.orEmpty(),
        description = repo.description.orEmpty(),
        platforms = platforms,
        stars = repo.stargazersCount,
        downloads = repo.downloadCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        releasedAgoDays = daysSinceIso(repo.latestReleaseDate) ?: Int.MAX_VALUE,
        releasedAt = repo.latestReleaseDate,
        onOpen = onClick,
        modifier = modifier,
        onLongPress = longPress,
        feed = feed,
        rank = rank,
        dailyStars = repo.dailyStars ?: 0,
        version = repo.latestReleaseTag,
        compact = compact,
        index = index,
    )

    if (showActions) {
        val uriHandler = LocalUriHandler.current
        KomiSheet(
            onDismiss = { showActions = false },
            placement = KomiSheetPlacement.Bottom,
            title = repo.fullName,
        ) {
            RepoActionRow(
                label = stringResource(Res.string.share_repository),
                icon = Icons.Default.Share,
                onClick = {
                    showActions = false
                    onShareClick()
                },
            )
            RepoActionRow(
                label = stringResource(Res.string.open_on_github),
                icon = Icons.Default.OpenInBrowser,
                onClick = {
                    showActions = false
                    uriHandler.openUri(repo.htmlUrl)
                },
            )
            onToggleSeen?.let { toggle ->
                RepoActionRow(
                    label =
                        if (discoveryRepositoryUi.isSeen) {
                            stringResource(Res.string.mark_as_unviewed)
                        } else {
                            stringResource(Res.string.mark_as_viewed)
                        },
                    icon = Icons.Outlined.Visibility,
                    onClick = {
                        showActions = false
                        toggle()
                    },
                )
            }
            onHideClick?.let { hide ->
                RepoActionRow(
                    label = stringResource(Res.string.hide_repository),
                    icon = Icons.Default.VisibilityOff,
                    tint = MaterialTheme.colorScheme.error,
                    onClick = {
                        showActions = false
                        hide()
                    },
                )
            }
        }
    }
}

@Composable
private fun RepoActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = Color.Unspecified,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint,
            modifier = Modifier.size(22.dp),
        )
        KomiText(text = label, role = KomiTextRole.Body, color = tint)
    }
}
