package zed.rainxch.githubstore.app.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.announcement.WhatsNewEntry
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.testing.KomiTestTags
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.whats_new_cta_dismiss
import zed.rainxch.githubstore.core.presentation.res.whats_new_cta_history
import zed.rainxch.githubstore.core.presentation.res.whats_new_sheet_heading
import zed.rainxch.githubstore.core.presentation.res.whats_new_version_label
import zed.rainxch.profile.presentation.whatsnew.SectionBlock

@Composable
fun WhatsNewSheet(
    entry: WhatsNewEntry,
    showHistoryAction: Boolean,
    onDismiss: () -> Unit,
    onViewHistory: () -> Unit,
) {
    KomiSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(entry)

            entry.sections.forEach { section ->
                SectionBlock(section)
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KomiButton(
                    onClick = onDismiss,
                    label = stringResource(Res.string.whats_new_cta_dismiss),
                    variant = KomiButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth().testTag(KomiTestTags.WHATS_NEW_DISMISS),
                )

                if (showHistoryAction) {
                    KomiButton(
                        onClick = onViewHistory,
                        label = stringResource(Res.string.whats_new_cta_history),
                        variant = KomiButtonVariant.Text,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SheetHeader(entry: WhatsNewEntry) {
    val colors = LocalPersonality.current.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KomiText(
            text = stringResource(Res.string.whats_new_sheet_heading, entry.versionName),
            role = KomiTextRole.CardTitle,
            color = colors.onSurface,
        )
        KomiText(
            text =
                stringResource(
                    Res.string.whats_new_version_label,
                    entry.versionName,
                    entry.releaseDate,
                ),
            role = KomiTextRole.Caption,
            color = colors.onSurfaceVariant,
        )
    }
}
