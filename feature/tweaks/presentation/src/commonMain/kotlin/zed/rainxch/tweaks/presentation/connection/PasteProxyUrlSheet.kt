package zed.rainxch.tweaks.presentation.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_paste_url_body
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_paste_url_cta
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_paste_url_error
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_paste_url_placeholder
import zed.rainxch.githubstore.core.presentation.res.tweaks_connection_paste_url_title

@Composable
fun PasteProxyUrlSheet(
    input: String,
    isError: Boolean,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors

    KomiSheet(
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KomiText(
                text = stringResource(Res.string.tweaks_connection_paste_url_title),
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
            )

            KomiText(
                text = stringResource(Res.string.tweaks_connection_paste_url_body),
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
            )

            KomiTextField(
                value = input,
                onValueChange = onInputChange,
                label = stringResource(Res.string.tweaks_connection_paste_url_placeholder),
                error = if (isError) stringResource(Res.string.tweaks_connection_paste_url_error) else null,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))

            KomiButton(
                onClick = onSubmit,
                label = stringResource(Res.string.tweaks_connection_paste_url_cta),
                variant = KomiButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth(),
                enabled = input.isNotBlank(),
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}
