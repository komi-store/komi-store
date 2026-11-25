package zed.rainxch.auth.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.auth.presentation.AuthenticationAction
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.pat_cancel
import zed.rainxch.githubstore.core.presentation.res.pat_input_label
import zed.rainxch.githubstore.core.presentation.res.pat_input_placeholder
import zed.rainxch.githubstore.core.presentation.res.pat_open_settings
import zed.rainxch.githubstore.core.presentation.res.pat_sheet_description
import zed.rainxch.githubstore.core.presentation.res.pat_sheet_title
import zed.rainxch.githubstore.core.presentation.res.pat_submit

@Composable
internal fun PatSignInSheet(
    input: String,
    error: String?,
    isSubmitting: Boolean,
    onAction: (AuthenticationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors

    KomiSheet(
        onDismiss = { if (!isSubmitting) onAction(AuthenticationAction.DismissPatSheet) },
        modifier = modifier,
        placement = KomiSheetPlacement.Bottom,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            KomiText(
                text = stringResource(Res.string.pat_sheet_title),
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
            )

            KomiText(
                text = stringResource(Res.string.pat_sheet_description),
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
            )

            KomiButton(
                onClick = { onAction(AuthenticationAction.OpenPatSettingsPage) },
                label = stringResource(Res.string.pat_open_settings),
                variant = KomiButtonVariant.Outline,
                size = KomiButtonSize.Md,
                fullWidth = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
            )

            KomiTextField(
                value = input,
                onValueChange = { onAction(AuthenticationAction.OnPatInputChanged(it)) },
                label = stringResource(Res.string.pat_input_label),
                placeholder = stringResource(Res.string.pat_input_placeholder),
                error = error,
                password = true,
                keyboardType = KeyboardType.Password,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KomiButton(
                    onClick = { onAction(AuthenticationAction.DismissPatSheet) },
                    label = stringResource(Res.string.pat_cancel),
                    enabled = !isSubmitting,
                    variant = KomiButtonVariant.Outline,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp),
                )

                KomiButton(
                    onClick = { onAction(AuthenticationAction.SubmitPat) },
                    label = stringResource(Res.string.pat_submit),
                    enabled = !isSubmitting && input.isNotBlank(),
                    loading = isSubmitting,
                    variant = KomiButtonVariant.Primary,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp),
                )
            }
        }
    }
}
