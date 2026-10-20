package zed.rainxch.details.presentation.components

import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Smartphone
import zed.rainxch.core.presentation.components.dividers.KomiHorizontalDivider
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.details.domain.model.SupportedLanguage
import zed.rainxch.details.presentation.model.SupportedLanguages
import zed.rainxch.githubstore.core.presentation.res.*

@Composable
fun LanguagePicker(
    isVisible: Boolean,
    query: String,
    languages: ImmutableList<SupportedLanguage>,
    selectedLanguageCode: String?,
    deviceLanguageCode: String,
    onQueryChange: (String) -> Unit,
    onLanguageSelected: (SupportedLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return

    val deviceLanguage = remember(deviceLanguageCode) {
        SupportedLanguages.all.find { it.code == deviceLanguageCode }
    }

    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    KomiSheet(onDismiss = onDismiss, placement = KomiSheetPlacement.Bottom) {
        Column(modifier = Modifier.fillMaxWidth()) {
            KomiText(
                text = stringResource(Res.string.translate_to),
                role = KomiTextRole.CardTitle,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            Spacer(Modifier.height(8.dp))

            KomiTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = stringResource(Res.string.search_language),
                leadingIcon = Icons.Rounded.Search,
                clearable = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (query.isBlank() && deviceLanguage != null) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(shape.corner))
                            .background(colors.primaryTint)
                            .clickable { onLanguageSelected(deviceLanguage) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KomiIcon(
                        imageVector = Icons.Default.Smartphone,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        KomiText(
                            text = deviceLanguage.displayName,
                            role = KomiTextRole.CardTitle,
                            color = colors.primary,
                        )
                        KomiText(
                            text = stringResource(Res.string.select_language),
                            role = KomiTextRole.Caption,
                            color = colors.primary,
                        )
                    }
                    if (deviceLanguage.code == selectedLanguageCode) {
                        KomiIcon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
            }

            KomiHorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(
                    items = languages,
                    key = { it.code },
                ) { language ->
                    LanguageListItem(
                        language = language,
                        isSelected = language.code == selectedLanguageCode,
                        onClick = { onLanguageSelected(language) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageListItem(
    language: SupportedLanguage,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val colors = LocalPersonality.current.colors
        Column(modifier = Modifier.weight(1f)) {
            KomiText(
                text = language.displayName,
                role = KomiTextRole.ItemTitle,
                color =
                    if (isSelected) {
                        colors.primary
                    } else {
                        colors.onSurface
                    },
            )
            KomiText(
                text = language.code,
                role = KomiTextRole.Caption,
                color = colors.outline,
            )
        }

        if (isSelected) {
            Spacer(Modifier.width(8.dp))
            KomiIcon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
