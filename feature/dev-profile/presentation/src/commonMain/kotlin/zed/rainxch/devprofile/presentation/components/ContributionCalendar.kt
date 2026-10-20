package zed.rainxch.devprofile.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.devprofile.domain.model.ContributionCalendar
import zed.rainxch.devprofile.domain.model.ContributionDay
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.dev_profile_contribution_activity
import zed.rainxch.githubstore.core.presentation.res.dev_profile_contributions_load_failed
import zed.rainxch.githubstore.core.presentation.res.dev_profile_contributions_less
import zed.rainxch.githubstore.core.presentation.res.dev_profile_contributions_more
import zed.rainxch.githubstore.core.presentation.res.dev_profile_contributions_this_year
import zed.rainxch.githubstore.core.presentation.res.loading

private val CELL_SIZE = 11.dp
private val CELL_GAP = 3.dp

@Composable
fun ContributionCalendarCard(
    contributions: ContributionCalendar?,
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
                else -> CalendarGrid(days = contributions.days)
            }
        }
    }
}

@Composable
private fun CalendarGrid(days: List<ContributionDay>) {
    if (days.isEmpty()) return
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val baseTint = remember(colors.surfaceContainerHigh) { colors.surfaceContainerHigh }
    val primary = colors.primary
    val palette = remember(primary, baseTint) {
        listOf(
            baseTint,
            primary.copy(alpha = 0.25f).compositeOver(baseTint),
            primary.copy(alpha = 0.5f).compositeOver(baseTint),
            primary.copy(alpha = 0.75f).compositeOver(baseTint),
            primary,
        )
    }
    val scrollState = rememberScrollState()

    val firstDayOfWeek = remember(days) {
        val first = days.firstOrNull()?.date ?: return@remember 0
        dateToDayOfWeek(first)
    }
    val totalCells = firstDayOfWeek + days.size
    val weeks = (totalCells + 6) / 7

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
    ) {
        Canvas(
            modifier = Modifier.size(
                width = (CELL_SIZE + CELL_GAP) * weeks,
                height = (CELL_SIZE + CELL_GAP) * 7,
            ),
        ) {
            val cellPx = CELL_SIZE.toPx()
            val gapPx = CELL_GAP.toPx()
            val stride = cellPx + gapPx
            val radius = CornerRadius(cellPx * 0.18f, cellPx * 0.18f)

            for (i in days.indices) {
                val cellIndex = firstDayOfWeek + i
                val week = cellIndex / 7
                val dow = cellIndex % 7
                val day = days[i]
                val color = palette[day.level.coerceIn(0, 4)]
                drawRoundRect(
                    color = color,
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
                modifier = Modifier
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

private fun dateToDayOfWeek(iso: String): Int {
    val parts = iso.split('-')
    if (parts.size != 3) return 0
    val y = parts[0].toIntOrNull() ?: return 0
    val m = parts[1].toIntOrNull() ?: return 0
    val d = parts[2].toIntOrNull() ?: return 0
    return zellerDayOfWeek(y, m, d)
}

private fun zellerDayOfWeek(year: Int, month: Int, day: Int): Int {
    var y = year
    var m = month
    if (m < 3) {
        m += 12
        y -= 1
    }
    val k = y % 100
    val j = y / 100
    val h = (day + (13 * (m + 1)) / 5 + k + k / 4 + j / 4 + 5 * j) % 7
    return ((h + 6) % 7)
}

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    val invA = 1f - a
    return Color(
        red = red * a + background.red * invA,
        green = green * a + background.green * invA,
        blue = blue * a + background.blue * invA,
        alpha = 1f,
    )
}
