package zed.rainxch.core.presentation.personality

import androidx.compose.runtime.Immutable
import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.core.presentation.personality.classic.ClassicMotion
import zed.rainxch.core.presentation.personality.classic.ClassicShadow
import zed.rainxch.core.presentation.personality.classic.ClassicShape
import zed.rainxch.core.presentation.personality.classic.ClassicType
import zed.rainxch.core.presentation.personality.classic.classicColors
import zed.rainxch.core.presentation.personality.model.PersonalityColors
import zed.rainxch.core.presentation.personality.model.PersonalityMotion
import zed.rainxch.core.presentation.personality.model.PersonalityShadow
import zed.rainxch.core.presentation.personality.model.PersonalityShape
import zed.rainxch.core.presentation.personality.model.PersonalityType

@Immutable
data class ClassicPersonality(
    override val colors: PersonalityColors,
    override val type: PersonalityType,
    override val shape: PersonalityShape,
    override val shadow: PersonalityShadow,
    override val motion: PersonalityMotion,
) : Personality

fun classicPersonality(
    dark: Boolean = false,
    amoled: Boolean = false,
    accent: AccentId = AccentId.COBALT,
): ClassicPersonality =
    ClassicPersonality(
        colors = classicColors(dark, amoled, accent),
        type = ClassicType,
        shape = ClassicShape,
        shadow = ClassicShadow,
        motion = ClassicMotion,
    )
