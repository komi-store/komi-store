package zed.rainxch.details.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.InstalledAppIcon
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.overlays.KomiDialog
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.dialogActionPalette
import zed.rainxch.details.presentation.model.DowngradeWarning
import zed.rainxch.details.presentation.model.SigningKeyPromptUi
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.cancel
import zed.rainxch.githubstore.core.presentation.res.confirm_uninstall_message
import zed.rainxch.githubstore.core.presentation.res.confirm_uninstall_title
import zed.rainxch.githubstore.core.presentation.res.details_unlink_external_app_dialog_body
import zed.rainxch.githubstore.core.presentation.res.details_unlink_external_app_dialog_confirm
import zed.rainxch.githubstore.core.presentation.res.details_unlink_external_app_dialog_title
import zed.rainxch.githubstore.core.presentation.res.dismiss
import zed.rainxch.githubstore.core.presentation.res.downgrade_warning_message
import zed.rainxch.githubstore.core.presentation.res.downgrade_warning_title
import zed.rainxch.githubstore.core.presentation.res.downgrade_warning_uninstall_note
import zed.rainxch.githubstore.core.presentation.res.install_anyway
import zed.rainxch.githubstore.core.presentation.res.install_permission_blocked_message
import zed.rainxch.githubstore.core.presentation.res.install_permission_unavailable
import zed.rainxch.githubstore.core.presentation.res.open_with_external_installer
import zed.rainxch.githubstore.core.presentation.res.signing_key_changed_message
import zed.rainxch.githubstore.core.presentation.res.signing_key_changed_title
import zed.rainxch.githubstore.core.presentation.res.uninstall
import zed.rainxch.githubstore.core.presentation.res.uninstall_first

// The page's prompts; each shows while its state asks for it.
@Composable
internal fun DetailsDialogs(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
) {
    state.downgradeWarning?.let { warning ->
        val targetName = state.installedApps.firstOrNull { it.packageName == warning.packageName }?.appName ?: warning.packageName
        DowngradeDialog(warning = warning, targetName = targetName, onAction = onAction)
    }
    state.signingKeyWarning?.let { warning -> SigningKeyDialog(warning = warning, onAction = onAction) }
    if (state.showUninstallConfirmation) {
        ConfirmDialog(
            title = Res.string.confirm_uninstall_title,
            text = stringResource(Res.string.confirm_uninstall_message, state.installedApp?.appName ?: ""),
            confirmLabel = Res.string.uninstall,
            onConfirm = { onAction(DetailsAction.OnConfirmUninstall) },
            onDismiss = { onAction(DetailsAction.OnDismissUninstallConfirmation) },
        )
    }
    if (state.showUnlinkConfirmation) {
        ConfirmDialog(
            title = Res.string.details_unlink_external_app_dialog_title,
            text = stringResource(Res.string.details_unlink_external_app_dialog_body, state.installedApp?.appName ?: ""),
            confirmLabel = Res.string.details_unlink_external_app_dialog_confirm,
            onConfirm = { onAction(DetailsAction.OnConfirmUnlinkExternalApp) },
            onDismiss = { onAction(DetailsAction.OnDismissUnlinkConfirmation) },
        )
    }
    if (state.showExternalInstallerPrompt) {
        ConfirmDialog(
            title = Res.string.install_permission_unavailable,
            text = stringResource(Res.string.install_permission_blocked_message),
            confirmLabel = Res.string.open_with_external_installer,
            dismissLabel = Res.string.dismiss,
            onConfirm = { onAction(DetailsAction.OpenWithExternalInstaller) },
            onDismiss = { onAction(DetailsAction.DismissExternalInstallerPrompt) },
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: StringResource,
    text: String,
    confirmLabel: StringResource,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: StringResource = Res.string.cancel,
) {
    KomiDialog(
        onDismissRequest = onDismiss,
        title = { KomiText(text = stringResource(title), role = KomiTextRole.CardTitle) },
        text = { KomiText(text = text, role = KomiTextRole.Body) },
        confirmButton = {
            KomiButton(
                onClick = onConfirm,
                label = stringResource(confirmLabel),
                variant = KomiButtonVariant.Text,
                size = KomiButtonSize.Sm,
            )
        },
        dismissButton = {
            KomiButton(
                onClick = onDismiss,
                label = stringResource(dismissLabel),
                variant = KomiButtonVariant.Text,
                size = KomiButtonSize.Sm,
            )
        },
    )
}

@Composable
private fun SigningKeyDialog(
    warning: SigningKeyPromptUi,
    onAction: (DetailsAction) -> Unit,
) {
    ConfirmDialog(
        title = Res.string.signing_key_changed_title,
        text =
            stringResource(
                Res.string.signing_key_changed_message,
                warning.expectedFingerprint.take(FINGERPRINT_PREVIEW_CHARS),
                warning.actualFingerprint.take(FINGERPRINT_PREVIEW_CHARS),
            ),
        confirmLabel = Res.string.install_anyway,
        onConfirm = { onAction(DetailsAction.OnOverrideSigningKeyWarning) },
        onDismiss = { onAction(DetailsAction.OnDismissSigningKeyWarning) },
    )
}

@Composable
private fun DowngradeDialog(
    warning: DowngradeWarning,
    targetName: String,
    onAction: (DetailsAction) -> Unit,
) {
    KomiDialog(
        onDismissRequest = { onAction(DetailsAction.OnDismissDowngradeWarning) },
        title = { KomiText(text = stringResource(Res.string.downgrade_warning_title), role = KomiTextRole.CardTitle) },
        text = { DowngradeDialogText(warning = warning, targetName = targetName) },
        confirmButton = { DowngradeDialogButtons(onAction = onAction) },
    )
}

@Composable
private fun DowngradeDialogText(
    warning: DowngradeWarning,
    targetName: String,
) {
    val personality = LocalPersonality.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            InstalledAppIcon(
                packageName = warning.packageName,
                appName = targetName,
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(personality.shape.cornerSmall)),
            )
            Column {
                KomiText(text = targetName, role = KomiTextRole.ItemTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                KomiText(
                    text = warning.packageName,
                    role = KomiTextRole.Caption,
                    color = personality.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        KomiText(
            text = stringResource(Res.string.downgrade_warning_message, warning.targetVersion, warning.currentVersion),
            role = KomiTextRole.Body,
        )
        KomiText(
            text = stringResource(Res.string.downgrade_warning_uninstall_note, warning.currentVersion),
            role = KomiTextRole.Body,
            color = personality.colors.error,
        )
    }
}

@Composable
private fun DowngradeDialogButtons(onAction: (DetailsAction) -> Unit) {
    val personality = LocalPersonality.current
    val palette = dialogActionPalette(darkSurface = personality.colors.surfaceContainerHigh.luminance() < 0.5f)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        DialogTextButton(Res.string.cancel, palette.muted) { onAction(DetailsAction.OnDismissDowngradeWarning) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogTextButton(Res.string.uninstall_first, personality.colors.error) { onAction(DetailsAction.OnConfirmDowngradeUninstall) }
            DialogTextButton(Res.string.install_anyway, palette.confirm) { onAction(DetailsAction.OnConfirmDowngradeInstall) }
        }
    }
}

@Composable
private fun DialogTextButton(
    label: StringResource,
    contentColor: Color,
    onClick: () -> Unit,
) {
    KomiButton(
        onClick = onClick,
        label = stringResource(label),
        variant = KomiButtonVariant.Text,
        size = KomiButtonSize.Sm,
        containerColor = Color.Transparent,
        contentColor = contentColor,
    )
}

private const val FINGERPRINT_PREVIEW_CHARS = 19
