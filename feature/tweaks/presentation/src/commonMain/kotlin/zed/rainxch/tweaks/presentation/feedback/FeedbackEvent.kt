package zed.rainxch.tweaks.presentation.feedback

import zed.rainxch.tweaks.presentation.feedback.model.FeedbackChannel

sealed interface FeedbackEvent {
    data class OnSent(
        val channel: FeedbackChannel,
    ) : FeedbackEvent

    data object OnSendError : FeedbackEvent
}
