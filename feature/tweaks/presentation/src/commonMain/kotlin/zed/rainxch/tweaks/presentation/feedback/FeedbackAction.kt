package zed.rainxch.tweaks.presentation.feedback

import zed.rainxch.tweaks.presentation.feedback.model.FeedbackCategory
import zed.rainxch.tweaks.presentation.feedback.model.FeedbackTopic

sealed interface FeedbackAction {
    sealed interface FieldEdit : FeedbackAction

    data class OnCategoryChange(
        val category: FeedbackCategory,
    ) : FeedbackAction

    data class OnTopicChange(
        val topic: FeedbackTopic,
    ) : FeedbackAction

    data class OnTitleChange(
        val value: String,
    ) : FieldEdit

    data class OnDescriptionChange(
        val value: String,
    ) : FieldEdit

    data class OnStepsToReproduceChange(
        val value: String,
    ) : FieldEdit

    data class OnExpectedActualChange(
        val value: String,
    ) : FieldEdit

    data class OnUseCaseChange(
        val value: String,
    ) : FieldEdit

    data class OnProposedSolutionChange(
        val value: String,
    ) : FieldEdit

    data class OnCurrentBehaviourChange(
        val value: String,
    ) : FieldEdit

    data class OnDesiredBehaviourChange(
        val value: String,
    ) : FieldEdit

    data object OnAttachDiagnosticsToggle : FeedbackAction

    data object OnSendViaEmail : FeedbackAction

    data object OnSendViaGithub : FeedbackAction

    data object OnDismiss : FeedbackAction
}
