package zed.rainxch.auth.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant

@Composable
internal fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    KomiButton(
        onClick = onClick,
        label = text,
        enabled = enabled,
        variant = KomiButtonVariant.Primary,
        size = KomiButtonSize.Lg,
        fullWidth = true,
        leadingIcon = leadingIcon,
        modifier =
            modifier
                .fillMaxWidth()
                .height(52.dp),
    )
}
