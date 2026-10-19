package zed.rainxch.core.presentation.components.text

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.manga.HeadlineMarker
import zed.rainxch.core.presentation.personality.manga.MangaAccent
import zed.rainxch.core.presentation.personality.manga.MangaPaper
import zed.rainxch.core.presentation.personality.mangaPersonality
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview

@Composable
fun KomiHeadline(
    text: String,
    modifier: Modifier = Modifier,
    role: KomiTextRole = KomiTextRole.SectionTitle,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
) {
    when (val personality = LocalPersonality.current) {
        is MangaPersonality -> {
            MangaHeadline(
                text = text,
                modifier = modifier,
                role = role,
                color = color,
                maxLines = maxLines,
                marker = personality.headlineMarker,
            )
        }

        is ClassicPersonality -> {
            KomiText(
                text = text,
                modifier = modifier,
                role = role,
                color = color,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MangaHeadline(
    text: String,
    modifier: Modifier = Modifier,
    role: KomiTextRole,
    color: Color,
    maxLines: Int,
    marker: HeadlineMarker,
) {
    if (marker == HeadlineMarker.None) {
        KomiText(
            text = text,
            modifier = modifier,
            role = role,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    val colors = LocalPersonality.current.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (marker) {
            HeadlineMarker.Stamp -> HeadlineStamp(fill = colors.primary, border = colors.outline)
            HeadlineMarker.SpeedLines -> HeadlineSpeedLines(color = colors.onSurface)
            HeadlineMarker.None -> Unit
        }
        KomiText(
            text = text,
            role = role,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val SkewStampShape =
    GenericShape { size, _ ->
        val skew = size.height * 0.30f
        moveTo(skew, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width - skew, size.height)
        lineTo(0f, size.height)
        close()
    }

@Composable
private fun HeadlineStamp(
    fill: Color,
    border: Color,
) {
    Box(
        modifier =
            Modifier
                .size(width = 12.dp, height = 22.dp)
                .background(color = fill, shape = SkewStampShape)
                .border(width = 2.dp, color = border, shape = SkewStampShape),
    )
}

@Composable
private fun HeadlineSpeedLines(color: Color) {
    Canvas(modifier = Modifier.size(width = 24.dp, height = 24.dp)) {
        val count = 4
        val stroke = 3.dp.toPx()
        val rise = size.height * 0.3f
        val gap = (size.height - rise) / (count + 1)
        for (i in 1..count) {
            val y = rise + gap * i
            drawLine(
                color = color,
                start = Offset(0f, y),
                end = Offset(size.width, y - rise),
                strokeWidth = stroke,
                cap = StrokeCap.Square,
            )
        }
    }
}

@Preview
@Composable
private fun KomiHeadlineStampPreview() {
    PersonalityPreview(mangaPersonality(headlineMarker = HeadlineMarker.Stamp)) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            KomiHeadline("Discover", role = KomiTextRole.Display)
            KomiHeadline("Trending Now")
        }
    }
}

@Preview
@Composable
private fun KomiHeadlineSpeedLinesPreview() {
    PersonalityPreview(mangaPersonality(headlineMarker = HeadlineMarker.SpeedLines)) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            KomiHeadline("Discover", role = KomiTextRole.Display)
            KomiHeadline("Trending Now")
        }
    }
}

@Preview
@Composable
private fun KomiHeadlineSpeedLinesDarkPreview() {
    PersonalityPreview(
        mangaPersonality(
            headlineMarker = HeadlineMarker.SpeedLines,
            paper = MangaPaper.NIGHT,
            accent = MangaAccent.FROST,
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            KomiHeadline("Discover", role = KomiTextRole.Display)
            KomiHeadline("Trending Now")
        }
    }
}

@Preview
@Composable
private fun KomiHeadlineClassicPreview() {
    PersonalityPreview(classicPersonality()) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            KomiHeadline("Discover", role = KomiTextRole.Display)
            KomiHeadline("Trending Now")
        }
    }
}

@Preview
@Composable
private fun KomiHeadlineNightPreview() {
    PersonalityPreview(
        mangaPersonality(
            paper = MangaPaper.NIGHT,
            accent = MangaAccent.SUN,
            headlineMarker = HeadlineMarker.Stamp,
        ),
    ) {
        KomiHeadline("Trending Now")
    }
}
