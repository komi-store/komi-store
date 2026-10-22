package zed.rainxch.apps.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import zed.rainxch.core.presentation.locals.LocalPersonality

internal val DowngradeWarningColor: Color
    @Composable get() = if (LocalPersonality.current.colors.isDark) Color(0xFFFF8A80) else Color(0xFFC62828)
