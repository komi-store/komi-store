package zed.rainxch.githubstore.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import zed.rainxch.core.presentation.personality.model.PersonalityMotion
import kotlin.math.sign

// Screens nudge in and out by a sixth of the width; a screen another one covers drifts a quarter.
private const val NUDGE_DIVISOR = 6
private const val COVERED_DRIFT_DIVISOR = 4

// Switching tabs slides toward the tab picked; anything else nudges in from the right.
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.tabAwareEnter(motion: PersonalityMotion): EnterTransition {
    val direction = tabDirection()
    val slide =
        if (direction != 0) {
            slideInHorizontally(initialOffsetX = { width -> width * direction }, animationSpec = motion.medium())
        } else {
            slideInHorizontally(initialOffsetX = { width -> width / NUDGE_DIVISOR }, animationSpec = motion.medium())
        }
    return slide + fadeIn(motion.medium())
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.tabAwareExit(motion: PersonalityMotion): ExitTransition {
    val direction = tabDirection()
    return if (direction != 0) {
        slideOutHorizontally(targetOffsetX = { width -> -width * direction }, animationSpec = motion.medium()) +
            fadeOut(motion.medium())
    } else {
        fadeOut(motion.short())
    }
}

internal fun slideBackOut(motion: PersonalityMotion): ExitTransition =
    slideOutHorizontally(
        targetOffsetX = { width -> width / NUDGE_DIVISOR },
        animationSpec = motion.medium(),
    ) + fadeOut(motion.medium())

internal fun pushIn(motion: PersonalityMotion): EnterTransition =
    slideInHorizontally(initialOffsetX = { width -> width }, animationSpec = motion.medium())

internal fun pushCovered(motion: PersonalityMotion): ExitTransition =
    slideOutHorizontally(targetOffsetX = { width -> -width / COVERED_DRIFT_DIVISOR }, animationSpec = motion.medium())

internal fun popUncovered(motion: PersonalityMotion): EnterTransition =
    slideInHorizontally(initialOffsetX = { width -> -width / COVERED_DRIFT_DIVISOR }, animationSpec = motion.medium())

internal fun popOut(motion: PersonalityMotion): ExitTransition =
    slideOutHorizontally(targetOffsetX = { width -> width }, animationSpec = motion.medium())

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabDirection(): Int {
    val from = initialState.bottomNavIndex()
    val to = targetState.bottomNavIndex()
    return if (from != null && to != null) (to - from).sign else 0
}
