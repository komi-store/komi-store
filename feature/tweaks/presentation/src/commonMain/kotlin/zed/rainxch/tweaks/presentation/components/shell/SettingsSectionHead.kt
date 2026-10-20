package zed.rainxch.tweaks.presentation.components.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality

private val SkewedStamp =
    GenericShape { size, _ ->
        val k = size.height * 0.21f
        moveTo(k, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width - k, size.height)
        lineTo(0f, size.height)
        close()
    }

@Composable
fun SettingsSectionHead(
    label: String,
    slot: TweaksDecorSlot,
    modifier: Modifier = Modifier,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val kicker = tweaksKicker(slot)
    val isManga = personality is MangaPersonality
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (isManga) {
            Box(
                modifier =
                    Modifier
                        .size(width = 11.dp, height = 21.dp)
                        .background(colors.primary, SkewedStamp)
                        .border(2.dp, colors.outline, SkewedStamp),
            )
        }

        KomiText(
            text = label,
            role = KomiTextRole.SectionTitle,
            color = colors.onSurface,
        )

        if (kicker != null) {
            KomiText(
                text = kicker,
                role = KomiTextRole.Caption,
                color = colors.onSurfaceVariant,
            )
        }

        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(if (isManga) 2.dp else 1.dp)
                    .background(colors.divider),
        )
    }
}
