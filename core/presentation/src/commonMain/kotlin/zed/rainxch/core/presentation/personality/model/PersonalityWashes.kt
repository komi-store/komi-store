package zed.rainxch.core.presentation.personality.model

import androidx.compose.ui.graphics.Color

// Accent washes for link tiles, a step above primaryTint: the tile fill, an icon well on it, and its outline.
val PersonalityColors.primaryWash: Color get() = primary.copy(alpha = 0.14f)
val PersonalityColors.primaryWell: Color get() = primary.copy(alpha = 0.22f)
val PersonalityColors.primaryEdge: Color get() = primary.copy(alpha = 0.35f)

val PersonalityColors.inkFaint: Color get() = onSurface.copy(alpha = 0.45f)
