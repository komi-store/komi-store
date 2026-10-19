package zed.rainxch.tweaks.presentation.hidden

sealed interface HiddenRepositoriesAction {
    data class OnUnhide(
        val repoId: Long,
    ) : HiddenRepositoriesAction

    data object OnUnhideAll : HiddenRepositoriesAction
}
