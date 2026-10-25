package zed.rainxch.core.presentation.components.reviews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import zed.rainxch.core.domain.model.reviews.ReviewPlatform
import zed.rainxch.core.domain.model.reviews.ReviewVote
import zed.rainxch.core.presentation.components.GitHubStoreImage
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.buttons.KomiIconButtonSize
import zed.rainxch.core.presentation.components.chips.KomiChip
import zed.rainxch.core.presentation.components.chips.KomiChipSize
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.KomiDropdown
import zed.rainxch.core.presentation.components.overlays.KomiMenuEntry
import zed.rainxch.core.presentation.components.overlays.KomiMenuItem
import zed.rainxch.core.presentation.components.overlays.KomiMenuTone
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.formatRelativeLong
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.ic_platform_android
import zed.rainxch.githubstore.core.presentation.res.ic_platform_linux
import zed.rainxch.githubstore.core.presentation.res.ic_platform_macos
import zed.rainxch.githubstore.core.presentation.res.ic_platform_windows
import zed.rainxch.githubstore.core.presentation.res.platform_section_android
import zed.rainxch.githubstore.core.presentation.res.platform_section_linux
import zed.rainxch.githubstore.core.presentation.res.platform_section_macos
import zed.rainxch.githubstore.core.presentation.res.platform_section_windows
import zed.rainxch.githubstore.core.presentation.res.reviews_action_delete
import zed.rainxch.githubstore.core.presentation.res.reviews_action_delete_reply
import zed.rainxch.githubstore.core.presentation.res.reviews_action_edit
import zed.rainxch.githubstore.core.presentation.res.reviews_action_reply
import zed.rainxch.githubstore.core.presentation.res.reviews_action_report
import zed.rainxch.githubstore.core.presentation.res.reviews_downvote
import zed.rainxch.githubstore.core.presentation.res.reviews_edited
import zed.rainxch.githubstore.core.presentation.res.reviews_hidden_notice
import zed.rainxch.githubstore.core.presentation.res.reviews_more_actions
import zed.rainxch.githubstore.core.presentation.res.reviews_reply_from
import zed.rainxch.githubstore.core.presentation.res.reviews_score
import zed.rainxch.githubstore.core.presentation.res.reviews_show_less
import zed.rainxch.githubstore.core.presentation.res.reviews_show_more
import zed.rainxch.githubstore.core.presentation.res.reviews_upvote

