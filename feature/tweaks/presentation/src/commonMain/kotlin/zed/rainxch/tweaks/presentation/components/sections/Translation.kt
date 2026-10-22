package zed.rainxch.tweaks.presentation.components.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.settings.SupportedTranslationLanguages
import zed.rainxch.core.domain.model.settings.TranslationProvider
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.chips.KomiChip
import zed.rainxch.core.presentation.components.chips.KomiChipKind
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.inputs.KomiSwitch
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.tweaks.presentation.TweaksAction
import zed.rainxch.tweaks.presentation.TweaksState
import zed.rainxch.tweaks.presentation.components.LanguagePickerSheet
import zed.rainxch.tweaks.presentation.components.shell.SettingsExpandableRow
import zed.rainxch.tweaks.presentation.components.shell.SettingsGroup
import zed.rainxch.tweaks.presentation.components.shell.SettingsRow

@Composable
fun translationSectionContent(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val provider = state.displayedTranslationProvider

    SettingsGroup(modifier = modifier) {
        SettingsExpandableRow(
            title = stringResource(Res.string.translation_provider_title),
            subtitle = providerLabel(provider),
            expanded = state.translationProviderExpanded,
            onToggle = { onAction(TweaksAction.OnTranslationProviderExpandToggle) },
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(TranslationProvider.entries) { entry ->
                    KomiChip(
                        label = providerLabel(entry),
                        kind = KomiChipKind.Filter,
                        selected = provider == entry,
                        onClick = { onAction(TweaksAction.OnTranslationProviderSelected(entry)) },
                    )
                }
            }
            when (provider) {
                TranslationProvider.YOUDAO -> YoudaoCredentialsForm(state, onAction)
                TranslationProvider.LIBRE_TRANSLATE -> LibreTranslateCredentialsForm(state, onAction)
                TranslationProvider.DEEPL -> DeeplCredentialsForm(state, onAction)
                TranslationProvider.MICROSOFT -> MicrosoftCredentialsForm(state, onAction)
                TranslationProvider.GOOGLE -> Unit
            }
        }

        SettingsRow(
            title = stringResource(Res.string.translation_auto_title),
            subtitle = stringResource(Res.string.translation_auto_subtitle),
            last = !state.autoTranslateEnabled,
            trailing = {
                KomiSwitch(
                    checked = state.autoTranslateEnabled,
                    onCheckedChange = { onAction(TweaksAction.OnAutoTranslateEnabledToggle(it)) },
                )
            },
        )
        if (state.autoTranslateEnabled) {
            Column(modifier = Modifier.padding(start = 15.dp, end = 15.dp, bottom = 15.dp)) {
                KomiText(
                    text = stringResource(Res.string.translation_auto_target_label),
                    role = KomiTextRole.Caption,
                    color = LocalPersonality.current.colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                TranslationTargetDropdown(
                    state = state,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun providerLabel(provider: TranslationProvider): String =
    when (provider) {
        TranslationProvider.GOOGLE -> stringResource(Res.string.translation_provider_google)
        TranslationProvider.YOUDAO -> stringResource(Res.string.translation_provider_youdao)
        TranslationProvider.LIBRE_TRANSLATE -> stringResource(Res.string.translation_provider_libre)
        TranslationProvider.DEEPL -> stringResource(Res.string.translation_provider_deepl)
        TranslationProvider.MICROSOFT -> stringResource(Res.string.translation_provider_microsoft)
    }

@Composable
private fun TranslationTargetDropdown(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val followSystemLabel = stringResource(Res.string.language_follow_system)
    val selectedTag = state.autoTranslateTargetLang
    val currentLabel =
        when (val match = SupportedTranslationLanguages.findByCode(selectedTag)) {
            null -> followSystemLabel
            else -> match.displayName
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(personality.shape.corner))
                .background(colors.surface)
                .clickable(onClick = { onAction(TweaksAction.OnTranslationTargetPickerOpen) })
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        KomiText(
            text = currentLabel,
            role = KomiTextRole.Label,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        Spacer(Modifier.size(8.dp))

        KomiIcon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }

    if (state.translationTargetPickerOpen) {
        LanguagePickerSheet(
            title = stringResource(Res.string.translation_auto_target_label),
            options = state.translationLanguageOptions,
            selectedId = selectedTag,
            query = state.languageQuery,
            onQueryChange = { onAction(TweaksAction.OnLanguageQueryChange(it)) },
            onSelect = { onAction(TweaksAction.OnAutoTranslateTargetSelected(it)) },
            onDismiss = { onAction(TweaksAction.OnTranslationTargetPickerDismiss) },
        )
    }
}

@Composable
private fun YoudaoCredentialsForm(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
) {
    val canSave = state.youdaoAppKey.isNotBlank() && state.youdaoAppSecret.isNotBlank()
    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KomiTextField(
            value = state.youdaoAppKey,
            onValueChange = { onAction(TweaksAction.OnYoudaoAppKeyChanged(it)) },
            label = stringResource(Res.string.translation_youdao_app_key),
            modifier = Modifier.fillMaxWidth(),
        )
        KomiTextField(
            value = state.youdaoAppSecret,
            onValueChange = { onAction(TweaksAction.OnYoudaoAppSecretChanged(it)) },
            label = stringResource(Res.string.translation_youdao_app_secret),
            password = true,
            keyboardType = KeyboardType.Password,
            modifier = Modifier.fillMaxWidth(),
        )
        KomiButton(
            onClick = { onAction(TweaksAction.OnYoudaoCredentialsSave) },
            label = stringResource(Res.string.translation_youdao_save),
            variant = KomiButtonVariant.Tonal,
            size = KomiButtonSize.Sm,
            enabled = canSave,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun LibreTranslateCredentialsForm(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KomiTextField(
            value = state.libreTranslateBaseUrl,
            onValueChange = { onAction(TweaksAction.OnLibreTranslateBaseUrlChanged(it)) },
            label = stringResource(Res.string.translation_libre_base_url),
            placeholder = "https://translate.disroot.org",
            keyboardType = KeyboardType.Uri,
            modifier = Modifier.fillMaxWidth(),
        )
        KomiTextField(
            value = state.libreTranslateApiKey,
            onValueChange = { onAction(TweaksAction.OnLibreTranslateApiKeyChanged(it)) },
            label = stringResource(Res.string.translation_libre_api_key),
            password = true,
            keyboardType = KeyboardType.Password,
            modifier = Modifier.fillMaxWidth(),
        )
        KomiButton(
            onClick = { onAction(TweaksAction.OnLibreTranslateCredentialsSave) },
            label = stringResource(Res.string.translation_libre_save),
            variant = KomiButtonVariant.Tonal,
            size = KomiButtonSize.Sm,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun DeeplCredentialsForm(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
) {
    val canSave = state.deeplAuthKey.isNotBlank()
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KomiText(
            text = stringResource(Res.string.translation_deepl_help),
            role = KomiTextRole.Body,
            color = LocalPersonality.current.colors.onSurfaceVariant,
        )
        KomiButton(
            onClick = { runCatching { uriHandler.openUri("https://www.deepl.com/pro-api") } },
            label = stringResource(Res.string.translation_deepl_get_free_key),
            variant = KomiButtonVariant.Text,
            size = KomiButtonSize.Sm,
            modifier = Modifier.align(Alignment.Start),
        )
        KomiTextField(
            value = state.deeplAuthKey,
            onValueChange = { onAction(TweaksAction.OnDeeplAuthKeyChanged(it)) },
            label = stringResource(Res.string.translation_deepl_auth_key),
            password = true,
            keyboardType = KeyboardType.Password,
            modifier = Modifier.fillMaxWidth(),
        )
        KomiButton(
            onClick = { onAction(TweaksAction.OnDeeplCredentialsSave) },
            label = stringResource(Res.string.translation_deepl_save),
            variant = KomiButtonVariant.Tonal,
            size = KomiButtonSize.Sm,
            enabled = canSave,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun MicrosoftCredentialsForm(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
) {
    val canSave = state.microsoftTranslatorKey.isNotBlank()
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KomiText(
            text = stringResource(Res.string.translation_microsoft_help),
            role = KomiTextRole.Body,
            color = LocalPersonality.current.colors.onSurfaceVariant,
        )
        KomiButton(
            onClick = { runCatching { uriHandler.openUri("https://portal.azure.com/#create/Microsoft.CognitiveServicesTextTranslation") } },
            label = stringResource(Res.string.translation_microsoft_get_free_key),
            variant = KomiButtonVariant.Text,
            size = KomiButtonSize.Sm,
            modifier = Modifier.align(Alignment.Start),
        )
        KomiTextField(
            value = state.microsoftTranslatorKey,
            onValueChange = { onAction(TweaksAction.OnMicrosoftTranslatorKeyChanged(it)) },
            label = stringResource(Res.string.translation_microsoft_key),
            password = true,
            keyboardType = KeyboardType.Password,
            modifier = Modifier.fillMaxWidth(),
        )
        KomiTextField(
            value = state.microsoftTranslatorRegion,
            onValueChange = { onAction(TweaksAction.OnMicrosoftTranslatorRegionChanged(it)) },
            label = stringResource(Res.string.translation_microsoft_region),
            placeholder = "global",
            modifier = Modifier.fillMaxWidth(),
        )
        KomiButton(
            onClick = { onAction(TweaksAction.OnMicrosoftTranslatorCredentialsSave) },
            label = stringResource(Res.string.translation_microsoft_save),
            variant = KomiButtonVariant.Tonal,
            size = KomiButtonSize.Sm,
            enabled = canSave,
            modifier = Modifier.align(Alignment.End),
        )
    }
}
