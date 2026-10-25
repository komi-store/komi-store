package zed.rainxch.details.presentation.components.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.reviews.RatingSummaryPanel
import zed.rainxch.core.presentation.components.reviews.RatingSummaryUi
import zed.rainxch.core.presentation.components.reviews.ReviewCard
import zed.rainxch.core.presentation.components.reviews.ReviewUi
import zed.rainxch.core.presentation.components.reviews.StarRatingInput
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.reviews_empty
import zed.rainxch.githubstore.core.presentation.res.reviews_rate_this_app
import zed.rainxch.githubstore.core.presentation.res.reviews_section_title
import zed.rainxch.githubstore.core.presentation.res.reviews_see_all

// One item, so the cards keep their own spacing instead of the list's 24dp.
fun LazyListScope.reviews(
    summary: RatingSummaryUi,
    topReviews: ImmutableList<ReviewUi>,
    canWrite: Boolean,
    onRate: (Int) -> Unit,
    onSeeAll: () -> Unit,
) {
    item(key = "reviews") {
        val colors = LocalPersonality.current.colors
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KomiText(
                text = stringResource(Res.string.reviews_section_title),
                role = KomiTextRole.SectionTitle,
                color = colors.onBackground,
            )
            if (summary.count > 0) {
                RatingSummaryPanel(summary = summary)
            } else {
                KomiText(
                    text = stringResource(Res.string.reviews_empty),
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                )
            }
            if (canWrite) RateThisApp(onRate = onRate)
            topReviews.forEach { review -> ReviewCard(review = review) }
            if (summary.count > 0) {
                KomiButton(
                    onClick = onSeeAll,
                    label = stringResource(Res.string.reviews_see_all),
                    variant = KomiButtonVariant.Tonal,
                )
            }
        }
    }
}

// Five empty stars: a tap fills the row up to that star, then opens the composer with that rating.
@Composable
private fun RateThisApp(onRate: (Int) -> Unit) {
    val colors = LocalPersonality.current.colors
    val scope = rememberCoroutineScope()
    val currentOnRate by rememberUpdatedState(onRate)
    var picked by remember { mutableIntStateOf(0) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KomiText(
            text = stringResource(Res.string.reviews_rate_this_app),
            role = KomiTextRole.ItemTitle,
            color = colors.onBackground,
        )
        StarRatingInput(
            rating = picked,
            onRatingChange = { rating ->
                picked = rating
                // Long enough to see the stars fill before the screen changes.
                scope.launch {
                    delay(RATE_HANDOFF_MILLIS)
                    currentOnRate(rating)
                }
            },
        )
    }
}

private const val RATE_HANDOFF_MILLIS = 280L
