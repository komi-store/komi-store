package zed.rainxch.reviews.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.manga.decoration.hardShadow

// An empty star on a soft-burst plate (an inked, tilted box in Manga) over [message].
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReviewsEmpty(
    message: StringResource,
    modifier: Modifier = Modifier,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val plate =
        if (personality is MangaPersonality) {
            Modifier
                .rotate(MANGA_PLATE_TILT)
                .hardShadow(offset = DpOffset(3.dp, 3.dp), color = colors.shadow)
                .background(colors.surface)
                .border(3.dp, colors.outline)
        } else {
            Modifier.background(colors.primaryContainer, MaterialShapes.SoftBurst.toShape())
        }
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.size(88.dp).then(plate), contentAlignment = Alignment.Center) {
            KomiIcon(
                imageVector = Icons.Rounded.StarBorder,
                contentDescription = null,
                tint = if (personality is MangaPersonality) colors.onSurface else colors.onPrimaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }
        KomiText(
            text = stringResource(message),
            role = KomiTextRole.Body,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private const val MANGA_PLATE_TILT = -4f
