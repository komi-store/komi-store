package zed.rainxch.core.presentation.components.reviews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.formatCount
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.reviews_count
import zed.rainxch.githubstore.core.presentation.res.reviews_star_bar

// The average, its stars and the review count on the left; one bar per star level on the right.
@Composable
fun RatingSummaryPanel(
    summary: RatingSummaryUi,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KomiText(
                text = summary.average?.let(::formatAverage) ?: "–",
                role = KomiTextRole.Display,
                color = colors.onSurface,
            )
            StarRating(rating = summary.average ?: 0.0, starSize = 16.dp)
            KomiText(
                text = pluralStringResource(Res.plurals.reviews_count, summary.count, formatCount(summary.count.toLong())),
                role = KomiTextRole.Caption,
                color = colors.onSurfaceVariant,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val largest = summary.starCounts.maxOrNull()?.takeIf { it > 0 } ?: 1
            summary.starCounts.forEachIndexed { index, count ->
                StarBar(stars = 5 - index, count = count, fraction = count.toFloat() / largest)
            }
        }
    }
}

@Composable
private fun StarBar(
    stars: Int,
    count: Int,
    fraction: Float,
) {
    val colors = LocalPersonality.current.colors
    val shape = RoundedCornerShape(50)
    val description = pluralStringResource(Res.plurals.reviews_star_bar, count, stars, count)
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KomiText(
            text = stars.toString(),
            role = KomiTextRole.LabelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(10.dp),
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(shape)
                    .background(colors.surfaceVariant),
        ) {
            if (fraction > 0f) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(shape)
                            .background(colors.primary),
                )
            }
        }
    }
}
