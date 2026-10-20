package zed.rainxch.core.presentation.components.overlays

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import zed.rainxch.core.presentation.components.bars.LocalInBar
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.layout.currentFormFactor
import zed.rainxch.core.presentation.layout.isCompact
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.manga.MangaAccent
import zed.rainxch.core.presentation.personality.manga.MangaPaper
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
    // In a bar the menu opens as a popup above its button, as on desktop, so the button can show
    // that its menu is open; elsewhere a phone gets a sheet.
    val sheet = currentFormFactor().isCompact && !LocalInBar.current
    val onPick: (KomiMenuItem) -> Unit = { item ->
        expanded = false
        onSelect(item)
    }

    Box(modifier = modifier) {
        CompositionLocalProvider(LocalMenuOpen provides expanded) {
            trigger { expanded = !expanded }
        }
        if (!sheet) {
            WideMenu(
                expanded = expanded,
                onDismiss = { expanded = false },
                entries = entries,
                value = value,
                onPick = onPick,
            )
        }
    }

    if (sheet && expanded) {
        KomiSheet(
            onDismiss = { expanded = false },
            placement = KomiSheetPlacement.Bottom,
        ) {
            KomiMenuList(entries = entries, onSelect = onPick, value = value)
        }
    }
}

// Whether the menu of the trigger being drawn is open; a bar's KomiIconButton shows a close mark.
internal val LocalMenuOpen = staticCompositionLocalOf { false }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
                MangaMenuEntries(entries = entries, value = value, onPick = onPick)
            }
        }

        is ClassicPersonality -> {
            DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
                ClassicMenuList(entries = entries, value = value, onPick = onPick, inPopup = true)
            }
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
            KomiMenuList(entries = PreviewMenu, onSelect = {})
        }
        Box(
            modifier =
                Modifier
                    .background(colors.surface)
                    .then(if (isManga) Modifier.border(3.dp, colors.outline) else Modifier),
        ) {
            KomiMenuList(entries = PreviewSorts, onSelect = {}, value = "stars")
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
