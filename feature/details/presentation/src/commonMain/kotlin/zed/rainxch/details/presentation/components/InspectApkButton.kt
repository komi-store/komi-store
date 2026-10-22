package zed.rainxch.details.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.KomiCoachmark
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_button_label
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_coachmark_body
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_coachmark_dismiss
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_coachmark_title

@Composable
fun InspectApkButton(
    showCoachmark: Boolean,
    onClick: () -> Unit,
    onCoachmarkDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val buttonShape = RoundedCornerShape(LocalPersonality.current.shape.cornerSmall)
    val pulse by rememberPulse(active = showCoachmark)
    val tilt by rememberTilt(active = showCoachmark)

    KomiCoachmark(
        visible = showCoachmark,
        title = stringResource(Res.string.apk_inspect_coachmark_title),
        text = stringResource(Res.string.apk_inspect_coachmark_body),
        dismissLabel = stringResource(Res.string.apk_inspect_coachmark_dismiss),
        onDismiss = onCoachmarkDismiss,
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .size(52.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                        rotationZ = tilt
                    }.clip(buttonShape)
                    .background(colors.surfaceContainerHigh)
                    .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            KomiIcon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(Res.string.apk_inspect_button_label),
                tint = colors.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun rememberPulse(active: Boolean): State<Float> {
    val motion = LocalPersonality.current.motion
    val on = active && motion.animates
    return rememberInfiniteTransition(label = "inspect-pulse")
        .animateFloat(
            initialValue = 1f,
            targetValue = if (on) 1.08f else 1f,
            animationSpec = infiniteRepeatable(animation = motion.ambient(), repeatMode = RepeatMode.Reverse),
            label = "inspect-pulse-scale",
        )
}

@Composable
private fun rememberTilt(active: Boolean): State<Float> {
    val motion = LocalPersonality.current.motion
    val on = active && motion.animates
    return rememberInfiniteTransition(label = "inspect-tilt")
        .animateFloat(
            initialValue = if (on) -6f else 0f,
            targetValue = if (on) 6f else 0f,
            animationSpec = infiniteRepeatable(animation = motion.ambient(), repeatMode = RepeatMode.Reverse),
            label = "inspect-tilt-deg",
        )
}
