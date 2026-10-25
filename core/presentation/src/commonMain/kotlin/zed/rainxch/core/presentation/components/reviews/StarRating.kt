package zed.rainxch.core.presentation.components.reviews

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StarHalf
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.formatCompactCount
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.reviews_average
import zed.rainxch.githubstore.core.presentation.res.reviews_compact_rating
import zed.rainxch.githubstore.core.presentation.res.reviews_rated
import zed.rainxch.githubstore.core.presentation.res.reviews_stars

// Rounds to the nearest half star; read out as "Rated 4.3 out of 5".
@Composable
fun StarRating(
    rating: Double,
    modifier: Modifier = Modifier,
    starSize: Dp = 14.dp,
) {
    val colors = LocalPersonality.current.colors
    val halves = (rating * 2).let { kotlin.math.round(it).toInt() }.coerceIn(0, 10)
    val description = stringResource(Res.string.reviews_rated, formatAverage(rating))
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        for (star in 1..5) {
            val icon =
                when {
                    halves >= star * 2 -> Icons.Rounded.Star
                    halves == star * 2 - 1 -> Icons.AutoMirrored.Rounded.StarHalf
                    else -> Icons.Rounded.StarBorder
                }
            val filled = halves >= star * 2 - 1
            KomiIcon(
                imageVector = icon,
                contentDescription = null,
                tint = if (filled) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(starSize),
            )
        }
    }
}

// Five tappable stars, each its own 44dp target. Stars that fill pop one after another from the left, so
// a tap on the fourth runs a wave along the row.
@Composable
fun StarRatingInput(
    rating: Int,
    onRatingChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The rating the row last showed; a plain holder, since the waves only need it at the next change.
    val shown = remember { intArrayOf(rating) }
    val from = shown[0]
    SideEffect { shown[0] = rating }
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (star in 1..5) {
            InputStar(
                star = star,
                filled = star <= rating,
                selected = rating == star,
                popDelayMillis = (star - from - 1) * STAR_STAGGER_MILLIS,
                onClick = { onRatingChange(star) },
            )
        }
    }
}

@Composable
private fun InputStar(
    star: Int,
    filled: Boolean,
    selected: Boolean,
    popDelayMillis: Int,
    onClick: () -> Unit,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val motion = personality.motion
    val description = pluralStringResource(Res.plurals.reviews_stars, star, star)
    val tint by animateColorAsState(
        targetValue = if (filled) colors.primary else colors.onSurfaceVariant,
        animationSpec = motion.fastEffects(),
        label = "star-tint",
    )
    val pop = remember { Animatable(1f) }
    LaunchedEffect(filled) {
        // A negative delay means the star was already filled before this change.
        if (!filled || popDelayMillis < 0) return@LaunchedEffect
        delay(popDelayMillis.toLong())
        pop.animateTo(STAR_POP_SCALE, motion.fastSpatial())
        pop.animateTo(1f, motion.bouncySpatial())
    }
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        KomiIcon(
            imageVector = if (filled) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = null,
            tint = tint,
            modifier =
                Modifier
                    .size(36.dp)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    },
        )
    }
}

// Stars plus "4.3 (128)", for a card or list row with no room for the full summary.
@Composable
fun CompactRating(
    average: Double,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StarRating(rating = average, starSize = 12.dp)
        KomiText(
            text = stringResource(Res.string.reviews_compact_rating, formatAverage(average), formatCompactCount(count)),
            role = KomiTextRole.Caption,
            color = LocalPersonality.current.colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// One decimal, with the locale's own decimal mark.
@Composable
fun formatAverage(average: Double): String {
    val tenths = kotlin.math.round(average * TENTHS).toInt()
    return stringResource(Res.string.reviews_average, tenths / TENTHS, tenths % TENTHS)
}

private const val TENTHS = 10
private const val STAR_STAGGER_MILLIS = 45
private const val STAR_POP_SCALE = 1.3f
