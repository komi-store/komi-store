package zed.rainxch.tweaks.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.language_follow_system
import zed.rainxch.tweaks.presentation.model.LanguagePickerOption

@Composable
fun LanguagePickerSheet(
    title: String,
    options: ImmutableList<LanguagePickerOption>,
    selectedId: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    intro: String? = null,
) {
    val filtered =
        if (query.isBlank()) {
            options
        } else {
            options.filter {
                it.title.contains(query, ignoreCase = true) ||
                    (it.tag?.contains(query, ignoreCase = true) == true)
            }
        }

    KomiSheet(
        onDismiss = onDismiss,
        modifier = modifier,
        title = title,
    ) {
        if (intro != null) {
            KomiText(
                text = intro,
                role = KomiTextRole.Body,
                color = LocalPersonality.current.colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )

            Spacer(Modifier.height(8.dp))
        }

        TweaksSearchField(
            query = query,
            onQueryChange = onQueryChange,
            onClear = { onQueryChange("") },
        )

        Spacer(Modifier.height(12.dp))

        if (query.isBlank()) {
            LanguageRow(
                title = stringResource(Res.string.language_follow_system),
                subtitleTag = null,
                leadingIcon = true,
                selected = selectedId == null,
                onClick = { onSelect(null) },
            )

            Spacer(Modifier.height(8.dp))
        }

        filtered.forEach { option ->
            LanguageRow(
                title = option.title,
                subtitleTag = option.tag,
                leadingIcon = false,
                selected = selectedId == option.id,
                onClick = { onSelect(option.id) },
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LanguageRow(
    title: String,
    subtitleTag: String?,
    leadingIcon: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    KomiSurface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (leadingIcon) {
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(LocalPersonality.current.shape.cornerSmall))
                            .background(colors.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    KomiIcon(
                        imageVector = Icons.Outlined.PhoneAndroid,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                KomiText(
                    text = title,
                    role = KomiTextRole.ItemTitle,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!subtitleTag.isNullOrBlank()) {
                    KomiText(
                        text = subtitleTag,
                        role = KomiTextRole.Caption,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (selected) {
                KomiIcon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
