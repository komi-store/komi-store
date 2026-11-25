package zed.rainxch.core.presentation.personality

import androidx.compose.ui.graphics.Color
import zed.rainxch.core.presentation.personality.model.PersonalityColors

// The contribution calendar's five activity levels, from the empty cell up to the full accent, each opaque.
val PersonalityColors.contributionLevels: List<Color>
    get() {
        val empty = surfaceContainerHigh
        return listOf(
            empty,
            primary.copy(alpha = 0.25f).opaqueOver(empty),
            primary.copy(alpha = 0.5f).opaqueOver(empty),
            primary.copy(alpha = 0.75f).opaqueOver(empty),
            primary,
        )
    }

private fun Color.opaqueOver(background: Color): Color {
    val invAlpha = 1f - alpha
    return Color(
        red = red * alpha + background.red * invAlpha,
        green = green * alpha + background.green * invAlpha,
        blue = blue * alpha + background.blue * invAlpha,
        alpha = 1f,
    )
}
