package zed.rainxch.apps.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.AppsAction
import zed.rainxch.apps.presentation.AppsViewModel
import zed.rainxch.apps.presentation.model.InstalledAppUi
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.cancel
import zed.rainxch.githubstore.core.presentation.res.confirm_discard_pending_message
import zed.rainxch.githubstore.core.presentation.res.confirm_discard_pending_title
import zed.rainxch.githubstore.core.presentation.res.discard_pending_install

@Composable
fun PendingDiscardSheet(
    app: InstalledAppUi,
    onAction: (AppsAction) -> Unit,
) {
    val confirmLabel = stringResource(Res.string.discard_pending_install)
    val cancelLabel = stringResource(Res.string.cancel)
    KomiSheet(
        onDismiss = {
            onAction(AppsAction.OnDismissDiscardPendingDialog)
        },
        placement = KomiSheetPlacement.Center,
        title = stringResource(Res.string.confirm_discard_pending_title),
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KomiButton(
                    onClick = {
                        onAction(AppsAction.OnDismissDiscardPendingDialog)
                    },
                    label = cancelLabel,
                    variant = KomiButtonVariant.Text,
                )
                KomiButton(
                    onClick = {
                        onAction(AppsAction.OnConfirmDiscardPendingInstall(app))
                    },
                    label = confirmLabel,
                    variant = KomiButtonVariant.Destructive,
                )
            }
        },
    ) {
        KomiText(
            text = stringResource(Res.string.confirm_discard_pending_message, app.appName),
            role = KomiTextRole.Body,
        )
    }
}