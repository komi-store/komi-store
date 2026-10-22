package zed.rainxch.tweaks.presentation.feedback.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.inputs.KomiSwitch
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfacePaper
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.feedback_diagnostics_header
import zed.rainxch.githubstore.core.presentation.res.feedback_diagnostics_include
import zed.rainxch.tweaks.presentation.feedback.model.DiagnosticsInfo
import zed.rainxch.tweaks.presentation.feedback.model.FeedbackChannel

@Composable
fun DiagnosticsPreview(
    diagnostics: DiagnosticsInfo?,
    channel: FeedbackChannel,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    KomiSurface(
        modifier = modifier.fillMaxWidth(),
        paper = KomiSurfacePaper.Background,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    KomiText(
                        text = stringResource(Res.string.feedback_diagnostics_header),
                        role = KomiTextRole.CardTitle,
                        color = colors.onSurface,
                    )
                    KomiText(
                        text = stringResource(Res.string.feedback_diagnostics_include),
                        role = KomiTextRole.Body,
                        color = colors.onSurfaceVariant,
                    )
                }
                KomiSwitch(
                    checked = enabled,
                    onCheckedChange = { onToggle() },
                )
            }

            if (enabled && diagnostics != null) {
                KomiText(
                    text = formatDiagnostics(diagnostics, channel),
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

private fun formatDiagnostics(
    d: DiagnosticsInfo,
    channel: FeedbackChannel,
): String {
    val sb = StringBuilder()
    sb.append("- App: Komi Store v").append(d.appVersion).append('\n')
    sb
        .append("- Platform: ")
        .append(d.platform)
        .append(' ')
        .append(d.osVersion)
        .append('\n')
    sb.append("- Locale: ").append(d.locale).append('\n')
    sb
        .append("- Theme: ")
        .append(d.themePalette)
        .append(" / ")
        .append(d.themeMode)
    d.installerType?.let { sb.append('\n').append("- Installer: ").append(it) }
    if (channel == FeedbackChannel.GITHUB) {
        d.githubUsername?.let { sb.append('\n').append("- GitHub user: @").append(it) }
    }
    return sb.toString()
}
