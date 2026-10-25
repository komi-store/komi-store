package zed.rainxch.core.presentation.components.reviews

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.manga.decoration.hardShadow
import zed.rainxch.core.presentation.spacing.Spacing
import zed.rainxch.core.presentation.utils.formatCount
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.reviews_count
import zed.rainxch.githubstore.core.presentation.res.reviews_star_bar
import kotlin.math.roundToInt

// The average on a cookie (an ink stamp in Manga) over its stars and the review count, beside one bar per
// star level. The cookie turns into place and the bars grow in, once per visit. With [onStarsClick], a bar
// picks its level, which stays lit as [selectedStars] while the other bars dim.
@Composable
fun RatingSummaryPanel(
    summary: RatingSummaryUi,
    modifier: Modifier = Modifier,
    selectedStars: Int? = null,
    onStarsClick: ((Int) -> Unit)? = null,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    // Not again when the panel scrolls back into view.
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val playEntrance = !entered && personality.motion.animates
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            AverageBadge(average = summary.average, playEntrance = playEntrance)
            StarRating(rating = summary.average ?: 0.0, starSize = 16.dp)
            KomiText(
                text = pluralStringResource(Res.plurals.reviews_count, summary.count, formatCount(summary.count.toLong())),
                role = KomiTextRole.Caption,
                color = colors.onSurfaceVariant,
            )
        }
        Column(
            modifier = Modifier.weight(1f).then(if (onStarsClick != null) Modifier.selectableGroup() else Modifier),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val largest = summary.starCounts.maxOrNull()?.takeIf { it > 0 } ?: 1
            summary.starCounts.forEachIndexed { index, count ->
                val stars = 5 - index
                StarBar(
                    stars = stars,
                    count = count,
                    fraction = count.toFloat() / largest,
                    selected = selectedStars == stars,
                    dimmed = selectedStars != null && selectedStars != stars,
                    entranceDelayMillis = if (playEntrance) index * BAR_STAGGER_MILLIS else null,
                    onClick = onStarsClick?.let { { it(stars) } },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AverageBadge(
    average: Double?,
    playEntrance: Boolean,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val manga = personality is MangaPersonality
    val turn = remember { Animatable(if (playEntrance) ENTRANCE_TURN else 0f) }
    LaunchedEffect(Unit) { turn.animateTo(0f, personality.motion.bouncySpatial()) }
    val cookie = MaterialShapes.Cookie9Sided.toShape()
    if (manga) {
        Box(
            modifier =
                Modifier
                    .size(76.dp)
                    .rotate(MANGA_STAMP_TILT)
                    .hardShadow(offset = DpOffset(3.dp, 3.dp), color = colors.shadow)
                    .background(colors.primary)
                    .border(3.dp, colors.outline),
            contentAlignment = Alignment.Center,
        ) {
            AverageText(average = average, color = colors.onPrimary)
        }
    } else {
        Box(modifier = Modifier.size(88.dp), contentAlignment = Alignment.Center) {
            // Only the cookie turns, so the number stays upright.
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { rotationZ = turn.value }
                        .background(colors.primaryContainer, cookie),
            )
            AverageText(average = average, color = colors.onPrimaryContainer)
        }
    }
}

// An empty star stands in when there is no average yet.
@Composable
private fun AverageText(
    average: Double?,
    color: Color,
) {
    if (average != null) {
        BasicText(
            text = formatAverage(average),
            style =
                LocalPersonality.current.type.displayEmphasized
                    .copy(color = color),
        )
    } else {
        KomiIcon(imageVector = Icons.Rounded.StarBorder, contentDescription = null, tint = color, modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun StarBar(
    stars: Int,
    count: Int,
    fraction: Float,
    selected: Boolean,
    dimmed: Boolean,
    entranceDelayMillis: Int?,
    onClick: (() -> Unit)?,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val manga = personality is MangaPersonality
    val description = pluralStringResource(Res.plurals.reviews_star_bar, count, stars, count)
    val rowShape = if (manga) RectangleShape else RoundedCornerShape(50)
    val grown = remember { Animatable(if (entranceDelayMillis != null) 0f else fraction) }
    LaunchedEffect(fraction) {
        entranceDelayMillis?.let { delay(it.toLong()) }
        grown.animateTo(fraction, personality.motion.defaultSpatial())
    }
    val interaction =
        if (onClick != null) {
            Modifier.clip(rowShape).selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        } else {
            Modifier
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(interaction)
                .then(
                    when {
                        !selected -> Modifier

                        // Manga's container is the bar's own red, so the row gets an ink frame instead.
                        manga -> Modifier.border(2.dp, colors.outline)

                        else -> Modifier.background(colors.primaryContainer, rowShape)
                    },
                ).semantics(mergeDescendants = true) { contentDescription = description }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KomiText(
            text = stars.toString(),
            role = KomiTextRole.LabelSmall,
            color =
                when {
                    !selected -> colors.onSurfaceVariant
                    manga -> colors.primary
                    else -> colors.onPrimaryContainer
                },
            modifier = Modifier.width(10.dp).clearAndSetSemantics {},
        )
        BarTrack(fraction = { grown.value }, dimmed = dimmed, modifier = Modifier.weight(1f))
    }
}

// A pill in Classic, an inked strip in Manga. [fraction] is read at layout, so growing doesn't recompose.
@Composable
private fun BarTrack(
    fraction: () -> Float,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val manga = personality is MangaPersonality
    val shape = if (manga) RectangleShape else RoundedCornerShape(50)
    Box(
        modifier =
            modifier
                .height(if (manga) 12.dp else 10.dp)
                .clip(shape)
                .background(if (manga) colors.surface else colors.surfaceVariant)
                .then(if (manga) Modifier.border(2.dp, colors.outline) else Modifier),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .layout { measurable, constraints ->
                        // A spring can overshoot; the fill stays within its track.
                        val width = (constraints.maxWidth * fraction().coerceIn(0f, 1f)).roundToInt()
                        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                        layout(width, placeable.height) { placeable.placeRelative(0, 0) }
                    }.clip(shape)
                    .background(if (dimmed) colors.outlineVariant else colors.primary),
        )
    }
}

private const val BAR_STAGGER_MILLIS = 60
private const val ENTRANCE_TURN = -40f
private const val MANGA_STAMP_TILT = -4f
