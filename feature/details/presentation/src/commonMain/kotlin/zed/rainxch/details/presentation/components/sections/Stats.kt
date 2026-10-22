package zed.rainxch.details.presentation.components.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.details.domain.model.RepoStats
import zed.rainxch.details.presentation.components.StatItem
import zed.rainxch.details.presentation.components.TextStatItem
import zed.rainxch.githubstore.core.presentation.res.*

fun LazyListScope.stats(
    repoStats: RepoStats,
    onOpenIssues: (() -> Unit)?,
    onOpenSecurity: (() -> Unit)?,
) {
    item {
        val colors = LocalPersonality.current.colors
        Spacer(Modifier.height(20.dp))

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            KomiText(
                text = stringResource(Res.string.details_stats_section),
                role = KomiTextRole.SectionTitle,
                color = colors.onBackground,
            )
        }

        StatsGrid(repoStats = repoStats, onOpenIssues = onOpenIssues, onOpenSecurity = onOpenSecurity)
    }
}

@Composable
private fun StatsGrid(
    repoStats: RepoStats,
    onOpenIssues: (() -> Unit)?,
    onOpenSecurity: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatItem(
                label = stringResource(Res.string.forks),
                stat = repoStats.forks,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatItem(
                label = stringResource(Res.string.stars),
                stat = repoStats.stars,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatItem(
                label = stringResource(Res.string.issues),
                stat = repoStats.openIssues,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = onOpenIssues,
            )
        }
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatItem(
                label = stringResource(Res.string.downloads),
                stat = repoStats.totalDownloads,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            TextStatItem(
                label = stringResource(Res.string.license),
                value = repoStats.license ?: stringResource(Res.string.license_none),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            if (onOpenSecurity != null) {
                TextStatItem(
                    label = stringResource(Res.string.repo_pages_details_security_button),
                    value = stringResource(Res.string.repo_pages_details_security_value),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = onOpenSecurity,
                )
            }
        }
    }
}
