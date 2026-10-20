package zed.rainxch.core.presentation.components.buttons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.bars.LocalInBar
import zed.rainxch.core.presentation.components.dividers.KomiVerticalDivider
import zed.rainxch.core.presentation.components.overlays.KomiDropdown
import zed.rainxch.core.presentation.components.overlays.KomiMenuEntry
import zed.rainxch.core.presentation.components.overlays.KomiMenuItem
import zed.rainxch.core.presentation.components.overlays.KomiMenuTone
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.manga.MangaAccent
import zed.rainxch.core.presentation.personality.manga.MangaPaper
import zed.rainxch.core.presentation.personality.mangaPersonality
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.spacing.Spacing
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.cd_more_actions

private const val DIVIDER_HEIGHT_FRACTION = 0.66f

@Composable
fun KomiActionRow(
    items: ImmutableList<KomiActionRowItem>,
    modifier: Modifier = Modifier,
    size: KomiIconButtonSize = KomiIconButtonSize.Md,
    maxVisible: Int = Int.MAX_VALUE,
    showLabels: Boolean = false,
) {
    val gap =
        when (LocalPersonality.current) {
            is MangaPersonality -> if (showLabels) Spacing.lg else Spacing.md
            is ClassicPersonality -> if (showLabels) Spacing.lg else Spacing.sm
        }
    val dividerHeight = size.metrics.box * DIVIDER_HEIGHT_FRACTION
    // A bar's pill already groups its buttons, and its overflow is the vertical one used elsewhere.
    val inBar = LocalInBar.current
    val overflowVariant =
        when (LocalPersonality.current) {
            is MangaPersonality -> KomiButtonVariant.Tonal
            is ClassicPersonality -> KomiButtonVariant.Text
        }

    val (visible, overflow, danger) =
        remember(items, maxVisible, showLabels) {
            val dangerItems = items.filter { it.variant == KomiButtonVariant.Destructive }
            val safeItems = items.filterNot { it.variant == KomiButtonVariant.Destructive }
            val canOverflow = !showLabels && safeItems.size > maxVisible
            Triple(
                if (canOverflow) safeItems.take(maxVisible) else safeItems,
                if (canOverflow) safeItems.drop(maxVisible) else emptyList(),
                dangerItems,
            )
        }
    val overflowEntries: ImmutableList<KomiMenuEntry> =
        remember(overflow) {
            overflow
                .map<KomiActionRowItem, KomiMenuEntry> { item ->
                    KomiMenuItem(
                        id = item.title,
                        label = item.title,
                        icon = item.icon,
                        tone = if (item.variant == KomiButtonVariant.Destructive) KomiMenuTone.Danger else KomiMenuTone.Default,
                        enabled = item.enabled,
                    )
                }.toImmutableList()
        }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visible.forEach { item ->
            KomiActionTile(item = item, size = size, showLabels = showLabels)
        }

        if (overflow.isNotEmpty()) {
            if (!inBar) {
                Box(modifier = Modifier.height(dividerHeight)) {
                    KomiVerticalDivider()
                }
            }

            KomiDropdown(
                entries = overflowEntries,
                onSelect = { menuItem -> overflow.firstOrNull { it.title == menuItem.id }?.onClick?.invoke() },
                trigger = { onClick ->
                    KomiIconButton(
                        icon = if (inBar) Icons.Rounded.MoreVert else Icons.Default.MoreHoriz,
                        contentDescription = stringResource(Res.string.cd_more_actions),
                        onClick = onClick,
                        variant = overflowVariant,
                        size = size,
                    )
                },
            )
        }

        if (danger.isNotEmpty()) {
            Box(modifier = Modifier.height(dividerHeight)) {
                KomiVerticalDivider()
            }

            danger.forEach { item ->
                KomiActionTile(item = item, size = size, showLabels = showLabels)
            }
        }
    }
}

@Composable
private fun KomiActionTile(
    item: KomiActionRowItem,
    size: KomiIconButtonSize,
    showLabels: Boolean,
) {
    if (showLabels) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            KomiIconButton(
                icon = item.icon,
                contentDescription = item.title,
                onClick = item.onClick,
                variant = item.variant,
                size = size,
                enabled = item.enabled,
            )

            KomiText(
                text = item.title,
                role = KomiTextRole.LabelSmall,
                color =
                    LocalPersonality.current.colors.onSurfaceVariant
                        .copy(alpha = if (item.enabled) 1f else 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        KomiIconButton(
            icon = item.icon,
            contentDescription = item.title,
            onClick = item.onClick,
            variant = item.variant,
            size = size,
            enabled = item.enabled,
        )
    }
}

private val previewActions =
    persistentListOf(
        KomiActionRowItem(Icons.Default.Share, "Share", {}),
        KomiActionRowItem(Icons.Default.Star, "Star", {}, variant = KomiButtonVariant.Primary),
        KomiActionRowItem(Icons.Default.Bookmark, "Save", {}),
        KomiActionRowItem(Icons.Default.Favorite, "Like", {}),
    )

private val previewOverflowActions =
    persistentListOf(
        KomiActionRowItem(Icons.Default.Star, "Star", {}, variant = KomiButtonVariant.Primary),
        KomiActionRowItem(Icons.Default.Share, "Share", {}),
        KomiActionRowItem(Icons.Default.Bookmark, "Save", {}),
        KomiActionRowItem(Icons.Default.Favorite, "Like", {}),
        KomiActionRowItem(Icons.Default.Refresh, "Sync", {}),
        KomiActionRowItem(Icons.Default.Settings, "Settings", {}),
    )

private val previewDestructiveActions =
    persistentListOf(
        KomiActionRowItem(Icons.Default.Edit, "Edit", {}),
        KomiActionRowItem(Icons.Default.Share, "Share", {}),
        KomiActionRowItem(Icons.Default.Settings, "Settings", {}),
        KomiActionRowItem(
            Icons.Default.Delete,
            "Delete",
            {},
            variant = KomiButtonVariant.Destructive,
        ),
    )

@Composable
private fun PreviewStack() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        KomiActionRow(items = previewActions)

        KomiActionRow(items = previewOverflowActions, maxVisible = 3)

        KomiActionRow(items = previewActions, showLabels = true)

        KomiActionRow(items = previewDestructiveActions)
    }
}

@Preview
@Composable
private fun KomiActionRowMangaPreview() {
    PersonalityPreview(mangaPersonality()) { PreviewStack() }
}

@Preview
@Composable
private fun KomiActionRowMangaNightPreview() {
    PersonalityPreview(
        mangaPersonality(
            paper = MangaPaper.NIGHT,
            accent = MangaAccent.SUN,
        ),
    ) { PreviewStack() }
}

@Preview
@Composable
private fun KomiActionRowMangaNordPreview() {
    PersonalityPreview(
        mangaPersonality(
            paper = MangaPaper.NORD,
            accent = MangaAccent.FROST,
        ),
    ) { PreviewStack() }
}

@Preview
@Composable
private fun KomiActionRowClassicPreview() {
    PersonalityPreview(classicPersonality()) { PreviewStack() }
}

@Preview
@Composable
private fun KomiActionRowClassicDarkPreview() {
    PersonalityPreview(classicPersonality(dark = true)) { PreviewStack() }
}
