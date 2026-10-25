package zed.rainxch.reviews.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.reviews.REVIEW_RATING_RANGE
import zed.rainxch.core.domain.model.reviews.ReportReason
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.inputs.KomiRadioButton
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.reviews.StarRatingInput
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.reviews_action_delete
import zed.rainxch.githubstore.core.presentation.res.reviews_composer_count
import zed.rainxch.githubstore.core.presentation.res.reviews_composer_hint
import zed.rainxch.githubstore.core.presentation.res.reviews_composer_pick_rating
import zed.rainxch.githubstore.core.presentation.res.reviews_composer_post
import zed.rainxch.githubstore.core.presentation.res.reviews_composer_public
import zed.rainxch.githubstore.core.presentation.res.reviews_composer_save
import zed.rainxch.githubstore.core.presentation.res.reviews_composer_title_edit
import zed.rainxch.githubstore.core.presentation.res.reviews_composer_title_new
import zed.rainxch.githubstore.core.presentation.res.reviews_composer_version
import zed.rainxch.githubstore.core.presentation.res.reviews_level_1
import zed.rainxch.githubstore.core.presentation.res.reviews_level_2
import zed.rainxch.githubstore.core.presentation.res.reviews_level_3
import zed.rainxch.githubstore.core.presentation.res.reviews_level_4
import zed.rainxch.githubstore.core.presentation.res.reviews_level_5
import zed.rainxch.githubstore.core.presentation.res.reviews_reply_hint
import zed.rainxch.githubstore.core.presentation.res.reviews_reply_send
import zed.rainxch.githubstore.core.presentation.res.reviews_reply_title
import zed.rainxch.githubstore.core.presentation.res.reviews_report_note
import zed.rainxch.githubstore.core.presentation.res.reviews_report_off_topic
import zed.rainxch.githubstore.core.presentation.res.reviews_report_offensive
import zed.rainxch.githubstore.core.presentation.res.reviews_report_other
import zed.rainxch.githubstore.core.presentation.res.reviews_report_send
import zed.rainxch.githubstore.core.presentation.res.reviews_report_spam
import zed.rainxch.githubstore.core.presentation.res.reviews_report_title
import zed.rainxch.reviews.presentation.ComposerState
import zed.rainxch.reviews.presentation.ReplyState
import zed.rainxch.reviews.presentation.ReportState

@Composable
fun ComposerSheet(
    composer: ComposerState,
    maxLength: Int,
    onRatingChange: (Int) -> Unit,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    KomiSheet(
        onDismiss = onDismiss,
        modifier = modifier,
        title = stringResource(if (composer.isEditing) Res.string.reviews_composer_title_edit else Res.string.reviews_composer_title_new),
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                if (composer.isEditing) {
                    KomiButton(
                        onClick = onDelete,
                        label = stringResource(Res.string.reviews_action_delete),
                        variant = KomiButtonVariant.Text,
                        enabled = !composer.isSubmitting,
                    )
                }
                KomiButton(
                    onClick = onSubmit,
                    label = stringResource(if (composer.isEditing) Res.string.reviews_composer_save else Res.string.reviews_composer_post),
                    enabled = composer.rating in REVIEW_RATING_RANGE && !composer.isSubmitting,
                    loading = composer.isSubmitting,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            composer.version?.let { version ->
                KomiText(
                    text = stringResource(Res.string.reviews_composer_version, version),
                    role = KomiTextRole.Label,
                    color = colors.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                StarRatingInput(rating = composer.rating, onRatingChange = onRatingChange)
                RatingWord(rating = composer.rating)
            }
            KomiTextField(
                value = composer.text,
                onValueChange = onTextChange,
                placeholder = stringResource(Res.string.reviews_composer_hint),
                helper = stringResource(Res.string.reviews_composer_count, composer.text.length, maxLength),
                multiline = true,
                rows = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            KomiText(
                text = stringResource(Res.string.reviews_composer_public),
                role = KomiTextRole.Caption,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ReportSheet(
    report: ReportState,
    onReasonChange: (ReportReason) -> Unit,
    onNoteChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KomiSheet(
        onDismiss = onDismiss,
        modifier = modifier,
        title = stringResource(Res.string.reviews_report_title),
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                KomiButton(
                    onClick = onSend,
                    label = stringResource(Res.string.reviews_report_send),
                    enabled = report.reason != null && !report.isSending,
                    loading = report.isSending,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ReportReason.entries.forEach { reason ->
                ReasonRow(
                    label = stringResource(reason.label()),
                    selected = report.reason == reason,
                    onClick = { onReasonChange(reason) },
                )
            }
            KomiTextField(
                value = report.note,
                onValueChange = onNoteChange,
                placeholder = stringResource(Res.string.reviews_report_note),
                multiline = true,
                rows = 3,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ReasonRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KomiRadioButton(selected = selected, onClick = null)
        KomiText(text = label, role = KomiTextRole.Body, color = LocalPersonality.current.colors.onSurface)
    }
}

@Composable
fun ReplySheet(
    reply: ReplyState,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KomiSheet(
        onDismiss = onDismiss,
        modifier = modifier,
        title = stringResource(Res.string.reviews_reply_title),
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                KomiButton(
                    onClick = onSend,
                    label = stringResource(Res.string.reviews_reply_send),
                    enabled = reply.text.isNotBlank() && !reply.isSending,
                    loading = reply.isSending,
                )
            }
        },
    ) {
        KomiTextField(
            value = reply.text,
            onValueChange = onTextChange,
            placeholder = stringResource(Res.string.reviews_reply_hint),
            multiline = true,
            rows = 5,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
    }
}

// The word for the picked level, which springs in as the rating changes; before a pick, the hint to tap.
@Composable
private fun RatingWord(rating: Int) {
    val personality = LocalPersonality.current
    val motion = personality.motion
    AnimatedContent(
        targetState = rating,
        transitionSpec = {
            (fadeIn(motion.fastEffects()) + scaleIn(motion.bouncySpatial(), initialScale = WORD_START_SCALE))
                .togetherWith(fadeOut(motion.fastEffects()))
        },
        label = "rating-word",
    ) { level ->
        KomiText(
            text = stringResource(level.ratingWord()),
            role = if (level == 0) KomiTextRole.Caption else KomiTextRole.ItemTitle,
            color = if (level == 0) personality.colors.onSurfaceVariant else personality.colors.primary,
        )
    }
}

// One word per level, one star first; no rating yet reads as the hint to tap.
private val ratingWords =
    listOf(
        Res.string.reviews_level_1,
        Res.string.reviews_level_2,
        Res.string.reviews_level_3,
        Res.string.reviews_level_4,
        Res.string.reviews_level_5,
    )

private fun Int.ratingWord(): StringResource = ratingWords.getOrNull(this - 1) ?: Res.string.reviews_composer_pick_rating

private fun ReportReason.label(): StringResource =
    when (this) {
        ReportReason.SPAM -> Res.string.reviews_report_spam
        ReportReason.OFFENSIVE -> Res.string.reviews_report_offensive
        ReportReason.OFF_TOPIC -> Res.string.reviews_report_off_topic
        ReportReason.OTHER -> Res.string.reviews_report_other
    }

private const val WORD_START_SCALE = 0.7f
