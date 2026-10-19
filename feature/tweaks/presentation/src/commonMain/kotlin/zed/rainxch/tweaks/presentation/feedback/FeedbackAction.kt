package zed.rainxch.tweaks.presentation.feedback

import zed.rainxch.tweaks.presentation.feedback.model.FeedbackCategory
import zed.rainxch.tweaks.presentation.feedback.model.FeedbackTopic

sealed interface FeedbackAction {
    data class OnCategoryChange(
        val category: FeedbackCategory,
    ) : FeedbackAction

    data class OnTopicChange(
        val topic: FeedbackTopic,
    ) : FeedbackAction

    data class OnTitleChange(
        val value: String,
    ) : FeedbackAction

    data class OnDescriptionChange(
        val value: String,
    ) : FeedbackAction

    data class OnStepsToReproduceChange(
        val value: String,
    ) : FeedbackAction

    data class OnExpectedActualChange(
        val value: String,
    ) : FeedbackAction

    data class OnUseCaseChange(
        val value: String,
    ) : FeedbackAction

    data class OnProposedSolutionChange(
        val value: String,
    ) : FeedbackAction

    data class OnCurrentBehaviourChange(
        val value: String,
    ) : FeedbackAction

    data class OnDesiredBehaviourChange(
        val value: String,
    ) : FeedbackAction

    data object OnAttachDiagnosticsToggle : FeedbackAction

    data object OnSendViaEmail : FeedbackAction

    data object OnSendViaGithub : FeedbackAction

    data object OnDismiss : FeedbackAction
}
