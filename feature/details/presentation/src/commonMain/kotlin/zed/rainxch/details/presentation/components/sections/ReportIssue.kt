package zed.rainxch.details.presentation.components.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.open_in_browser
import zed.rainxch.githubstore.core.presentation.res.report_issue

fun LazyListScope.reportIssue(repoUrl: String) {
    item {
        val uriHandler = LocalUriHandler.current
        val colors = LocalPersonality.current.colors
        val rowShape = RoundedCornerShape(LocalPersonality.current.shape.corner)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(rowShape)
                    .border(width = 1.dp, color = colors.outline, shape = rowShape)
                    .background(colors.surface)
                    .clickable { uriHandler.openUri("${repoUrl.trimEnd('/')}/issues") }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KomiIcon(
                imageVector = Icons.Default.BugReport,
                contentDescription = stringResource(Res.string.report_issue),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            KomiText(
                text = stringResource(Res.string.report_issue),
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            KomiIcon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(Res.string.open_in_browser),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
