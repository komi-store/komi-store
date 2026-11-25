package zed.rainxch.repopages.presentation.utils

import androidx.compose.ui.graphics.Color
import zed.rainxch.core.presentation.personality.LabelFallbackColor

private const val RGB_HEX_DIGITS = 6
private const val HEX_RADIX = 16

internal fun parseLabelColor(hex: String): Color {
    val clean = hex.trim().removePrefix("#")
    val argb = if (clean.length == RGB_HEX_DIGITS) "FF$clean".toLongOrNull(HEX_RADIX) else null
    return argb?.let { Color(it) } ?: LabelFallbackColor
}
