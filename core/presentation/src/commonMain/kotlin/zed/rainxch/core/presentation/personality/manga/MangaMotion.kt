package zed.rainxch.core.presentation.personality.manga

import zed.rainxch.core.presentation.personality.model.KomiSpring
import zed.rainxch.core.presentation.personality.model.MotionLevel
import zed.rainxch.core.presentation.personality.model.PersonalityMotion

internal fun mangaMotion(level: MotionLevel): PersonalityMotion =
    when (level) {
        MotionLevel.FULL -> mangaMotion(level, shortMs = 150, mediumMs = 300, longMs = 500)
        MotionLevel.SUBTLE -> mangaMotion(level, shortMs = 100, mediumMs = 200, longMs = 350)
        MotionLevel.OFF -> mangaMotion(level, shortMs = 0, mediumMs = 0, longMs = 0)
    }

// Material's standard springs: no bounce, so panels land like a cut between frames.
private fun mangaMotion(
    level: MotionLevel,
    shortMs: Int,
    mediumMs: Int,
    longMs: Int,
): PersonalityMotion =
    PersonalityMotion(
        level = level,
        fastSpatialSpring = KomiSpring(dampingRatio = 0.9f, stiffness = 1400f),
        defaultSpatialSpring = KomiSpring(dampingRatio = 0.9f, stiffness = 700f),
        bouncySpatialSpring = KomiSpring(dampingRatio = 0.9f, stiffness = 1400f),
        fastEffectsSpring = KomiSpring(dampingRatio = 1f, stiffness = 3800f),
        defaultEffectsSpring = KomiSpring(dampingRatio = 1f, stiffness = 1600f),
        slowEffectsSpring = KomiSpring(dampingRatio = 1f, stiffness = 800f),
        shortMs = shortMs,
        mediumMs = mediumMs,
        longMs = longMs,
        ambientMs = 1000,
    )
