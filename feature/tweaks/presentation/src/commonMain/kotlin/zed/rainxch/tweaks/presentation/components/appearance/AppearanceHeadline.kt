package zed.rainxch.tweaks.presentation.components.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.core.domain.model.appearance.AppPersonality
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.classic.classicAccentSwatch
import zed.rainxch.core.presentation.personality.classic.systemWallpaperColorScheme
import zed.rainxch.core.presentation.personality.manga.mangaAccentSwatch
import zed.rainxch.core.presentation.personality.toAccentId
import zed.rainxch.core.presentation.personality.toMangaAccent
import zed.rainxch.tweaks.presentation.components.shell.SettingsGroup
import zed.rainxch.tweaks.presentation.components.shell.TweaksDecorSlot
import zed.rainxch.tweaks.presentation.components.shell.tweaksKicker
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.tweaks_accent_cobalt
import zed.rainxch.githubstore.core.presentation.res.tweaks_accent_crimson
import zed.rainxch.githubstore.core.presentation.res.tweaks_accent_frost
import zed.rainxch.githubstore.core.presentation.res.tweaks_accent_mono
import zed.rainxch.githubstore.core.presentation.res.tweaks_accent_sun
import zed.rainxch.githubstore.core.presentation.res.tweaks_accent_wallpaper

private val AccentOrder =
    listOf(
        AccentId.CRIMSON,
        AccentId.COBALT,
        AccentId.SUN,
        AccentId.FROST,
        AccentId.MONO,
    )

@Composable
fun AppearanceHeadline(
    personality: AppPersonality,
    accent: AccentId,
    onPersonalitySelected: (AppPersonality) -> Unit,
    onAccentSelected: (AccentId) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(modifier = modifier) {
        Column(modifier = Modifier.padding(start = 15.dp, end = 15.dp, top = 16.dp, bottom = 17.dp)) {
            HeadlineLabel(latin = "Personality", slot = TweaksDecorSlot.Personality)
            Row(
                modifier = Modifier.padding(top = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PersonalityTile(
                    kind = AppPersonality.MANGA,
                    selected = personality == AppPersonality.MANGA,
                    onClick = { onPersonalitySelected(AppPersonality.MANGA) },
                    modifier = Modifier.weight(1f),
                )
                PersonalityTile(
                    kind = AppPersonality.CLASSIC,
                    selected = personality == AppPersonality.CLASSIC,
                    onClick = { onPersonalitySelected(AppPersonality.CLASSIC) },
                    modifier = Modifier.weight(1f),
                )
            }

            HeadlineLabel(latin = "Accent", slot = TweaksDecorSlot.Accent, modifier = Modifier.padding(top = 18.dp))
            AccentRow(accent = accent, onAccentSelected = onAccentSelected, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun HeadlineLabel(
    latin: String,
    slot: TweaksDecorSlot,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val kicker = tweaksKicker(slot)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KomiText(
            text = latin,
            role = KomiTextRole.Stamp,
            color = colors.onSurfaceVariant,
        )
        if (kicker != null) {
            KomiText(
                text = kicker,
                role = KomiTextRole.Caption,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccentRow(
    accent: AccentId,
    onAccentSelected: (AccentId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isManga = LocalPersonality.current is MangaPersonality
    val colors = LocalPersonality.current.colors
    val wallpaper = if (isManga) null else systemWallpaperColorScheme(colors.isDark)
    val swatches =
        remember(isManga, colors.isDark, wallpaper) {
            val wallpaperSwatch = wallpaper?.let { AccentId.WALLPAPER to (it.primary to it.onPrimary) }
            listOfNotNull(wallpaperSwatch) +
                AccentOrder.map { entry ->
                    entry to if (isManga) mangaAccentSwatch(entry.toMangaAccent()) else classicAccentSwatch(entry, colors.isDark)
                }
        }
    // Manga has no wallpaper accent and draws it as Crimson, so Crimson shows as picked.
    val picked = if (isManga) accent.toMangaAccent().toAccentId() else accent

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        swatches.forEach { (entry, swatch) ->
            AccentSwatch(
                fill = swatch?.first ?: colors.onSurface,
                onFill = swatch?.second ?: colors.background,
                mono = isManga && entry == AccentId.MONO,
                selected = picked == entry,
                label = stringResource(entry.label),
                onClick = { onAccentSelected(entry) },
                icon = if (entry == AccentId.WALLPAPER) Icons.Outlined.Wallpaper else null,
            )
        }
    }
}

private val AccentId.label: StringResource
    get() =
        when (this) {
            AccentId.MONO -> Res.string.tweaks_accent_mono
            AccentId.CRIMSON -> Res.string.tweaks_accent_crimson
            AccentId.COBALT -> Res.string.tweaks_accent_cobalt
            AccentId.SUN -> Res.string.tweaks_accent_sun
            AccentId.FROST -> Res.string.tweaks_accent_frost
            AccentId.WALLPAPER -> Res.string.tweaks_accent_wallpaper
        }
