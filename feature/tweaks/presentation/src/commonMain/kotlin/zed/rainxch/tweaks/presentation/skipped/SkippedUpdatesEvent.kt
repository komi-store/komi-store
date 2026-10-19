package zed.rainxch.tweaks.presentation.skipped

sealed interface SkippedUpdatesEvent {
    data class Unskipped(
        val appName: String,
    ) : SkippedUpdatesEvent

    data object Failure : SkippedUpdatesEvent
}
