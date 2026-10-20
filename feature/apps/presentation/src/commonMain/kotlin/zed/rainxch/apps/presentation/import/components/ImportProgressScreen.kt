package zed.rainxch.apps.presentation.import.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.import.model.ImportPhase
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.external_import_progress_auto_importing
import zed.rainxch.githubstore.core.presentation.res.external_import_progress_scanning
import zed.rainxch.githubstore.core.presentation.res.external_import_progress_skip
import zed.rainxch.githubstore.core.presentation.res.external_import_progress_subtitle_count
import zed.rainxch.githubstore.core.presentation.res.external_import_progress_working

@Composable
fun ImportProgressScreen(
    phase: ImportPhase,
    totalCandidates: Int,
    modifier: Modifier = Modifier,
    canSkip: Boolean = false,
    onSkip: () -> Unit = {},
) {
    val headline =
        when (phase) {
            ImportPhase.Scanning -> stringResource(Res.string.external_import_progress_scanning)
            ImportPhase.AutoImporting -> stringResource(Res.string.external_import_progress_auto_importing)
            else -> stringResource(Res.string.external_import_progress_working)
        }

    val colors = LocalPersonality.current.colors

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            KomiCircularProgress(
                modifier = Modifier.size(56.dp),
            )

            KomiText(
                text = headline,
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            KomiText(
                text =
                    pluralStringResource(
                        Res.plurals.external_import_progress_subtitle_count,
                        totalCandidates,
                        totalCandidates,
                    ),
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            AnimatedVisibility(
                visible = canSkip,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                KomiButton(
                    onClick = onSkip,
                    label = stringResource(Res.string.external_import_progress_skip),
                    variant = KomiButtonVariant.Text,
                )
            }
        }
    }
}
