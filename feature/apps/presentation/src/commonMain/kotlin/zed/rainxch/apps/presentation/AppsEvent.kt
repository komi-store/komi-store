package zed.rainxch.apps.presentation

sealed interface AppsEvent {
    data class ShowError(
        val message: String,
    ) : AppsEvent

    data class ShowSuccess(
        val message: String,
    ) : AppsEvent

    data class NavigateToRepo(
        val repoId: Long,
        val sourceHost: String? = null,
        val owner: String? = null,
        val repo: String? = null,
        val initialAssetName: String? = null,
        val packageName: String? = null,
    ) : AppsEvent

    data object NavigateToExternalImport : AppsEvent
}
