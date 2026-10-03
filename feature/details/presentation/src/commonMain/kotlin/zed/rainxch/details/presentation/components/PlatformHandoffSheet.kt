package zed.rainxch.details.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.utils.PlatformRelease
import zed.rainxch.core.domain.utils.assetPlatformOf
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.buttons.KomiIconButtonSize
import zed.rainxch.core.presentation.components.dividers.KomiHorizontalDivider
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.formatFileSize
import zed.rainxch.core.presentation.utils.formatIsoDateOrRaw
import zed.rainxch.core.presentation.utils.toIcon
import zed.rainxch.core.presentation.utils.toLabel
import zed.rainxch.details.presentation.DetailsAction
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.get_it_on_platform
import zed.rainxch.githubstore.core.presentation.res.open_in_browser
import zed.rainxch.githubstore.core.presentation.res.share_link

@Composable
fun PlatformHandoffSheet(
    handoff: PlatformRelease,
    onAction: (DetailsAction) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val (platform, release) = handoff
    val assets = remember(handoff) {
        release.assets.filter { assetPlatformOf(it.name) == platform }
    }

    KomiSheet(
        onDismiss = { onAction(DetailsAction.OnDismissPlatformHandoff) },
        placement = KomiSheetPlacement.Bottom,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(vertical = 6.dp),
        ) {
            platform.toIcon()?.let { icon ->
                KomiIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
            KomiText(
                text = stringResource(Res.string.get_it_on_platform, platform.toLabel()),
                role = KomiTextRole.Title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                color = colors.onSurface,
                uppercase = false,
            )
        }
        KomiText(
            text = "${release.tagName} · ${formatIsoDateOrRaw(release.publishedAt)}",
            role = KomiTextRole.Body,
            fontSize = 13.sp,
            color = colors.onSurfaceVariant,
            uppercase = false,
        )
        Spacer(Modifier.size(12.dp))

        val shareLabel = stringResource(Res.string.share_link)
        val openLabel = stringResource(Res.string.open_in_browser)
        assets.forEachIndexed { index, asset ->
            if (index > 0) KomiHorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    KomiText(
                        text = asset.name,
                        role = KomiTextRole.Body,
                        fontSize = 14.sp,
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        uppercase = false,
                    )
                    KomiText(
                        text = formatFileSize(asset.size),
                        role = KomiTextRole.Label,
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        uppercase = false,
                    )
                }
                KomiIconButton(
                    icon = Icons.Default.Share,
                    contentDescription = shareLabel,
                    onClick = { onAction(DetailsAction.OnShareAssetLink(asset.downloadUrl)) },
                    variant = KomiButtonVariant.Text,
                    size = KomiIconButtonSize.Sm,
                )
                KomiIconButton(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = openLabel,
                    onClick = { onAction(DetailsAction.OnDownloadForTransfer(asset.downloadUrl)) },
                    variant = KomiButtonVariant.Text,
                    size = KomiIconButtonSize.Sm,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
    }
}
