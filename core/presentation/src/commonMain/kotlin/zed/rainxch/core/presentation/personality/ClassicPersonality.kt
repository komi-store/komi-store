package zed.rainxch.core.presentation.personality

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.core.presentation.personality.classic.ClassicMotion
import zed.rainxch.core.presentation.personality.classic.ClassicShadow
import zed.rainxch.core.presentation.personality.classic.ClassicShape
import zed.rainxch.core.presentation.personality.classic.ClassicType
import zed.rainxch.core.presentation.personality.classic.classicColorScheme
import zed.rainxch.core.presentation.personality.classic.toClassicColors
import zed.rainxch.core.presentation.personality.model.PersonalityColors
import zed.rainxch.core.presentation.personality.model.PersonalityMotion
import zed.rainxch.core.presentation.personality.model.PersonalityShadow
import zed.rainxch.core.presentation.personality.model.PersonalityShape
import zed.rainxch.core.presentation.personality.model.PersonalityType

@Immutable
data class ClassicPersonality(
    val colorScheme: ColorScheme,
    override val type: PersonalityType,
    override val shape: PersonalityShape,
    override val shadow: PersonalityShadow,
    override val motion: PersonalityMotion,
) : Personality {
    override val colors: PersonalityColors = colorScheme.toClassicColors()
}

fun classicPersonality(
    dark: Boolean = false,
    amoled: Boolean = false,
    accent: AccentId = AccentId.COBALT,
    wallpaper: ColorScheme? = null,
): ClassicPersonality =
    ClassicPersonality(
        colorScheme = classicColorScheme(accent, dark, amoled, wallpaper),
        type = ClassicType,
        shape = ClassicShape,
        shadow = ClassicShadow,
        motion = ClassicMotion,
    )
