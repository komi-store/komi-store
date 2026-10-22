package zed.rainxch.tweaks.presentation.feedback.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.domain.isAndroid
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.feedback_close
import zed.rainxch.githubstore.core.presentation.res.feedback_field_description
import zed.rainxch.githubstore.core.presentation.res.feedback_field_title
import zed.rainxch.githubstore.core.presentation.res.feedback_title
import zed.rainxch.tweaks.presentation.feedback.FeedbackAction
import zed.rainxch.tweaks.presentation.feedback.FeedbackEvent
import zed.rainxch.tweaks.presentation.feedback.FeedbackState
import zed.rainxch.tweaks.presentation.feedback.FeedbackViewModel
import zed.rainxch.tweaks.presentation.feedback.model.FeedbackChannel

@Composable
fun FeedbackBottomSheet(
    onDismiss: () -> Unit,
    onSent: (FeedbackChannel) -> Unit,
    onError: () -> Unit,
    viewModel: FeedbackViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is FeedbackEvent.OnSent -> onSent(event.channel)
            FeedbackEvent.OnSendError -> onError()
        }
    }

    val dismiss = {
        viewModel.onAction(FeedbackAction.OnDismiss)
        onDismiss()
    }

    if (isAndroid()) {
        KomiSheet(
            onDismiss = dismiss,
        ) {
            FeedbackContent(
                state = state,
                onAction = viewModel::onAction,
                onDismiss = dismiss,
                scrollable = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
        }
    } else {
        Dialog(
            onDismissRequest = dismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .heightIn(max = 720.dp)
                    .clip(RoundedCornerShape(LocalPersonality.current.shape.corner))
                    .background(LocalPersonality.current.colors.surface),
            ) {
                FeedbackContent(
                    state = state,
                    onAction = viewModel::onAction,
                    onDismiss = dismiss,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun FeedbackContent(
    state: FeedbackState,
    onAction: (FeedbackAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = if (scrollable) modifier.verticalScroll(scrollState) else modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FeedbackHeader(onDismiss = onDismiss)

        SectionLabel(text = stringResource(Res.string.feedback_field_title) + " *", topGap = 0.dp)
        KomiTextField(
            value = state.title,
            onValueChange = { onAction(FeedbackAction.OnTitleChange(it)) },
            label = stringResource(Res.string.feedback_field_title),
            modifier = Modifier.fillMaxWidth(),
        )

        SectionLabel(text = stringResource(Res.string.feedback_field_description) + " *")
        KomiTextField(
            value = state.description,
            onValueChange = { onAction(FeedbackAction.OnDescriptionChange(it)) },
            label = stringResource(Res.string.feedback_field_description),
            multiline = true,
            rows = 4,
            modifier = Modifier.fillMaxWidth(),
        )

        CategorySelector(
            selected = state.category,
            onSelected = { onAction(FeedbackAction.OnCategoryChange(it)) },
        )

        TopicSelector(
            selected = state.topic,
            onSelected = { onAction(FeedbackAction.OnTopicChange(it)) },
        )

        ConditionalFields(state = state, onAction = onAction)

        DiagnosticsPreview(
            diagnostics = state.diagnostics,
            channel = FeedbackChannel.GITHUB,
            enabled = state.attachDiagnostics,
            onToggle = { onAction(FeedbackAction.OnAttachDiagnosticsToggle) },
        )

        SendActions(
            canSend = state.canSend,
            isSending = state.isSending,
            onSendEmail = { onAction(FeedbackAction.OnSendViaEmail) },
            onSendGithub = { onAction(FeedbackAction.OnSendViaGithub) },
        )

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun FeedbackHeader(onDismiss: () -> Unit) {
    val colors = LocalPersonality.current.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        KomiText(
            text = stringResource(Res.string.feedback_title),
            role = KomiTextRole.CardTitle,
            color = colors.onSurface,
        )
        KomiIconButton(
            icon = Icons.Default.Close,
            contentDescription = stringResource(Res.string.feedback_close),
            onClick = onDismiss,
            variant = KomiButtonVariant.Text,
        )
    }
}

@Composable
private fun SectionLabel(text: String, topGap: androidx.compose.ui.unit.Dp = 4.dp) {
    Column {
        if (topGap > 0.dp) Spacer(Modifier.height(topGap))
        KomiText(
            text = text,
            role = KomiTextRole.LabelSmall,
            color = LocalPersonality.current.colors.onSurfaceVariant,
        )
    }
}
