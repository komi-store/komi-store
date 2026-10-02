package zed.rainxch.apps.presentation

import zed.rainxch.apps.presentation.model.InstalledAppUi
import zed.rainxch.apps.presentation.model.AppSortRule
import zed.rainxch.apps.presentation.model.DeviceAppUi
import zed.rainxch.apps.presentation.model.GithubAssetUi
import zed.rainxch.apps.presentation.model.ImportSummaryBucket

sealed interface AppsAction {
    data object OnNavigateBackClick : AppsAction

    data class OnSearchChange(
        val query: String,
    ) : AppsAction

    data class OnSortRuleSelected(
        val sortRule: AppSortRule,
    ) : AppsAction

    data class OnOpenApp(
        val app: InstalledAppUi,
    ) : AppsAction

    data class OnUpdateApp(
        val app: InstalledAppUi,
    ) : AppsAction

    data class OnCancelUpdate(
        val packageName: String,
    ) : AppsAction

    data object OnUpdateAll : AppsAction

    data object OnCancelUpdateAll : AppsAction

    data object OnCheckAllForUpdates : AppsAction

    data object OnRefresh : AppsAction

    data object OnLifecycleResume : AppsAction

    data object OnToggleUpToDateSection : AppsAction

    data object OnToggleUpdatesSection : AppsAction

    data class OnTwoPaneSelect(
        val packageName: String?,
    ) : AppsAction

    data class OnNavigateToRepo(
        val repoId: Long,
        val sourceHost: String? = null,
        val owner: String? = null,
        val repo: String? = null,
        val initialAssetName: String? = null,
        val packageName: String? = null,
    ) : AppsAction

    data class OnUninstallApp(
        val app: InstalledAppUi,
    ) : AppsAction

    data class OnUninstallConfirmed(val app: InstalledAppUi) : AppsAction
    data object OnDismissUninstallDialog : AppsAction

    data object OnAddByLinkClick : AppsAction
    data object OnDismissLinkSheet : AppsAction
    data class OnDeviceAppSearchChange(val query: String) : AppsAction
    data class OnDeviceAppSelected(val app: DeviceAppUi) : AppsAction
    data class OnLinkSuggestionSelected(
        val owner: String,
        val repo: String,

        val sourceHost: String? = null,
    ) : AppsAction
    data object OnLinkEnterUrlManually : AppsAction
    data object OnRetryLinkSearch : AppsAction
    data class OnRepoUrlChanged(val url: String) : AppsAction
    data object OnValidateAndLinkRepo : AppsAction
    data object OnBackToAppPicker : AppsAction
    data object OnBackToSmartMatch : AppsAction
    data class OnLinkAssetSelected(val asset: GithubAssetUi) : AppsAction
    data object OnBackToEnterUrl : AppsAction

    data class OnLinkAssetFilterChanged(val filter: String) : AppsAction

    data class OnLinkFallbackToggled(val enabled: Boolean) : AppsAction

    data class OnTogglePreReleases(val packageName: String, val enabled: Boolean) : AppsAction

    data class OnToggleUpdateCheck(val packageName: String, val enabled: Boolean) : AppsAction

    data class OnSkipReleaseTag(val packageName: String, val tag: String) : AppsAction

    data class OnUnskipReleaseTag(val packageName: String) : AppsAction

    data class OnOpenAdvancedSettings(val app: InstalledAppUi) : AppsAction
    data object OnDismissAdvancedSettings : AppsAction
    data class OnAdvancedFilterChanged(val filter: String) : AppsAction
    data class OnAdvancedFallbackToggled(val enabled: Boolean) : AppsAction
    data object OnAdvancedSaveFilter : AppsAction
    data object OnAdvancedClearFilter : AppsAction
    data object OnAdvancedRefreshPreview : AppsAction

    data class OnOpenVariantPicker(
        val app: InstalledAppUi,
        val resumeUpdateAfterPick: Boolean = false,
    ) : AppsAction
    data object OnDismissVariantPicker : AppsAction
    data class OnVariantSelected(val variant: String?) : AppsAction
    data object OnResetVariantToAuto : AppsAction

    data object OnExportApps : AppsAction
    data object OnExportObtainium : AppsAction
    data object OnImportApps : AppsAction
    data object OnDismissImportSummary : AppsAction

    data class OnToggleImportSummaryBucket(
        val bucket: ImportSummaryBucket,
    ) : AppsAction

    data object OnDismissKaoBanner : AppsAction
    data object OnKaoLearnMore : AppsAction

    data class OnInstallPendingApp(
        val app: InstalledAppUi,
    ) : AppsAction

    data class OnDiscardPendingInstall(
        val app: InstalledAppUi,
    ) : AppsAction

    data class OnConfirmDiscardPendingInstall(
        val app: InstalledAppUi,
    ) : AppsAction

    data object OnDismissDiscardPendingDialog : AppsAction

    data object OnImportProposalReview : AppsAction

    data object OnImportProposalDismiss : AppsAction

    data object OnRescanForGithubApps : AppsAction

    data object OnAddFromStarredClick : AppsAction
}
