package zed.rainxch.details.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
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

    Box(modifier = modifier) {
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

        if (showCoachmark) {
            Coachmark(onDismiss = onCoachmarkDismiss)
        }
    }
}

@Composable
private fun rememberPulse(active: Boolean) =
    rememberInfiniteTransition(label = "inspect-pulse")
        .animateFloat(
            initialValue = if (active) 1f else 1f,
            targetValue = if (active) 1.08f else 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 900),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "inspect-pulse-scale",
        )

@Composable
private fun rememberTilt(active: Boolean) =
    rememberInfiniteTransition(label = "inspect-tilt")
        .animateFloat(
            initialValue = if (active) -6f else 0f,
            targetValue = if (active) 6f else 0f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1300),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "inspect-tilt-deg",
        )

@Composable
private fun Coachmark(onDismiss: () -> Unit) {
    val colors = LocalPersonality.current.colors
    Popup(
        alignment = Alignment.TopEnd,
        offset =
            androidx.compose.ui.unit
                .IntOffset(x = 0, y = -260),
        properties =
            PopupProperties(
                focusable = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
        onDismissRequest = onDismiss,
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Box(
                modifier =
                    Modifier
                        .width(260.dp)
                        .clip(RoundedCornerShape(LocalPersonality.current.shape.corner))
                        .background(colors.primary),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        KomiIcon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        KomiText(
                            text = stringResource(Res.string.apk_inspect_coachmark_title),
                            role = KomiTextRole.Title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = colors.onPrimary,
                            uppercase = false,
                        )
                    }
                    KomiText(
                        text = stringResource(Res.string.apk_inspect_coachmark_body),
                        role = KomiTextRole.Body,
                        fontSize = 13.sp,
                        color = colors.onPrimary.copy(alpha = 0.9f),
                    )
                    Row(
                        modifier = Modifier.padding(top = 2.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        KomiButton(
                            onClick = onDismiss,
                            label = stringResource(Res.string.apk_inspect_coachmark_dismiss),
                            variant = KomiButtonVariant.Text,
                            size = KomiButtonSize.Sm,
                        )
                    }
                }
            }

            Box(
                modifier =
                    Modifier
                        .padding(end = 24.dp)
                        .size(width = 16.dp, height = 8.dp)
                        .arrowDown(colors.primary),
            )
        }
    }
}

private fun Modifier.arrowDown(color: androidx.compose.ui.graphics.Color): Modifier =
    this.fillMaxSize().background(
        color = color,
        shape = TriangleDownShape,
    )

private val TriangleDownShape =
    androidx.compose.foundation.shape.GenericShape { size, _ ->
        moveTo(0f, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width / 2f, size.height)
        close()
    }
