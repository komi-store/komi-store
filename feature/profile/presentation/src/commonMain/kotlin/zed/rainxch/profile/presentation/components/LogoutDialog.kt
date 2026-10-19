package zed.rainxch.profile.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.close
import zed.rainxch.githubstore.core.presentation.res.logout
import zed.rainxch.githubstore.core.presentation.res.logout_confirmation
import zed.rainxch.githubstore.core.presentation.res.logout_revocation_note
import zed.rainxch.githubstore.core.presentation.res.warning

@Composable
fun LogoutDialog(
    onDismissRequest: () -> Unit,
    onLogout: () -> Unit,
) {
    KomiSheet(
        onDismiss = onDismissRequest,
        placement = KomiSheetPlacement.Center,
        title = stringResource(Res.string.warning),
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KomiButton(
                    onClick = onDismissRequest,
                    label = stringResource(Res.string.close),
                    variant = KomiButtonVariant.Text,
                )
                KomiButton(
                    onClick = onLogout,
                    label = stringResource(Res.string.logout),
                    variant = KomiButtonVariant.Destructive,
                )
            }
        },
    ) {
        KomiText(
            text = stringResource(Res.string.logout_confirmation),
            role = KomiTextRole.Body,
        )
        Spacer(Modifier.height(8.dp))
        KomiText(
            text = stringResource(Res.string.logout_revocation_note),
            role = KomiTextRole.Body,
        )
    }
}
