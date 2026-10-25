package zed.rainxch.core.presentation.components.reviews

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.formatCompactCount
import zed.rainxch.githubstore.core.presentation.res.Res
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

// Five tappable stars for the write sheet; each is its own 44dp target.
@Composable
fun StarRatingInput(
    rating: Int,
    onRatingChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (star in 1..5) {
            val description = pluralStringResource(Res.plurals.reviews_stars, star, star)
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .selectable(selected = rating == star, role = Role.RadioButton) { onRatingChange(star) }
                        .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                KomiIcon(
                    imageVector = if (star <= rating) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = null,
                    tint = if (star <= rating) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
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
            text = "${formatAverage(average)} (${formatCompactCount(count)})",
            role = KomiTextRole.Caption,
            color = LocalPersonality.current.colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

fun formatAverage(average: Double): String {
    val tenths = kotlin.math.round(average * TENTHS).toInt()
    return "${tenths / TENTHS}.${tenths % TENTHS}"
}

private const val TENTHS = 10
