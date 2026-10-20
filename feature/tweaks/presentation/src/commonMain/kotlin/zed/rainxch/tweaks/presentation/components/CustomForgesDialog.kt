package zed.rainxch.tweaks.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.chips.KomiChip
import zed.rainxch.core.presentation.components.chips.KomiChipKind
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.overlays.KomiDialog
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.tweaks.presentation.TweaksAction
import zed.rainxch.tweaks.presentation.TweaksState

@Composable
fun CustomForgesDialog(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    KomiDialog(
        onDismissRequest = { onAction(TweaksAction.OnDismissCustomForgesDialog) },
        title = { KomiText(stringResource(Res.string.custom_forges_dialog_title), role = KomiTextRole.CardTitle) },
        text = {
            Column {

                KomiText(
                    text = stringResource(Res.string.custom_forges_dialog_builtin_note),
                    role = KomiTextRole.Body,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(personality.shape.cornerSmall))
                        .background(colors.primaryContainer)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                KomiText(
                    text = stringResource(Res.string.custom_forges_dialog_help),
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KomiTextField(
                        value = state.customForgeDraft,
                        onValueChange = { onAction(TweaksAction.OnCustomForgeDraftChanged(it)) },
                        placeholder = "forgejo.example.com",
                        error = state.customForgeError,
                        modifier = Modifier.weight(1f),
                    )
                    KomiButton(
                        onClick = { onAction(TweaksAction.OnAddCustomForge) },
                        label = stringResource(Res.string.custom_forges_add_button),
                        variant = KomiButtonVariant.Text,
                        size = KomiButtonSize.Sm,
                    )
                }
                if (state.customForgeError != null) {
                    KomiText(
                        text = state.customForgeError,
                        role = KomiTextRole.Body,
                        color = colors.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (state.customForgeHosts.isEmpty()) {
                    KomiText(
                        text = stringResource(Res.string.custom_forges_empty),
                        role = KomiTextRole.Body,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp).padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(items = state.customForgeHosts.toList(), key = { it }) { host ->
                            KomiChip(
                                label = host,
                                kind = KomiChipKind.Input,
                                onRemove = { onAction(TweaksAction.OnRemoveCustomForge(host)) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            KomiButton(
                onClick = { onAction(TweaksAction.OnDismissCustomForgesDialog) },
                label = stringResource(Res.string.done),
                variant = KomiButtonVariant.Text,
                size = KomiButtonSize.Sm,
            )
        },
    )
}
