package zed.rainxch.core.presentation.components.overlays

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.layout.currentFormFactor
import zed.rainxch.core.presentation.layout.isCompact
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.manga.MangaAccent
import zed.rainxch.core.presentation.personality.manga.MangaPaper
import zed.rainxch.core.presentation.personality.manga.decoration.inkFocusRing
import zed.rainxch.core.presentation.personality.mangaPersonality
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview

@Composable
fun KomiDropdown(
    entries: ImmutableList<KomiMenuEntry>,
    onSelect: (KomiMenuItem) -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    trigger: @Composable (onClick: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val compact = currentFormFactor().isCompact
    val onPick: (KomiMenuItem) -> Unit = { item ->
        expanded = false
        onSelect(item)
    }

    Box(modifier = modifier) {
        trigger { expanded = !expanded }
        if (!compact) {
            WideMenu(
                expanded = expanded,
                onDismiss = { expanded = false },
                entries = entries,
                value = value,
                onPick = onPick,
            )
        }
    }

    if (compact && expanded) {
        KomiSheet(
            onDismiss = { expanded = false },
            placement = KomiSheetPlacement.Bottom,
        ) {
            MenuEntries(entries = entries, value = value, onPick = onPick)
        }
    }
}

@Composable
private fun WideMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    entries: ImmutableList<KomiMenuEntry>,
    value: String?,
    onPick: (KomiMenuItem) -> Unit,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    when (personality) {
        is MangaPersonality -> {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = onDismiss,
                shape = RectangleShape,
                containerColor = colors.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(3.dp, colors.outline),
            ) {
                MenuEntries(entries = entries, value = value, onPick = onPick)
            }
        }

        is ClassicPersonality -> {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = onDismiss,
            ) {
                MenuEntries(entries = entries, value = value, onPick = onPick)
            }
        }
    }
}

@Composable
private fun MenuEntries(
    entries: ImmutableList<KomiMenuEntry>,
    value: String?,
    onPick: (KomiMenuItem) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val isManga = LocalPersonality.current is MangaPersonality
    entries.forEach { entry ->
        when (entry) {
            KomiMenuDivider -> {
                HorizontalDivider(
                    thickness = if (isManga) 2.dp else 1.dp,
                    color = if (isManga) colors.outline.copy(alpha = 0.3f) else colors.outlineVariant,
                )
            }

            is KomiMenuItem -> {
                KomiMenuRow(
                    item = entry,
                    selected = value != null && value == entry.id,
                    onClick = { onPick(entry) },
                )
            }
        }
    }
}

@Composable
private fun KomiMenuRow(
    item: KomiMenuItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val isManga = personality is MangaPersonality
    val fg = if (item.tone == KomiMenuTone.Danger) colors.error else colors.onSurface
    val alpha = if (item.enabled) 1f else 0.4f

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val wash =
        if (isManga && item.enabled && (hovered || pressed)) colors.onSurface.copy(alpha = 0.10f) else Color.Transparent

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (isManga) 40.dp else 48.dp)
                .hoverable(interaction, enabled = item.enabled)
                .then(if (isManga) Modifier.inkFocusRing(focused = { focused }, color = colors.primary) else Modifier)
                .clickable(
                    enabled = item.enabled,
                    interactionSource = interaction,
                    indication = if (isManga) null else ripple(),
                    onClick = onClick,
                ).background(wash)
                .padding(horizontal = if (isManga) 14.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isManga) 11.dp else 12.dp),
    ) {
        item.icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier.size(if (isManga) 17.dp else 20.dp),
                tint = fg.copy(alpha = alpha),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            KomiText(
                text = item.label,
                role = KomiTextRole.Body,
                color = fg.copy(alpha = alpha),
                fontSize = 14.sp,
                fontWeight = if (isManga) FontWeight.W700 else null,
                uppercase = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.subtitle?.let { subtitle ->
                KomiText(
                    text = subtitle,
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant.copy(alpha = alpha),
                    fontSize = 12.sp,
                    uppercase = false,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(if (isManga) 16.dp else 18.dp),
                tint = colors.primary.copy(alpha = alpha),
            )
        }
    }
}

private val PreviewMenu =
    persistentListOf(
        KomiMenuItem(id = "share", label = "Share", icon = Icons.Default.Share),
        KomiMenuItem(id = "report", label = "Report issue", icon = Icons.Default.Flag),
        KomiMenuDivider,
        KomiMenuItem(id = "remove", label = "Uninstall", icon = Icons.Default.Delete, tone = KomiMenuTone.Danger),
    )

private val PreviewSorts =
    persistentListOf<KomiMenuEntry>(
        KomiMenuItem(id = "stars", label = "Most stars"),
        KomiMenuItem(id = "recent", label = "Recently updated"),
        KomiMenuItem(id = "downloads", label = "Most downloads"),
        KomiMenuItem(id = "name", label = "Name (A–Z)"),
    )

@Composable
private fun PreviewMenuPanel() {
    val colors = LocalPersonality.current.colors
    val isManga = LocalPersonality.current is MangaPersonality
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        KomiDropdown(
            entries = PreviewMenu,
            onSelect = {},
            trigger = { onClick -> KomiIconButton(Icons.Default.MoreVert, "More", onClick, variant = KomiButtonVariant.Outline) },
        )
        Box(
            modifier =
                Modifier
                    .background(colors.surface)
                    .then(if (isManga) Modifier.border(3.dp, colors.outline) else Modifier),
        ) {
            Column {
                MenuEntries(entries = PreviewMenu, value = null, onPick = {})
            }
        }
        Box(
            modifier =
                Modifier
                    .background(colors.surface)
                    .then(if (isManga) Modifier.border(3.dp, colors.outline) else Modifier),
        ) {
            Column {
                MenuEntries(entries = PreviewSorts, value = "stars", onPick = {})
            }
        }
    }
}

@Preview
@Composable
private fun KomiDropdownMangaPreview() {
    PersonalityPreview(mangaPersonality()) { PreviewMenuPanel() }
}

@Preview
@Composable
private fun KomiDropdownMangaNightPreview() {
    PersonalityPreview(mangaPersonality(paper = MangaPaper.NIGHT, accent = MangaAccent.SUN)) { PreviewMenuPanel() }
}

@Preview
@Composable
private fun KomiDropdownClassicPreview() {
    PersonalityPreview(classicPersonality()) { PreviewMenuPanel() }
}
