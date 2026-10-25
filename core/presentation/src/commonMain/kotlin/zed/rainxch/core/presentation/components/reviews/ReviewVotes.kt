package zed.rainxch.core.presentation.components.reviews

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.reviews.ReviewVote
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.manga.decoration.hardShadow
import zed.rainxch.core.presentation.spacing.Spacing
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.reviews_downvote
import zed.rainxch.githubstore.core.presentation.res.reviews_score
import zed.rainxch.githubstore.core.presentation.res.reviews_upvote

// Up, the net score and down as one connected group: three tonal segments with round ends in Classic, one
// inked strip in Manga, where the gaps between segments show the ink. The viewer's vote fills with the
// accent and pops, and the score rolls up or down as it changes. Without onVote (a preview, the viewer's
// own review) only the score shows.
@Composable
internal fun VotePill(
    score: Int,
    vote: ReviewVote?,
    onVote: ((ReviewVote) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val manga = personality is MangaPersonality
    val ink =
        if (manga) {
            Modifier
                .hardShadow(offset = DpOffset(2.dp, 2.dp), color = colors.shadow)
                .background(colors.outline)
                .border(2.dp, colors.outline)
                .padding(2.dp)
        } else {
            Modifier
        }
    Row(
        modifier = modifier.height(VOTE_HEIGHT).then(ink),
        horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onVote != null) {
            VoteSegment(vote = ReviewVote.UP, selected = vote == ReviewVote.UP, onVote = onVote)
        }
        ScoreSegment(score = score, voted = vote != null, alone = onVote == null)
        if (onVote != null) {
            VoteSegment(vote = ReviewVote.DOWN, selected = vote == ReviewVote.DOWN, onVote = onVote)
        }
    }
}

@Composable
private fun VoteSegment(
    vote: ReviewVote,
    selected: Boolean,
    onVote: (ReviewVote) -> Unit,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val motion = personality.motion
    val manga = personality is MangaPersonality
    val container by animateColorAsState(
        targetValue =
            when {
                selected -> colors.primary
                manga -> colors.surface
                else -> colors.surfaceVariant
            },
        animationSpec = motion.fastEffects(),
        label = "vote-container",
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        animationSpec = motion.fastEffects(),
        label = "vote-content",
    )
    // Pops only when the vote is cast here, not when a card that already carries it appears.
    val wasSelected = remember { mutableStateOf(selected) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected && !wasSelected.value) {
            pop.animateTo(VOTE_POP_SCALE, motion.fastSpatial())
            pop.animateTo(1f, motion.bouncySpatial())
        }
        wasSelected.value = selected
    }
    val shape = segmentShape(manga = manga, selected = selected, start = vote == ReviewVote.UP)
    Box(
        modifier =
            Modifier
                .fillMaxHeight()
                .widthIn(min = 44.dp)
                .clip(shape)
                .background(container)
                .clickable(role = Role.Button) { onVote(vote) }
                .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        KomiIcon(
            imageVector = if (vote == ReviewVote.UP) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
            contentDescription = stringResource(if (vote == ReviewVote.UP) Res.string.reviews_upvote else Res.string.reviews_downvote),
            tint = content,
            modifier =
                Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    },
        )
    }
}

@Composable
private fun ScoreSegment(
    score: Int,
    voted: Boolean,
    alone: Boolean,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val motion = personality.motion
    val manga = personality is MangaPersonality
    val shape =
        when {
            manga -> RectangleShape
            alone -> RoundedCornerShape(PILL)
            else -> RoundedCornerShape(INNER_CORNER)
        }
    val scoreLabel = stringResource(Res.string.reviews_score, score)
    Row(
        modifier =
            Modifier
                .fillMaxHeight()
                .clip(shape)
                .background(if (manga) colors.surface else colors.surfaceVariant)
                .clearAndSetSemantics { contentDescription = scoreLabel }
                .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (alone) {
            KomiIcon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
        AnimatedContent(
            targetState = score,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically(motion.fastSpatial()) { if (up) it else -it } + fadeIn(motion.fastEffects()))
                    .togetherWith(slideOutVertically(motion.fastSpatial()) { if (up) -it else it } + fadeOut(motion.fastEffects()))
                    .using(SizeTransform(clip = true))
            },
            label = "review-score",
        ) { value ->
            KomiText(
                text = value.toString(),
                role = KomiTextRole.Label,
                color = if (voted) colors.primary else colors.onSurface,
            )
        }
    }
}

// Classic's connected shapes: round outer ends, small inner corners, and a full pill once selected.
private fun segmentShape(
    manga: Boolean,
    selected: Boolean,
    start: Boolean,
): Shape =
    when {
        manga -> {
            RectangleShape
        }

        selected -> {
            RoundedCornerShape(PILL)
        }

        start -> {
            RoundedCornerShape(topStart = PILL, bottomStart = PILL, topEnd = INNER_CORNER, bottomEnd = INNER_CORNER)
        }

        else -> {
            RoundedCornerShape(topStart = INNER_CORNER, bottomStart = INNER_CORNER, topEnd = PILL, bottomEnd = PILL)
        }
    }

private val VOTE_HEIGHT = 36.dp
private val SEGMENT_GAP = 2.dp
private val INNER_CORNER = 6.dp
private val PILL = 999.dp
private const val VOTE_POP_SCALE = 1.35f
