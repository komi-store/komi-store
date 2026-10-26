package zed.rainxch.core.presentation.personality

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import dev.snipme.highlights.model.SyntaxTheme
import dev.snipme.highlights.model.SyntaxThemes

object CodeSyntaxStyle {
    val bold: SpanStyle = SpanStyle(fontWeight = FontWeight.Bold)

    fun theme(dark: Boolean): SyntaxTheme = if (dark) SyntaxThemes.darcula() else SyntaxThemes.atom()

    // The highlighter gives colors as 0xRRGGBB, with no alpha byte.
    fun color(rgb: Int): SpanStyle = SpanStyle(color = Color(rgb).copy(alpha = 1f))
}
