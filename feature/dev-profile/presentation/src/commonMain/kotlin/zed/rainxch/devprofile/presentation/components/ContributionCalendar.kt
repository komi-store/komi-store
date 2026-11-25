package zed.rainxch.devprofile.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.contributionLevels
import zed.rainxch.devprofile.presentation.model.ContributionCalendarUi
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.dev_profile_contribution_activity
import zed.rainxch.githubstore.core.presentation.res.dev_profile_contributions_less
import zed.rainxch.githubstore.core.presentation.res.dev_profile_contributions_load_failed
import zed.rainxch.githubstore.core.presentation.res.dev_profile_contributions_more
import zed.rainxch.githubstore.core.presentation.res.dev_profile_contributions_this_year
import zed.rainxch.githubstore.core.presentation.res.loading

private val CELL_SIZE = 11.dp
private val CELL_GAP = 3.dp
private const val DAYS_PER_WEEK = 7

@Composable
fun ContributionCalendarCard(
    contributions: ContributionCalendarUi?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    KomiSurface(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                KomiText(
                    text = stringResource(Res.string.dev_profile_contribution_activity),
                    role = KomiTextRole.SectionTitle,
                    color = colors.onSurface,
                )

                contributions?.let {
                    KomiText(
                        text = stringResource(Res.string.dev_profile_contributions_this_year, it.totalLastYear),
                        role = KomiTextRole.Mono,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                isLoading && contributions == null -> {
                    KomiText(
                        text = stringResource(Res.string.loading),
                        role = KomiTextRole.Body,
                        color = colors.onSurfaceVariant,
                    )
                }

                contributions == null -> {
                    KomiText(
                        text = stringResource(Res.string.dev_profile_contributions_load_failed),
                        role = KomiTextRole.Body,
                        color = colors.onSurfaceVariant,
                    )
                }

                contributions.levels.isNotEmpty() -> {
                    CalendarGrid(calendar = contributions)
                }
            }
        }
    }
}

@Composable
private fun CalendarGrid(
    calendar: ContributionCalendarUi,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val palette = remember(colors.primary, colors.surfaceContainerHigh) { colors.contributionLevels }
    val scrollState = rememberScrollState()

    val levels = calendar.levels
    val firstDayOfWeek = calendar.firstDayOfWeek
    val weeks = (firstDayOfWeek + levels.size + DAYS_PER_WEEK - 1) / DAYS_PER_WEEK

    Column(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
        ) {
            Canvas(
                modifier =
                    Modifier.size(
                        width = (CELL_SIZE + CELL_GAP) * weeks,
                        height = (CELL_SIZE + CELL_GAP) * DAYS_PER_WEEK,
                    ),
            ) {
                val cellPx = CELL_SIZE.toPx()
                val gapPx = CELL_GAP.toPx()
                val stride = cellPx + gapPx
                val radius = CornerRadius(cellPx * 0.18f, cellPx * 0.18f)

                for (i in levels.indices) {
                    val cellIndex = firstDayOfWeek + i
                    val week = cellIndex / DAYS_PER_WEEK
                    val dow = cellIndex % DAYS_PER_WEEK
                    drawRoundRect(
                        color = palette[levels[i].coerceIn(0, palette.lastIndex)],
                        topLeft = Offset(week * stride, dow * stride),
                        size = Size(cellPx, cellPx),
                        cornerRadius = radius,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            KomiText(
                text = stringResource(Res.string.dev_profile_contributions_less),
                role = KomiTextRole.Overline,
                color = colors.onSurfaceVariant,
            )

            palette.forEach { cell ->
                Box(
                    modifier =
                        Modifier
                            .size(CELL_SIZE)
                            .clip(RoundedCornerShape(shape.cornerSmall))
                            .background(cell),
                )
            }

            KomiText(
                text = stringResource(Res.string.dev_profile_contributions_more),
                role = KomiTextRole.Overline,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
