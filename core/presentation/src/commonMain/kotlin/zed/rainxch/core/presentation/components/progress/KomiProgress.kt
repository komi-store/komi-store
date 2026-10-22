package zed.rainxch.core.presentation.components.progress

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KomiCircularProgress(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val personality = LocalPersonality.current
    val resolved = if (color != Color.Unspecified) color else personality.colors.primary
    when (personality) {
        is MangaPersonality -> {
            CircularProgressIndicator(
                modifier = modifier,
                color = resolved,
                strokeWidth = 4.dp,
                strokeCap = StrokeCap.Butt,
            )
        }

        is ClassicPersonality -> {
            CircularWavyProgressIndicator(
                modifier = modifier,
                color = resolved,
            )
        }
    }
}

@Composable
fun KomiCircularProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val personality = LocalPersonality.current
    val resolved = if (color != Color.Unspecified) color else personality.colors.primary
    CircularProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = resolved,
        strokeCap = if (personality is MangaPersonality) StrokeCap.Butt else StrokeCap.Round,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KomiLinearProgress(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val personality = LocalPersonality.current
    val resolved = if (color != Color.Unspecified) color else personality.colors.primary
    when (personality) {
        is MangaPersonality -> {
            LinearProgressIndicator(
                modifier = modifier,
                color = resolved,
                trackColor = personality.colors.surfaceVariant,
                strokeCap = StrokeCap.Butt,
            )
        }

        is ClassicPersonality -> {
            LinearWavyProgressIndicator(
                modifier = modifier,
                color = resolved,
            )
        }
    }
}

@Composable
fun KomiLinearProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val personality = LocalPersonality.current
    val resolved = if (color != Color.Unspecified) color else personality.colors.primary
    LinearProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = resolved,
        trackColor = personality.colors.surfaceVariant,
        strokeCap = if (personality is MangaPersonality) StrokeCap.Butt else StrokeCap.Round,
    )
}

// A screen or panel waiting a few seconds for its content. Classic shows Material 3 Expressive's
// shape-morphing indicator, Manga its ink spinner. Waits inside buttons and rows, and long ones
// (sign-in, scans), stay on KomiCircularProgress.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KomiLoadingIndicator(modifier: Modifier = Modifier) {
    when (val personality = LocalPersonality.current) {
        is MangaPersonality -> KomiCircularProgress(modifier = modifier)
        is ClassicPersonality -> LoadingIndicator(modifier = modifier, color = personality.colors.primary)
    }
}
