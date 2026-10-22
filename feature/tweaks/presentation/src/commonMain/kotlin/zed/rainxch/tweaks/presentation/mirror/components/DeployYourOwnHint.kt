package zed.rainxch.tweaks.presentation.mirror.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.mirror_deploy_your_own_hint

@Composable
fun DeployYourOwnHint(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KomiText(
        text = stringResource(Res.string.mirror_deploy_your_own_hint),
        role = KomiTextRole.Body,
        color = LocalPersonality.current.colors.primary,
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 16.dp, horizontal = 12.dp),
    )
}
