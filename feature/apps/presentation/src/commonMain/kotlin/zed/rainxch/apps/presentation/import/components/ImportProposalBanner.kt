package zed.rainxch.apps.presentation.import.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.external_import_proposal_banner_body
import zed.rainxch.githubstore.core.presentation.res.external_import_proposal_banner_dismiss
import zed.rainxch.githubstore.core.presentation.res.external_import_proposal_banner_headline
import zed.rainxch.githubstore.core.presentation.res.external_import_proposal_banner_review

@Composable
fun ImportProposalBanner(
    pendingCount: Int,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(shape.corner))
                .background(colors.primaryContainer)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KomiIcon(
            imageVector = Icons.Outlined.FileDownload,
            contentDescription = null,
            tint = colors.onPrimaryContainer,
            modifier = Modifier.size(24.dp),
        )

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            KomiText(
                text =
                    pluralStringResource(
                        Res.plurals.external_import_proposal_banner_headline,
                        pendingCount,
                        pendingCount,
                    ),
                role = KomiTextRole.CardTitle,
                color = colors.onPrimaryContainer,
            )

            KomiText(
                text = stringResource(Res.string.external_import_proposal_banner_body),
                role = KomiTextRole.Body,
                color = colors.onPrimaryContainer,
            )
        }

        Spacer(Modifier.width(8.dp))

        KomiButton(
            onClick = onReview,
            label = stringResource(Res.string.external_import_proposal_banner_review),
            variant = KomiButtonVariant.Text,
            size = KomiButtonSize.Sm,
        )

        KomiIconButton(
            icon = Icons.Default.Close,
            contentDescription = stringResource(Res.string.external_import_proposal_banner_dismiss),
            onClick = onDismiss,
        )
    }
}
