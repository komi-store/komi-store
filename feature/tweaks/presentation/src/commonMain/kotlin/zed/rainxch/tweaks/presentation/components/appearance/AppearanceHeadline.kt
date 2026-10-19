package zed.rainxch.tweaks.presentation.components.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.core.domain.model.appearance.AppPersonality
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.classic.classicAccentSwatch
import zed.rainxch.core.presentation.personality.manga.mangaAccentSwatch
import zed.rainxch.core.presentation.personality.toMangaAccent
import zed.rainxch.tweaks.presentation.components.shell.SettingsGroup
import zed.rainxch.tweaks.presentation.components.shell.TweaksDecorSlot
import zed.rainxch.tweaks.presentation.components.shell.tweaksKicker

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
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AccentOrder.forEach { entry ->
                    val isManga = LocalPersonality.current is MangaPersonality
                    val colors = LocalPersonality.current.colors
                    val mangaSwatch = if (isManga) mangaAccentSwatch(entry.toMangaAccent()) else null
                    val classicSwatch = if (isManga) null else classicAccentSwatch(entry, colors.isDark)

                    AccentSwatch(
                        fill = mangaSwatch?.first ?: classicSwatch?.first ?: colors.onSurface,
                        onFill = mangaSwatch?.second ?: classicSwatch?.second ?: colors.background,
                        mono = isManga && entry == AccentId.MONO,
                        selected = accent == entry,
                        onClick = { onAccentSelected(entry) },
                    )
                }
            }
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
            role = KomiTextRole.LegacyStamp,
            color = colors.onSurfaceVariant,
            fontSize = 13.sp,
        )
        if (kicker != null) {
            KomiText(
                text = kicker,
                role = KomiTextRole.LegacyLabel,
                color = colors.onSurfaceVariant,
                fontSize = 11.sp,
                uppercase = false,
            )
        }
    }
}
