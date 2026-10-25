package zed.rainxch.reviews.presentation.moderation

sealed interface ModerationEvent {
    data class OnMessage(
        val message: String,
    ) : ModerationEvent
}
