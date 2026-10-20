package zed.rainxch.apps.presentation.components

import zed.rainxch.core.presentation.utils.formatFileSize
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.dividers.KomiHorizontalDivider
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.inputs.KomiSwitch
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.AppsAction
import zed.rainxch.apps.presentation.AppsState
import zed.rainxch.apps.presentation.model.AdvancedPreviewMessage
import zed.rainxch.apps.presentation.model.GithubAssetUi
import zed.rainxch.githubstore.core.presentation.res.*

@Composable
fun AdvancedAppSettingsBottomSheet(
    state: AppsState,
    onAction: (AppsAction) -> Unit,
) {
    val app = state.advancedSettingsApp ?: return
    val colors = LocalPersonality.current.colors

    KomiSheet(
        onDismiss = { onAction(AppsAction.OnDismissAdvancedSettings) },
        placement = KomiSheetPlacement.Bottom,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KomiIcon(
                    imageVector = Icons.Default.FilterAlt,
                    contentDescription = null,
                    tint = colors.primary,
                )

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    KomiText(
                        text = stringResource(Res.string.advanced_settings_title),
                        role = KomiTextRole.CardTitle,
                        color = colors.onSurface,
                    )

                    KomiText(
                        text = "${app.repoOwner}/${app.repoName}",
                        role = KomiTextRole.Body,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            KomiText(
                text = stringResource(Res.string.advanced_settings_description),
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))

            val advancedSupporting = when {
                state.advancedFilterError != null ->
                    stringResource(Res.string.asset_filter_invalid)
                else -> stringResource(Res.string.asset_filter_help)
            }
            KomiTextField(
                value = state.advancedFilterDraft,
                onValueChange = { onAction(AppsAction.OnAdvancedFilterChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(Res.string.asset_filter_label),
                placeholder = stringResource(Res.string.asset_filter_placeholder),
                leadingIcon = Icons.Rounded.FilterAlt,
                onClear = { onAction(AppsAction.OnAdvancedClearFilter) },
                helper = if (state.advancedFilterError == null) advancedSupporting else null,
                error = if (state.advancedFilterError != null) advancedSupporting else null,
                enabled = !state.advancedSavingFilter,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    KomiText(
                        text = stringResource(Res.string.fallback_older_releases_title),
                        role = KomiTextRole.Label,
                        color = colors.onSurface,
                    )

                    KomiText(
                        text = stringResource(Res.string.fallback_older_releases_description),
                        role = KomiTextRole.Body,
                        color = colors.onSurfaceVariant,
                    )
                }

                KomiSwitch(
                    checked = state.advancedFallbackDraft,
                    onCheckedChange = { onAction(AppsAction.OnAdvancedFallbackToggled(it)) },
                    enabled = !state.advancedSavingFilter,
                )
            }

            Spacer(Modifier.height(16.dp))

            KomiHorizontalDivider(
                color = colors.divider,
            )

            Spacer(Modifier.height(16.dp))

            KomiText(
                text = stringResource(Res.string.advanced_filter_variant_relation),
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            VariantRow(
                pinnedVariant = app.preferredAssetVariant,
                isStale = app.preferredVariantStale,
                onClick = { onAction(AppsAction.OnOpenVariantPicker(app)) },
            )

            Spacer(Modifier.height(16.dp))

            KomiHorizontalDivider(
                color = colors.divider,
            )

            Spacer(Modifier.height(16.dp))

            PreviewSection(
                isLoading = state.advancedPreviewLoading,
                matchedAssets = state.advancedPreviewMatched,
                matchedTag = state.advancedPreviewTag,
                message = state.advancedPreviewMessage,
                onRefresh = { onAction(AppsAction.OnAdvancedRefreshPreview) },
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                KomiButton(
                    onClick = { onAction(AppsAction.OnDismissAdvancedSettings) },
                    label = stringResource(Res.string.cancel),
                    variant = KomiButtonVariant.Outline,
                    enabled = !state.advancedSavingFilter,
                    modifier = Modifier.weight(1f),
                )

                KomiButton(
                    onClick = { onAction(AppsAction.OnAdvancedSaveFilter) },
                    label = stringResource(Res.string.advanced_save),
                    variant = KomiButtonVariant.Tonal,
                    enabled = !state.advancedSavingFilter && state.advancedFilterError == null,
                    loading = state.advancedSavingFilter,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PreviewSection(
    isLoading: Boolean,
    matchedAssets: ImmutableList<GithubAssetUi>,
    matchedTag: String?,
    message: AdvancedPreviewMessage?,
    onRefresh: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KomiIcon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )

        Spacer(Modifier.width(8.dp))

        KomiText(
            text = stringResource(Res.string.advanced_preview_title),
            role = KomiTextRole.CardTitle,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )

        val refreshLabel = stringResource(Res.string.advanced_preview_refresh)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(enabled = !isLoading, onClick = onRefresh)
                .semantics { contentDescription = refreshLabel },
            contentAlignment = Alignment.Center,
        ) {
            KomiIcon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = if (isLoading) colors.disabledContent else colors.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(4.dp))

    when {
        isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center,
            ) {
                KomiCircularProgress(
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        message == AdvancedPreviewMessage.NoMatch -> {
            KomiText(
                text = stringResource(Res.string.advanced_preview_no_match),
                role = KomiTextRole.Body,
                color = colors.error,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        message == AdvancedPreviewMessage.PreviewFailed || message == AdvancedPreviewMessage.SaveFailed -> {
            KomiText(
                text = stringResource(Res.string.advanced_preview_failed),
                role = KomiTextRole.Body,
                color = colors.error,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        matchedAssets.isEmpty() -> {
            KomiText(
                text = stringResource(Res.string.advanced_preview_pending),
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        else -> {
            if (matchedTag != null) {
                KomiText(
                    text =
                        pluralStringResource(
                            Res.plurals.advanced_preview_release,
                            matchedAssets.size,
                            matchedTag,
                            matchedAssets.size,
                        ),
                    role = KomiTextRole.Label,
                    color = colors.primary,
                )

                Spacer(Modifier.height(6.dp))
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 0.dp, max = 180.dp),
            ) {
                items(matchedAssets, key = { it.id }) { asset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KomiIcon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp),
                        )

                        Spacer(Modifier.width(10.dp))

                        KomiText(
                            text = asset.name,
                            role = KomiTextRole.Body,
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        KomiText(
                            text = formatFileSize(asset.size),
                            role = KomiTextRole.Caption,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VariantRow(
    pinnedVariant: String?,
    isStale: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KomiIcon(
            imageVector = if (isStale) Icons.Default.Warning else Icons.Default.Tune,
            contentDescription = null,
            tint =
                if (isStale) {
                    colors.error
                } else {
                    colors.primary
                },
            modifier = Modifier.size(20.dp),
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            KomiText(
                text = stringResource(Res.string.variant_picker_title),
                role = KomiTextRole.Label,
                color = colors.onSurface,
            )

            KomiText(
                text =
                    when {
                        isStale ->
                            stringResource(Res.string.variant_picker_stale_title)
                        pinnedVariant.isNullOrBlank() ->
                            stringResource(Res.string.variant_picker_auto_subtitle)
                        else ->
                            stringResource(Res.string.variant_picker_pinned, pinnedVariant)
                    },
                role = KomiTextRole.Body,
                color =
                    if (isStale) {
                        colors.error
                    } else {
                        colors.onSurfaceVariant
                    },
            )
        }

        KomiIcon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

