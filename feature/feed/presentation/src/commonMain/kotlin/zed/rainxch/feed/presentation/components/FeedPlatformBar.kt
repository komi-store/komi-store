package zed.rainxch.feed.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.utils.toIcon
import zed.rainxch.core.presentation.utils.toLabel
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.feed_browsing
import zed.rainxch.githubstore.core.presentation.res.feed_platform_all

@Composable
fun FeedPlatformBar(
    platform: DiscoveryPlatform,
    onOpenPicker: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KomiText(
            text = stringResource(Res.string.feed_browsing),
            role = KomiTextRole.Label,
        )

        KomiButton(
            onClick = onOpenPicker,
            label = if (platform == DiscoveryPlatform.All) stringResource(Res.string.feed_platform_all) else platform.toLabel(),
            variant = KomiButtonVariant.Primary,
            size = KomiButtonSize.Sm,
            leadingIcon = if (platform == DiscoveryPlatform.All) null else platform.toIcon(),
            trailingIcon = Icons.Rounded.KeyboardArrowDown,
        )
    }
}
