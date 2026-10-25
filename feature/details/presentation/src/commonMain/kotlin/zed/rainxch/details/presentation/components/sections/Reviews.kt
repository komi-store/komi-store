package zed.rainxch.details.presentation.components.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.reviews.RatingSummaryPanel
import zed.rainxch.core.presentation.components.reviews.RatingSummaryUi
import zed.rainxch.core.presentation.components.reviews.ReviewCard
import zed.rainxch.core.presentation.components.reviews.ReviewUi
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.reviews_empty
import zed.rainxch.githubstore.core.presentation.res.reviews_section_title
import zed.rainxch.githubstore.core.presentation.res.reviews_see_all
import zed.rainxch.githubstore.core.presentation.res.reviews_write

// One item, so the cards keep their own spacing instead of the list's 24dp.
fun LazyListScope.reviews(
    summary: RatingSummaryUi,
    topReviews: ImmutableList<ReviewUi>,
    canWrite: Boolean,
    onWrite: () -> Unit,
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
            topReviews.forEach { review -> ReviewCard(review = review) }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canWrite) {
                    KomiButton(
                        onClick = onWrite,
                        label = stringResource(Res.string.reviews_write),
                        variant = KomiButtonVariant.Tonal,
                        leadingIcon = Icons.Rounded.RateReview,
                    )
                }
                if (summary.count > 0) {
                    KomiButton(
                        onClick = onSeeAll,
                        label = stringResource(Res.string.reviews_see_all),
                        variant = KomiButtonVariant.Outline,
                    )
                }
            }
        }
    }
}
