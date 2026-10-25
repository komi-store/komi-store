package zed.rainxch.recentlyviewed.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.buttons.KomiIconButtonSize
import zed.rainxch.core.presentation.components.cards.KomiLibraryRow
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.formatRelativeLong
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.library_viewed
import zed.rainxch.githubstore.core.presentation.res.recently_viewed_remove_cd
import zed.rainxch.recentlyviewed.presentation.model.RecentlyViewedRepo

// Swiping a row toward the start drops it from the history; the X does the same for a mouse or a
// screen reader.
@Composable
fun RecentlyViewedItem(
    repo: RecentlyViewedRepo,
    onItemClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onDevProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val currentOnRemove by rememberUpdatedState(onRemoveClick)
    SwipeToDismissBox(
        state = rememberSwipeToDismissBoxState(),
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { currentOnRemove() },
        backgroundContent = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(personality.shape.corner))
                        .background(colors.error)
                        .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                KomiIcon(imageVector = Icons.Rounded.DeleteSweep, contentDescription = null, tint = colors.onError)
            }
        },
    ) {
        KomiLibraryRow(
            name = repo.repoName,
            owner = repo.repoOwner,
            onClick = onItemClick,
            description = repo.repoDescription,
            avatarUrl = repo.repoOwnerAvatarUrl,
            installedPackageName = repo.installedPackageName,
            platforms = repo.platforms,
            version = repo.latestVersion,
            stars = repo.stars,
            status = repo.status,
            context = stringResource(Res.string.library_viewed, formatRelativeLong(repo.viewedAt)),
            contextIcon = Icons.Outlined.History,
            onOwnerClick = onDevProfileClick,
            action = {
                KomiIconButton(
                    icon = Icons.Outlined.Close,
                    contentDescription = stringResource(Res.string.recently_viewed_remove_cd),
                    onClick = onRemoveClick,
                    variant = KomiButtonVariant.Text,
                    size = KomiIconButtonSize.Sm,
                )
            },
        )
    }
}
