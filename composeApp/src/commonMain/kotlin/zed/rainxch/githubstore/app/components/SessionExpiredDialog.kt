package zed.rainxch.githubstore.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.KomiDialog
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.continue_as_guest
import zed.rainxch.githubstore.core.presentation.res.session_expired_hint
import zed.rainxch.githubstore.core.presentation.res.session_expired_message
import zed.rainxch.githubstore.core.presentation.res.session_expired_title
import zed.rainxch.githubstore.core.presentation.res.sign_in_again

@Composable
fun SessionExpiredDialog(
    onDismiss: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    KomiDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = {
            KomiIcon(
                imageVector = Icons.Default.LockOpen,
                contentDescription = null,
                tint = colors.error,
            )
        },
        title = {
            KomiText(
                text = stringResource(Res.string.session_expired_title),
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KomiText(
                    text = stringResource(Res.string.session_expired_message),
                    role = KomiTextRole.Body,
                    color = colors.outline,
                )

                KomiText(
                    text = stringResource(Res.string.session_expired_hint),
                    role = KomiTextRole.Body,
                    color = colors.primary,
                )
            }
        },
        confirmButton = {
            KomiButton(
                onClick = onSignIn,
                label = stringResource(Res.string.sign_in_again),
                variant = KomiButtonVariant.Primary,
                size = KomiButtonSize.Sm,
            )
        },
        dismissButton = {
            KomiButton(
                onClick = onDismiss,
                label = stringResource(Res.string.continue_as_guest),
                variant = KomiButtonVariant.Text,
                size = KomiButtonSize.Sm,
            )
        },
    )
}
