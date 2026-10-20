package zed.rainxch.home.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.components.overlays.KomiMenuItem
import zed.rainxch.core.presentation.components.overlays.KomiMenuList
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement
import zed.rainxch.core.presentation.utils.toIcon
import zed.rainxch.core.presentation.utils.toLabel
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.feed_platform_all
import zed.rainxch.githubstore.core.presentation.res.feed_platform_picker_title
import zed.rainxch.githubstore.core.presentation.res.feed_platform_picker_title_jp

@Composable
fun HomePlatformPicker(
    platforms: ImmutableList<DiscoveryPlatform>,
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
                platforms
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
