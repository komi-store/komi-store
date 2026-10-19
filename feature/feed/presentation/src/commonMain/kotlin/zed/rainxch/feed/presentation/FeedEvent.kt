package zed.rainxch.feed.presentation

sealed interface FeedEvent {
    data class OnMessage(
        val message: String,
    ) : FeedEvent

    data object OnScrollToTop : FeedEvent
}
