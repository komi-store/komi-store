package zed.rainxch.reviews.presentation

sealed interface ReviewsEvent {
    data class OnMessage(
        val message: String,
    ) : ReviewsEvent

    data object OnNavigateToAuthentication : ReviewsEvent
}
