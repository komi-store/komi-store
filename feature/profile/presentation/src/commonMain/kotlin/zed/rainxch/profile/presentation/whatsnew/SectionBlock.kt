package zed.rainxch.profile.presentation.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.announcement.WhatsNewSection
import zed.rainxch.core.domain.model.announcement.WhatsNewSectionType
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfaceElevation
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.whats_new_section_fixed
import zed.rainxch.githubstore.core.presentation.res.whats_new_section_heads_up
import zed.rainxch.githubstore.core.presentation.res.whats_new_section_improved
import zed.rainxch.githubstore.core.presentation.res.whats_new_section_new

@Composable
fun SectionBlock(section: WhatsNewSection) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(type = section.type)

        KomiSurface(
            modifier = Modifier.fillMaxWidth(),
            elevation = KomiSurfaceElevation.Card,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                section.bullets.forEach { bullet ->
                    BulletRow(text = bullet)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(type: WhatsNewSectionType) {
    val colors = LocalPersonality.current.colors
    val label: String
    val color: Color
    when (type) {
        WhatsNewSectionType.NEW -> {
            label = stringResource(Res.string.whats_new_section_new)
            color = colors.primary
        }

        WhatsNewSectionType.IMPROVED -> {
            label = stringResource(Res.string.whats_new_section_improved)
            color = colors.primary
        }

        WhatsNewSectionType.FIXED -> {
            label = stringResource(Res.string.whats_new_section_fixed)
            color = colors.primary
        }

        WhatsNewSectionType.HEADS_UP -> {
            label = stringResource(Res.string.whats_new_section_heads_up)
            color = colors.error
        }
    }

    KomiText(
        text = label,
        role = KomiTextRole.Overline,
        color = color,
    )
}

@Composable
private fun BulletRow(text: String) {
    val colors = LocalPersonality.current.colors
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KomiText(
            text = "•",
            role = KomiTextRole.Body,
            color = colors.onSurfaceVariant,
        )
        KomiText(
            text = text,
            role = KomiTextRole.Body,
            color = colors.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
