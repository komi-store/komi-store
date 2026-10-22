package zed.rainxch.tweaks.presentation.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.overlays.KomiDialog
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.*

@Composable
fun ClearDownloadsDialog(
    cacheSize: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    KomiDialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false,
            ),
        modifier =
            modifier
                .padding(16.dp)
                .clip(RoundedCornerShape(LocalPersonality.current.shape.corner))
                .background(colors.surfaceContainer)
                .padding(20.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KomiText(
                text = stringResource(Res.string.delete_downloads_confirmation_title),
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
            )

            KomiText(
                text = stringResource(Res.string.delete_downloads_confirmation_message, cacheSize),
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                KomiButton(
                    onClick = onDismissRequest,
                    label = stringResource(Res.string.cancel),
                    variant = KomiButtonVariant.Text,
                    size = KomiButtonSize.Sm,
                )

                KomiButton(
                    onClick = onConfirm,
                    label = stringResource(Res.string.delete_all),
                    variant = KomiButtonVariant.Destructive,
                    size = KomiButtonSize.Sm,
                )
            }
        }
    }
}
