package zed.rainxch.details.presentation

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import zed.rainxch.core.domain.model.apk.ApkInspection
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.account.github.GithubUserProfile
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.system.SystemArchitecture
import zed.rainxch.details.domain.model.ReleaseCategory
import zed.rainxch.details.domain.model.RepoStats
import zed.rainxch.details.presentation.model.AttestationStatus
import zed.rainxch.details.presentation.model.DowngradeWarning
import zed.rainxch.details.presentation.model.DownloadStage
import zed.rainxch.details.presentation.model.InstallLogItem
import zed.rainxch.details.presentation.model.SigningKeyWarning
import zed.rainxch.details.presentation.model.TranslationState
import zed.rainxch.details.presentation.model.TranslationTarget

internal data class RawDetailsState(
    val isLoading: Boolean = true,
    val isCurrentUserOwner: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshCooldownUntilEpochMs: Long? = null,
    val errorMessage: String? = null,
    val userProfile: GithubUserProfile? = null,
    val repository: GithubRepoSummary? = null,
    val primaryAsset: GithubAsset? = null,
    val installableAssets: List<GithubAsset> = emptyList(),
    val selectedRelease: GithubRelease? = null,
    val allReleases: List<GithubRelease> = emptyList(),
    val releasesLoadFailed: Boolean = false,
    val isRetryingReleases: Boolean = false,
    val isReleaseSelectorVisible: Boolean = false,
    val selectedReleaseCategory: ReleaseCategory = ReleaseCategory.STABLE,
    val isVersionPickerVisible: Boolean = false,
    val stats: RepoStats? = null,
    val readmeMarkdown: String? = null,
    val readmeLanguage: String? = null,
    val installLogs: List<InstallLogItem> = emptyList(),
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
    val isInstallDropdownExpanded: Boolean = false,
    val isAppManagerAvailable: Boolean = false,
    val isAppManagerEnabled: Boolean = false,
    val installedApp: InstalledApp? = null,
    val installedApps: List<InstalledApp> = emptyList(),
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
    val signingKeyWarning: SigningKeyWarning? = null,
    val showExternalInstallerPrompt: Boolean = false,
    val pendingInstallFilePath: String? = null,
    val showUninstallConfirmation: Boolean = false,
    val showUnlinkConfirmation: Boolean = false,
    val attestationStatus: AttestationStatus = AttestationStatus.UNCHECKED,
    val stalledStableSinceDays: Int? = null,
    val mergedChangelog: String? = null,
    val mergedChangelogBaseTag: String? = null,
    val latestStableHasInstallableAsset: Boolean = false,
    val apkInspection: ApkInspection? = null,
    val isApkInspectSheetVisible: Boolean = false,
    val isApkInspectLoading: Boolean = false,
    val isApkInspectCoachmarkPending: Boolean = false,
    val isChannelChipCoachmarkPending: Boolean = false,
    val showAllPlatforms: Boolean = false,
    val devicePlatform: DiscoveryPlatform = DiscoveryPlatform.Android,
    val releasePlatforms: ImmutableMap<Long, Set<DiscoveryPlatform>> = persistentMapOf(),
    val deviceBuildReleaseIds: ImmutableSet<Long> = persistentSetOf(),
    val releaseLines: ImmutableMap<Long, String> = persistentMapOf(),
    val handoffPlatform: DiscoveryPlatform? = null,
)
