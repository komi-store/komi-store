package zed.rainxch.core.presentation.components.discovery

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.buttons.KomiIconButtonSize
import zed.rainxch.core.presentation.components.overlays.KomiMenuItem
import zed.rainxch.core.presentation.components.overlays.KomiMenuList
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement
import zed.rainxch.core.presentation.utils.toIcon
import zed.rainxch.core.presentation.utils.toLabel
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.feed_cd_search
import zed.rainxch.githubstore.core.presentation.res.feed_platform_all
import zed.rainxch.githubstore.core.presentation.res.feed_platform_picker_title
import zed.rainxch.githubstore.core.presentation.res.feed_platform_picker_title_jp

// The backend's feed and charts have no iOS data, and /v1/feed rejects platform=ios; iOS
// discovery is in Search.
private val FeedPlatforms = DiscoveryPlatform.entries - DiscoveryPlatform.Ios

// Explore's and Top Charts' action: the platform filter, labelled with the current platform.
@Composable
fun DiscoveryPlatformButton(
    platform: DiscoveryPlatform,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val all = platform == DiscoveryPlatform.All
    KomiButton(
        onClick = onClick,
        label = if (all) stringResource(Res.string.feed_platform_all) else platform.toLabel(),
        variant = KomiButtonVariant.Primary,
        size = KomiButtonSize.Sm,
        leadingIcon = if (all) null else platform.toIcon(),
        trailingIcon = Icons.Rounded.KeyboardArrowDown,
        modifier = modifier,
    )
}

// Manga's way into Search from Explore and Top Charts; Classic has the tab card's search field.
@Composable
fun DiscoverySearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KomiIconButton(
        icon = Icons.Rounded.Search,
        contentDescription = stringResource(Res.string.feed_cd_search),
        onClick = onClick,
        size = KomiIconButtonSize.Sm,
        modifier = modifier,
    )
}

@Composable
fun DiscoveryPlatformPicker(
    selected: DiscoveryPlatform,
    onSelect: (DiscoveryPlatform) -> Unit,
    onDismiss: () -> Unit,
) {
    val allLabel = stringResource(Res.string.feed_platform_all)
    KomiSheet(
        onDismiss = onDismiss,
        placement = KomiSheetPlacement.Bottom,
        title = stringResource(Res.string.feed_platform_picker_title),
        titleJp = stringResource(Res.string.feed_platform_picker_title_jp),
    ) {
        KomiMenuList(
            entries =
                FeedPlatforms
                    .map { platform ->
                        KomiMenuItem(
                            id = platform.name,
                            label = if (platform == DiscoveryPlatform.All) allLabel else platform.toLabel(),
                            icon = platform.toIcon() ?: Icons.Rounded.Devices,
                        )
                    }.toImmutableList(),
            onSelect = { item -> onSelect(DiscoveryPlatform.valueOf(item.id)) },
            value = selected.name,
        )
    }
}
