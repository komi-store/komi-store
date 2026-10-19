package zed.rainxch.tweaks.presentation.skipped

sealed interface SkippedUpdatesEvent {
    data class Unskipped(
        val appName: String,
    ) : SkippedUpdatesEvent

    data class Failure(
        val message: String,
    ) : SkippedUpdatesEvent
}
