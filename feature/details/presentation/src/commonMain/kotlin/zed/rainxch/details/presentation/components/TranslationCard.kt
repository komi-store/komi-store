package zed.rainxch.details.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfaceElevation
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.details.presentation.model.SupportedLanguages
import zed.rainxch.details.presentation.model.TranslationState
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.translation_card_cancel
import zed.rainxch.githubstore.core.presentation.res.translation_card_change_language
import zed.rainxch.githubstore.core.presentation.res.translation_card_detected_source
import zed.rainxch.githubstore.core.presentation.res.translation_card_retry
import zed.rainxch.githubstore.core.presentation.res.translation_card_show_original
import zed.rainxch.githubstore.core.presentation.res.translation_card_show_translation
import zed.rainxch.githubstore.core.presentation.res.translation_card_subtitle_idle
import zed.rainxch.githubstore.core.presentation.res.translation_card_subtitle_original
import zed.rainxch.githubstore.core.presentation.res.translation_card_subtitle_translated
import zed.rainxch.githubstore.core.presentation.res.translation_card_subtitle_translating
import zed.rainxch.githubstore.core.presentation.res.translation_card_target_label
import zed.rainxch.githubstore.core.presentation.res.translation_card_title
import zed.rainxch.githubstore.core.presentation.res.translation_card_translate_to

@Composable
fun TranslationCard(
    state: TranslationState,
    deviceLanguageCode: String,
    onPickLanguage: () -> Unit,
    onTranslate: (String) -> Unit,
    onToggle: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val effectiveTargetCode = state.targetLanguageCode ?: deviceLanguageCode
    val effectiveTargetName =
        state.targetLanguageDisplayName
            ?: SupportedLanguages.all.firstOrNull { it.code == effectiveTargetCode }?.displayName
            ?: effectiveTargetCode

    KomiSurface(
        modifier = modifier.fillMaxWidth(),
        elevation = KomiSurfaceElevation.Flat,
        contentPadding = PaddingValues(16.dp),
    ) {
        Column {
            Header(state = state, displayName = effectiveTargetName)

            Spacer(Modifier.height(14.dp))

            TargetLanguageRow(
                displayName = effectiveTargetName,
                onPick = onPickLanguage,
            )

            Spacer(Modifier.height(12.dp))

            ActionRow(
                state = state,
                effectiveTargetCode = effectiveTargetCode,
                effectiveTargetName = effectiveTargetName,
                onTranslate = onTranslate,
                onToggle = onToggle,
                onCancel = onCancel,
            )

            if (state.error != null) {
                Spacer(Modifier.height(10.dp))
                ErrorRow(
                    message = state.error,
                    onRetry = { onTranslate(effectiveTargetCode) },
                )
            }

            if (state.translatedText != null && state.detectedSourceLanguage != null) {
                Spacer(Modifier.height(8.dp))
                KomiText(
                    text =
                        stringResource(
                            Res.string.translation_card_detected_source,
                            SupportedLanguages.all.firstOrNull { it.code == state.detectedSourceLanguage }?.displayName
                                ?: state.detectedSourceLanguage,
                        ),
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Header(
    state: TranslationState,
    displayName: String,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(shape.corner))
                    .background(colors.primaryTint),
            contentAlignment = Alignment.Center,
        ) {
            KomiIcon(
                imageVector = Icons.Outlined.Translate,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            KomiText(
                text = stringResource(Res.string.translation_card_title),
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
            )
            val subtitle =
                when {
                    state.isTranslating -> {
                        stringResource(Res.string.translation_card_subtitle_translating)
                    }

                    state.translatedText != null && state.isShowingTranslation -> {
                        stringResource(Res.string.translation_card_subtitle_translated, displayName)
                    }

                    state.translatedText != null && !state.isShowingTranslation -> {
                        stringResource(Res.string.translation_card_subtitle_original)
                    }

                    else -> {
                        stringResource(Res.string.translation_card_subtitle_idle)
                    }
                }
            KomiText(
                text = subtitle,
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TargetLanguageRow(
    displayName: String,
    onPick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val rowShape = RoundedCornerShape(shape.corner)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KomiText(
            text = stringResource(Res.string.translation_card_target_label),
            role = KomiTextRole.Overline,
            color = colors.onSurfaceVariant,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(rowShape)
                    .background(colors.surfaceContainerHigh)
                    .border(1.dp, colors.divider, rowShape)
                    .clickable(onClick = onPick)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KomiIcon(
                imageVector = Icons.Outlined.Language,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            KomiText(
                text = displayName,
                role = KomiTextRole.Label,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            KomiIcon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = stringResource(Res.string.translation_card_change_language),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ActionRow(
    state: TranslationState,
    effectiveTargetCode: String,
    effectiveTargetName: String,
    onTranslate: (String) -> Unit,
    onToggle: () -> Unit,
    onCancel: () -> Unit,
) {
    when {
        state.isTranslating -> {
            KomiButton(
                onClick = onCancel,
                label = stringResource(Res.string.translation_card_cancel),
                variant = KomiButtonVariant.Outline,
                loading = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        state.translatedText != null -> {
            KomiButton(
                onClick = onToggle,
                label =
                    if (state.isShowingTranslation) {
                        stringResource(Res.string.translation_card_show_original)
                    } else {
                        stringResource(Res.string.translation_card_show_translation)
                    },
                variant = KomiButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        else -> {
            KomiButton(
                onClick = { onTranslate(effectiveTargetCode) },
                label =
                    stringResource(
                        Res.string.translation_card_translate_to,
                        effectiveTargetName,
                    ),
                variant = KomiButtonVariant.Primary,
                leadingIcon = Icons.Outlined.Translate,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ErrorRow(
    message: String,
    onRetry: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(shape.corner))
                .background(colors.error)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KomiText(
            text = message,
            role = KomiTextRole.Body,
            color = colors.onError,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(shape.cornerSmall))
                    .background(colors.error)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            KomiText(
                text = stringResource(Res.string.translation_card_retry),
                role = KomiTextRole.Overline,
                color = colors.onError,
            )
        }
    }
}
