package zed.rainxch.apps.presentation.import.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.import.model.RepoSuggestionUi
import zed.rainxch.apps.presentation.import.model.SuggestionChipTone
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.formatCompactCount
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.external_import_card_owner_byline
import zed.rainxch.githubstore.core.presentation.res.external_import_match_confidence_a11y
import zed.rainxch.githubstore.core.presentation.res.external_import_match_confidence_chip

@Composable
fun RepoCandidateRow(
    suggestion: RepoSuggestionUi,
    onPick: (RepoSuggestionUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val chipBg =
        when (suggestion.chipTone) {
            SuggestionChipTone.Primary -> colors.primaryContainer
            SuggestionChipTone.Muted -> colors.surfaceVariant
        }
    val chipFg =
        when (suggestion.chipTone) {
            SuggestionChipTone.Primary -> colors.onPrimaryContainer
            SuggestionChipTone.Muted -> colors.onSurfaceVariant
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { onPick(suggestion) }
                .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            KomiText(
                text = suggestion.repo,
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KomiText(
                    text =
                        stringResource(
                            Res.string.external_import_card_owner_byline,
                            suggestion.owner,
                        ),
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(shape.cornerSmall))
                            .background(
                                if (suggestion.sourceHost == null) {
                                    colors.surfaceVariant
                                } else {
                                    colors.primaryContainer
                                },
                            ),
                ) {
                    KomiText(
                        text =
                            when {
                                suggestion.sourceHost == null -> {
                                    "GitHub"
                                }

                                suggestion.sourceHost.equals("codeberg.org", ignoreCase = true) -> {
                                    "Codeberg"
                                }

                                else -> {
                                    suggestion.sourceHost
                                }
                            },
                        role = KomiTextRole.Caption,
                        color =
                            if (suggestion.sourceHost == null) {
                                colors.onSurfaceVariant
                            } else {
                                colors.onPrimaryContainer
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            if (!suggestion.description.isNullOrBlank()) {
                KomiText(
                    text = suggestion.description,
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (suggestion.stars != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KomiIcon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )

                    Spacer(Modifier.width(4.dp))

                    KomiText(
                        text = formatCompactCount(suggestion.stars),
                        role = KomiTextRole.Caption,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        val confidenceLabel =
            stringResource(Res.string.external_import_match_confidence_a11y, suggestion.confidencePercent)
        Box(
            modifier =
                Modifier
                    .semantics {
                        contentDescription = confidenceLabel
                    }.clip(RoundedCornerShape(shape.corner))
                    .background(chipBg),
        ) {
            KomiText(
                text =
                    stringResource(
                        Res.string.external_import_match_confidence_chip,
                        suggestion.confidencePercent,
                    ),
                role = KomiTextRole.Caption,
                color = chipFg,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}
