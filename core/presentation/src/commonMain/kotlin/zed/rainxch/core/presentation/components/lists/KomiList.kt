package zed.rainxch.core.presentation.components.lists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.inputs.KomiSwitch
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.manga.MangaAccent
import zed.rainxch.core.presentation.personality.manga.MangaPaper
import zed.rainxch.core.presentation.personality.manga.decoration.hardShadow
import zed.rainxch.core.presentation.personality.manga.decoration.inkFocusRing
import zed.rainxch.core.presentation.personality.mangaPersonality
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.spacing.Spacing

private val SelectionBarWidth = 5.dp
private val ClassicDividerInset = 56.dp
private const val DISABLED_ALPHA = 0.45f

// Classic draws Material 3 Expressive's segmented list: one tonal tile per row, 2dp apart,
// with the outer corners rounded more than the inner ones.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KomiList(
    items: ImmutableList<KomiListItem>,
    modifier: Modifier = Modifier,
) {
    when (val personality = LocalPersonality.current) {
        is MangaPersonality -> {
            KomiListContainer(modifier = modifier) {
                items.forEachIndexed { index, item ->
                    ItemRow(item = item, showDivider = index < items.lastIndex)
                }
            }
        }

        is ClassicPersonality -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                items.forEachIndexed { index, item ->
                    ItemRow(
                        item = item,
                        showDivider = false,
                        modifier =
                            Modifier
                                .clip(ListItemDefaults.segmentedShapes(index, items.size).shape)
                                .background(personality.colors.surfaceContainer),
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: KomiListItem,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    KomiListRow(
        title = item.title,
        modifier = modifier,
        subtitle = item.subtitle,
        icon = item.icon,
        onClick = rowClick(item),
        onLongClick = item.onLongClick,
        destructive = item.destructive,
        selected = item.selected,
        enabled = item.enabled,
        showDivider = showDivider,
        trailing = { RowTrailing(item.trailing) },
    )
}

@Composable
fun KomiListContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    when (val personality = LocalPersonality.current) {
        is MangaPersonality -> {
            val colors = personality.colors
            Column(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .hardShadow(offset = DpOffset(4.dp, 4.dp), color = colors.shadow, shape = RectangleShape)
                        .background(color = colors.surface)
                        .border(width = personality.shape.borderPanel, color = colors.outline),
                content = content,
            )
        }

        is ClassicPersonality -> {
            val colors = personality.colors
            Column(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(personality.shape.corner))
                        .background(color = colors.surfaceContainer)
                        .padding(6.dp),
                content = content,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KomiListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    strong: Boolean = false,
    destructive: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    showDivider: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val shape = personality.shape
    val isManga = personality is MangaPersonality
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val titleColor =
        when {
            destructive -> colors.error
            selected && !isManga -> colors.onPrimaryContainer
            else -> colors.onSurface
        }
    val subtitleColor =
        if (selected && !isManga) colors.onPrimaryContainer else colors.onSurfaceVariant
    val clickModifier =
        when {
            onClick == null && onLongClick == null -> {
                Modifier
            }

            isManga -> {
                Modifier.combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onLongClick = onLongClick,
                    onClick = onClick ?: {},
                )
            }

            else -> {
                Modifier.combinedClickable(enabled = enabled, onLongClick = onLongClick, onClick = onClick ?: {})
            }
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (isManga) Modifier.inkFocusRing(focused = { focused }, color = colors.primary) else Modifier)
                .then(if (!isManga) Modifier.clip(RoundedCornerShape(shape.cornerSmall)) else Modifier)
                .then(rowFill(selected, hovered, isManga, colors.surfaceVariant, colors.primaryContainer))
                .then(rowDivider(showDivider, isManga, icon != null, colors.outline, colors.outlineVariant))
                .then(selectionBar(selected && isManga, colors.primary))
                .then(clickModifier)
                .padding(
                    horizontal = if (isManga) 14.dp else 12.dp,
                    vertical = if (isManga) 12.dp else 10.dp,
                ).alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isManga) 14.dp else 16.dp),
    ) {
        if (icon != null) {
            RowLeadingIcon(
                icon = icon,
                isManga = isManga,
                selected = selected,
                destructive = destructive,
                colors = colors,
                borderWidth = shape.borderChip,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            KomiText(
                text = title,
                role = if (strong) KomiTextRole.CardTitle else KomiTextRole.ItemTitle,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            subtitle?.let {
                KomiText(
                    text = it,
                    role = KomiTextRole.Body,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        trailing?.invoke()
    }
}

@Composable
private fun RowLeadingIcon(
    icon: ImageVector,
    isManga: Boolean,
    selected: Boolean,
    destructive: Boolean,
    colors: zed.rainxch.core.presentation.personality.model.PersonalityColors,
    borderWidth: androidx.compose.ui.unit.Dp,
) {
    if (isManga) {
        Box(
            modifier =
                Modifier
                    .size(42.dp)
                    .background(color = colors.background)
                    .border(width = borderWidth, color = colors.outline),
            contentAlignment = Alignment.Center,
        ) {
            KomiIcon(
                imageVector = icon,
                contentDescription = null,
                tint = if (destructive) colors.error else colors.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
    } else {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.primary else colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            KomiIcon(
                imageVector = icon,
                contentDescription = null,
                tint =
                    when {
                        selected -> colors.onPrimary
                        destructive -> colors.error
                        else -> colors.onSurfaceVariant
                    },
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private fun rowFill(
    selected: Boolean,
    hovered: Boolean,
    isManga: Boolean,
    mangaFill: androidx.compose.ui.graphics.Color,
    classicFill: androidx.compose.ui.graphics.Color,
): Modifier =
    when {
        selected -> Modifier.background(if (isManga) mangaFill else classicFill)
        hovered && isManga -> Modifier.background(mangaFill)
        else -> Modifier
    }

private fun rowDivider(
    show: Boolean,
    isManga: Boolean,
    hasLeading: Boolean,
    mangaColor: androidx.compose.ui.graphics.Color,
    classicColor: androidx.compose.ui.graphics.Color,
): Modifier =
    if (!show) {
        Modifier
    } else {
        Modifier.drawBehind {
            val stroke = (if (isManga) 2.dp else 1.dp).toPx()
            val startX = if (!isManga && hasLeading) ClassicDividerInset.toPx() else 0f
            val y = size.height - stroke / 2f
            drawLine(
                color = if (isManga) mangaColor else classicColor,
                start = Offset(startX, y),
                end = Offset(size.width, y),
                strokeWidth = stroke,
            )
        }
    }

private fun selectionBar(
    show: Boolean,
    color: androidx.compose.ui.graphics.Color,
): Modifier =
    if (!show) {
        Modifier
    } else {
        Modifier.drawBehind {
            drawRect(color = color, size = Size(SelectionBarWidth.toPx(), size.height))
        }
    }

@Composable
private fun RowTrailing(trailing: KomiListTrailing) {
    val colors = LocalPersonality.current.colors
    val isManga = LocalPersonality.current is MangaPersonality
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (trailing) {
            KomiListTrailing.None -> {}

            KomiListTrailing.Chevron -> {
                Chevron()
            }

            is KomiListTrailing.Value -> {
                KomiText(text = trailing.text, role = KomiTextRole.Mono, color = colors.onSurfaceVariant, maxLines = 1)

                Chevron()
            }

            is KomiListTrailing.Badge -> {
                Box(
                    modifier =
                        Modifier
                            .then(if (isManga) Modifier else Modifier.clip(RoundedCornerShape(50)))
                            .background(if (isManga) colors.primary else colors.error)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    KomiText(
                        text = trailing.count.toString(),
                        role = KomiTextRole.Mono,
                        color = if (isManga) colors.onPrimary else colors.onError,
                        maxLines = 1,
                    )
                }

                Chevron()
            }

            KomiListTrailing.UnreadDot -> {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .then(if (isManga) Modifier else Modifier.clip(CircleShape))
                            .background(colors.error),
                )

                Chevron()
            }

            is KomiListTrailing.Toggle -> {
                KomiSwitch(checked = trailing.checked, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun Chevron() {
    KomiIcon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = LocalPersonality.current.colors.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
}

private fun rowClick(item: KomiListItem): () -> Unit {
    val trailing = item.trailing
    return if (trailing is KomiListTrailing.Toggle) {
        { trailing.onCheckedChange(!trailing.checked) }
    } else {
        item.onClick
    }
}

@Composable
private fun PreviewLists() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        KomiList(
            items =
                persistentListOf(
                    KomiListItem(
                        title = "Stars",
                        subtitle = "Your starred repositories from GitHub",
                        icon = Icons.Outlined.Star,
                        selected = true,
                        onClick = {},
                    ),
                    KomiListItem(
                        title = "Favourites",
                        subtitle = "Saved locally",
                        icon = Icons.Outlined.Favorite,
                        trailing = KomiListTrailing.Value("24"),
                        onClick = {},
                    ),
                    KomiListItem(
                        title = "Recently Viewed",
                        subtitle = "Repositories you have visited",
                        icon = Icons.Outlined.Schedule,
                        onClick = {},
                    ),
                ),
        )

        KomiList(
            items =
                persistentListOf(
                    KomiListItem(
                        title = "What's New",
                        subtitle = "Recent releases",
                        icon = Icons.Outlined.Campaign,
                        trailing = KomiListTrailing.Badge(3),
                        onClick = {},
                    ),
                    KomiListItem(
                        title = "Announcements",
                        subtitle = "News and notices",
                        icon = Icons.Outlined.Notifications,
                        trailing = KomiListTrailing.UnreadDot,
                        onClick = {},
                    ),
                    KomiListItem(
                        title = "Push notifications",
                        subtitle = "Toggles in place",
                        icon = Icons.Outlined.Notifications,
                        trailing = KomiListTrailing.Toggle(checked = true, onCheckedChange = {}),
                        onClick = {},
                    ),
                    KomiListItem(
                        title = "Logout",
                        icon = Icons.AutoMirrored.Filled.Logout,
                        trailing = KomiListTrailing.None,
                        destructive = true,
                        onClick = {},
                    ),
                ),
        )
    }
}

@Preview
@Composable
private fun KomiListMangaDayPreview() {
    PersonalityPreview(mangaPersonality()) { PreviewLists() }
}

@Preview
@Composable
private fun KomiListMangaNightPreview() {
    PersonalityPreview(mangaPersonality(paper = MangaPaper.NIGHT, accent = MangaAccent.SUN)) { PreviewLists() }
}

@Preview
@Composable
private fun KomiListMangaNordPreview() {
    PersonalityPreview(mangaPersonality(paper = MangaPaper.NORD, accent = MangaAccent.FROST)) { PreviewLists() }
}

@Preview
@Composable
private fun KomiListClassicPreview() {
    PersonalityPreview(classicPersonality()) { PreviewLists() }
}

@Preview
@Composable
private fun KomiListClassicDarkPreview() {
    PersonalityPreview(classicPersonality(dark = true)) { PreviewLists() }
}
