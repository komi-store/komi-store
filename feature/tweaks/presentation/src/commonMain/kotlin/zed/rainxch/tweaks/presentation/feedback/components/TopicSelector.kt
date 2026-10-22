package zed.rainxch.tweaks.presentation.feedback.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.feedback_topic_label
import zed.rainxch.tweaks.presentation.feedback.model.FeedbackTopic

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicSelector(
    selected: FeedbackTopic,
    onSelected: (FeedbackTopic) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        KomiText(
            text = stringResource(Res.string.feedback_topic_label),
            role = KomiTextRole.Overline,
            color = LocalPersonality.current.colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FeedbackTopic.entries.forEach { topic ->
                FeedbackPillChip(
                    label = stringResource(topic.label),
                    isSelected = topic == selected,
                    onClick = { onSelected(topic) },
                )
            }
        }
    }
}
