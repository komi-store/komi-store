package zed.rainxch.tweaks.presentation.components.sections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.tweaks_storage_downloaded_apks_body
import zed.rainxch.githubstore.core.presentation.res.tweaks_storage_downloaded_apks_clear
import zed.rainxch.githubstore.core.presentation.res.tweaks_storage_downloaded_apks_title
import zed.rainxch.githubstore.core.presentation.res.tweaks_storage_empty_size
import zed.rainxch.githubstore.core.presentation.res.tweaks_storage_using_label
import zed.rainxch.tweaks.presentation.TweaksAction
import zed.rainxch.tweaks.presentation.TweaksState
import zed.rainxch.tweaks.presentation.components.shell.SettingsGroup
import zed.rainxch.tweaks.presentation.components.shell.SettingsRow

@Composable
fun StorageSectionContent(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val emptySize = stringResource(Res.string.tweaks_storage_empty_size)
    val sizeDisplay = state.cacheSize.ifBlank { emptySize }
    val isEmpty = sizeDisplay == emptySize

    SettingsGroup(modifier = modifier) {
        SettingsRow(
            title = stringResource(Res.string.tweaks_storage_downloaded_apks_title),
            subtitle = stringResource(Res.string.tweaks_storage_using_label, sizeDisplay),
            last = true,
            trailing = {
                KomiButton(
                    onClick = { onAction(TweaksAction.OnClearCacheClick) },
                    label = stringResource(Res.string.tweaks_storage_downloaded_apks_clear),
                    variant = KomiButtonVariant.Destructive,
                    size = KomiButtonSize.Sm,
                    enabled = !isEmpty,
                    leadingIcon = Icons.Outlined.DeleteOutline,
                )
            },
        )
    }
}
