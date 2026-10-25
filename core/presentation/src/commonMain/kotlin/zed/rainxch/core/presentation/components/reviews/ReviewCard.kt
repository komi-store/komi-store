package zed.rainxch.core.presentation.components.reviews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import zed.rainxch.core.presentation.components.overlays.KomiDropdown
import zed.rainxch.core.presentation.components.overlays.KomiMenuEntry
import zed.rainxch.core.presentation.components.overlays.KomiMenuItem
import zed.rainxch.core.presentation.components.overlays.KomiMenuTone
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfacePaper
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
import zed.rainxch.githubstore.core.presentation.res.reviews_edited
import zed.rainxch.githubstore.core.presentation.res.reviews_hidden_notice
import zed.rainxch.githubstore.core.presentation.res.reviews_more_actions
import zed.rainxch.githubstore.core.presentation.res.reviews_show_less
import zed.rainxch.githubstore.core.presentation.res.reviews_show_more
import zed.rainxch.githubstore.core.presentation.res.reviews_your_review

// A null callback hides its action: the menu only lists what this viewer may do with this review. The
// viewer's own review sits on a tinted card under a "Your review" label.
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
        paper = if (review.isOwn) KomiSurfacePaper.Tinted else KomiSurfacePaper.Surface,
        contentPadding = PaddingValues(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (review.isOwn) {
                KomiText(
                    text = stringResource(Res.string.reviews_your_review),
                    role = KomiTextRole.Overline,
                    color = colors.primary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReviewAvatar(url = review.authorAvatarUrl, size = 32.dp)
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
                VotePill(score = review.score, vote = review.vote, onVote = onVote)
            }
            review.reply?.let { ReplyBubble(it) }
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
internal fun ReviewAvatar(
    url: String?,
    size: Dp,
    placeholder: Color = LocalPersonality.current.colors.surfaceVariant,
) {
    GitHubStoreImage(
        imageModel = { url },
        modifier =
            Modifier
                .size(size)
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
