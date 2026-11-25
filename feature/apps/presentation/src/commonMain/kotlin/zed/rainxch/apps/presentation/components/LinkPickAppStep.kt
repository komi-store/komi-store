package zed.rainxch.apps.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.model.DeviceAppUi
import zed.rainxch.core.domain.model.installation.InstallerCategory
import zed.rainxch.core.presentation.components.dividers.KomiHorizontalDivider
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.installer_category_play_store
import zed.rainxch.githubstore.core.presentation.res.installer_category_side_store
import zed.rainxch.githubstore.core.presentation.res.installer_category_sideloaded
import zed.rainxch.githubstore.core.presentation.res.installer_category_system_update
import zed.rainxch.githubstore.core.presentation.res.installer_category_vendor_store
import zed.rainxch.githubstore.core.presentation.res.link_app_title
import zed.rainxch.githubstore.core.presentation.res.no_apps_found
import zed.rainxch.githubstore.core.presentation.res.pick_installed_app
import zed.rainxch.githubstore.core.presentation.res.search_apps_hint

@Composable
internal fun PickAppStep(
    deviceApps: ImmutableList<DeviceAppUi>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onAppSelect: (DeviceAppUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
    ) {
        KomiText(
            text = stringResource(Res.string.link_app_title),
            role = KomiTextRole.CardTitle,
            color = colors.onSurface,
        )

        Spacer(Modifier.height(4.dp))

        KomiText(
            text = stringResource(Res.string.pick_installed_app),
            role = KomiTextRole.Body,
            color = colors.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        KomiTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(Res.string.search_apps_hint),
            leadingIcon = Icons.Rounded.Search,
            clearable = true,
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(400.dp),
        ) {
            items(
                items = deviceApps,
                key = { it.packageName },
            ) { app ->
                DeviceAppItem(
                    app = app,
                    onClick = { onAppSelect(app) },
                )

                KomiHorizontalDivider(
                    color = colors.divider,
                )
            }

            if (deviceApps.isEmpty()) {
                item {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        KomiText(
                            text = stringResource(Res.string.no_apps_found),
                            role = KomiTextRole.Body,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DeviceAppItem(
    app: DeviceAppUi,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            KomiText(
                text = app.appName,
                role = KomiTextRole.Label,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                InstallerCategoryChip(category = app.installerCategory)

                Spacer(Modifier.width(6.dp))

                KomiText(
                    text = app.packageName,
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        app.versionName?.let { version ->
            KomiText(
                text = version,
                role = KomiTextRole.Caption,
                color = colors.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp),
            )
        }
    }
}

@Composable
private fun InstallerCategoryChip(category: InstallerCategory) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val highlighted = category.isHighlighted()
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(shape.cornerSmall))
                .background(if (highlighted) colors.primaryContainer else colors.surfaceVariant),
    ) {
        KomiText(
            text = stringResource(category.label()),
            role = KomiTextRole.Caption,
            color = if (highlighted) colors.onPrimaryContainer else colors.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun InstallerCategory.isHighlighted(): Boolean =
    when (this) {
        InstallerCategory.SIDE_STORE, InstallerCategory.SIDELOADED, InstallerCategory.VENDOR_STORE -> true
        InstallerCategory.PLAY_STORE, InstallerCategory.SYSTEM_UPDATE -> false
    }

private fun InstallerCategory.label(): StringResource =
    when (this) {
        InstallerCategory.SIDE_STORE -> Res.string.installer_category_side_store
        InstallerCategory.SIDELOADED -> Res.string.installer_category_sideloaded
        InstallerCategory.VENDOR_STORE -> Res.string.installer_category_vendor_store
        InstallerCategory.PLAY_STORE -> Res.string.installer_category_play_store
        InstallerCategory.SYSTEM_UPDATE -> Res.string.installer_category_system_update
    }
