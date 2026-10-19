package zed.rainxch.tweaks.presentation.components.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.core.domain.model.appearance.AppPersonality
import zed.rainxch.core.presentation.components.buttons.KomiIconButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiSegmented
import zed.rainxch.core.presentation.components.buttons.KomiSegmentedItem
import zed.rainxch.core.presentation.components.inputs.KomiSwitch
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.tweaks.presentation.components.shell.SettingsGroup
import zed.rainxch.tweaks.presentation.components.shell.SettingsRow
import zed.rainxch.tweaks.presentation.components.shell.SettingsSectionHead
import zed.rainxch.tweaks.presentation.components.shell.SettingsValuePill
import zed.rainxch.tweaks.presentation.components.shell.TweaksDecorSlot

@Preview
@Composable
private fun AppearanceSectionPreview() {
    PersonalityPreview {
        var personality by remember { mutableStateOf(AppPersonality.MANGA) }
        var accent by remember { mutableStateOf(AccentId.CRIMSON) }
        var mode by remember { mutableStateOf("dark") }
        var amoled by remember { mutableStateOf(false) }
        val colors = LocalPersonality.current.colors

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSectionHead(label = "Look & Feel", slot = TweaksDecorSlot.LookAndFeel)
            AppearanceHeadline(
                personality = personality,
                accent = accent,
                onPersonalitySelected = { personality = it },
                onAccentSelected = { accent = it },
            )
            SettingsGroup {
                SettingsRow(
                    title = "Mode",
                    subtitle = "Light / Dark / System",
                    trailing = {
                        KomiSegmented(
                            selected = mode,
                            onSelect = { mode = it },
                            size = KomiIconButtonSize.Sm,
                            items =
                                persistentListOf(
                                    KomiSegmentedItem(value = "light", title = "Light"),
                                    KomiSegmentedItem(value = "dark", title = "Dark"),
                                    KomiSegmentedItem(value = "system", title = "Sys"),
                                ),
                        )
                    },
                )
                SettingsRow(
                    title = "AMOLED black",
                    subtitle = "Pure-black surfaces (dark mode)",
                    last = true,
                    trailing = {
                        KomiSwitch(checked = amoled, onCheckedChange = { amoled = it })
                    },
                )
            }
            SettingsGroup {
                SettingsRow(
                    title = "App language",
                    subtitle = "Follow system · 13 locales",
                    last = true,
                    trailing = { SettingsValuePill(value = "English", onClick = {}) },
                )
            }
        }
    }
}
