package zed.rainxch.core.presentation.components.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.manga.decoration.inkFocusRing

// A menu's options on their own, for a sheet that lists them (a picker).
@Composable
fun KomiMenuList(
    entries: ImmutableList<KomiMenuEntry>,
    onSelect: (KomiMenuItem) -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
) {
    when (LocalPersonality.current) {
        is MangaPersonality -> {
            Column(modifier = modifier) {
                MangaMenuEntries(entries = entries, value = value, onPick = onSelect)
            }
        }

        is ClassicPersonality -> {
            ClassicMenuList(entries = entries, value = value, onPick = onSelect, inPopup = false, modifier = modifier)
        }
    }
}

// Material's expressive menu: the options between dividers form a group on a rounded surface, and
// each option is a shape of its own that morphs when pressed or selected, with its ripple inside.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ClassicMenuList(
    entries: ImmutableList<KomiMenuEntry>,
    value: String?,
    onPick: (KomiMenuItem) -> Unit,
    inPopup: Boolean,
    modifier: Modifier = Modifier,
) {
    val groups = remember(entries) { entries.groupsBetweenDividers() }
    Column(
        modifier = modifier.then(if (inPopup) Modifier.width(IntrinsicSize.Max) else Modifier.fillMaxWidth()),
        verticalArrangement = Arrangement.spacedBy(MenuDefaults.GroupSpacing),
    ) {
        groups.forEachIndexed { groupIndex, items ->
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(groupIndex, groups.size),
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = if (inPopup) MenuDefaults.ShadowElevation else 0.dp,
            ) {
                items.forEachIndexed { index, item ->
                    ClassicMenuItem(
                        item = item,
                        selectable = value != null,
                        selected = value == item.id,
                        shapes = MenuDefaults.itemShape(index, items.size),
                        onClick = { onPick(item) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClassicMenuItem(
    item: KomiMenuItem,
    selectable: Boolean,
    selected: Boolean,
    shapes: MenuItemShapes,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val text: @Composable () -> Unit = {
        KomiText(
            text = item.label,
            role = KomiTextRole.Label,
            color = LocalContentColor.current,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val supportingText: (@Composable () -> Unit)? =
        item.subtitle?.let { subtitle ->
            @Composable {
                KomiText(
                    text = subtitle,
                    role = KomiTextRole.Caption,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    // A pick-one option without an icon keeps an empty slot where its check appears: Material's
    // item otherwise under-reports its width and a long label is cut off in a popup.
    val leadingIcon: (@Composable () -> Unit)? =
        when {
            item.icon != null -> {
                { Icon(imageVector = item.icon, contentDescription = null, modifier = Modifier.size(MenuIconSize)) }
            }

            selectable -> {
                { Spacer(modifier = Modifier.size(MenuIconSize)) }
            }

            else -> {
                null
            }
        }

    if (selectable) {
        DropdownMenuItem(
            selected = selected,
            onClick = onClick,
            text = text,
            shapes = shapes,
            leadingIcon = leadingIcon,
            selectedLeadingIcon = { Icon(imageVector = Icons.Rounded.Check, contentDescription = null) },
            enabled = item.enabled,
            supportingText = supportingText,
        )
    } else {
        DropdownMenuItem(
            onClick = onClick,
            text = text,
            shape = shapes.shape,
            leadingIcon = leadingIcon,
            enabled = item.enabled,
            colors =
                if (item.tone == KomiMenuTone.Danger) {
                    MenuDefaults.itemColors(textColor = colors.error, leadingIconColor = colors.error)
                } else {
                    MenuDefaults.itemColors()
                },
            supportingText = supportingText,
        )
    }
}

private fun ImmutableList<KomiMenuEntry>.groupsBetweenDividers(): List<List<KomiMenuItem>> {
    val groups = mutableListOf(mutableListOf<KomiMenuItem>())
    forEach { entry ->
        when (entry) {
            KomiMenuDivider -> groups += mutableListOf<KomiMenuItem>()
            is KomiMenuItem -> groups.last() += entry
        }
    }
    return groups.filter { it.isNotEmpty() }
}

@Composable
internal fun MangaMenuEntries(
    entries: ImmutableList<KomiMenuEntry>,
    value: String?,
    onPick: (KomiMenuItem) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    entries.forEach { entry ->
        when (entry) {
            KomiMenuDivider -> {
                HorizontalDivider(thickness = 2.dp, color = colors.divider)
            }

            is KomiMenuItem -> {
                MangaMenuRow(
                    item = entry,
                    selected = value != null && value == entry.id,
                    onClick = { onPick(entry) },
                )
            }
        }
    }
}

@Composable
private fun MangaMenuRow(
    item: KomiMenuItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val fg = if (item.tone == KomiMenuTone.Danger) colors.error else colors.onSurface
    val alpha = if (item.enabled) 1f else 0.4f

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val wash = if (item.enabled && (hovered || pressed)) colors.pressOverlay else Color.Transparent

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .hoverable(interaction, enabled = item.enabled)
                .inkFocusRing(focused = { focused }, color = colors.primary)
                .clickable(
                    enabled = item.enabled,
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ).background(wash)
                .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item.icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = fg.copy(alpha = alpha),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            KomiText(
                text = item.label,
                role = KomiTextRole.Label,
                color = fg.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.subtitle?.let { subtitle ->
                KomiText(
                    text = subtitle,
                    role = KomiTextRole.Caption,
                    color = colors.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = colors.primary.copy(alpha = alpha),
            )
        }
    }
}

// Material's menu icon size; custom vectors would otherwise draw at their own size.
private val MenuIconSize = 24.dp
