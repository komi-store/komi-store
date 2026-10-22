package zed.rainxch.core.presentation.components.buttons

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.components.overlays.LocalMenuOpen
import zed.rainxch.core.presentation.locals.LocalPersonality

// In a bar an icon swaps with a bouncy scale (the layout toggle), and a menu's trigger shows a
// close mark while its menu is open. The old icon leaves without overshoot, so it never flips.
@Composable
internal fun BarIcon(
    icon: ImageVector,
    contentDescription: String,
) {
    val motion = LocalPersonality.current.motion
    AnimatedContent(
        targetState = if (LocalMenuOpen.current) Icons.Rounded.Close else icon,
        transitionSpec = {
            (scaleIn(motion.bouncySpatial()) + fadeIn(motion.fastEffects())) togetherWith
                (scaleOut(motion.fastEffects()) + fadeOut(motion.fastEffects())) using
                SizeTransform(clip = false)
        },
        label = "barIcon",
    ) { shown ->
        Icon(imageVector = shown, contentDescription = contentDescription, modifier = Modifier.size(BarIconSize))
    }
}

private val BarIconSize = 24.dp
