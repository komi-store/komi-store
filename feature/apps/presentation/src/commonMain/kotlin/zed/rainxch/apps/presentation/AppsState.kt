package zed.rainxch.apps.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import zed.rainxch.apps.domain.model.ImportResult
import zed.rainxch.apps.presentation.model.AdvancedPreviewMessage
import zed.rainxch.apps.presentation.model.AppGroup
import zed.rainxch.apps.presentation.model.AppItem
import zed.rainxch.apps.presentation.model.AppSortRule
import zed.rainxch.apps.presentation.model.DeviceAppUi
import zed.rainxch.apps.presentation.model.ImportSummaryBucket
import zed.rainxch.apps.presentation.model.GithubAssetUi
import zed.rainxch.apps.presentation.model.GithubRepoInfoUi
import zed.rainxch.apps.presentation.model.InstalledAppUi
import zed.rainxch.apps.presentation.model.LinkStep
import zed.rainxch.apps.presentation.model.UpdateAllProgress
import zed.rainxch.apps.presentation.model.VariantOption
import zed.rainxch.apps.presentation.model.VariantPickerError
import zed.rainxch.core.domain.system.OrchestratedDownload
import zed.rainxch.core.domain.system.RepoMatchSuggestion

data class AppsState(
    val apps: ImmutableList<AppItem> = persistentListOf(),
    val filteredApps: ImmutableList<AppItem> = persistentListOf(),
    val searchQuery: String = "",
    val sortRule: AppSortRule = AppSortRule.UpdatesFirst,
    val isLoading: Boolean = false,
    val isUpdatingAll: Boolean = false,
    val updateAllProgress: UpdateAllProgress? = null,
    val updateAllButtonEnabled: Boolean = true,
    val isCheckingForUpdates: Boolean = false,
    val lastCheckedTimestamp: Long? = null,
    val isRefreshing: Boolean = false,

    val isUpToDateSectionExpanded: Boolean = true,
    val isUpdatesSectionExpanded: Boolean = true,

    val showLinkSheet: Boolean = false,
    val linkStep: LinkStep = LinkStep.PickApp,
    val deviceApps: ImmutableList<DeviceAppUi> = persistentListOf(),
    val deviceAppSearchQuery: String = "",
    val selectedDeviceApp: DeviceAppUi? = null,
    val repoUrl: String = "",
    val linkSearchLoading: Boolean = false,
    val linkSuggestions: ImmutableList<RepoMatchSuggestion> = persistentListOf(),
    val linkSearchError: String? = null,
    val isValidatingRepo: Boolean = false,
    val repoValidationError: String? = null,
    val linkValidationStatus: String? = null,
    val linkInstallableAssets: ImmutableList<GithubAssetUi> = persistentListOf(),
    val linkAssetsReleaseTag: String? = null,
    val linkSelectedAsset: GithubAssetUi? = null,
    val linkDownloadProgress: Int? = null,
    val fetchedRepoInfo: GithubRepoInfoUi? = null,

    val linkAssetFilter: String = "",

    val linkAssetFilterError: String? = null,

    val linkFallbackToOlder: Boolean = false,

    val advancedSettingsApp: InstalledAppUi? = null,
    val advancedFilterDraft: String = "",
    val advancedFallbackDraft: Boolean = false,
    val advancedFilterError: String? = null,
    val advancedPreviewLoading: Boolean = false,
    val advancedPreviewMatched: ImmutableList<GithubAssetUi> = persistentListOf(),
    val advancedPreviewTag: String? = null,
    val advancedPreviewMessage: AdvancedPreviewMessage? = null,
    val advancedSavingFilter: Boolean = false,

    val variantPickerApp: InstalledAppUi? = null,
    val variantPickerLoading: Boolean = false,
    val variantPickerOptions: ImmutableList<VariantOption> = persistentListOf(),
    val variantPickerCurrentVariant: String? = null,
    val variantPickerError: VariantPickerError? = null,

    val variantPickerResumeUpdateAfterPick: Boolean = false,

    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val importSummary: ImportResult? = null,
    val expandedImportBuckets: ImmutableSet<ImportSummaryBucket> = persistentSetOf(),

    val appPendingUninstall: InstalledAppUi? = null,

    val appPendingDiscard: InstalledAppUi? = null,

    val pendingExternalImportCount: Int = 0,
    val showImportProposalBanner: Boolean = false,
    val isExternalImportInFlight: Boolean = false,

    val showKaoBanner: Boolean = false,
    val linkSourceHost: String? = null,

    val filteredDeviceApps: ImmutableList<DeviceAppUi> = persistentListOf(),
    val filteredLinkAssets: ImmutableList<GithubAssetUi> = persistentListOf(),
    val pendingApps: ImmutableList<AppItem> = persistentListOf(),
    val updateApps: ImmutableList<AppItem> = persistentListOf(),
    val idleApps: ImmutableList<AppItem> = persistentListOf(),
    val pendingGroups: ImmutableList<AppGroup> = persistentListOf(),
    val updateGroups: ImmutableList<AppGroup> = persistentListOf(),
    val idleGroups: ImmutableList<AppGroup> = persistentListOf(),

    // Orchestrator downloads whose key matches no loaded library row (an app added by link and
    // not installed yet is keyed "owner/name", not by package). Rendered as transient cards
    // built from the orchestrator entry alone: there is no DB row to back them.
    val inProgressDownloads: ImmutableList<OrchestratedDownload> = persistentListOf(),

    // Library rows whose download now lives on an "in progress" card. They are filtered out of
    // every library group so an app is on screen exactly once during its download.
    val hiddenRowPackages: ImmutableSet<String> = persistentSetOf(),
)
