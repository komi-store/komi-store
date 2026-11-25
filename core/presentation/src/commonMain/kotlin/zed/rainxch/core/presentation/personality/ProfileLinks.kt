package zed.rainxch.core.presentation.personality

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import zed.rainxch.core.presentation.personality.model.PersonalityColors

// A developer profile's links: an @mention in the bio, and the outline of a link chip.
val PersonalityColors.mentionSpan: SpanStyle get() = SpanStyle(color = primary, fontWeight = FontWeight.SemiBold)

val PersonalityColors.linkChipEdge: Color get() = primary.copy(alpha = 0.4f)
