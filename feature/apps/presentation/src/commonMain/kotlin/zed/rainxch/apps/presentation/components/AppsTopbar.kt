package zed.rainxch.apps.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.AppsAction
import zed.rainxch.apps.presentation.AppsState
import zed.rainxch.apps.presentation.model.AppSortRule
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.KomiDropdown
import zed.rainxch.core.presentation.components.overlays.KomiMenuItem
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.add_from_starred_title
import zed.rainxch.githubstore.core.presentation.res.bottom_nav_apps_title
import zed.rainxch.githubstore.core.presentation.res.cd_more_actions
import zed.rainxch.githubstore.core.presentation.res.check_for_updates
import zed.rainxch.githubstore.core.presentation.res.export_apps
import zed.rainxch.githubstore.core.presentation.res.export_apps_obtainium
import zed.rainxch.githubstore.core.presentation.res.external_import_rescan_menu
import zed.rainxch.githubstore.core.presentation.res.import_apps
import zed.rainxch.githubstore.core.presentation.res.sort_apps
import zed.rainxch.githubstore.core.presentation.res.sort_name
import zed.rainxch.githubstore.core.presentation.res.sort_recently_updated
import zed.rainxch.githubstore.core.presentation.res.sort_updates_first

@Composable
fun AppsTopbar(
    onAction: (AppsAction) -> Unit,
    state: AppsState,
    search: (@Composable () -> Unit)? = null,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val sortEntries = persistentListOf(
        KomiMenuItem(
            id = AppSortRule.UpdatesFirst.name,
            label = stringResource(Res.string.sort_updates_first),
        ),
        KomiMenuItem(
            id = AppSortRule.RecentlyUpdated.name,
            label = stringResource(Res.string.sort_recently_updated),
        ),
        KomiMenuItem(
            id = AppSortRule.Name.name,
            label = stringResource(Res.string.sort_name),
        ),
    )
    val onSortSelected: (KomiMenuItem) -> Unit = { item ->
        onAction(AppsAction.OnSortRuleSelected(AppSortRule.fromName(item.id)))
    }
    val moreEntries = persistentListOf(
        KomiMenuItem(
            id = "export_apps",
            label = stringResource(Res.string.export_apps),
            icon = Icons.Rounded.FileUpload,
        ),
        KomiMenuItem(
            id = "export_obtainium",
            label = stringResource(Res.string.export_apps_obtainium),
            icon = Icons.Rounded.FileUpload,
        ),
        KomiMenuItem(
            id = "import_apps",
            label = stringResource(Res.string.import_apps),
            icon = Icons.Rounded.FileDownload,
        ),
        KomiMenuItem(
            id = "rescan",
            label = stringResource(Res.string.external_import_rescan_menu),
            icon = Icons.Rounded.Search,
        ),
        KomiMenuItem(
            id = "add_from_starred",
            label = stringResource(Res.string.add_from_starred_title),
            icon = Icons.Rounded.Star,
        ),
    )
    val onMoreSelected: (KomiMenuItem) -> Unit = { item ->
        when (item.id) {
            "export_apps" -> onAction(AppsAction.OnExportApps)
            "export_obtainium" -> onAction(AppsAction.OnExportObtainium)
            "import_apps" -> onAction(AppsAction.OnImportApps)
            "rescan" -> onAction(AppsAction.OnRescanForGithubApps)
            "add_from_starred" -> onAction(AppsAction.OnAddFromStarredClick)
        }
    }
    val sortLabel = stringResource(Res.string.sort_apps)
    val checkLabel = stringResource(Res.string.check_for_updates)

    KomiTopBar(
        title = stringResource(Res.string.bottom_nav_apps_title),
        search = search,
        actions = {
            if (LocalPersonality.current is MangaPersonality) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(shape.cornerSmall))
                        .background(colors.surface)
                        .border(
                            width = 1.dp,
                            color = colors.outline,
                            shape = RoundedCornerShape(shape.cornerSmall),
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KomiDropdown(
                        entries = sortEntries,
                        onSelect = onSortSelected,
                        value = state.sortRule.name,
                        trigger = { onClick ->
                            Box(
                                modifier = Modifier
                                    .clickable { onClick() }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                KomiIcon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = sortLabel,
                                    tint = colors.onSurface,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                    )

                    Box(
                        modifier = Modifier
                            .size(1.dp, 20.dp)
                            .background(colors.divider),
                    )

                    Box(
                        modifier = Modifier
                            .clickable { onAction(AppsAction.OnCheckAllForUpdates) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        KomiIcon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = checkLabel,
                            tint = colors.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(1.dp, 20.dp)
                            .background(colors.divider),
                    )

                    KomiDropdown(
                        entries = moreEntries,
                        onSelect = onMoreSelected,
                        trigger = { onClick ->
                            Box(
                                modifier = Modifier
                                    .clickable { onClick() }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                KomiIcon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = null,
                                    tint = colors.onSurface,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                    )
                }
            } else {
                KomiDropdown(
                    entries = sortEntries,
                    onSelect = onSortSelected,
                    value = state.sortRule.name,
                    trigger = { onClick ->
                        KomiIconButton(icon = Icons.AutoMirrored.Rounded.Sort, contentDescription = sortLabel, onClick = onClick)
                    },
                )
                KomiIconButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = checkLabel,
                    onClick = { onAction(AppsAction.OnCheckAllForUpdates) },
                )
                KomiDropdown(
                    entries = moreEntries,
                    onSelect = onMoreSelected,
                    trigger = { onClick ->
                        KomiIconButton(
                            icon = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(Res.string.cd_more_actions),
                            onClick = onClick,
                        )
                    },
                )
            }
        },
    )
}
