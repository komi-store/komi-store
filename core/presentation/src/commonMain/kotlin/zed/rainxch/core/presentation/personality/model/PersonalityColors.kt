package zed.rainxch.core.presentation.personality.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Immutable
data class PersonalityColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val outline: Color,
    val outlineVariant: Color,
    val divider: Color,
    val error: Color,
    val onError: Color,
    val shadow: Color,
    val scrim: Color,
    val screentoneOpacity: Float,
    val gridOpacity: Float,
) {
    val isDark: Boolean get() = background.luminance() < 0.5f

    // Material's state-layer opacities over the base colors, so tints stay in step with the accent.
    val primaryTint: Color get() = primary.copy(alpha = 0.12f)
    val onPrimaryTint: Color get() = onPrimary.copy(alpha = 0.2f)
    val errorTint: Color get() = error.copy(alpha = 0.12f)
    val pressOverlay: Color get() = onSurface.copy(alpha = 0.1f)
    val disabledContent: Color get() = onSurface.copy(alpha = 0.38f)
    val disabledContainer: Color get() = onSurface.copy(alpha = 0.12f)
    val scrimOverlay: Color get() = scrim.copy(alpha = 0.5f)
}
