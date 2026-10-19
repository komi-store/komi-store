package zed.rainxch.tweaks.presentation.skipped

sealed interface SkippedUpdatesAction {
    data class OnUnskip(
        val packageName: String,
    ) : SkippedUpdatesAction
}
