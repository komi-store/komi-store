package zed.rainxch.apps.presentation.import.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.external_import_auto_summary_body
import zed.rainxch.githubstore.core.presentation.res.external_import_auto_summary_continue
import zed.rainxch.githubstore.core.presentation.res.external_import_auto_summary_headline
import zed.rainxch.githubstore.core.presentation.res.external_import_auto_summary_more_count
import zed.rainxch.githubstore.core.presentation.res.external_import_auto_summary_review_hint
import zed.rainxch.githubstore.core.presentation.res.external_import_auto_summary_undo_all

@Composable
fun AutoImportSummaryScreen(
    autoLinkedCount: Int,
    autoLinkedLabels: ImmutableList<String>,
    cardsRemaining: Int,
    onContinue: () -> Unit,
    onUndoAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors

    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 480.dp),
        ) {
            KomiIcon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(72.dp),
            )

            KomiText(
                text =
                    pluralStringResource(
                        Res.plurals.external_import_auto_summary_headline,
                        autoLinkedCount,
                        autoLinkedCount,
                    ),
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )

            KomiText(
                text = stringResource(Res.string.external_import_auto_summary_body),
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (cardsRemaining > 0) {
                KomiText(
                    text =
                        pluralStringResource(
                            Res.plurals.external_import_auto_summary_review_hint,
                            cardsRemaining,
                            cardsRemaining,
                        ),
                    role = KomiTextRole.Body,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
            }

            if (autoLinkedLabels.isNotEmpty()) {
                val visibleLabels = autoLinkedLabels.take(MAX_VISIBLE_CHIPS)
                val overflowCount = autoLinkedLabels.size - visibleLabels.size

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    visibleLabels.forEach { label ->
                        ChipSurface(text = label)
                    }

                    if (overflowCount > 0) {
                        ChipSurface(
                            text =
                                stringResource(
                                    Res.string.external_import_auto_summary_more_count,
                                    overflowCount,
                                ),
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KomiButton(
                    onClick = onUndoAll,
                    label = stringResource(Res.string.external_import_auto_summary_undo_all),
                    variant = KomiButtonVariant.Outline,
                )

                KomiButton(
                    onClick = onContinue,
                    label = stringResource(Res.string.external_import_auto_summary_continue),
                    variant = KomiButtonVariant.Primary,
                )
            }
        }
    }
}

@Composable
private fun ChipSurface(text: String) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape

    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(shape.corner))
                .background(colors.primaryContainer),
    ) {
        KomiText(
            text = text,
            role = KomiTextRole.LabelSmall,
            color = colors.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private const val MAX_VISIBLE_CHIPS = 5
