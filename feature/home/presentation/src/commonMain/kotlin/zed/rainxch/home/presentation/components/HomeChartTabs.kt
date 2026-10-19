package zed.rainxch.home.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.manga.decoration.hardShadow
import zed.rainxch.core.presentation.personality.usesDecor
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.home_chart_popular
import zed.rainxch.githubstore.core.presentation.res.home_chart_popular_jp
import zed.rainxch.githubstore.core.presentation.res.home_chart_releases
import zed.rainxch.githubstore.core.presentation.res.home_chart_releases_jp
import zed.rainxch.githubstore.core.presentation.res.home_chart_trending
import zed.rainxch.githubstore.core.presentation.res.home_chart_trending_jp
import zed.rainxch.home.presentation.model.ChartTab

@Composable
fun HomeChartTabs(
    selected: ChartTab,
    onSelect: (ChartTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChartTab.entries.forEach { tab ->
            ChartTabSegment(
                tab = tab,
                selected = tab == selected,
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChartTabSegment(
    tab: ChartTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val shape = RoundedCornerShape(personality.shape.cornerSmall)

    val bg = if (selected) colors.primary else colors.surface
    val fg = if (selected) colors.onPrimary else colors.onSurface
    val kicker = if (selected) colors.onPrimary.copy(alpha = 0.85f) else colors.onSurfaceVariant

    val borderWidth =
        when (personality) {
            is MangaPersonality -> 2.5.dp
            is ClassicPersonality -> 1.dp
        }
    val shadowModifier =
        when (personality) {
            is MangaPersonality -> {
                if (selected) {
                    Modifier.hardShadow(offset = DpOffset(3.dp, 3.dp), color = colors.shadow, shape = shape)
                } else {
                    Modifier
                }
            }

            is ClassicPersonality -> {
                Modifier
            }
        }

    Column(
        modifier =
            modifier
                .then(shadowModifier)
                .clip(shape)
                .background(bg)
                .border(BorderStroke(borderWidth, colors.outline), shape)
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KomiText(
            text =
                stringResource(
                    when (tab) {
                        ChartTab.Trending -> Res.string.home_chart_trending
                        ChartTab.Releases -> Res.string.home_chart_releases
                        ChartTab.Popular -> Res.string.home_chart_popular
                    },
                ),
            role = KomiTextRole.Label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.W800,
            maxLines = 1,
        )

        if (personality.usesDecor) {
            KomiText(
                text =
                    stringResource(
                        when (tab) {
                            ChartTab.Trending -> Res.string.home_chart_trending_jp
                            ChartTab.Releases -> Res.string.home_chart_releases_jp
                            ChartTab.Popular -> Res.string.home_chart_popular_jp
                        },
                    ),
                role = KomiTextRole.Label,
                color = kicker,
                fontSize = 9.sp,
                uppercase = false,
                maxLines = 1,
            )
        }
    }
}
