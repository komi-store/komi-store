package zed.rainxch.core.presentation.components.buttons

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import zed.rainxch.core.presentation.components.overlays.KomiTooltip
import zed.rainxch.core.presentation.components.shapes.MorphShape
import zed.rainxch.core.presentation.locals.LocalPersonality

// The bar's floating buttons (back, the screen's FAB): a tonal cookie that answers a press with
// its whole body instead of a ripple. It dips, rounds into a circle and turns its icon half a
// turn on the personality's bouncy spring, and confirms the tap with a haptic tick.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun KomiCookieButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val personality = LocalPersonality.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = personality.motion.bouncySpatial(),
        label = "cookiePress",
    )
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    val shape = remember(morph, press) { MorphShape(morph, press) }
    KomiTooltip(text = contentDescription, modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .size(CookieSize)
                    .graphicsLayer {
                        scaleX = 1f - PRESS_DIP * press
                        scaleY = 1f - PRESS_DIP * press
                    }.shadow(FloatingElevation, shape)
                    .clip(shape)
                    .background(containerColor)
                    .then(if (focused) Modifier.border(FocusRing, personality.colors.primary, shape) else Modifier)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onClick()
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.graphicsLayer { rotationZ = PRESS_TURN * press },
            )
        }
    }
}

private val CookieSize = 56.dp

// Material's level 3, a FAB's: the cookies float over the content.
private val FloatingElevation = 6.dp

private val FocusRing = 2.dp

private const val PRESS_DIP = 0.2f

private const val PRESS_TURN = 180f
