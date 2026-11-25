package zed.rainxch.core.presentation.personality

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import zed.rainxch.core.presentation.personality.model.PersonalityColors

// A download's progress fill while it runs, and while it is paused.
val PersonalityColors.progressFill: Color get() = primary.copy(alpha = 0.22f)

val PersonalityColors.pausedProgressFill: Color get() = onSurfaceVariant.copy(alpha = 0.16f)

// Secondary content drawn in a color the caller picks, such as a caption under a button's label.
val Color.secondaryContent: Color get() = copy(alpha = 0.78f)

// A badge's or chip's fill in its own text color; the strong one marks something dangerous.
val Color.softFill: Color get() = copy(alpha = 0.16f)

val Color.strongFill: Color get() = copy(alpha = 0.22f)

// The plate behind a large icon, in the icon's color.
val Color.iconPlate: Color get() = copy(alpha = 0.15f)

// A row's fill while the pointer hovers it, in the row's content color.
val Color.hoverFill: Color get() = copy(alpha = 0.08f)

// A Japanese kicker beside a label, in the label's color.
val Color.kickerContent: Color get() = copy(alpha = 0.8f)

// The fill behind a favourite toggle that is on, under its error-colored heart.
val PersonalityColors.favouriteFill: Color get() = error.copy(alpha = 0.45f)

// Collapsed content fades into this color toward its bottom edge.
val Color.fadeOutBrush: Brush
    get() =
        Brush.verticalGradient(
            colorStops =
                arrayOf(
                    0f to copy(alpha = 0f),
                    0.35f to copy(alpha = 0.10f),
                    0.6f to copy(alpha = 0.35f),
                    0.8f to copy(alpha = 0.7f),
                    1f to this,
                ),
        )
