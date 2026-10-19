package zed.rainxch.core.presentation.components.buttons

import androidx.compose.ui.graphics.Color
import zed.rainxch.core.presentation.personality.model.PersonalityColors
import zed.rainxch.core.presentation.personality.model.ensureContrast

internal fun mangaButtonContainer(
    variant: KomiButtonVariant,
    colors: PersonalityColors,
): Color =
    when (variant) {
        KomiButtonVariant.Primary -> colors.primary
        KomiButtonVariant.Destructive -> colors.error
        KomiButtonVariant.Tonal -> colors.surfaceVariant
        KomiButtonVariant.Outline, KomiButtonVariant.Text -> Color.Transparent
    }

internal fun mangaButtonContent(
    variant: KomiButtonVariant,
    colors: PersonalityColors,
    ambient: Color,
): Color =
    when (variant) {
        KomiButtonVariant.Primary -> {
            ensureContrast(colors.onPrimary, colors.primary, colors.onBackground, colors.background)
        }

        KomiButtonVariant.Destructive -> {
            ensureContrast(colors.onError, colors.error, colors.onBackground, colors.background)
        }

        KomiButtonVariant.Tonal -> {
            ensureContrast(colors.onSurface, colors.surfaceVariant, colors.onBackground, colors.background)
        }

        KomiButtonVariant.Outline, KomiButtonVariant.Text -> {
            ambient
        }
    }
