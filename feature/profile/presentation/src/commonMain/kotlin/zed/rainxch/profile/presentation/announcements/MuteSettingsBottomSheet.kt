package zed.rainxch.profile.presentation.announcements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.announcement.AnnouncementCategory
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.inputs.KomiSwitch
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.announcements_mute_locked
import zed.rainxch.githubstore.core.presentation.res.announcements_mute_settings_title

@Composable
fun MuteSettingsBottomSheet(
    mutedCategories: Set<AnnouncementCategory>,
    onToggle: (AnnouncementCategory, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    KomiSheet(
        onDismiss = onDismiss,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(PaddingValues(horizontal = 24.dp, vertical = 8.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KomiText(
                text = stringResource(Res.string.announcements_mute_settings_title),
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
            )

            AnnouncementCategory.entries.forEach { category ->
                val locked = !category.isMutable
                CategoryRow(
                    category = category,
                    label = stringResource(categoryLabel(category)),
                    enabled = if (locked) true else category !in mutedCategories,
                    locked = locked,
                    onToggle = { newEnabled -> onToggle(category, !newEnabled) },
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CategoryRow(
    category: AnnouncementCategory,
    label: String,
    enabled: Boolean,
    locked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val rowModifier =
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .let { base ->
                if (locked) {
                    base
                } else {
                    base.toggleable(
                        value = enabled,
                        role = Role.Switch,
                        onValueChange = onToggle,
                    )
                }
            }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KomiText(
            text = label,
            role = KomiTextRole.Body,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (locked) {
            KomiIcon(
                imageVector = Icons.Filled.Lock,
                contentDescription = stringResource(Res.string.announcements_mute_locked),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        KomiSwitch(
            checked = enabled,
            onCheckedChange = null,
            enabled = !locked,
        )
    }
}
