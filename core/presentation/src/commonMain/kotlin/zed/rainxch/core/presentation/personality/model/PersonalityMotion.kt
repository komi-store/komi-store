package zed.rainxch.core.presentation.personality.model

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

@Immutable
data class KomiSpring(
    val dampingRatio: Float,
    val stiffness: Float,
)

// Spatial springs move and resize things and may overshoot; effects springs fade and
// recolor and never do. Fast suits small parts (buttons, chips), default suits panels.
@Immutable
data class PersonalityMotion(
    val level: MotionLevel,
    val fastSpatialSpring: KomiSpring,
    val defaultSpatialSpring: KomiSpring,
    val bouncySpatialSpring: KomiSpring,
    val fastEffectsSpring: KomiSpring,
    val defaultEffectsSpring: KomiSpring,
    val slowEffectsSpring: KomiSpring,
    val shortMs: Int,
    val mediumMs: Int,
    val longMs: Int,
    val ambientMs: Int,
) {
    val animates: Boolean get() = level != MotionLevel.OFF

    fun <T> fastSpatial(): FiniteAnimationSpec<T> = fastSpatialSpring.spec(animates)

    fun <T> defaultSpatial(): FiniteAnimationSpec<T> = defaultSpatialSpring.spec(animates)

    // A press or a swap that should feel alive: softer and springier than fastSpatial.
    fun <T> bouncySpatial(): FiniteAnimationSpec<T> = bouncySpatialSpring.spec(animates)

    fun <T> fastEffects(): FiniteAnimationSpec<T> = fastEffectsSpring.spec(animates)

    fun <T> defaultEffects(): FiniteAnimationSpec<T> = defaultEffectsSpring.spec(animates)

    fun <T> slowEffects(): FiniteAnimationSpec<T> = slowEffectsSpring.spec(animates)

    fun <T> short(
        delayMillis: Int = 0,
        easing: Easing = FastOutSlowInEasing,
    ): TweenSpec<T> = tween(shortMs, delayMillis, easing)

    fun <T> medium(
        delayMillis: Int = 0,
        easing: Easing = FastOutSlowInEasing,
    ): TweenSpec<T> = tween(mediumMs, delayMillis, easing)

    fun <T> long(
        delayMillis: Int = 0,
        easing: Easing = FastOutSlowInEasing,
    ): TweenSpec<T> = tween(longMs, delayMillis, easing)

    // For looping decoration (pulses, shimmer); callers stop the loop when nothing animates.
    fun <T> ambient(easing: Easing = FastOutSlowInEasing): TweenSpec<T> = tween(ambientMs, easing = easing)
}

private fun <T> KomiSpring.spec(animates: Boolean): FiniteAnimationSpec<T> =
    if (animates) spring(dampingRatio = dampingRatio, stiffness = stiffness) else snap()
