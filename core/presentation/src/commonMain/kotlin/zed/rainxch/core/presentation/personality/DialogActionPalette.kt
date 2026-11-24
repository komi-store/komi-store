package zed.rainxch.core.presentation.personality

import androidx.compose.ui.graphics.Color

// Fixed action colors for a warning dialog, chosen by how dark its surface is rather than by the theme's accent.
class DialogActionPalette(
    val muted: Color,
    val confirm: Color,
)

fun dialogActionPalette(darkSurface: Boolean): DialogActionPalette = if (darkSurface) DialogActionsDark else DialogActionsLight

private val DialogActionsLight = DialogActionPalette(muted = Color(0xFF747474), confirm = Color(0xFF3B5BDB))

private val DialogActionsDark = DialogActionPalette(muted = Color(0xFFBCBCBC), confirm = Color(0xFFB6C4FF))