// A null callback hides its action: the menu only lists what this viewer may do with this review.
@Composable
fun ReviewCard(
    review: ReviewUi,
    modifier: Modifier = Modifier,
    onVote: ((ReviewVote) -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onReply: (() -> Unit)? = null,
    onDeleteReply: (() -> Unit)? = null,
) {
    val colors = LocalPersonality.current.colors
    KomiSurface(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(url = review.authorAvatarUrl, size = 32)
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    KomiText(
                        text = review.authorLogin,
                        role = KomiTextRole.ItemTitle,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        StarRating(rating = review.rating.toDouble(), starSize = 12.dp)
                        review.createdAt?.let { iso ->
                            KomiText(
                                text = formatRelativeLong(iso),
                                role = KomiTextRole.Caption,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        if (review.edited) {
                            KomiText(
                                text = stringResource(Res.string.reviews_edited),
                                role = KomiTextRole.Caption,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                ReviewMenu(
                    onReport = onReport,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onReply = onReply,
                    onDeleteReply = onDeleteReply,
                )
            }
            if (review.isHidden) {
                KomiText(
                    text = stringResource(Res.string.reviews_hidden_notice),
                    role = KomiTextRole.Caption,
                    color = colors.error,
                )
            }
            ReviewTags(review)
            review.text?.let { ExpandableText(text = it) }
            if (onVote != null || review.score != 0) {
                VoteRow(review = review, onVote = onVote)
            }
            review.reply?.let { ReplyBlock(it) }
        }
    }
}

@Composable
private fun ReviewTags(review: ReviewUi) {
    val platform = review.platform
    if (review.version == null && platform == null && review.appLabel == null) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        review.appLabel?.let { KomiChip(label = it, size = KomiChipSize.Sm, tilt = false) }
        review.version?.let { KomiChip(label = it, size = KomiChipSize.Sm, tilt = false) }
        if (platform != null) {
            KomiChip(
                label = platform.label(),
                size = KomiChipSize.Sm,
                tilt = false,
                leadingIcon = platform.icon(),
            )
        }
    }
}

@Composable
private fun ExpandableText(text: String) {
    val colors = LocalPersonality.current.colors
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    val long = text.length > COLLAPSED_CHARS || text.count { it == '\n' } >= COLLAPSED_LINES
    Column {
        KomiText(
            text = text,
            role = KomiTextRole.Body,
            color = colors.onSurface,
            maxLines = if (expanded || !long) Int.MAX_VALUE else COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        if (long) {
            KomiButton(
                onClick = { expanded = !expanded },
                label = stringResource(if (expanded) Res.string.reviews_show_less else Res.string.reviews_show_more),
                variant = KomiButtonVariant.Text,
                size = KomiButtonSize.Sm,
            )
        }
    }
}

// Up, the net score, down. The viewer's own vote is the filled button; without onVote (a preview, the
// viewer's own review) only the score shows.
@Composable
private fun VoteRow(
    review: ReviewUi,
    onVote: ((ReviewVote) -> Unit)?,
) {
    val colors = LocalPersonality.current.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (onVote != null) {
            VoteButton(vote = ReviewVote.UP, selected = review.vote == ReviewVote.UP, onVote = onVote)
        } else {
            KomiIcon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        val scoreLabel = stringResource(Res.string.reviews_score, review.score)
        KomiText(
            text = review.score.toString(),
            role = KomiTextRole.Label,
            color = if (review.vote != null) colors.primary else colors.onSurface,
            modifier = Modifier.semantics { contentDescription = scoreLabel },
        )
        if (onVote != null) {
            VoteButton(vote = ReviewVote.DOWN, selected = review.vote == ReviewVote.DOWN, onVote = onVote)
        }
    }
}

@Composable
private fun VoteButton(
    vote: ReviewVote,
    selected: Boolean,
    onVote: (ReviewVote) -> Unit,
) {
    KomiIconButton(
        icon = if (vote == ReviewVote.UP) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
        contentDescription = stringResource(if (vote == ReviewVote.UP) Res.string.reviews_upvote else Res.string.reviews_downvote),
        onClick = { onVote(vote) },
        variant = if (selected) KomiButtonVariant.Primary else KomiButtonVariant.Outline,
        size = KomiIconButtonSize.Sm,
        modifier = Modifier.semantics { this.selected = selected },
    )
}

@Composable
private fun ReplyBlock(reply: ReviewReplyUi) {
    val colors = LocalPersonality.current.colors
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(LocalPersonality.current.shape.corner))
                .background(colors.surfaceVariant)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The block is surfaceVariant already, so the empty-avatar circle needs another fill to show.
            Avatar(url = reply.authorAvatarUrl, size = 20, placeholder = colors.surface)
            Spacer(Modifier.size(8.dp))
            KomiText(
                text = stringResource(Res.string.reviews_reply_from, reply.authorLogin),
                role = KomiTextRole.Label,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            reply.createdAt?.let { iso ->
                Spacer(Modifier.size(6.dp))
                KomiText(
                    text = formatRelativeLong(iso),
                    role = KomiTextRole.Caption,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        KomiText(text = reply.text, role = KomiTextRole.Body, color = colors.onSurface)
    }
}

@Composable
private fun ReviewMenu(
    onReport: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onReply: (() -> Unit)?,
    onDeleteReply: (() -> Unit)?,
) {
    val actions =
        listOfNotNull(
            onEdit?.let { ReviewMenuItem(ID_EDIT, stringResource(Res.string.reviews_action_edit), it) },
            onReply?.let { ReviewMenuItem(ID_REPLY, stringResource(Res.string.reviews_action_reply), it) },
            onDeleteReply?.let {
                ReviewMenuItem(ID_DELETE_REPLY, stringResource(Res.string.reviews_action_delete_reply), it, danger = true)
            },
            onReport?.let { ReviewMenuItem(ID_REPORT, stringResource(Res.string.reviews_action_report), it) },
            onDelete?.let { ReviewMenuItem(ID_DELETE, stringResource(Res.string.reviews_action_delete), it, danger = true) },
        )
    if (actions.isEmpty()) return
    val entries: List<KomiMenuEntry> =
        actions.map {
            KomiMenuItem(id = it.id, label = it.label, tone = if (it.danger) KomiMenuTone.Danger else KomiMenuTone.Default)
        }
    KomiDropdown(
        entries = entries.toImmutableList(),
        onSelect = { picked -> actions.firstOrNull { it.id == picked.id }?.onClick?.invoke() },
        trigger = { open ->
            KomiIconButton(
                icon = Icons.Rounded.MoreVert,
                contentDescription = stringResource(Res.string.reviews_more_actions),
                onClick = open,
                variant = KomiButtonVariant.Text,
                size = KomiIconButtonSize.Sm,
            )
        },
    )
}

private class ReviewMenuItem(
    val id: String,
    val label: String,
    val onClick: () -> Unit,
    val danger: Boolean = false,
)

@Composable
private fun Avatar(
    url: String?,
    size: Int,
    placeholder: Color = LocalPersonality.current.colors.surfaceVariant,
) {
    GitHubStoreImage(
        imageModel = { url },
        modifier =
            Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(placeholder),
        showLoadingIndicator = false,
    )
}

@Composable
private fun ReviewPlatform.label(): String =
    stringResource(
        when (this) {
            ReviewPlatform.ANDROID -> Res.string.platform_section_android
            ReviewPlatform.WINDOWS -> Res.string.platform_section_windows
            ReviewPlatform.MACOS -> Res.string.platform_section_macos
            ReviewPlatform.LINUX -> Res.string.platform_section_linux
        },
    )

@Composable
private fun ReviewPlatform.icon() =
    vectorResource(
        when (this) {
            ReviewPlatform.ANDROID -> Res.drawable.ic_platform_android
            ReviewPlatform.WINDOWS -> Res.drawable.ic_platform_windows
            ReviewPlatform.MACOS -> Res.drawable.ic_platform_macos
            ReviewPlatform.LINUX -> Res.drawable.ic_platform_linux
        },
    )

private const val COLLAPSED_LINES = 6
private const val COLLAPSED_CHARS = 360
private const val ID_EDIT = "edit"
private const val ID_REPLY = "reply"
private const val ID_DELETE_REPLY = "delete_reply"
private const val ID_REPORT = "report"
private const val ID_DELETE = "delete"
