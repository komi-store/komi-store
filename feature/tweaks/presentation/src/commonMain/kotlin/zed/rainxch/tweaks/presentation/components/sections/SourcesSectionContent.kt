package zed.rainxch.tweaks.presentation.components.sections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.custom_forges_count
import zed.rainxch.githubstore.core.presentation.res.custom_forges_entry_label
import zed.rainxch.githubstore.core.presentation.res.remove_search_history_item
import zed.rainxch.githubstore.core.presentation.res.tweaks_sources_add_a_host
import zed.rainxch.githubstore.core.presentation.res.tweaks_sources_github_mirror_title
import zed.rainxch.githubstore.core.presentation.res.tweaks_sources_mirror_default
import zed.rainxch.tweaks.presentation.TweaksAction
import zed.rainxch.tweaks.presentation.TweaksState
import zed.rainxch.tweaks.presentation.components.CustomForgesDialog
import zed.rainxch.tweaks.presentation.components.shell.SettingsDrillRow
import zed.rainxch.tweaks.presentation.components.shell.SettingsGroup
import zed.rainxch.tweaks.presentation.components.shell.SettingsRow

@Composable
fun SourcesSectionContent(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
    onNavigateToMirrorPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hosts = state.customForgeHosts.sorted()
    SettingsGroup(modifier = modifier) {
        SettingsDrillRow(
            title = stringResource(Res.string.tweaks_sources_github_mirror_title),
            subtitle = stringResource(Res.string.tweaks_sources_mirror_default),
            onClick = onNavigateToMirrorPicker,
        )
        SettingsDrillRow(
            title = stringResource(Res.string.custom_forges_entry_label),
            subtitle =
                if (hosts.isEmpty()) {
                    stringResource(Res.string.tweaks_sources_add_a_host)
                } else {
                    pluralStringResource(Res.plurals.custom_forges_count, hosts.size, hosts.size)
                },
            last = hosts.isEmpty(),
            onClick = { onAction(TweaksAction.OnOpenCustomForgesDialog) },
        )
        hosts.forEachIndexed { index, host ->
            SettingsRow(
                title = host,
                last = index == hosts.lastIndex,
                trailing = {
                    KomiIconButton(
                        icon = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(Res.string.remove_search_history_item),
                        onClick = { onAction(TweaksAction.OnRemoveCustomForge(host)) },
                        variant = KomiButtonVariant.Destructive,
                    )
                },
            )
        }
    }

    if (state.showCustomForgesDialog) {
        CustomForgesDialog(
            state = state,
            onAction = { onAction(it) },
        )
    }
}
