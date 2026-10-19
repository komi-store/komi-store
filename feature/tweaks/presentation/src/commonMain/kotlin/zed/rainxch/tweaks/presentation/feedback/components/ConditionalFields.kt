package zed.rainxch.tweaks.presentation.feedback.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.feedback_field_current_behaviour
import zed.rainxch.githubstore.core.presentation.res.feedback_field_desired_behaviour
import zed.rainxch.githubstore.core.presentation.res.feedback_field_expected_actual
import zed.rainxch.githubstore.core.presentation.res.feedback_field_proposed_solution
import zed.rainxch.githubstore.core.presentation.res.feedback_field_steps
import zed.rainxch.githubstore.core.presentation.res.feedback_field_use_case
import zed.rainxch.tweaks.presentation.feedback.FeedbackAction
import zed.rainxch.tweaks.presentation.feedback.FeedbackState
import zed.rainxch.tweaks.presentation.feedback.model.FeedbackCategory

@Composable
fun ConditionalFields(
    state: FeedbackState,
    onAction: (FeedbackAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state.category) {
            FeedbackCategory.BUG -> {
                MultilineField(
                    value = state.stepsToReproduce,
                    label = stringResource(Res.string.feedback_field_steps),
                    onValueChange = { onAction(FeedbackAction.OnStepsToReproduceChange(it)) },
                )
                MultilineField(
                    value = state.expectedActual,
                    label = stringResource(Res.string.feedback_field_expected_actual),
                    onValueChange = { onAction(FeedbackAction.OnExpectedActualChange(it)) },
                )
            }

            FeedbackCategory.FEATURE_REQUEST -> {
                MultilineField(
                    value = state.useCase,
                    label = stringResource(Res.string.feedback_field_use_case),
                    onValueChange = { onAction(FeedbackAction.OnUseCaseChange(it)) },
                )
                MultilineField(
                    value = state.proposedSolution,
                    label = stringResource(Res.string.feedback_field_proposed_solution),
                    onValueChange = { onAction(FeedbackAction.OnProposedSolutionChange(it)) },
                )
            }

            FeedbackCategory.CHANGE_REQUEST -> {
                MultilineField(
                    value = state.currentBehaviour,
                    label = stringResource(Res.string.feedback_field_current_behaviour),
                    onValueChange = { onAction(FeedbackAction.OnCurrentBehaviourChange(it)) },
                )
                MultilineField(
                    value = state.desiredBehaviour,
                    label = stringResource(Res.string.feedback_field_desired_behaviour),
                    onValueChange = { onAction(FeedbackAction.OnDesiredBehaviourChange(it)) },
                )
            }

            FeedbackCategory.OTHER -> { }
        }
    }
}

@Composable
private fun MultilineField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
) {
    KomiTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        multiline = true,
        rows = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}
