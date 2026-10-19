package zed.rainxch.feed.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.toIcon
import zed.rainxch.core.presentation.utils.toLabel
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.feed_platform_all
import zed.rainxch.githubstore.core.presentation.res.feed_platform_picker_title
import zed.rainxch.githubstore.core.presentation.res.feed_platform_picker_title_jp

// The backend feed has no iOS data and rejects platform=ios.
private val FeedPlatforms = DiscoveryPlatform.entries - DiscoveryPlatform.Ios

@Composable
fun FeedPlatformPicker(
    selected: DiscoveryPlatform,
    onSelect: (DiscoveryPlatform) -> Unit,
    onDismiss: () -> Unit,
) {
    KomiSheet(
        onDismiss = onDismiss,
        placement = KomiSheetPlacement.Bottom,
        title = stringResource(Res.string.feed_platform_picker_title),
        titleJp = stringResource(Res.string.feed_platform_picker_title_jp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FeedPlatforms.forEach { platform ->
                FeedPlatformRow(
                    platform = platform,
                    isSelected = platform == selected,
                    onClick = { onSelect(platform) },
                )
            }
        }
    }
}

@Composable
private fun FeedPlatformRow(
    platform: DiscoveryPlatform,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val content = if (isSelected) LocalPersonality.current.colors.onPrimary else LocalPersonality.current.colors.onSurface

    KomiSurface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isSelected) LocalPersonality.current.colors.primary else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            platform.toIcon()?.let { icon ->
                KomiIcon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = content,
                )
            }

            KomiText(
                text = if (platform == DiscoveryPlatform.All) stringResource(Res.string.feed_platform_all) else platform.toLabel(),
                role = KomiTextRole.Title,
                color = content,
                modifier = Modifier.weight(1f),
            )

            if (isSelected) {
                KomiIcon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = content,
                )
            }
        }
    }
}
