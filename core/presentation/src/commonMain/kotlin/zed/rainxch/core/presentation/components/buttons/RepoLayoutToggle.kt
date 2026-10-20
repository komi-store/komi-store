package zed.rainxch.core.presentation.components.buttons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.feed_layout_description_grid
import zed.rainxch.githubstore.core.presentation.res.feed_layout_description_list

@Composable
fun RepoLayoutToggle(
    isGridLayout: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: KomiIconButtonSize = KomiIconButtonSize.Md,
) {
    KomiIconButton(
        icon = if (isGridLayout) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
        contentDescription =
            stringResource(
                if (isGridLayout) Res.string.feed_layout_description_list else Res.string.feed_layout_description_grid,
            ),
        onClick = onToggle,
        variant = KomiButtonVariant.Primary,
        size = size,
        modifier = modifier,
    )
}
