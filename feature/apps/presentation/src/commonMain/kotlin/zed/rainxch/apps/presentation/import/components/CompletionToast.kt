package zed.rainxch.apps.presentation.import.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.external_import_completion_action_view_all
import zed.rainxch.githubstore.core.presentation.res.external_import_completion_headline
import zed.rainxch.githubstore.core.presentation.res.external_import_completion_skipped_subline

@Composable
fun CompletionToast(
    trackedCount: Int,
    skipped: Int,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KomiIcon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(72.dp),
            )

            KomiText(
                text =
                    pluralStringResource(
                        Res.plurals.external_import_completion_headline,
                        trackedCount,
                        trackedCount,
                    ),
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )

            if (skipped > 0) {
                KomiText(
                    text = stringResource(Res.string.external_import_completion_skipped_subline, skipped),
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            KomiButton(
                onClick = onExit,
                label = stringResource(Res.string.external_import_completion_action_view_all),
                variant = KomiButtonVariant.Primary,
            )
        }
    }
}
