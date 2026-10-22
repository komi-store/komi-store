package zed.rainxch.tweaks.presentation.hidden

sealed interface HiddenRepositoriesEvent {
    data class Unhidden(
        val repoFullName: String,
    ) : HiddenRepositoriesEvent

    data object UnhiddenAll : HiddenRepositoriesEvent

    data object Failure : HiddenRepositoriesEvent
}
