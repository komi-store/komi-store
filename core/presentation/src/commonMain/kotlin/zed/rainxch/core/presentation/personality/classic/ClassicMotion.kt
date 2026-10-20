package zed.rainxch.core.presentation.personality.classic

import zed.rainxch.core.presentation.personality.model.KomiSpring
import zed.rainxch.core.presentation.personality.model.MotionLevel
import zed.rainxch.core.presentation.personality.model.PersonalityMotion

// Material 3 Expressive's motion scheme: spatial springs overshoot a little, effects never do.
internal val ClassicMotion =
    PersonalityMotion(
        level = MotionLevel.SUBTLE,
        fastSpatialSpring = KomiSpring(dampingRatio = 0.6f, stiffness = 800f),
        defaultSpatialSpring = KomiSpring(dampingRatio = 0.8f, stiffness = 380f),
        bouncySpatialSpring = KomiSpring(dampingRatio = 0.5f, stiffness = 200f),
        fastEffectsSpring = KomiSpring(dampingRatio = 1f, stiffness = 3800f),
        defaultEffectsSpring = KomiSpring(dampingRatio = 1f, stiffness = 1600f),
        slowEffectsSpring = KomiSpring(dampingRatio = 1f, stiffness = 800f),
        shortMs = 150,
        mediumMs = 300,
        longMs = 500,
        ambientMs = 1200,
    )
