package zed.rainxch.apps.presentation

import zed.rainxch.apps.presentation.model.AppSortRule
import zed.rainxch.apps.presentation.model.DeviceAppUi
import zed.rainxch.apps.presentation.model.GithubAssetUi
import zed.rainxch.apps.presentation.model.ImportSummaryBucket
import zed.rainxch.apps.presentation.model.InstalledAppUi

sealed interface AppsAction {
    sealed interface Library : AppsAction

    sealed interface ImportProposal : AppsAction

    sealed interface Update : AppsAction

    sealed interface DownloadTask : AppsAction {
        val key: String
    }

    sealed interface LinkNavigation : AppsAction

    sealed interface LinkInput : AppsAction

    sealed interface AppRow : AppsAction

    sealed interface AdvancedFilter : AppsAction

    sealed interface Variant : AppsAction

    sealed interface Backup : AppsAction

    data object OnNavigateBackClick : AppsAction

    data class OnSearchChange(
        val query: String,
    ) : Library

    data class OnSortRuleSelected(
        val sortRule: AppSortRule,
    ) : Library

    data class OnOpenApp(
        val app: InstalledAppUi,
    ) : AppRow

    data class OnUpdateApp(
        val app: InstalledAppUi,
    ) : AppsAction

    data class OnCancelInProgressDownload(
        override val key: String,
    ) : DownloadTask

    data class OnResumeInProgressDownload(
        override val key: String,
    ) : DownloadTask

    data class OnRetryInProgressDownload(
        override val key: String,
    ) : DownloadTask

    data class OnDiscardInProgressDownload(
        override val key: String,
    ) : DownloadTask

    data class OnInstallInProgressDownload(
        override val key: String,
    ) : DownloadTask

    data class OnDismissInProgressDownload(
        override val key: String,
    ) : DownloadTask

    data object OnUpdateAll : Update

    data object OnCancelUpdateAll : Update

    data object OnCheckAllForUpdates : Library

    data object OnRefresh : Library

    data object OnLifecycleResume : Library

    data object OnToggleUpToDateSection : Library

    data object OnToggleUpdatesSection : Library

    data class OnNavigateToRepo(
        val repoId: Long,
        val sourceHost: String? = null,
        val owner: String? = null,
        val repo: String? = null,
        val packageName: String? = null,
    ) : Library

    data class OnUninstallApp(
        val app: InstalledAppUi,
    ) : AppRow

    data class OnUninstallConfirmed(
        val app: InstalledAppUi,
    ) : AppRow

    data object OnDismissUninstallDialog : AppRow

    data object OnAddByLinkClick : LinkNavigation

    data object OnDismissLinkSheet : LinkNavigation

    data class OnDeviceAppSearchChange(
        val query: String,
    ) : LinkInput

    data class OnDeviceAppSelected(
        val app: DeviceAppUi,
    ) : LinkInput

    data class OnLinkSuggestionSelected(
        val owner: String,
        val repo: String,
        val sourceHost: String? = null,
    ) : LinkInput

    data object OnLinkEnterUrlManually : LinkNavigation

    data object OnRetryLinkSearch : LinkNavigation

    data class OnRepoUrlChanged(
        val url: String,
    ) : LinkInput

    data object OnValidateAndLinkRepo : LinkInput

    data object OnBackToAppPicker : LinkNavigation

    data object OnBackToSmartMatch : LinkNavigation

    data class OnLinkAssetSelected(
        val asset: GithubAssetUi,
    ) : LinkInput

    data object OnBackToEnterUrl : LinkNavigation

    data class OnLinkAssetFilterChanged(
        val filter: String,
    ) : LinkInput

    data class OnLinkFallbackToggled(
        val enabled: Boolean,
    ) : LinkInput

    data class OnTogglePreReleases(
        val packageName: String,
        val enabled: Boolean,
    ) : AppRow

    data class OnToggleUpdateCheck(
        val packageName: String,
        val enabled: Boolean,
    ) : AppRow

    data class OnSkipReleaseTag(
        val packageName: String,
        val tag: String,
    ) : AppRow

    data class OnUnskipReleaseTag(
        val packageName: String,
    ) : AppRow

    data class OnOpenAdvancedSettings(
        val app: InstalledAppUi,
    ) : AdvancedFilter

    data object OnDismissAdvancedSettings : AdvancedFilter

    data class OnAdvancedFilterChanged(
        val filter: String,
    ) : AdvancedFilter

    data class OnAdvancedFallbackToggled(
        val enabled: Boolean,
    ) : AdvancedFilter

    data object OnAdvancedSaveFilter : AdvancedFilter

    data object OnAdvancedClearFilter : AdvancedFilter

    data object OnAdvancedRefreshPreview : AdvancedFilter

    data class OnOpenVariantPicker(
        val app: InstalledAppUi,
        val resumeUpdateAfterPick: Boolean = false,
    ) : Variant

    data object OnDismissVariantPicker : Variant

    data class OnVariantSelected(
        val variant: String?,
    ) : Variant

    data object OnResetVariantToAuto : Variant

    data object OnExportApps : Backup

    data object OnExportObtainium : Backup

    data object OnImportApps : Backup

    data object OnDismissImportSummary : Backup

    data class OnToggleImportSummaryBucket(
        val bucket: ImportSummaryBucket,
    ) : Backup

    data class OnInstallPendingApp(
        val app: InstalledAppUi,
    ) : Update

    data class OnDiscardPendingInstall(
        val app: InstalledAppUi,
    ) : Update

    data class OnConfirmDiscardPendingInstall(
        val app: InstalledAppUi,
    ) : Update

    data object OnDismissDiscardPendingDialog : Update

    data object OnImportProposalReview : ImportProposal

    data object OnImportProposalDismiss : ImportProposal

    data object OnRescanForGithubApps : ImportProposal

    data object OnAddFromStarredClick : AppsAction
}
