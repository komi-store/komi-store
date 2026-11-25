package zed.rainxch.search.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.clipboard_link_detected
import zed.rainxch.githubstore.core.presentation.res.detected_links
import zed.rainxch.githubstore.core.presentation.res.dismiss
import zed.rainxch.githubstore.core.presentation.res.open_in_app
import zed.rainxch.search.presentation.model.ParsedGithubLink

@Composable
internal fun ClipboardBanner(
    links: ImmutableList<ParsedGithubLink>,
    onOpenLink: (ParsedGithubLink) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    KomiSurface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KomiText(
                    text = stringResource(Res.string.clipboard_link_detected),
                    role = KomiTextRole.Overline,
                    color = colors.onSurfaceVariant,
                )

                Box(
                    modifier =
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(shape.cornerSmall))
                            .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    KomiIcon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.dismiss),
                        modifier = Modifier.size(16.dp),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            links.forEach { link ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(shape.cornerSmall))
                            .clickable { onOpenLink(link) }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    KomiIcon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.primary,
                    )

                    KomiText(
                        text = "${link.owner}/${link.repo}",
                        role = KomiTextRole.Label,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )

                    KomiIcon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(Res.string.open_in_app),
                        modifier = Modifier.size(16.dp),
                        tint = colors.primary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun DetectedLinksSection(
    links: ImmutableList<ParsedGithubLink>,
    onOpenLink: (ParsedGithubLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
    ) {
        KomiText(
            text = stringResource(Res.string.detected_links),
            role = KomiTextRole.Overline,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        links.forEach { link ->
            KomiSurface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                onClick = { onOpenLink(link) },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    KomiIcon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colors.primary,
                    )

                    KomiText(
                        text = "${link.owner}/${link.repo}",
                        role = KomiTextRole.Label,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )

                    KomiText(
                        text = stringResource(Res.string.open_in_app),
                        role = KomiTextRole.Overline,
                        color = colors.primary,
                    )
                }
            }
        }
    }
}
