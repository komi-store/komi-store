package zed.rainxch.profile.presentation.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.bars.KomiTopBarSize
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfaceElevation
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.domain.model.announcement.WhatsNewEntry
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.whats_new_history_empty
import zed.rainxch.githubstore.core.presentation.res.whats_new_title

@Composable
fun WhatsNewHistoryScreen(
    entries: List<WhatsNewEntry>,
    onNavigateBack: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    KomiScaffold(
        topBar = {
            KomiTopBar(
                title = stringResource(Res.string.whats_new_title),
                size = KomiTopBarSize.Compact,
                onBack = onNavigateBack,
            )
        },
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                KomiText(
                    text = stringResource(Res.string.whats_new_history_empty),
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(entries) { entry ->
                    WhatsNewEntryCard(entry)
                }
            }
        }
    }
}

@Composable
fun WhatsNewEntryCard(entry: WhatsNewEntry) {
    val colors = LocalPersonality.current.colors
    KomiSurface(
        modifier = Modifier.fillMaxWidth(),
        elevation = KomiSurfaceElevation.Card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                KomiText(
                    text = entry.versionName,
                    role = KomiTextRole.CardTitle,
                    color = colors.onSurface,
                )
                KomiText(
                    text = entry.releaseDate,
                    role = KomiTextRole.Caption,
                    color = colors.onSurfaceVariant,
                )
            }

            entry.sections.forEach { section ->
                SectionBlock(section)
            }
        }
    }
}
