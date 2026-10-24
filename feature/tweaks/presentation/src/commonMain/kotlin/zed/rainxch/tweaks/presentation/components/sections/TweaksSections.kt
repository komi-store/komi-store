package zed.rainxch.tweaks.presentation.components.sections

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.feedback_hub_subtitle
import zed.rainxch.githubstore.core.presentation.res.section_app_block
import zed.rainxch.githubstore.core.presentation.res.section_connectivity
import zed.rainxch.githubstore.core.presentation.res.section_installs_and_updates
import zed.rainxch.githubstore.core.presentation.res.section_look_and_feel
import zed.rainxch.githubstore.core.presentation.res.section_privacy_and_data
import zed.rainxch.githubstore.core.presentation.res.select_language
import zed.rainxch.githubstore.core.presentation.res.tweaks_diagnostics_section_title
import zed.rainxch.githubstore.core.presentation.res.tweaks_entry_access_tokens
import zed.rainxch.githubstore.core.presentation.res.tweaks_entry_feedback
import zed.rainxch.githubstore.core.presentation.res.tweaks_entry_subtitle_tap
import zed.rainxch.tweaks.presentation.TweaksAction
import zed.rainxch.tweaks.presentation.TweaksState
import zed.rainxch.tweaks.presentation.components.appearance.AppearanceHeadline
import zed.rainxch.core.domain.model.appearance.AppPersonality
import zed.rainxch.tweaks.presentation.components.shell.SettingsDrillRow
import zed.rainxch.tweaks.presentation.components.shell.SettingsGroup
import zed.rainxch.tweaks.presentation.components.shell.SettingsRow
import zed.rainxch.tweaks.presentation.components.shell.SettingsSectionHead
import zed.rainxch.tweaks.presentation.components.shell.SettingsValuePill
import zed.rainxch.tweaks.presentation.components.shell.TweaksDecorSlot

@Composable
fun ColumnScope.lookAndFeelSection(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
    personality: AppPersonality,
    accent: AccentId,
    onPersonalitySelected: (AppPersonality) -> Unit,
    onAccentSelected: (AccentId) -> Unit,
    currentLanguageLabel: String,
    onOpenLanguage: () -> Unit,
    showHead: Boolean = true,
) {
    if (showHead) SettingsSectionHead(stringResource(Res.string.section_look_and_feel), TweaksDecorSlot.LookAndFeel)
    AppearanceHeadline(
        personality = personality,
        accent = accent,
        onPersonalitySelected = onPersonalitySelected,
        onAccentSelected = onAccentSelected,
    )
    appearanceSectionContent(state = state, onAction = onAction)
    SettingsGroup {
        SettingsRow(
            title = stringResource(Res.string.select_language),
            subtitle = currentLanguageLabel,
            last = true,
            onClick = onOpenLanguage,
            trailing = { SettingsValuePill(value = currentLanguageLabel, onClick = onOpenLanguage) },
        )
    }
}

@Composable
fun ColumnScope.connectivitySection(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
    onNavigateToMirrorPicker: () -> Unit,
    showHead: Boolean = true,
) {
    if (showHead) SettingsSectionHead(stringResource(Res.string.section_connectivity), TweaksDecorSlot.Connectivity)
    connectionSectionContent(state = state, onAction = onAction)
    SourcesSectionContent(
        state = state,
        onAction = onAction,
        onNavigateToMirrorPicker = onNavigateToMirrorPicker,
    )
    translationSectionContent(state = state, onAction = onAction)
}

@Composable
fun ColumnScope.installsSection(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
    onNavigateToSkippedUpdates: () -> Unit,
    showHead: Boolean = true,
) {
    if (showHead) SettingsSectionHead(stringResource(Res.string.section_installs_and_updates), TweaksDecorSlot.InstallsUpdates)
    installSectionContent(state = state, onAction = onAction)
    updatesSectionContent(
        state = state,
        onAction = onAction,
        onNavigateToSkippedUpdates = onNavigateToSkippedUpdates,
    )
}

@Composable
fun ColumnScope.privacySection(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
    onNavigateToHiddenRepositories: () -> Unit,
    onNavigateToHostTokens: () -> Unit,
    showHead: Boolean = true,
) {
    if (showHead) SettingsSectionHead(stringResource(Res.string.section_privacy_and_data), TweaksDecorSlot.PrivacyData)
    storageSectionContent(state = state, onAction = onAction)
    privacySectionContent(
        state = state,
        onAction = onAction,
        onNavigateToHiddenRepositories = onNavigateToHiddenRepositories,
    )
    SettingsGroup {
        SettingsDrillRow(
            title = stringResource(Res.string.tweaks_entry_access_tokens),
            subtitle = stringResource(Res.string.tweaks_entry_subtitle_tap),
            onClick = onNavigateToHostTokens,
            last = true,
        )
    }
}

@Composable
fun ColumnScope.appSection(
    onOpenFeedback: () -> Unit,
    showHead: Boolean = true,
) {
    if (showHead) SettingsSectionHead(stringResource(Res.string.section_app_block), TweaksDecorSlot.App)
    SettingsGroup {
        SettingsDrillRow(
            title = stringResource(Res.string.tweaks_entry_feedback),
            subtitle = stringResource(Res.string.feedback_hub_subtitle),
            onClick = onOpenFeedback,
            last = true,
        )
    }
}

@Composable
fun ColumnScope.diagnosticsSection(
    onAction: (TweaksAction) -> Unit,
    showHead: Boolean = true,
) {
    if (showHead) {
        SettingsSectionHead(
            label = stringResource(Res.string.tweaks_diagnostics_section_title),
            slot = TweaksDecorSlot.Diagnostics,
        )
    }
    DiagnosticsSectionContent(onAction = onAction)
}
