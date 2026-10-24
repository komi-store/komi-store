package zed.rainxch.tweaks.presentation.components.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.tweaks_diagnostics_share_action
import zed.rainxch.githubstore.core.presentation.res.tweaks_diagnostics_share_body
import zed.rainxch.githubstore.core.presentation.res.tweaks_diagnostics_share_title
import zed.rainxch.githubstore.core.presentation.res.tweaks_diagnostics_warning
import zed.rainxch.tweaks.presentation.TweaksAction
import zed.rainxch.tweaks.presentation.components.shell.SettingsGroup
import zed.rainxch.tweaks.presentation.components.shell.SettingsRow

@Composable
fun DiagnosticsSectionContent(
    onAction: (TweaksAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SettingsGroup {
            SettingsRow(
                title = stringResource(Res.string.tweaks_diagnostics_share_title),
                subtitle = stringResource(Res.string.tweaks_diagnostics_share_body),
                last = true,
                trailing = {
                    KomiButton(
                        onClick = { onAction(TweaksAction.OnShareDiagnosticLogs) },
                        label = stringResource(Res.string.tweaks_diagnostics_share_action),
                        variant = KomiButtonVariant.Tonal,
                        size = KomiButtonSize.Sm,
                    )
                },
            )
        }

        KomiText(
            text = stringResource(Res.string.tweaks_diagnostics_warning),
            role = KomiTextRole.Caption,
            color = LocalPersonality.current.colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 4.dp),
        )
    }
}
