package zed.rainxch.apps.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.dividers.KomiHorizontalDivider
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.KomiDialog
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.AppsAction
import zed.rainxch.apps.presentation.AppsState
import zed.rainxch.apps.presentation.model.VariantPickerError
import zed.rainxch.githubstore.core.presentation.res.*

@Composable
fun VariantPickerDialog(
    state: AppsState,
    onAction: (AppsAction) -> Unit,
) {
    val app = state.variantPickerApp ?: return
    val colors = LocalPersonality.current.colors

    KomiDialog(
        onDismissRequest = { onAction(AppsAction.OnDismissVariantPicker) },
        title = {
            Column {
                KomiText(
                    text = stringResource(Res.string.variant_picker_title),
                    role = KomiTextRole.CardTitle,
                )

                Spacer(Modifier.height(2.dp))

                KomiText(
                    text = app.appName,
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (app.preferredVariantStale) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                colors.error,
                                RoundedCornerShape(LocalPersonality.current.shape.corner),
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KomiIcon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = colors.onError,
                            modifier = Modifier.size(20.dp),
                        )

                        Spacer(Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            KomiText(
                                text = stringResource(Res.string.variant_picker_stale_title),
                                role = KomiTextRole.Label,
                                color = colors.onError,
                            )

                            if (state.variantPickerCurrentVariant != null) {
                                KomiText(
                                    text = stringResource(
                                        Res.string.variant_picker_stale_was,
                                        state.variantPickerCurrentVariant,
                                    ),
                                    role = KomiTextRole.Body,
                                    color = colors.onError,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                }

                KomiText(
                    text = stringResource(Res.string.variant_picker_description),
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))

                when {
                    state.variantPickerLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            KomiCircularProgress()
                        }
                    }

                    state.variantPickerError != null -> {
                        KomiText(
                            text = stringResource(
                                when (state.variantPickerError) {
                                    VariantPickerError.NoAssets -> Res.string.variant_picker_no_assets
                                    VariantPickerError.NoPinnableVariants -> Res.string.variant_picker_no_pinnable
                                    VariantPickerError.LoadFailed -> Res.string.variant_picker_load_failed
                                    VariantPickerError.SaveFailed -> Res.string.variant_picker_save_failed
                                },
                            ),
                            role = KomiTextRole.Body,
                            color = colors.error,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }

                    else -> {
                        val current = state.variantPickerCurrentVariant
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 0.dp, max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            item {
                                VariantRow(
                                    isSelected = current == null,
                                    title = stringResource(Res.string.variant_picker_auto_title),
                                    subtitle = stringResource(Res.string.variant_picker_auto_subtitle),
                                    leadingIcon = Icons.Default.AutoAwesome,
                                    onClick = { onAction(AppsAction.OnResetVariantToAuto) },
                                )

                                KomiHorizontalDivider(
                                    color = colors.divider,
                                )
                            }

                            items(state.variantPickerOptions, key = { it.assetId }) { option ->
                                VariantRow(
                                    isSelected = option.variant.equals(current, ignoreCase = true),
                                    title = option.variant,
                                    subtitle = option.subtitle,
                                    onClick = { onAction(AppsAction.OnVariantSelected(option.variant)) },
                                )
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            KomiButton(
                onClick = {
                    onAction(AppsAction.OnDismissVariantPicker)
                    onAction(AppsAction.OnOpenAdvancedSettings(app))
                },
                label = stringResource(Res.string.variant_picker_open_filter),
                variant = KomiButtonVariant.Text,
                size = KomiButtonSize.Sm,
            )
        },
        confirmButton = {
            KomiButton(
                onClick = { onAction(AppsAction.OnDismissVariantPicker) },
                label = stringResource(Res.string.cancel),
                variant = KomiButtonVariant.Text,
                size = KomiButtonSize.Sm,
            )
        },
    )
}

@Composable
private fun VariantRow(
    isSelected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
) {
    val colors = LocalPersonality.current.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KomiIcon(
            imageVector =
                when {
                    isSelected -> Icons.Default.RadioButtonChecked
                    else -> Icons.Default.RadioButtonUnchecked
                },
            contentDescription = null,
            tint =
                if (isSelected) {
                    colors.primary
                } else {
                    colors.onSurfaceVariant
                },
            modifier = Modifier.size(20.dp),
        )

        Spacer(Modifier.width(12.dp))

        if (leadingIcon != null) {
            KomiIcon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )

            Spacer(Modifier.width(8.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            KomiText(
                text = title,
                role = if (isSelected) KomiTextRole.Label else KomiTextRole.Body,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            KomiText(
                text = subtitle,
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isSelected) {
            KomiIcon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
