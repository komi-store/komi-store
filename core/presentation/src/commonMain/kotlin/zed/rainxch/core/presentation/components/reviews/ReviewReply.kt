package zed.rainxch.core.presentation.components.reviews

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.shapes.SpeechBubbleShape
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.spacing.Spacing
import zed.rainxch.core.presentation.utils.formatRelativeLong
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.reviews_developer
import zed.rainxch.githubstore.core.presentation.res.reviews_reply_from

// The developer's reply as a speech bubble whose tail points up at the review: the accent's container in
// Classic, an inked balloon on paper in Manga. A Developer badge says who is talking.
@Composable
internal fun ReplyBubble(
    reply: ReviewReplyUi,
    modifier: Modifier = Modifier,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val manga = personality is MangaPersonality
    val shape =
        remember {
            SpeechBubbleShape(corner = BUBBLE_CORNER, tailStart = TAIL_START, tailWidth = TAIL_WIDTH, tailHeight = TAIL_HEIGHT)
        }
    val ink = if (manga) colors.onSurface else colors.onPrimaryContainer
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(if (manga) colors.surface else colors.primaryContainer, shape)
                .then(if (manga) Modifier.border(2.dp, colors.outline, shape) else Modifier)
                .padding(start = 12.dp, end = 12.dp, top = TAIL_HEIGHT + 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // The empty-avatar circle needs a fill that differs from the bubble's to show.
            ReviewAvatar(url = reply.authorAvatarUrl, size = 20.dp, placeholder = if (manga) colors.surfaceVariant else colors.surface)
            KomiText(
                text = stringResource(Res.string.reviews_reply_from, reply.authorLogin),
                role = KomiTextRole.Label,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            DeveloperBadge()
        }
        KomiText(text = reply.text, role = KomiTextRole.Body, color = ink)
        reply.createdAt?.let { iso ->
            KomiText(text = formatRelativeLong(iso), role = KomiTextRole.Caption, color = ink, maxLines = 1)
        }
    }
}

@Composable
private fun DeveloperBadge() {
    val personality = LocalPersonality.current
    val colors = personality.colors
    KomiText(
        text = stringResource(Res.string.reviews_developer),
        role = KomiTextRole.LabelSmall,
        color = colors.onPrimary,
        maxLines = 1,
        modifier =
            Modifier
                .background(colors.primary, if (personality is MangaPersonality) RectangleShape else CircleShape)
                .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

private val BUBBLE_CORNER = 18.dp
private val TAIL_START = 18.dp
private val TAIL_WIDTH = 16.dp
private val TAIL_HEIGHT = 10.dp
