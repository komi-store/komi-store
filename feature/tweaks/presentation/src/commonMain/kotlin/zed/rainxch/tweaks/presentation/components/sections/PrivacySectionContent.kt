package zed.rainxch.tweaks.presentation.components.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.inputs.KomiSwitch
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.cancel
import zed.rainxch.githubstore.core.presentation.res.tweaks_privacy_clear_viewed_body
import zed.rainxch.githubstore.core.presentation.res.tweaks_privacy_clear_viewed_dialog_body
import zed.rainxch.githubstore.core.presentation.res.tweaks_privacy_clear_viewed_dialog_confirm
import zed.rainxch.githubstore.core.presentation.res.tweaks_privacy_clear_viewed_dialog_title
import zed.rainxch.githubstore.core.presentation.res.tweaks_privacy_clear_viewed_title
import zed.rainxch.githubstore.core.presentation.res.tweaks_privacy_clipboard_body
import zed.rainxch.githubstore.core.presentation.res.tweaks_privacy_clipboard_title
import zed.rainxch.githubstore.core.presentation.res.tweaks_privacy_hide_seen_body
import zed.rainxch.githubstore.core.presentation.res.tweaks_privacy_hide_seen_title
import zed.rainxch.githubstore.core.presentation.res.tweaks_privacy_hidden_repos_body
import zed.rainxch.githubstore.core.presentation.res.tweaks_privacy_hidden_repos_title
import zed.rainxch.tweaks.presentation.TweaksAction
import zed.rainxch.tweaks.presentation.TweaksState
import zed.rainxch.tweaks.presentation.components.shell.SettingsDrillRow
import zed.rainxch.tweaks.presentation.components.shell.SettingsGroup
import zed.rainxch.tweaks.presentation.components.shell.SettingsRow

@Composable
fun privacySectionContent(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToHiddenRepositories: () -> Unit,
) {
    SettingsGroup(modifier = modifier) {
        SettingsRow(
            title = stringResource(Res.string.tweaks_privacy_hide_seen_title),
            subtitle = stringResource(Res.string.tweaks_privacy_hide_seen_body),
            trailing = {
                KomiSwitch(
                    checked = state.isHideSeenEnabled,
                    onCheckedChange = { onAction(TweaksAction.OnHideSeenToggled(it)) },
                )
            },
        )
        SettingsRow(
            title = stringResource(Res.string.tweaks_privacy_clear_viewed_title),
            subtitle = stringResource(Res.string.tweaks_privacy_clear_viewed_body),
            trailing = {
                KomiButton(
                    onClick = { onAction(TweaksAction.OnClearSeenHistoryRequest) },
                    label = stringResource(Res.string.tweaks_privacy_clear_viewed_dialog_confirm),
                    variant = KomiButtonVariant.Destructive,
                    size = KomiButtonSize.Sm,
                )
            },
        )
        SettingsDrillRow(
            title = stringResource(Res.string.tweaks_privacy_hidden_repos_title),
            subtitle = stringResource(Res.string.tweaks_privacy_hidden_repos_body),
            onClick = onNavigateToHiddenRepositories,
        )
        SettingsRow(
            title = stringResource(Res.string.tweaks_privacy_clipboard_title),
            subtitle = stringResource(Res.string.tweaks_privacy_clipboard_body),
            last = true,
            trailing = {
                KomiSwitch(
                    checked = state.autoDetectClipboardLinks,
                    onCheckedChange = { onAction(TweaksAction.OnAutoDetectClipboardToggled(it)) },
                )
            },
        )
    }

    if (state.isClearSeenHistoryDialogVisible) {
        val onDismiss = { onAction(TweaksAction.OnClearSeenHistoryDismiss) }
        KomiSheet(
            onDismiss = onDismiss,
            placement = KomiSheetPlacement.Center,
            title = stringResource(Res.string.tweaks_privacy_clear_viewed_dialog_title),
            footer = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KomiButton(
                        onClick = onDismiss,
                        label = stringResource(Res.string.cancel),
                        variant = KomiButtonVariant.Text,
                    )
                    KomiButton(
                        onClick = { onAction(TweaksAction.OnClearSeenHistoryConfirm) },
                        label = stringResource(Res.string.tweaks_privacy_clear_viewed_dialog_confirm),
                        variant = KomiButtonVariant.Destructive,
                    )
                }
            },
        ) {
            KomiText(
                text = stringResource(Res.string.tweaks_privacy_clear_viewed_dialog_body),
                role = KomiTextRole.Body,
            )
        }
    }
}
