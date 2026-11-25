package zed.rainxch.core.presentation.personality

import androidx.compose.ui.graphics.Color

// A GitHub issue label's chip fills in the label's own color; a color that won't parse falls back to this gray.
val Color.labelFill: Color get() = copy(alpha = 0.18f)

val LabelFallbackColor = Color(0xFF888888)
