package zed.rainxch.details.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.outlined.Link
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfaceElevation
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.details_linked_repo_banner_body
import zed.rainxch.githubstore.core.presentation.res.details_linked_repo_banner_title
import zed.rainxch.githubstore.core.presentation.res.details_unlink_external_app_dialog_confirm

@Composable
fun LinkedRepoBanner(
    owner: String,
    repo: String,
    onUnlink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    KomiSurface(
        modifier = modifier,
        elevation = KomiSurfaceElevation.Flat,
        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KomiIcon(
                imageVector = Icons.Outlined.Link,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                KomiText(
                    text = stringResource(Res.string.details_linked_repo_banner_title, owner, repo),
                    role = KomiTextRole.Body,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    uppercase = false,
                )
                KomiText(
                    text = stringResource(Res.string.details_linked_repo_banner_body),
                    role = KomiTextRole.Body,
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(8.dp))

            KomiButton(
                onClick = onUnlink,
                label = stringResource(Res.string.details_unlink_external_app_dialog_confirm),
                variant = KomiButtonVariant.Text,
                size = KomiButtonSize.Sm,
                leadingIcon = Icons.Default.LinkOff,
            )
        }
    }
}
