package zed.rainxch.tweaks.presentation.components.shell

import androidx.compose.runtime.Composable
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.model.PersonalityDecor

@Composable
fun tweaksKicker(slot: TweaksDecorSlot): String? =
    when (LocalPersonality.current.decor) {
        PersonalityDecor.None -> null
        PersonalityDecor.MangaKicker -> slot.mangaKicker
    }
