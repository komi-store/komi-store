package zed.rainxch.details.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.system.SystemArchitecture
import zed.rainxch.core.domain.utils.AssetOwnership
import zed.rainxch.core.presentation.components.reviews.RatingSummaryUi
import zed.rainxch.core.presentation.components.reviews.ReviewUi
import zed.rainxch.details.domain.model.ReleaseCategory
import zed.rainxch.details.presentation.model.ApkInspectionUi
import zed.rainxch.details.presentation.model.AssetUi
import zed.rainxch.details.presentation.model.AttestationStatus
import zed.rainxch.details.presentation.model.DowngradeWarning
import zed.rainxch.details.presentation.model.DownloadStage
import zed.rainxch.details.presentation.model.InstallLogItem
import zed.rainxch.details.presentation.model.InstalledAppUi
import zed.rainxch.details.presentation.model.OwnerProfileUi
import zed.rainxch.details.presentation.model.PlatformHandoffUi
import zed.rainxch.details.presentation.model.PlatformReleaseUi
import zed.rainxch.details.presentation.model.ReleaseUi
import zed.rainxch.details.presentation.model.RepoStatsUi
import zed.rainxch.details.presentation.model.RepositoryUi
import zed.rainxch.details.presentation.model.SigningKeyPromptUi
import zed.rainxch.details.presentation.model.TranslationState
import zed.rainxch.details.presentation.model.TranslationTarget

data class DetailsState(
    val isLoading: Boolean = true,
    val isCurrentUserOwner: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshCooldownUntilEpochMs: Long? = null,
    val errorMessage: String? = null,
    val userProfile: OwnerProfileUi? = null,
    val repository: RepositoryUi? = null,
    val primaryAsset: AssetUi? = null,
    val installableAssets: ImmutableList<AssetUi> = persistentListOf(),
    val crossPlatformAssets: ImmutableList<AssetUi> = persistentListOf(),
    val assetVariantStatuses: ImmutableMap<Long, AssetOwnership.VariantStatus> = persistentMapOf(),
    val pinnedVariantLabel: String? = null,
    val selectedRelease: ReleaseUi? = null,
    val allReleases: ImmutableList<ReleaseUi> = persistentListOf(),
    val releasesLoadFailed: Boolean = false,
    val isRetryingReleases: Boolean = false,
    val isReleaseSelectorVisible: Boolean = false,
    val selectedReleaseCategory: ReleaseCategory = ReleaseCategory.STABLE,
    val isVersionPickerVisible: Boolean = false,
    val stats: RepoStatsUi? = null,
    val readmeMarkdown: String? = null,
    val readmeLanguage: String? = null,
    val installLogs: ImmutableList<InstallLogItem> = persistentListOf(),
    val isDownloading: Boolean = false,
    val downloadProgressPercent: Int? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val isInstalling: Boolean = false,
    val downloadError: String? = null,
    val installError: String? = null,
    val downloadStage: DownloadStage = DownloadStage.IDLE,
    val systemArchitecture: SystemArchitecture = SystemArchitecture.UNKNOWN,
    val isObtainiumAvailable: Boolean = false,
    val isObtainiumEnabled: Boolean = false,
    val isAppManagerAvailable: Boolean = false,
    val isAppManagerEnabled: Boolean = false,
    val installedApp: InstalledAppUi? = null,
    val installedApps: ImmutableList<InstalledAppUi> = persistentListOf(),
    val isFavourite: Boolean = false,
    val isStarred: Boolean = false,
    val isTrackingApp: Boolean = false,
    val isAboutExpanded: Boolean = false,
    val isWhatsNewExpanded: Boolean = false,
    val aboutMeasuredHeightPx: Float? = null,
    val whatsNewMeasuredHeightPx: Float? = null,
    val aboutTranslation: TranslationState = TranslationState(),
    val whatsNewTranslation: TranslationState = TranslationState(),
    val isLanguagePickerVisible: Boolean = false,
    val languagePickerTarget: TranslationTarget? = null,
    val deviceLanguageCode: String = "en",
    val isComingFromUpdate: Boolean = false,
    val downgradeWarning: DowngradeWarning? = null,
    val signingKeyWarning: SigningKeyPromptUi? = null,
    val showExternalInstallerPrompt: Boolean = false,
    val pendingInstallFilePath: String? = null,
    val showUninstallConfirmation: Boolean = false,
    val showUnlinkConfirmation: Boolean = false,
    val attestationStatus: AttestationStatus = AttestationStatus.UNCHECKED,
    val stalledStableSinceDays: Int? = null,
    val mergedChangelog: String? = null,
    val mergedChangelogBaseTag: String? = null,
    val latestStableHasInstallableAsset: Boolean = false,
    val apkInspection: ApkInspectionUi? = null,
    val isApkInspectSheetVisible: Boolean = false,
    val isApkInspectLoading: Boolean = false,
    val isApkInspectCoachmarkPending: Boolean = false,
    val isChannelChipCoachmarkPending: Boolean = false,
    val showAllPlatforms: Boolean = false,
    val filteredReleases: ImmutableList<ReleaseUi> = persistentListOf(),
    val latestStableRelease: ReleaseUi? = null,
    val canSwitchToStable: Boolean = false,
    val isPendingInstallReady: Boolean = false,
    val devicePlatform: DiscoveryPlatform = DiscoveryPlatform.Android,
    val platformReleases: ImmutableList<PlatformReleaseUi> = persistentListOf(),
    val releasePlatforms: ImmutableMap<Long, ImmutableSet<DiscoveryPlatform>> = persistentMapOf(),
    val deviceBuildReleaseIds: ImmutableSet<Long> = persistentSetOf(),
    val selectedHasDeviceBuild: Boolean = true,
    val newerReleasesLackDeviceBuild: Boolean = false,
    val deviceBuildTarget: ReleaseUi? = null,
    val handoff: PlatformHandoffUi? = null,
    val releaseLines: ImmutableMap<Long, String> = persistentMapOf(),
    val selectedAppLabel: String? = null,
    val reviewSummary: RatingSummaryUi? = null,
    val topReviews: ImmutableList<ReviewUi> = persistentListOf(),
)
