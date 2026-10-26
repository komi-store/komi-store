package zed.rainxch.core.presentation.locals

import androidx.compose.runtime.staticCompositionLocalOf
import zed.rainxch.core.presentation.personality.statusColors
import zed.rainxch.core.presentation.status.StatusColors

val LocalStatusColors = staticCompositionLocalOf<StatusColors> { statusColors(dark = false) }
