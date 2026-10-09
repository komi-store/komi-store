package zed.rainxch.apps.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.model.AppItem
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.inputs.KomiCheckbox
import zed.rainxch.core.presentation.components.overlays.KomiDropdown
import zed.rainxch.core.presentation.components.overlays.KomiMenuItem
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.advanced_settings_open
import zed.rainxch.githubstore.core.presentation.res.apps_compact_more_actions
import zed.rainxch.githubstore.core.presentation.res.apps_ignore_updates
import zed.rainxch.githubstore.core.presentation.res.apps_menu_item_active
import zed.rainxch.githubstore.core.presentation.res.apps_skip_version
import zed.rainxch.githubstore.core.presentation.res.apps_skip_version_unskip
import zed.rainxch.githubstore.core.presentation.res.pre_release_badge
import zed.rainxch.githubstore.core.presentation.res.variant_picker_open

// The management toolbar — pre-release toggle, asset filter, variant picker, overflow — shared by
// the big library card and the in-progress card, so the two cannot drift apart while a download
// hands off from one to the other.
@Composable
internal fun AppCardToolbar(
    appItem: AppItem,
    onAdvancedSettingsClick: () -> Unit,
    onPickVariantClick: () -> Unit,
    onTogglePreReleases: (Boolean) -> Unit,
    onToggleUpdateCheck: (Boolean) -> Unit,
    onSkipVersionClick: () -> Unit,
    onUnskipVersionClick: () -> Unit,
    // The row's busy gate is the default — a pending-install row keeps its controls locked.
    // The in-progress card passes true: the card is busy for its whole life by definition, and
    // a toolbar disabled for that entire life is not a lock, it is a dead control.
    controlsEnabled: Boolean = !appItem.isBusy,
) {
    val colors = LocalPersonality.current.colors
    val app = appItem.installedApp

    Spacer(Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val preReleaseString = stringResource(Res.string.pre_release_badge)
        KomiText(
            text = preReleaseString,
            role = KomiTextRole.Body,
            fontSize = 13.sp,
            color = colors.onSurfaceVariant,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val advancedFilterDescription =
                stringResource(Res.string.advanced_settings_open)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(enabled = controlsEnabled, onClick = onAdvancedSettingsClick)
                    .semantics {
                        contentDescription = advancedFilterDescription
                    },
                contentAlignment = Alignment.Center,
            ) {
                KomiIcon(
                    imageVector = Icons.Default.FilterAlt,
                    contentDescription = null,
                    tint =
                        if (appItem.hasFilter) {
                            colors.primary
                        } else {
                            colors.onSurfaceVariant
                        },
                )
            }

            val pickVariantDescription =
                stringResource(Res.string.variant_picker_open)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(enabled = controlsEnabled, onClick = onPickVariantClick)
                    .semantics {
                        contentDescription = pickVariantDescription
                    },
                contentAlignment = Alignment.Center,
            ) {
                KomiIcon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint =
                        when {
                            app.preferredVariantStale -> colors.error
                            appItem.hasPin -> colors.primary
                            else -> colors.onSurfaceVariant
                        },
                )
            }

            KomiCheckbox(
                checked = app.includePreReleases,
                onCheckedChange = onTogglePreReleases,
                enabled = controlsEnabled,
                modifier =
                    Modifier.semantics {
                        contentDescription = preReleaseString
                    },
            )

            val moreActionsLabel =
                stringResource(Res.string.apps_compact_more_actions, app.appName)
            val ignoreUpdatesBase = stringResource(Res.string.apps_ignore_updates)
            val ignoreUpdatesActive =
                stringResource(Res.string.apps_menu_item_active, ignoreUpdatesBase)
            val unskipLabel = stringResource(Res.string.apps_skip_version_unskip)
            val skipLabel = stringResource(Res.string.apps_skip_version)
            val rowOverflowEntries = buildList {
                add(
                    KomiMenuItem(
                        id = "toggle_update_check",
                        label = if (!app.updateCheckEnabled) ignoreUpdatesActive else ignoreUpdatesBase,
                    ),
                )
                if (app.skippedReleaseTag != null) {
                    add(KomiMenuItem(id = "unskip_version", label = unskipLabel))
                } else if (appItem.canSkipVersion) {
                    add(KomiMenuItem(id = "skip_version", label = skipLabel))
                }
            }.toImmutableList()

            KomiDropdown(
                entries = rowOverflowEntries,
                onSelect = { item ->
                    when (item.id) {
                        "toggle_update_check" -> onToggleUpdateCheck(!app.updateCheckEnabled)
                        "unskip_version" -> onUnskipVersionClick()
                        "skip_version" -> onSkipVersionClick()
                    }
                },
                trigger = { onClick ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(enabled = controlsEnabled, onClick = onClick)
                            .semantics {
                                contentDescription = moreActionsLabel
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        KomiIcon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}
