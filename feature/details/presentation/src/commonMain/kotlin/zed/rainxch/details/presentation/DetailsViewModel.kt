package zed.rainxch.details.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.apk.ApkPackageInfo
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.repository.FavoriteRepo
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.isReallyInstalled
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.error.RefreshError
import zed.rainxch.core.domain.model.error.RefreshException
import zed.rainxch.core.domain.model.account.github.isEffectivelyPreRelease
import zed.rainxch.core.domain.network.Downloader
import zed.rainxch.core.domain.repository.ExternalImportRepository
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.SeenReposRepository
import zed.rainxch.core.domain.repository.StarredRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.ApkInspector
import zed.rainxch.core.domain.system.DownloadOrchestrator
import zed.rainxch.core.domain.system.DownloadSpec
import zed.rainxch.core.domain.system.DownloadStage as OrchestratorStage
import zed.rainxch.core.domain.system.InstallOutcome
import zed.rainxch.core.domain.system.InstallPolicy
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.DownloadStagePhase
import zed.rainxch.core.domain.system.OrchestratedDownload
import zed.rainxch.core.domain.system.phase
import zed.rainxch.core.domain.model.installation.InstallerType
import zed.rainxch.core.domain.repository.UserSessionRepository
import zed.rainxch.core.domain.system.PackageMonitor
import zed.rainxch.core.domain.use_cases.SyncInstalledAppsUseCase
import zed.rainxch.core.domain.utils.platforms
import zed.rainxch.core.domain.utils.toDiscoveryPlatform
import zed.rainxch.core.presentation.utils.daysSinceIso
import zed.rainxch.core.domain.utils.AssetFilter
import zed.rainxch.core.domain.utils.AssetOwnership
import zed.rainxch.core.domain.utils.AssetVariant
import zed.rainxch.core.domain.utils.ReleaseLines
import zed.rainxch.core.domain.utils.VersionMath
import zed.rainxch.core.domain.helpers.BrowserHelper
import zed.rainxch.core.domain.helpers.ShareManager
import zed.rainxch.details.domain.model.ApkValidationResult
import zed.rainxch.details.domain.model.FingerprintCheckResult
import zed.rainxch.details.domain.model.ReleaseCategory
import zed.rainxch.details.domain.model.SaveInstalledAppParams
import zed.rainxch.details.domain.model.UpdateInstalledAppParams
import zed.rainxch.details.domain.repository.DetailsRepository
import zed.rainxch.details.domain.repository.TranslationRepository
import zed.rainxch.details.domain.system.AttestationVerifier
import zed.rainxch.details.domain.system.VerificationResult
import zed.rainxch.details.domain.system.InstallationManager
import zed.rainxch.details.domain.util.VersionHelper
import zed.rainxch.details.presentation.model.AttestationStatus
import zed.rainxch.details.presentation.model.DowngradeWarning
import zed.rainxch.details.presentation.model.DownloadStage
import zed.rainxch.details.presentation.model.InstallLogItem
import zed.rainxch.details.presentation.model.LogResult
import zed.rainxch.details.presentation.model.LogResult.Error
import zed.rainxch.details.presentation.model.SigningKeyWarning
import zed.rainxch.details.presentation.model.SupportedLanguages
import zed.rainxch.details.presentation.model.TranslationState
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.failed_to_load
import zed.rainxch.githubstore.core.presentation.res.star_added
import zed.rainxch.githubstore.core.presentation.res.star_removed
import zed.rainxch.githubstore.core.presentation.res.star_sign_in_required
import zed.rainxch.githubstore.core.presentation.res.added_to_favourites
import zed.rainxch.githubstore.core.presentation.res.details_unlink_external_app_failure
import zed.rainxch.githubstore.core.presentation.res.details_unlink_external_app_success
import zed.rainxch.githubstore.core.presentation.res.failed_to_load_details
import zed.rainxch.githubstore.core.presentation.res.failed_to_open_app
import zed.rainxch.githubstore.core.presentation.res.failed_to_share_link
import zed.rainxch.githubstore.core.presentation.res.failed_to_uninstall
import zed.rainxch.githubstore.core.presentation.res.installer_saved_downloads
import zed.rainxch.githubstore.core.presentation.res.releases_unavailable_temporarily
import zed.rainxch.githubstore.core.presentation.res.link_copied_to_clipboard
import zed.rainxch.githubstore.core.presentation.res.rate_limit_exceeded
import zed.rainxch.githubstore.core.presentation.res.rate_limit_exceeded_retry_in
import zed.rainxch.githubstore.core.presentation.res.rate_limit_exceeded_signin_hint
import zed.rainxch.githubstore.core.presentation.res.removed_from_favourites
import zed.rainxch.githubstore.core.presentation.res.translation_failed
import zed.rainxch.githubstore.core.presentation.res.update_package_mismatch
import zed.rainxch.githubstore.core.presentation.res.variant_first_pin_toast
import zed.rainxch.githubstore.core.presentation.res.variant_first_pin_toast_generic
import zed.rainxch.githubstore.core.presentation.res.variant_unpinned_toast
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock.System
import kotlin.time.ExperimentalTime

private const val RELEASES_REVALIDATE_MIN_AGE_MS = 5L * 60L * 1000L

class DetailsViewModel(
    private val repositoryId: Long,
    private val ownerParam: String,
    private val repoParam: String,
    private val sourceHostParam: String?,
    private val detailsRepository: DetailsRepository,
    private val downloader: Downloader,
    private val installer: Installer,
    private val platform: Platform,
    private val helper: BrowserHelper,
    private val shareManager: ShareManager,
    private val installedAppsRepository: InstalledAppsRepository,
    private val favouritesRepository: FavouritesRepository,
    private val starredRepository: StarredRepository,
    private val packageMonitor: PackageMonitor,
    private val syncInstalledAppsUseCase: SyncInstalledAppsUseCase,
    private val translationRepository: TranslationRepository,
    private val logger: KomiStoreLogger,
    private val isComingFromUpdate: Boolean,
    private val tweaksRepository: TweaksRepository,
    private val seenReposRepository: SeenReposRepository,
    private val installationManager: InstallationManager,
    private val attestationVerifier: AttestationVerifier,
    private val downloadOrchestrator: DownloadOrchestrator,
    private val externalImportRepository: ExternalImportRepository,
    private val apkInspector: ApkInspector,
    private val systemInstallSerializer: zed.rainxch.core.domain.system.SystemInstallSerializer,
    private val userSessionRepository: UserSessionRepository,
    private val packageNameParam: String? = null,
) : ViewModel() {
    private var hasLoadedInitialData = false

    // Backed by a flow rather than a plain field: the display mirror below stands down while this
    // VM owns a download job, and it has to be told when that job ends. A plain field would leave
    // any orchestrator transition that happened during the download unread forever.
    private val localDownloadJob = MutableStateFlow<Job?>(null)

    private var currentDownloadJob: Job?
        get() = localDownloadJob.value
        set(value) {
            localDownloadJob.value = value
            value?.invokeOnCompletion { localDownloadJob.compareAndSet(value, null) }
        }

    private var currentAssetName: String? = null
    private var aboutTranslationJob: Job? = null
    private var whatsNewTranslationJob: Job? = null

    private val _state = MutableStateFlow(RawDetailsState(devicePlatform = platform.toDiscoveryPlatform()))
    val state: StateFlow<DetailsState> =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    loadInitial()
                    observeApkInspectCoachmark()
                    observeChannelChipCoachmark()
                    observeCurrentUserForBadge()
                    observeShowAllPlatforms()

                    hasLoadedInitialData = true
                }
            }
            .map { it.toView() }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                DetailsState(),
            )

    private val _events = Channel<DetailsEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val rateLimited = AtomicBoolean(false)

    // Declared last among the initialisers: viewModelScope dispatches on Main.immediate, so the
    // collector this starts can run before the properties above exist.
    init {
        observeOrchestratorForDisplay()
    }

    fun confirmUninstall() {
        _state.update { it.copy(showUninstallConfirmation = false) }
        val installedApp = _state.value.installedApp ?: return
        logger.debug("Uninstalling app (confirmed): ${installedApp.packageName}")
        viewModelScope.launch {
            try {
                installer.uninstall(installedApp.packageName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to request uninstall for ${installedApp.packageName}: ${e.message}")
                _events.send(
                    DetailsEvent.OnMessage(
                        getString(Res.string.failed_to_uninstall, installedApp.packageName),
                    ),
                )
            }
        }
    }

    private fun confirmUnlinkExternalApp() {
        _state.update { it.copy(showUnlinkConfirmation = false) }
        val installedApp = _state.value.installedApp ?: return
        val packageName = installedApp.packageName
        logger.debug("Unlinking externally-imported app: $packageName")
        viewModelScope.launch {
            try {

                installedAppsRepository.executeInTransaction {
                    externalImportRepository.unlink(packageName)
                    installedAppsRepository.deleteInstalledApp(packageName)
                }
                _events.send(
                    DetailsEvent.OnMessage(
                        getString(Res.string.details_unlink_external_app_success),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to unlink $packageName: ${e.message}")
                _events.send(
                    DetailsEvent.OnMessage(
                        getString(Res.string.details_unlink_external_app_failure),
                    ),
                )
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    fun onAction(action: DetailsAction) {
        when (action) {
            DetailsAction.Retry -> {
                hasLoadedInitialData = false
                loadInitial()
            }

            DetailsAction.RetryReleases -> retryReleases()

            DetailsAction.Refresh -> refresh()

            DetailsAction.OnDismissDowngradeWarning -> {
                dismissDowngradeWarning()
            }

            DetailsAction.OnConfirmDowngradeInstall -> {
                val warning = _state.value.downgradeWarning ?: return
                dismissDowngradeWarning()
                val installedApp = _state.value.installedApp
                viewModelScope.launch {
                    val consentStillApplies =
                        _state.value.selectedRelease?.tagName == warning.targetVersion
                    if (consentStillApplies &&
                        installedApp != null &&
                        VersionMath.isExactSameVersion(
                            installedApp.latestVersion,
                            warning.currentVersion,
                        )
                    ) {
                        installedAppsRepository.setSkippedReleaseTag(
                            installedApp.packageName,
                            installedApp.latestVersion,
                        )
                    }
                    if (_state.value.selectedRelease?.tagName == warning.targetVersion) {
                        install(ignoreDowngrade = true)
                    } else {
                        install()
                    }
                }
            }

            DetailsAction.OnConfirmDowngradeUninstall -> {
                val warning = _state.value.downgradeWarning ?: return
                dismissDowngradeWarning()
                uninstallPackage(warning.packageName)
            }

            DetailsAction.OnDismissSigningKeyWarning -> {
                _state.update {
                    it.copy(
                        signingKeyWarning = null,
                        downloadStage = DownloadStage.IDLE,
                    )
                }
                currentAssetName = null
            }

            DetailsAction.OnOverrideSigningKeyWarning -> {
                overrideSigningKeyWarning()
            }

            DetailsAction.InstallPrimary -> {
                install()
            }

            DetailsAction.OnRequestUninstall -> {
                _state.update { it.copy(showUninstallConfirmation = true) }
            }

            DetailsAction.OnDismissUninstallConfirmation -> {
                _state.update { it.copy(showUninstallConfirmation = false) }
            }

            DetailsAction.OnConfirmUninstall -> {
                confirmUninstall()
            }

            DetailsAction.UninstallApp -> {
                uninstallApp()
            }

            DetailsAction.OnUnlinkExternalApp -> {
                _state.update { it.copy(showUnlinkConfirmation = true) }
            }

            DetailsAction.OnDismissUnlinkConfirmation -> {
                _state.update { it.copy(showUnlinkConfirmation = false) }
            }

            DetailsAction.OnConfirmUnlinkExternalApp -> {
                confirmUnlinkExternalApp()
            }

            is DetailsAction.DownloadAsset -> {
                val release = _state.value.selectedRelease
                downloadAsset(
                    downloadUrl = action.downloadUrl,
                    assetName = action.assetName,
                    sizeBytes = action.sizeBytes,
                    releaseTag = release?.tagName ?: "",
                )
            }

            DetailsAction.PauseDownload -> {
                pauseCurrentDownload()
            }

            DetailsAction.ResumeDownload -> {
                resumeCurrentDownload()
            }

            DetailsAction.DiscardDownload -> {
                discardCurrentDownload()
            }

            DetailsAction.OnToggleFavorite -> {
                toggleFavourite()
            }

            DetailsAction.OnToggleStar -> {
                toggleStar()
            }

            DetailsAction.OnShareClick -> {
                share()
            }

            DetailsAction.UpdateApp -> {
                update()
            }

            DetailsAction.OpenApp -> {
                openApp()
            }

            DetailsAction.OpenRepoInBrowser -> {
                _state.value.repository?.htmlUrl?.let {
                    helper.openUrl(url = it)
                }
            }

            DetailsAction.OpenAuthorInBrowser -> {
                _state.value.userProfile?.htmlUrl?.let {
                    helper.openUrl(url = it)
                }
            }

            DetailsAction.OpenInObtainium -> {
                openObtainium()
            }

            DetailsAction.OpenInAppManager -> {
                openAppManager()
            }

            DetailsAction.OnToggleInstallDropdown -> {
                _state.update {
                    it.copy(isInstallDropdownExpanded = !it.isInstallDropdownExpanded)
                }
            }

            is DetailsAction.SelectReleaseCategory -> {
                selectReleaseCategory(action)
            }

            is DetailsAction.SelectRelease -> {
                val release = action.release
                val (installable, primary) = recomputeAssetsForRelease(release)
                val newInstalledApp =
                    pickPrimaryInstalledApp(
                        _state.value.installedApps,
                        primary?.name,
                        installable,
                        releaseTag = release.tagName,
                    )
                whatsNewTranslationJob?.cancel()

                _state.update {
                    it.copy(
                        installedApp = newInstalledApp,
                        selectedRelease = release,
                        installableAssets = installable,
                        primaryAsset = primary,
                        isVersionPickerVisible = false,
                        whatsNewTranslation = TranslationState(),
                        whatsNewMeasuredHeightPx = null,
                    )
                }
            }

            DetailsAction.ToggleVersionPicker -> {
                _state.update {
                    it.copy(isVersionPickerVisible = !it.isVersionPickerVisible)
                }
            }

            DetailsAction.ToggleAboutExpanded -> {
                _state.update {
                    it.copy(isAboutExpanded = !it.isAboutExpanded)
                }
            }

            DetailsAction.ToggleWhatsNewExpanded -> {
                _state.update {
                    it.copy(isWhatsNewExpanded = !it.isWhatsNewExpanded)
                }
            }

            is DetailsAction.OnAboutMeasured -> {
                val current = _state.value.aboutMeasuredHeightPx
                if (current == null || action.heightPx > current) {
                    _state.update { it.copy(aboutMeasuredHeightPx = action.heightPx) }
                }
            }

            is DetailsAction.OnWhatsNewMeasured -> {
                val current = _state.value.whatsNewMeasuredHeightPx
                if (current == null || action.heightPx > current) {
                    _state.update { it.copy(whatsNewMeasuredHeightPx = action.heightPx) }
                }
            }

            is DetailsAction.TranslateAbout -> {
                val readme = _state.value.readmeMarkdown ?: return
                aboutTranslationJob?.cancel()
                aboutTranslationJob =
                    translateContent(
                        text = readme,
                        targetLanguageCode = action.targetLanguageCode,
                        updateState = { ts -> _state.update { it.copy(aboutTranslation = ts) } },
                        getCurrentState = { _state.value.aboutTranslation },
                    )
            }

            is DetailsAction.TranslateWhatsNew -> {
                val description = _state.value.selectedRelease?.description ?: return
                whatsNewTranslationJob?.cancel()
                whatsNewTranslationJob =
                    translateContent(
                        text = description,
                        targetLanguageCode = action.targetLanguageCode,
                        updateState = { ts -> _state.update { it.copy(whatsNewTranslation = ts) } },
                        getCurrentState = { _state.value.whatsNewTranslation },
                    )
            }

            DetailsAction.ToggleAboutTranslation -> {
                _state.update {
                    val current = it.aboutTranslation
                    it.copy(aboutTranslation = current.copy(isShowingTranslation = !current.isShowingTranslation))
                }
            }

            DetailsAction.ToggleWhatsNewTranslation -> {
                _state.update {
                    val current = it.whatsNewTranslation
                    it.copy(whatsNewTranslation = current.copy(isShowingTranslation = !current.isShowingTranslation))
                }
            }

            is DetailsAction.ShowLanguagePicker -> {
                _state.update {
                    it.copy(
                        isLanguagePickerVisible = true,
                        languagePickerTarget = action.target,
                    )
                }
            }

            DetailsAction.DismissLanguagePicker -> {
                _state.update {
                    it.copy(isLanguagePickerVisible = false, languagePickerTarget = null)
                }
            }

            DetailsAction.OpenWithExternalInstaller -> {
                openExternalInstaller()
            }

            DetailsAction.DismissExternalInstallerPrompt -> {
                _state.value =
                    _state.value.copy(
                        showExternalInstallerPrompt = false,
                        pendingInstallFilePath = null,
                    )
            }

            DetailsAction.InstallWithExternalApp -> {
                installViaExternalApp()
            }

            DetailsAction.OnNavigateBackClick -> {
                // Handled in composable
            }

            is DetailsAction.OpenDeveloperProfile -> {
                // Handled in composable
            }

            is DetailsAction.OnPlatformChipClick -> {
                if (action.platform == _state.value.devicePlatform) {
                    jumpToDeviceBuild()
                } else {
                    _state.update { it.copy(handoffPlatform = action.platform) }
                }
            }

            DetailsAction.OnJumpToDeviceBuild -> {
                jumpToDeviceBuild()
            }

            DetailsAction.OnDismissPlatformHandoff -> {
                _state.update { it.copy(handoffPlatform = null) }
            }

            is DetailsAction.OnShareAssetLink -> {
                runCatching { shareManager.shareText(action.assetUrl) }
                    .onFailure { logger.warn("Share asset link failed: ${it.message}") }
            }

            is DetailsAction.OnMessage -> {
                // Handled in composable
            }

            is DetailsAction.SelectDownloadAsset -> {
                val newPrimary = pickPrimaryInstalledApp(
                    apps = _state.value.installedApps,
                    primaryAssetName = action.release.name,
                    releaseAssets = _state.value.installableAssets,
                    releaseTag = _state.value.selectedRelease?.tagName,
                )
                _state.update { state ->
                    state.copy(
                        primaryAsset = action.release,
                        installedApp = newPrimary,
                    )
                }
                if (newPrimary != null) {
                    persistPreferredVariantOnPick(action.release)
                }
            }

            is DetailsAction.OnSelectInstalledApp -> {
                switchToInstalledApp(action.packageName)
            }

            DetailsAction.ToggleReleaseAssetsPicker -> {
                _state.update { state -> state.copy(isReleaseSelectorVisible = !state.isReleaseSelectorVisible) }
            }

            DetailsAction.UnpinPreferredVariant -> {
                unpinPreferredVariant()
            }

            DetailsAction.ToggleIncludeBetas -> {

                acknowledgeChannelChipCoachmark()
                toggleIncludeBetas()
            }

            DetailsAction.SwitchToStable -> {
                switchToStable()
            }

            DetailsAction.OnInspectApk -> {
                openApkInspectSheet()
            }

            DetailsAction.OnDismissApkInspect -> {
                _state.update {
                    it.copy(isApkInspectSheetVisible = false)
                }
            }

            DetailsAction.OnAcknowledgeApkInspectCoachmark -> {
                acknowledgeApkInspectCoachmark()
            }

            DetailsAction.OnAcknowledgeChannelChipCoachmark -> {
                acknowledgeChannelChipCoachmark()
            }

            is DetailsAction.OnToggleShowAllPlatforms -> {
                viewModelScope.launch {
                    try {
                        tweaksRepository.setShowAllPlatforms(action.enabled)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        logger.warn("Toggle show-all-platforms failed: ${e.message}")
                    }
                }
            }

            is DetailsAction.OnDownloadForTransfer -> {

                helper.openUrl(action.assetUrl) { err ->
                    logger.warn("Open transfer download failed: $err")
                }
            }
        }
    }

    private fun openApkInspectSheet() {
        val installed = _state.value.installedApp
        val parkedPath = installed?.pendingInstallFilePath
        val packageName = installed?.packageName
        val isPending = installed?.isPendingInstall == true
        if (installed == null && parkedPath == null) {
            logger.warn("openApkInspectSheet: nothing inspectable in current state")
            return
        }
        _state.update {
            it.copy(
                isApkInspectSheetVisible = true,
                isApkInspectLoading = true,
                apkInspection = null,
            )
        }
        viewModelScope.launch {
            val inspection =
                if (packageName != null && !isPending) {
                    apkInspector.inspectInstalled(packageName)
                        ?: parkedPath?.let { apkInspector.inspectFile(it) }
                } else if (parkedPath != null) {
                    apkInspector.inspectFile(parkedPath)
                        ?: packageName?.let { apkInspector.inspectInstalled(it) }
                } else if (packageName != null) {
                    apkInspector.inspectInstalled(packageName)
                } else {
                    null
                }
            _state.update {
                it.copy(
                    isApkInspectLoading = false,
                    apkInspection = inspection,
                )
            }

            acknowledgeApkInspectCoachmark()
        }
    }

    private fun acknowledgeApkInspectCoachmark() {
        if (!_state.value.isApkInspectCoachmarkPending) return
        _state.update { it.copy(isApkInspectCoachmarkPending = false) }
        viewModelScope.launch {
            runCatching { tweaksRepository.setApkInspectCoachmarkShown(true) }
                .onFailure { t ->
                    logger.warn("Failed to persist APK inspect coachmark flag: ${t.message}")
                }
        }
    }

    private fun acknowledgeChannelChipCoachmark() {

        _state.update { it.copy(isChannelChipCoachmarkPending = false) }
        viewModelScope.launch {
            runCatching { tweaksRepository.setChannelChipCoachmarkShown(true) }
                .onFailure { t ->
                    logger.warn("Failed to persist channel chip coachmark flag: ${t.message}")
                }
        }
    }

    private data class ReleaseInsights(
        val stalledStableSinceDays: Int?,
        val mergedChangelog: String?,
        val mergedChangelogBaseTag: String?,
        val latestStableHasInstallableAsset: Boolean,
    )

    @OptIn(ExperimentalTime::class)
    private fun computeReleaseInsights(
        allReleases: List<GithubRelease>,
        installedApp: InstalledApp?,
    ): ReleaseInsights {

        val (merged, mergedBase) =
            if (installedApp != null && allReleases.size > 1) {
                val installedTag = installedApp.installedVersion
                val newer =
                    allReleases.filter { release ->
                        VersionMath.isVersionNewer(release.tagName, installedTag)
                    }
                if (newer.size >= 2) {
                    val body =
                        newer.joinToString(separator = "\n\n") { release ->
                            val heading = "— ${release.tagName} —"
                            val notes = release.description?.trim().orEmpty()
                            if (notes.isEmpty()) heading else "$heading\n$notes"
                        }
                    body to installedTag
                } else {
                    null to null
                }
            } else {
                null to null
            }

        val latestStable =
            allReleases
                .filter { !it.isEffectivelyPreRelease() }
                .maxByOrNull { it.publishedAt }

        val stalledDays: Int? =
            run {
                val stable = latestStable ?: return@run null
                val preReleasesAfter =
                    allReleases.any { release ->
                        release.isEffectivelyPreRelease() &&
                                VersionMath.isVersionNewer(release.tagName, stable.tagName)
                    }
                if (!preReleasesAfter) return@run null
                val days = daysSinceIso(stable.publishedAt) ?: return@run null
                if (days >= STALLED_STABLE_THRESHOLD_DAYS) days else null
            }

        val latestStableHasInstallableAsset =
            latestStable?.assets?.any { installer.isAssetInstallable(it.name) } == true

        return ReleaseInsights(
            stalledStableSinceDays = stalledDays,
            mergedChangelog = merged,
            mergedChangelogBaseTag = mergedBase,
            latestStableHasInstallableAsset = latestStableHasInstallableAsset,
        )
    }

    private fun toggleIncludeBetas() {
        val app = _state.value.installedApp ?: return
        val newValue = !app.includePreReleases
        viewModelScope.launch {
            try {
                installedAppsRepository.setIncludePreReleases(
                    packageName = app.packageName,
                    enabled = newValue,
                )

                installedAppsRepository.checkForUpdates(app.packageName)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.warn("toggleIncludeBetas failed for ${app.packageName}: ${t.message}")
            }
        }
    }

    private fun switchToStable() {
        val stable = _state.value.latestStableRelease() ?: return

        val (_, primary) = recomputeAssetsForRelease(stable, _state.value.installedApp)
        if (primary == null) {
            logger.warn(
                "switchToStable: stable ${stable.tagName} has no installable asset; skipping",
            )
            return
        }
        onAction(DetailsAction.SelectRelease(stable))
        onAction(DetailsAction.InstallPrimary)
    }

    private fun persistPreferredVariantOnPick(picked: GithubAsset) {
        val installedApp = _state.value.installedApp ?: return
        val installable = _state.value.installableAssets
        val fingerprint =
            AssetVariant.fingerprintFromPickedAsset(
                pickedAssetName = picked.name,
                siblingAssetCount = installable.size,
                releaseTag = _state.value.selectedRelease?.tagName,
            ) ?: return

        val serializedTokens = AssetVariant.serializeTokens(fingerprint.tokens)
        val pickedIndex = installable.indexOfFirst { it.id == picked.id }.takeIf { it >= 0 }

        val currentVariant = installedApp.preferredAssetVariant
        val currentTokens = installedApp.preferredAssetTokens
        val currentGlob = installedApp.assetGlobPattern
        val newSiblingCount = installable.size.takeIf { it > 0 }
        val sameVariant =
            if (fingerprint.variant == null && currentVariant == null) {
                true
            } else {
                fingerprint.variant?.equals(currentVariant, ignoreCase = true) == true
            }
        val isSameFingerprint =
            sameVariant &&
                    serializedTokens == currentTokens &&
                    fingerprint.glob == currentGlob &&
                    pickedIndex == installedApp.pickedAssetIndex &&
                    newSiblingCount == installedApp.pickedAssetSiblingCount

        val isFirstPin =
            currentVariant.isNullOrBlank() &&
                    currentTokens.isNullOrBlank() &&
                    currentGlob.isNullOrBlank()

        val shouldSave = !isSameFingerprint || installedApp.preferredVariantStale
        if (!shouldSave) return

        viewModelScope.launch {
            try {
                installedAppsRepository.setPreferredVariant(
                    packageName = installedApp.packageName,
                    variant = fingerprint.variant,
                    tokens = serializedTokens,
                    glob = fingerprint.glob,
                    pickedIndex = pickedIndex,
                    siblingCount = newSiblingCount,
                )
                if (isFirstPin) {
                    val label = fingerprint.variant
                        ?: fingerprint.tokens.firstOrNull()
                        ?: ""
                    val message =
                        if (label.isNotEmpty()) {
                            getString(Res.string.variant_first_pin_toast, label)
                        } else {
                            getString(Res.string.variant_first_pin_toast_generic)
                        }
                    _events.send(DetailsEvent.OnMessage(message))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(
                    "Failed to persist preferred variant for " +
                            "${installedApp.packageName}: ${e.message}",
                )
            }
        }
    }

    private fun unpinPreferredVariant() {
        val installedApp = _state.value.installedApp ?: return
        viewModelScope.launch {
            try {
                installedAppsRepository.clearPreferredVariant(installedApp.packageName)
                _events.send(
                    DetailsEvent.OnMessage(getString(Res.string.variant_unpinned_toast)),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(
                    "Failed to clear preferred variant for " +
                            "${installedApp.packageName}: ${e.message}",
                )
            }
        }
    }

    private fun observeCurrentUserForBadge() {
        viewModelScope.launch {
            combine(
                userSessionRepository.getUser(),
                _state
                    .map { it.repository?.owner?.login }
                    .distinctUntilChanged(),
            ) { user, ownerLogin ->
                val login = user?.username
                login != null && ownerLogin != null && ownerLogin.equals(login, ignoreCase = true)
            }.collect { isOwner ->
                _state.update { it.copy(isCurrentUserOwner = isOwner) }
            }
        }
    }

    private fun observeApkInspectCoachmark() {
        viewModelScope.launch {
            val alreadyShown =
                runCatching { tweaksRepository.getApkInspectCoachmarkShown().first() }
                    .getOrDefault(true)
            if (alreadyShown) return@launch

            val firstStable = _state.first { !it.isLoading }
            val installedAtOpen =
                firstStable.installedApp?.isReallyInstalled() == true
            if (!installedAtOpen) return@launch
            _state.update { it.copy(isApkInspectCoachmarkPending = true) }
        }
    }

    private fun observeShowAllPlatforms() {
        viewModelScope.launch {
            tweaksRepository.getShowAllPlatforms().collect { enabled ->
                _state.update { it.copy(showAllPlatforms = enabled) }
            }
        }
    }

    private fun observeChannelChipCoachmark() {
        viewModelScope.launch {
            val alreadyShown =
                runCatching { tweaksRepository.getChannelChipCoachmarkShown().first() }
                    .getOrDefault(true)
            if (alreadyShown) return@launch

            val firstStable = _state.first { !it.isLoading }
            if (firstStable.installedApp == null) return@launch
            _state.update { it.copy(isChannelChipCoachmarkPending = true) }
        }
    }

    private fun retryReleases() {
        val repo = _state.value.repository ?: return
        if (_state.value.isRetryingReleases) return
        viewModelScope.launch {
            val prevCategory = _state.value.selectedReleaseCategory
            _state.update { it.copy(isRetryingReleases = true, releasesLoadFailed = false) }
            try {
                val releases =
                    detailsRepository.getAllReleases(
                        owner = repo.owner.login,
                        repo = repo.name,
                        defaultBranch = repo.defaultBranch,
                        sourceHost = sourceHostParam,
                    )

                val deviceBuildIds = deviceBuildReleaseIds(releases)
                val byPrevCategory =
                    releases.filter { it.id in deviceBuildIds }.firstInCategory(prevCategory)
                        ?: releases.firstInCategory(prevCategory)
                val selected = byPrevCategory
                    ?: releases.firstOrNull { !it.isEffectivelyPreRelease() }
                    ?: releases.firstOrNull()

                val resolvedCategory = when {
                    byPrevCategory != null -> prevCategory
                    selected?.isEffectivelyPreRelease() == true -> ReleaseCategory.PRE_RELEASE
                    else -> ReleaseCategory.STABLE
                }
                val (installable, primary) =
                    recomputeAssetsForRelease(selected, _state.value.installedApp)
                val newInstalledApp =
                    pickPrimaryInstalledApp(
                        _state.value.installedApps,
                        primary?.name,
                        installable,
                        releases,
                        selected?.tagName,
                    )
                val insights = computeReleaseInsights(releases, newInstalledApp)
                _state.update {
                    it.copy(
                        installedApp = newInstalledApp,
                        allReleases = releases,
                        releasePlatforms = platformsByRelease(releases),
                        deviceBuildReleaseIds = deviceBuildIds,
                        releaseLines = releaseLines(releases),
                        releasesLoadFailed = false,
                        isRetryingReleases = false,
                        selectedRelease = selected,
                        selectedReleaseCategory = resolvedCategory,
                        installableAssets = installable,
                        primaryAsset = primary,
                        stalledStableSinceDays = insights.stalledStableSinceDays,
                        mergedChangelog = insights.mergedChangelog,
                        mergedChangelogBaseTag = insights.mergedChangelogBaseTag,
                        latestStableHasInstallableAsset =
                            insights.latestStableHasInstallableAsset,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: RateLimitException) {
                _state.update {
                    it.copy(isRetryingReleases = false, releasesLoadFailed = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {

                logger.warn("Retry failed to load releases: ${t.message}")
                viewModelScope.launch {
                    _events.send(
                        DetailsEvent.OnMessage(
                            getString(Res.string.releases_unavailable_temporarily),
                        ),
                    )
                }
                _state.update {
                    it.copy(isRetryingReleases = false, releasesLoadFailed = true)
                }
            }
        }
    }

    private fun List<GithubRelease>.firstInCategory(category: ReleaseCategory): GithubRelease? =
        when (category) {
            ReleaseCategory.STABLE -> firstOrNull { !it.isEffectivelyPreRelease() }
            ReleaseCategory.PRE_RELEASE -> firstOrNull { it.isEffectivelyPreRelease() }
            ReleaseCategory.ALL -> firstOrNull()
        }

    private fun recomputeAssetsForRelease(
        release: GithubRelease?,
        installedAppOverride: InstalledApp? = _state.value.installedApp,
        anchorAssetName: String? =
            _state.value.primaryAsset?.name ?: installedAppOverride?.installedAssetName,
    ): Pair<List<GithubAsset>, GithubAsset?> {
        val releaseTag = release?.tagName
        val installable =
            release
                ?.assets
                ?.filter { asset ->
                    installer.isAssetInstallable(asset.name)
                }.orEmpty()
        val candidates =
            assetsOfSameApp(installable, releaseTag, installedAppOverride, anchorAssetName)

        val variantMatch = AssetVariant.resolvePreferredAsset(
            assets = candidates,
            pinnedVariant = installedAppOverride?.preferredAssetVariant,
            pinnedTokens = AssetVariant.deserializeTokens(installedAppOverride?.preferredAssetTokens),
            pinnedGlob = installedAppOverride?.assetGlobPattern,
            releaseTag = releaseTag,
        )
        val samePositionMatch =
            if (variantMatch == null && candidates.size == installable.size) {
                AssetVariant.resolveBySamePosition(
                    assets = installable,
                    originalIndex = installedAppOverride?.pickedAssetIndex,
                    siblingCountAtPickTime = installedAppOverride?.pickedAssetSiblingCount,
                )
            } else {
                null
            }
        val primary = variantMatch ?: samePositionMatch ?: installer.choosePrimaryAsset(candidates)
        return installable to primary
    }

    private fun assetsOfSameApp(
        installable: List<GithubAsset>,
        releaseTag: String?,
        installedApp: InstalledApp?,
        anchorAssetName: String?,
    ): List<GithubAsset> {
        val filter = AssetFilter.parse(installedApp?.assetFilterRegex)?.getOrNull()
        if (filter != null) {
            val filtered = installable.filter { filter.matches(it.name) }
            if (filtered.isNotEmpty()) return filtered
        }
        return AssetOwnership.narrowToApp(
            installable,
            anchorAssetName,
            releaseTag,
            tagOfAnchor(anchorAssetName, installedApp),
        )
    }

    // The release an anchor name came from: the selection it was picked in, or the installed,
    // latest or pending version the app recorded with that name.
    private fun tagOfAnchor(anchorAssetName: String?, app: InstalledApp?): String? =
        when {
            anchorAssetName == null -> null
            anchorAssetName == _state.value.primaryAsset?.name -> _state.value.selectedRelease?.tagName
            app == null -> null
            anchorAssetName == app.installedAssetName -> app.installedVersion
            anchorAssetName == app.latestAssetName -> app.latestVersion
            anchorAssetName == app.pendingInstallAssetName -> app.pendingInstallVersion
            else -> null
        }

    private fun pickPrimaryInstalledApp(
        apps: List<InstalledApp>,
        primaryAssetName: String?,
        releaseAssets: List<GithubAsset>,
        releaseHistory: List<GithubRelease> = _state.value.allReleases,
        releaseTag: String? = null,
    ): InstalledApp? {
        if (apps.isEmpty()) return null
        if (primaryAssetName == null) {
            return apps.singleOrNull() ?: apps.firstOrNull { !it.isUpdateAvailable } ?: apps.first()
        }
        return AssetOwnership.ownerOf(primaryAssetName, apps, releaseAssets, releaseHistory, releaseTag)
    }

    private fun switchToInstalledApp(packageName: String) {
        val current = _state.value
        val app = current.installedApps.firstOrNull { it.packageName == packageName } ?: return
        val anchor = app.installedAssetName ?: app.latestAssetName ?: app.pendingInstallAssetName
        val category = current.selectedReleaseCategory
        val inCategory =
            current.allReleases.firstOwnedBy(app, category, current.installedApps, anchor)
        val release =
            inCategory
                ?: current.allReleases.firstOwnedBy(
                    app,
                    ReleaseCategory.ALL,
                    current.installedApps,
                    anchor,
                )
                ?: return
        val resolvedCategory =
            when {
                inCategory != null -> category
                release.isEffectivelyPreRelease() -> ReleaseCategory.PRE_RELEASE
                else -> ReleaseCategory.STABLE
            }
        val (installable, primary) = recomputeAssetsForRelease(release, app, anchor)
        val insights = computeReleaseInsights(current.allReleases, app)
        whatsNewTranslationJob?.cancel()

        _state.update {
            it.copy(
                installedApp = app,
                selectedRelease = release,
                selectedReleaseCategory = resolvedCategory,
                installableAssets = installable,
                primaryAsset = primary,
                isVersionPickerVisible = false,
                whatsNewTranslation = TranslationState(),
                whatsNewMeasuredHeightPx = null,
                mergedChangelog = insights.mergedChangelog,
                mergedChangelogBaseTag = insights.mergedChangelogBaseTag,
                stalledStableSinceDays = insights.stalledStableSinceDays,
                latestStableHasInstallableAsset = insights.latestStableHasInstallableAsset,
            )
        }
    }

    private fun List<GithubRelease>.firstOwnedBy(
        app: InstalledApp,
        category: ReleaseCategory,
        repoApps: List<InstalledApp>,
        anchorAssetName: String?,
    ): GithubRelease? =
        firstOrNull { release ->
            val inCategory =
                when (category) {
                    ReleaseCategory.STABLE -> !release.isEffectivelyPreRelease()
                    ReleaseCategory.PRE_RELEASE -> release.isEffectivelyPreRelease()
                    ReleaseCategory.ALL -> true
                }
            if (!inCategory) return@firstOrNull false
            val (installable, primary) = recomputeAssetsForRelease(release, app, anchorAssetName)
            primary != null &&
                pickPrimaryInstalledApp(repoApps, primary.name, installable, this, release.tagName)?.packageName ==
                app.packageName
        }

    private fun observeInstalledApp(repoId: Long) {
        viewModelScope.launch {
            installedAppsRepository
                .getAppsByRepoIdAsFlow(repoId)
                .distinctUntilChanged()
                .collect { apps ->

                    val primaryAssetName = _state.value.primaryAsset?.name
                    val releaseAssets = _state.value.installableAssets
                    val primary =
                        if (primaryAssetName == null && !packageNameParam.isNullOrBlank()) {
                            apps.firstOrNull { it.packageName == packageNameParam }
                                ?: pickPrimaryInstalledApp(apps, null, releaseAssets)
                        } else {
                            pickPrimaryInstalledApp(
                                apps,
                                primaryAssetName,
                                releaseAssets,
                                releaseTag = _state.value.selectedRelease?.tagName,
                            )
                        }

                    val insights = computeReleaseInsights(_state.value.allReleases, primary)
                    _state.update {
                        it.copy(
                            installedApp = primary,
                            installedApps = apps,
                            mergedChangelog = insights.mergedChangelog,
                            mergedChangelogBaseTag = insights.mergedChangelogBaseTag,
                            stalledStableSinceDays = insights.stalledStableSinceDays,
                            latestStableHasInstallableAsset =
                                insights.latestStableHasInstallableAsset,
                        )
                    }
                }
        }
    }

    // TODO: drives [downloader] directly instead of [downloadOrchestrator], so a download
    //  started here stays invisible in the library. Left alone deliberately: the user is on
    //  this screen while it runs, and unifying it needs the stage-vocabulary cleanup.
    private fun installViaExternalApp() {
        currentDownloadJob?.cancel()
        val job = viewModelScope.launch {
            try {
                val primary = _state.value.primaryAsset
                val release = _state.value.selectedRelease

                if (primary != null && release != null) {
                    currentAssetName = primary.name

                    appendLog(
                        assetName = primary.name,
                        size = primary.size,
                        tag = release.tagName,
                        result = LogResult.DownloadStarted,
                    )

                    _state.value =
                        _state.value.copy(
                            downloadError = null,
                            installError = null,
                            downloadProgressPercent = null,
                            downloadStage = DownloadStage.DOWNLOADING,
                        )

                    downloader
                        .download(primary.downloadUrl, primary.name)
                        .collect { p ->
                            _state.value =
                                _state.value.copy(downloadProgressPercent = p.percent)
                            if (p.percent == 100) {
                                _state.value =
                                    _state.value.copy(downloadStage = DownloadStage.VERIFYING)
                            }
                        }

                    val filePath =
                        downloader.getDownloadedFilePath(primary.name)
                            ?: throw IllegalStateException("Downloaded file not found")

                    appendLog(
                        assetName = primary.name,
                        size = primary.size,
                        tag = release.tagName,
                        result = LogResult.Downloaded,
                    )

                    _state.value = _state.value.copy(downloadStage = DownloadStage.IDLE)
                    currentAssetName = null

                    installer.openWithExternalInstaller(filePath)

                    appendLog(
                        assetName = primary.name,
                        size = primary.size,
                        tag = release.tagName,
                        result = LogResult.OpenedInExternalInstaller,
                    )
                }
            } catch (e: CancellationException) {
                logger.debug("Install with external app cancelled")
                _state.value = _state.value.copy(downloadStage = DownloadStage.IDLE)
                currentAssetName = null
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Failed to install with external app: ${t.message}")
                _state.value =
                    _state.value.copy(
                        downloadStage = DownloadStage.IDLE,
                        installError = t.message,
                    )
                currentAssetName = null

                _state.value.primaryAsset?.let { asset ->
                    _state.value.selectedRelease?.let { release ->
                        appendLog(
                            assetName = asset.name,
                            size = asset.size,
                            tag = release.tagName,
                            result = Error(t.message),
                        )
                    }
                }
            }
        }

        currentDownloadJob = job

        _state.update {
            it.copy(isInstallDropdownExpanded = false)
        }
    }

    private fun openExternalInstaller() {
        val filePath = _state.value.pendingInstallFilePath
        if (filePath != null) {
            try {
                installer.openWithExternalInstaller(filePath)
                _state.value.primaryAsset?.let { asset ->
                    _state.value.selectedRelease?.let { release ->
                        appendLog(
                            assetName = asset.name,
                            size = asset.size,
                            tag = release.tagName,
                            result = LogResult.OpenedInExternalInstaller,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Failed to open with external installer: ${t.message}")
                _state.value = _state.value.copy(installError = t.message)
            }
        }
        _state.value =
            _state.value.copy(
                showExternalInstallerPrompt = false,
                pendingInstallFilePath = null,
            )
    }

    private fun selectReleaseCategory(action: DetailsAction.SelectReleaseCategory) {
        val newCategory = action.category
        val filtered =
            when (newCategory) {
                ReleaseCategory.STABLE -> _state.value.allReleases.filter { !it.isEffectivelyPreRelease() }
                ReleaseCategory.PRE_RELEASE -> _state.value.allReleases.filter { it.isEffectivelyPreRelease() }
                ReleaseCategory.ALL -> _state.value.allReleases
            }
        val deviceBuildIds = _state.value.deviceBuildReleaseIds
        val newSelected = filtered.firstOrNull { it.id in deviceBuildIds } ?: filtered.firstOrNull()
        val (installable, primary) = recomputeAssetsForRelease(newSelected)
        val newInstalledApp =
            pickPrimaryInstalledApp(
                _state.value.installedApps,
                primary?.name,
                installable,
                releaseTag = newSelected?.tagName,
            )

        whatsNewTranslationJob?.cancel()
        _state.update {
            it.copy(
                installedApp = newInstalledApp,
                selectedReleaseCategory = newCategory,
                selectedRelease = newSelected,
                installableAssets = installable,
                primaryAsset = primary,
                whatsNewTranslation = TranslationState(),
            )
        }
    }

    private fun jumpToDeviceBuild() {
        val current = _state.value
        val deviceBuildIds = current.deviceBuildReleaseIds
        if (current.selectedRelease?.id in deviceBuildIds) return
        val selectedLine = current.selectedRelease?.let { current.releaseLines[it.id] }
        val targets = current.allReleases.filter {
            it.id in deviceBuildIds && (selectedLine == null || current.releaseLines[it.id] == selectedLine)
        }
        val inCategory = targets.firstInCategory(current.selectedReleaseCategory)
        when {
            inCategory != null -> onAction(DetailsAction.SelectRelease(inCategory))
            targets.isNotEmpty() -> {
                onAction(DetailsAction.SelectReleaseCategory(ReleaseCategory.ALL))
                onAction(DetailsAction.SelectRelease(targets.first()))
            }
        }
    }

    private fun releaseLines(releases: List<GithubRelease>): ImmutableMap<Long, String> =
        if (ReleaseLines.isMultiLine(releases)) {
            releases.associate { it.id to ReleaseLines.of(it.tagName) }.toImmutableMap()
        } else {
            persistentMapOf()
        }

    private fun platformsByRelease(
        releases: List<GithubRelease>,
    ): ImmutableMap<Long, Set<DiscoveryPlatform>> =
        releases.associate { it.id to it.platforms() }.toImmutableMap()

    private fun deviceBuildReleaseIds(releases: List<GithubRelease>): ImmutableSet<Long> =
        releases
            .filter { release -> release.assets.any { installer.isAssetInstallable(it.name) } }
            .map { it.id }
            .toImmutableSet()

    // TODO: same bypass as [installViaExternalApp]: [downloader] directly, so the library never
    //  sees this download. See the note there.
    private fun openAppManager() {
        // Tracked and superseded like the sibling path: untracked, it left the mirror's guard
        // open, and a leftover observer would keep writing this screen's stage alongside it.
        currentDownloadJob?.cancel()
        currentDownloadJob = viewModelScope.launch {
            try {
                val primary = _state.value.primaryAsset
                val release = _state.value.selectedRelease

                if (primary != null && release != null) {
                    currentAssetName = primary.name

                    appendLog(
                        assetName = primary.name,
                        size = primary.size,
                        tag = release.tagName,
                        result = LogResult.PreparingForAppManager,
                    )

                    _state.value =
                        _state.value.copy(
                            downloadError = null,
                            installError = null,
                            downloadProgressPercent = null,
                            downloadStage = DownloadStage.DOWNLOADING,
                        )

                    downloader.download(primary.downloadUrl, primary.name).collect { p ->
                        _state.value =
                            _state.value.copy(downloadProgressPercent = p.percent)
                        if (p.percent == 100) {
                            _state.value =
                                _state.value.copy(downloadStage = DownloadStage.VERIFYING)
                        }
                    }

                    val filePath =
                        downloader.getDownloadedFilePath(primary.name)
                            ?: throw IllegalStateException("Downloaded file not found")

                    appendLog(
                        assetName = primary.name,
                        size = primary.size,
                        tag = release.tagName,
                        result = LogResult.Downloaded,
                    )

                    _state.value = _state.value.copy(downloadStage = DownloadStage.IDLE)
                    currentAssetName = null

                    installer.openInAppManager(
                        filePath = filePath,
                        onOpenInstaller = {
                            viewModelScope.launch {
                                _events.send(
                                    DetailsEvent.OnOpenRepositoryInApp(APP_MANAGER_REPO_ID),
                                )
                            }
                        },
                    )

                    appendLog(
                        assetName = primary.name,
                        size = primary.size,
                        tag = release.tagName,
                        result = LogResult.OpenedInAppManager,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Failed to open in AppManager: ${t.message}")
                _state.value =
                    _state.value.copy(
                        downloadStage = DownloadStage.IDLE,
                        installError = t.message,
                    )
                currentAssetName = null

                _state.value.primaryAsset?.let { asset ->
                    _state.value.selectedRelease?.let { release ->
                        appendLog(
                            assetName = asset.name,
                            size = asset.size,
                            tag = release.tagName,
                            result = Error(t.message),
                        )
                    }
                }
            }
        }
        _state.update {
            it.copy(isInstallDropdownExpanded = false)
        }
    }

    private fun openObtainium() {
        val repo = _state.value.repository
        repo?.owner?.login?.let {
            installer.openInObtainium(
                repoOwner = it,
                repoName = repo.name,
                onOpenInstaller = {
                    viewModelScope.launch {
                        _events.send(
                            DetailsEvent.OnOpenRepositoryInApp(OBTAINIUM_REPO_ID),
                        )
                    }
                },
            )
        }
        _state.update {
            it.copy(isInstallDropdownExpanded = false)
        }
    }

    private fun openApp() {
        val installedApp = _state.value.installedApp ?: return
        val launched = installer.openApp(installedApp.packageName)
        if (!launched) {
            viewModelScope.launch {
                _events.send(
                    DetailsEvent.OnMessage(
                        getString(
                            Res.string.failed_to_open_app,
                            installedApp.appName,
                        ),
                    ),
                )
            }
        }
    }

    private fun update() {
        val installedApp = _state.value.installedApp
        val selectedRelease = _state.value.selectedRelease

        if (installedApp != null && selectedRelease != null && installedApp.isUpdateAvailable) {
            val latestAsset =
                _state.value.primaryAsset
                    ?: _state.value.installableAssets.firstOrNull {
                        it.name == installedApp.latestAssetName
                    }
                    ?: _state.value.installableAssets.firstOrNull {
                        it.name == installedApp.installedAssetName
                    }

            if (latestAsset != null) {
                installAsset(
                    downloadUrl = latestAsset.downloadUrl,
                    assetName = latestAsset.name,
                    sizeBytes = latestAsset.size,
                    releaseTag = selectedRelease.tagName,
                    isUpdate = true,
                )
            }
        }
    }

    private fun share() {
        viewModelScope.launch {
            _state.value.repository?.let { repo ->
                runCatching {
                    shareManager.shareText("https://github-store.org/app?repo=${repo.fullName}")
                }.onFailure { t ->
                    logger.error("Failed to share link: ${t.message}")
                    _events.send(
                        DetailsEvent.OnMessage(getString(Res.string.failed_to_share_link)),
                    )
                    return@launch
                }

                if (platform != Platform.ANDROID) {
                    _events.send(DetailsEvent.OnMessage(getString(Res.string.link_copied_to_clipboard)))
                }
            }
        }
    }

    private fun toggleFavourite() {
        viewModelScope.launch {
            try {
                val repo = _state.value.repository ?: return@launch
                val selectedRelease = _state.value.selectedRelease

                val favoriteRepo =
                    FavoriteRepo(
                        repoId = repo.id,
                        repoName = repo.name,
                        repoOwner = repo.owner.login,
                        repoOwnerAvatarUrl = repo.owner.avatarUrl,
                        repoDescription = repo.description,
                        primaryLanguage = repo.language,
                        repoUrl = repo.htmlUrl,
                        latestVersion = selectedRelease?.tagName,
                        latestReleaseUrl = selectedRelease?.htmlUrl,
                        addedAt = System.now().toEpochMilliseconds(),
                        lastSyncedAt = System.now().toEpochMilliseconds(),
                    )

                favouritesRepository.toggleFavorite(favoriteRepo)

                val newFavoriteState = favouritesRepository.isFavoriteSync(repo.id)
                _state.value = _state.value.copy(isFavourite = newFavoriteState)

                _events.send(
                    element =
                        DetailsEvent.OnMessage(
                            message =
                                getString(
                                    resource =
                                        if (newFavoriteState) {
                                            Res.string.added_to_favourites
                                        } else {
                                            Res.string.removed_from_favourites
                                        },
                                ),
                        ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Failed to toggle favorite: ${t.message}")
            }
        }
    }

    private fun toggleStar() {
        viewModelScope.launch {
            val repo = _state.value.repository ?: return@launch
            if (!userSessionRepository.isCurrentlyUserLoggedIn()) {
                _events.send(DetailsEvent.OnMessage(getString(Res.string.star_sign_in_required)))
                return@launch
            }
            val target = !_state.value.isStarred
            _state.update { it.copy(isStarred = target) }
            starredRepository
                .setStarred(repo.owner.login, repo.name, target)
                .onSuccess {
                    _events.send(
                        DetailsEvent.OnMessage(
                            getString(if (target) Res.string.star_added else Res.string.star_removed),
                        ),
                    )
                    runCatching { starredRepository.syncStarredRepos(forceRefresh = true) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isStarred = !target) }
                    _events.send(
                        DetailsEvent.OnMessage(e.message ?: getString(Res.string.failed_to_load)),
                    )
                }
        }
    }

    // Pause keeps the download on screen: the orchestrator parks it as Paused with its bytes intact,
    // and the entry observer stays alive across the pause so a resumed run still installs once it
    // reaches AwaitingInstall. Pausing is not a cancel, so nothing is logged.
    private fun pauseCurrentDownload() {
        val packageKey = orchestratorKey()
        viewModelScope.launch {
            try {
                downloadOrchestrator.cancel(packageKey)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Failed to pause orchestrator download: ${t.message}")
            }
        }
    }

    private fun resumeCurrentDownload() {
        val packageKey = orchestratorKey()
        viewModelScope.launch {
            try {
                downloadOrchestrator.resume(packageKey)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Failed to resume orchestrator download: ${t.message}")
            }
        }
    }

    // "Delete task" drops the bytes and the entry, never the installed app. It is the old ✕'s
    // successor, so it keeps that action's Cancelled log line.
    private fun discardCurrentDownload() {
        currentDownloadJob?.cancel()
        currentDownloadJob = null

        val packageKey = orchestratorKey()
        viewModelScope.launch {
            try {
                downloadOrchestrator.discard(packageKey)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Failed to discard orchestrator download: ${t.message}")
            }
        }

        val assetName = currentAssetName
        if (assetName != null) {
            val releaseTag = _state.value.selectedRelease?.tagName ?: ""
            val totalSize = _state.value.totalBytes ?: _state.value.downloadedBytes
            appendLog(
                assetName = assetName,
                tag = releaseTag,
                size = totalSize,
                result = LogResult.Cancelled,
            )
            logger.debug("Download discarded via orchestrator: $assetName")
        }

        currentAssetName = null
        _state.value =
            _state.value.copy(
                isDownloading = false,
                downloadProgressPercent = null,
                downloadStage = DownloadStage.IDLE,
            )
    }

    private fun uninstallApp() {
        val installedApp = _state.value.installedApp ?: return
        uninstallPackage(installedApp.packageName)
    }

    private fun uninstallPackage(packageName: String) {
        logger.debug("Uninstalling app: $packageName")
        viewModelScope.launch {
            try {
                installer.uninstall(packageName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to request uninstall for $packageName: ${e.message}")
                _events.send(
                    DetailsEvent.OnMessage(
                        getString(Res.string.failed_to_uninstall, packageName),
                    ),
                )
            }
        }
    }

    private fun install(ignoreDowngrade: Boolean = false) {
        val primary = _state.value.primaryAsset
        val release = _state.value.selectedRelease
        val installedApp = _state.value.installedApp

        if (primary != null && release != null) {
            if (!ignoreDowngrade &&
                installedApp != null &&
                !installedApp.isPendingInstall &&
                VersionHelper.normalizeVersion(release.tagName) !=
                VersionHelper.normalizeVersion(
                    installedApp.installedVersion,
                ) &&
                platform == Platform.ANDROID
            ) {
                val isDowngrade =
                    VersionHelper.isDowngradeVersion(
                        candidate = release.tagName,
                        current = installedApp.installedVersion,
                        allReleases = _state.value.allReleases,
                    )

                if (isDowngrade) {
                    _state.update {
                        it.copy(
                            downgradeWarning =
                                DowngradeWarning(
                                    packageName = installedApp.packageName,
                                    currentVersion = installedApp.installedVersion,
                                    targetVersion = release.tagName,
                                ),
                        )
                    }
                    return
                }
            }

            installAsset(
                downloadUrl = primary.downloadUrl,
                assetName = primary.name,
                sizeBytes = primary.size,
                releaseTag = release.tagName,
            )
        }
    }

    private fun overrideSigningKeyWarning() {
        val warning = _state.value.signingKeyWarning ?: return
        _state.update { it.copy(signingKeyWarning = null) }
        dismissDowngradeWarning()
        viewModelScope.launch {
            try {
                val ext = warning.pendingAssetName.substringAfterLast('.', "").lowercase()

                val gatePackageName =
                    if (platform == Platform.ANDROID) warning.pendingApkInfo.packageName else null
                if (gatePackageName != null) {
                    systemInstallSerializer.awaitFreeAndMarkPending(gatePackageName)
                }
                val installOutcome =
                    try {
                        installer.install(warning.pendingFilePath, ext)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        if (gatePackageName != null) {
                            systemInstallSerializer.markCompleted(gatePackageName)
                        }
                        throw e
                    }

                if (platform == Platform.ANDROID) {
                    saveInstalledAppToDatabase(
                        apkInfo = warning.pendingApkInfo,
                        assetName = warning.pendingAssetName,
                        assetUrl = warning.pendingDownloadUrl,
                        assetSize = warning.pendingSizeBytes,
                        releaseTag = warning.pendingReleaseTag,
                        isUpdate = warning.pendingIsUpdate,
                        installOutcome = installOutcome,
                    )
                }

                _state.value = _state.value.copy(downloadStage = DownloadStage.IDLE)
                currentAssetName = null
                appendLog(
                    assetName = warning.pendingAssetName,
                    size = warning.pendingSizeBytes,
                    tag = warning.pendingReleaseTag,
                    result = if (warning.pendingIsUpdate) LogResult.Updated else LogResult.Installed,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Install after override failed: ${t.message}")
                _state.value =
                    _state.value.copy(
                        downloadStage = DownloadStage.IDLE,
                        installError = t.message,
                    )
                currentAssetName = null
            }
        }
    }

    private fun dismissDowngradeWarning() {
        _state.update {
            it.copy(
                downgradeWarning = null,
            )
        }
    }

    private fun installAsset(
        downloadUrl: String,
        assetName: String,
        sizeBytes: Long,
        releaseTag: String,
        isUpdate: Boolean = false,
    ) {

        currentDownloadJob?.cancel()
        val packageKey = orchestratorKey()
        val asset = _state.value.primaryAsset
        val repository = _state.value.repository
        if (asset == null || repository == null) {
            logger.warn("installAsset called with missing primaryAsset/repository")
            return
        }
        currentAssetName = assetName

        val parkedFilePath = parkedFilePathIfMatches(releaseTag, assetName)
        if (parkedFilePath != null) {
            logger.debug("Reusing parked file for $releaseTag / $assetName")
            currentDownloadJob = viewModelScope.launch {
                try {
                    appendLog(
                        assetName = assetName,
                        size = sizeBytes,
                        tag = releaseTag,
                        result = LogResult.Downloaded,
                    )
                    installAsset(
                        isUpdate = isUpdate,
                        filePath = parkedFilePath,
                        assetName = assetName,
                        downloadUrl = downloadUrl,
                        sizeBytes = sizeBytes,
                        releaseTag = releaseTag,
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    logger.error("Install of parked file failed: ${t.message}")
                    _state.value =
                        _state.value.copy(
                            downloadStage = DownloadStage.IDLE,
                            installError = t.message,
                        )
                    currentAssetName = null
                    appendLog(
                        assetName = assetName,
                        size = sizeBytes,
                        tag = releaseTag,
                        result = Error(t.message),
                    )
                }
            }
            return
        }

        appendLog(
            assetName = assetName,
            size = sizeBytes,
            tag = releaseTag,
            result =
                if (isUpdate) {
                    LogResult.UpdateStarted
                } else {
                    LogResult.DownloadStarted
                },
        )

        currentDownloadJob = viewModelScope.launch {
            try {
                val installerType =
                    try {
                        tweaksRepository.getInstallerType().first()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        InstallerType.DEFAULT
                    }
                val policy =
                    when {
                        platform != Platform.ANDROID -> InstallPolicy.AlwaysInstall
                        installerType == InstallerType.SHIZUKU -> InstallPolicy.AlwaysInstall
                        installerType == InstallerType.DHIZUKU -> InstallPolicy.AlwaysInstall
                        else -> InstallPolicy.InstallWhileForeground
                    }

                downloadOrchestrator.enqueue(
                    DownloadSpec(
                        packageName = packageKey,
                        repoOwner = repository.owner.login,
                        repoName = repository.name,
                        repoOwnerAvatarUrl = repository.owner.avatarUrl,
                        asset = asset,
                        displayAppName = repository.name,
                        installPolicy = policy,
                        releaseTag = releaseTag,
                    ),
                )

                _state.value =
                    _state.value.copy(
                        downloadError = null,
                        installError = null,
                        downloadProgressPercent = null,
                        downloadStage = DownloadStage.DOWNLOADING,
                        downloadedBytes = 0L,
                        totalBytes = sizeBytes,
                        attestationStatus = AttestationStatus.UNCHECKED,
                    )

                observeOrchestratorEntry(
                    packageKey = packageKey,
                    downloadUrl = downloadUrl,
                    assetName = assetName,
                    sizeBytes = sizeBytes,
                    releaseTag = releaseTag,
                    isUpdate = isUpdate,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Install failed: ${t.message}")
                t.printStackTrace()
                _state.value =
                    _state.value.copy(
                        downloadStage = DownloadStage.IDLE,
                        installError = t.message,
                    )
                currentAssetName = null
                appendLog(
                    assetName = assetName,
                    size = sizeBytes,
                    tag = releaseTag,
                    result = Error(t.message),
                )
            }
        }
    }

    private fun parkedFilePathIfMatches(
        releaseTag: String,
        assetName: String,
    ): String? {
        val installedApp = _state.value.installedApp ?: return null
        val parkedPath = installedApp.pendingInstallFilePath ?: return null
        val parkedVersion = installedApp.pendingInstallVersion ?: return null
        val parkedAsset = installedApp.pendingInstallAssetName ?: return null
        if (parkedVersion != releaseTag) return null
        if (parkedAsset != assetName) return null

        return try {
            val file = File(parkedPath)
            if (file.exists() && file.length() > 0) parkedPath else null
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            logger.warn("Failed to stat parked install file: ${t.message}")
            null
        }
    }

    private fun orchestratorKey(state: RawDetailsState = _state.value): String {
        val packageName = state.installedApp?.packageName
        if (packageName != null) return packageName
        val owner = state.repository?.owner?.login ?: return NO_ORCHESTRATOR_KEY
        val name = state.repository?.name ?: return NO_ORCHESTRATOR_KEY
        return "$owner/$name"
    }

    private fun observeOrchestratorForDisplay() {
        viewModelScope.launch {
            combine(
                downloadOrchestrator.downloads,
                _state.map { orchestratorKey(it) }.distinctUntilChanged(),
                localDownloadJob,
            ) { downloads, packageKey, job -> Triple(downloads[packageKey], packageKey, job) }
                .collect { (entry, packageKey, job) ->
                    if (packageKey == NO_ORCHESTRATOR_KEY) return@collect
                    if (job?.isActive == true) return@collect
                    mirrorOrchestratorStage(entry)
                }
        }
    }

    private fun mirrorOrchestratorStage(entry: OrchestratedDownload?) {
        _state.update { current ->
            when (entry?.stage) {
                // A Queued entry carries a null percent on purpose, and it is left null here: the
                // screen renders that as indeterminate, whereas falling back to the previous value
                // would show a finished run's percentage over a download that has not started.
                OrchestratorStage.Queued,
                OrchestratorStage.Downloading,
                -> current.copy(
                    downloadStage = DownloadStage.DOWNLOADING,
                    downloadProgressPercent = entry.progressPercent,
                    downloadedBytes = entry.bytesDownloaded,
                    totalBytes = entry.totalBytes ?: current.totalBytes,
                )

                // Paused holds the bar where it stopped and the bytes it reached, so the screen
                // reads as a download waiting to continue rather than a finished one.
                OrchestratorStage.Paused -> current.copy(
                    downloadStage = DownloadStage.PAUSED,
                    downloadProgressPercent = entry.progressPercent,
                    downloadedBytes = entry.bytesDownloaded,
                    totalBytes = entry.totalBytes ?: current.totalBytes,
                )

                OrchestratorStage.Installing ->
                    current.copy(downloadStage = DownloadStage.INSTALLING)

                // Nothing live for this package: it parked, finished or was cancelled while the
                // screen was gone. A stale progress bar is worse than a resting one, and the byte
                // counts belong to the run that just ended, so they go with it.
                null,
                OrchestratorStage.AwaitingInstall,
                OrchestratorStage.Completed,
                OrchestratorStage.Cancelled,
                OrchestratorStage.Failed,
                -> {
                    val isResting =
                        current.downloadStage == DownloadStage.IDLE &&
                            current.downloadProgressPercent == null &&
                            current.totalBytes == null
                    if (isResting) {
                        current
                    } else {
                        current.copy(
                            downloadStage = DownloadStage.IDLE,
                            downloadProgressPercent = null,
                            downloadedBytes = 0L,
                            totalBytes = null,
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun observeOrchestratorEntry(
        packageKey: String,
        downloadUrl: String,
        assetName: String,
        sizeBytes: Long,
        releaseTag: String,
        isUpdate: Boolean,
    ) {
        var installFired = false
        downloadOrchestrator
            .observe(packageKey)
            // The settling entry is emitted and then the flow completes, so this job ends. It
            // matters beyond tidiness: this job is what holds the display mirror down, and an
            // observer that outlives the download would keep the mirror disabled for the rest of
            // the ViewModel's life. Paused is the one exception: it keeps collecting so a resumed
            // run still installs on AwaitingInstall instead of parking silently.
            .transformWhile { entry ->
                emit(entry)
                entry == null ||
                    entry.stage.phase == DownloadStagePhase.Live ||
                    entry.stage.phase == DownloadStagePhase.Installing ||
                    entry.stage == OrchestratorStage.Paused
            }
            .collect { entry ->
            if (entry == null) {

                if (_state.value.downloadStage != DownloadStage.IDLE) {
                    _state.value =
                        _state.value.copy(
                            downloadStage = DownloadStage.IDLE,
                            downloadProgressPercent = null,
                        )
                }
                currentAssetName = null
                return@collect
            }

            _state.value =
                _state.value.copy(
                    downloadProgressPercent = entry.progressPercent,
                    downloadedBytes = entry.bytesDownloaded,
                    totalBytes = entry.totalBytes ?: sizeBytes,
                )

            when (entry.stage) {
                OrchestratorStage.Queued -> {

                    _state.value = _state.value.copy(downloadStage = DownloadStage.DOWNLOADING)
                }

                OrchestratorStage.Downloading -> {
                    _state.value = _state.value.copy(downloadStage = DownloadStage.DOWNLOADING)
                }

                OrchestratorStage.Installing -> {
                    _state.value = _state.value.copy(downloadStage = DownloadStage.INSTALLING)
                }

                OrchestratorStage.AwaitingInstall -> {

                    if (installFired) return@collect
                    installFired = true
                    val filePath = entry.filePath ?: return@collect
                    _state.value =
                        _state.value.copy(downloadStage = DownloadStage.VERIFYING)
                    appendLog(
                        assetName = assetName,
                        size = sizeBytes,
                        tag = releaseTag,
                        result = LogResult.Downloaded,
                    )

                    try {
                        installAsset(
                            isUpdate = isUpdate,
                            filePath = filePath,
                            assetName = assetName,
                            downloadUrl = downloadUrl,
                            sizeBytes = sizeBytes,
                            releaseTag = releaseTag,
                        )

                        downloadOrchestrator.dismiss(packageKey)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        logger.error("Foreground install failed: ${t.message}")
                        _state.value =
                            _state.value.copy(
                                downloadStage = DownloadStage.IDLE,
                                installError = t.message,
                            )
                        appendLog(
                            assetName = assetName,
                            size = sizeBytes,
                            tag = releaseTag,
                            result = Error(t.message),
                        )
                    }
                }

                OrchestratorStage.Completed -> {
                    val resolvedOutcome = entry.installOutcome ?: InstallOutcome.COMPLETED
                    val isCompleted = resolvedOutcome == InstallOutcome.COMPLETED

                    _state.value = _state.value.copy(downloadStage = DownloadStage.IDLE)
                    currentAssetName = null
                    appendLog(
                        assetName = assetName,
                        size = sizeBytes,
                        tag = releaseTag,
                        result = when {
                            !isCompleted -> LogResult.Downloaded
                            isUpdate -> LogResult.Updated
                            else -> LogResult.Installed
                        },
                    )

                    if (platform == Platform.ANDROID) {
                        val filePath = entry.filePath
                        if (filePath != null) {
                            runCatching {
                                val validation = installationManager.validateApk(
                                    filePath = filePath,
                                    isUpdate = isUpdate,
                                    trackedPackageName = _state.value.installedApp?.packageName,
                                )
                                if (validation is ApkValidationResult.Valid) {
                                    saveInstalledAppToDatabase(
                                        apkInfo = validation.apkInfo,
                                        assetName = assetName,
                                        assetUrl = downloadUrl,
                                        assetSize = sizeBytes,
                                        releaseTag = releaseTag,
                                        isUpdate = isUpdate,
                                        installOutcome = resolvedOutcome,
                                        parkedFilePath = filePath,
                                    )
                                } else {
                                    logger.warn(
                                        "Orchestrator install settled (outcome=$resolvedOutcome) " +
                                                "but APK validation failed: $validation",
                                    )
                                }
                            }.onFailure { t ->
                                logger.error("Failed to persist orchestrator install: ${t.message}")
                            }
                        } else {
                            logger.warn(
                                "Orchestrator install settled (outcome=$resolvedOutcome) " +
                                        "but filePath is null; DB not updated",
                            )
                        }
                    }

                    if (isCompleted) {
                        downloadOrchestrator.dismiss(packageKey)
                    }
                    return@collect
                }

                // Paused is a first-class state on this screen now: the bar and the bytes stay as
                // the entry carries them, and the resume button restarts the same run. The observer
                // above stays alive across the pause, so pausing logs nothing.
                OrchestratorStage.Paused -> {
                    _state.value = _state.value.copy(downloadStage = DownloadStage.PAUSED)
                }

                OrchestratorStage.Cancelled -> {
                    _state.value =
                        _state.value.copy(
                            downloadStage = DownloadStage.IDLE,
                            downloadProgressPercent = null,
                        )
                    currentAssetName = null
                    appendLog(
                        assetName = assetName,
                        size = sizeBytes,
                        tag = releaseTag,
                        result = LogResult.Cancelled,
                    )
                    return@collect
                }

                OrchestratorStage.Failed -> {
                    _state.value =
                        _state.value.copy(
                            downloadStage = DownloadStage.IDLE,
                            downloadError = entry.errorMessage,
                        )
                    currentAssetName = null
                    appendLog(
                        assetName = assetName,
                        size = sizeBytes,
                        tag = releaseTag,
                        result = Error(entry.errorMessage),
                    )
                    _state.value.repository?.id?.let {
                    }
                    downloadOrchestrator.dismiss(packageKey)
                    return@collect
                }
            }
        }
    }

    private suspend fun installAsset(
        isUpdate: Boolean,
        filePath: String,
        assetName: String,
        downloadUrl: String,
        sizeBytes: Long,
        releaseTag: String,
    ) {
        _state.value = _state.value.copy(downloadStage = DownloadStage.INSTALLING)

        val ext = assetName.substringAfterLast('.', "").lowercase()
        val isApk = ext == "apk"
        var validatedApkInfo: ApkPackageInfo? = null

        if (isApk) {
            val validationResult =
                installationManager.validateApk(
                    filePath = filePath,
                    isUpdate = isUpdate,
                    trackedPackageName = _state.value.installedApp?.packageName,
                )

            when (validationResult) {
                is ApkValidationResult.ExtractionFailed -> {

                    logger.warn(
                        "Could not extract APK info for $assetName, " +
                                "proceeding with unvalidated install",
                    )
                }

                is ApkValidationResult.PackageMismatch -> {
                    logger.error(
                        "Package name mismatch on update: " +
                                "APK=${validationResult.apkPackageName}, " +
                                "installed=${validationResult.installedPackageName}",
                    )
                    _state.value =
                        _state.value.copy(
                            downloadStage = DownloadStage.IDLE,
                            installError =
                                getString(
                                    Res.string.update_package_mismatch,
                                    validationResult.apkPackageName,
                                    validationResult.installedPackageName,
                                ),
                        )
                    currentAssetName = null
                    appendLog(
                        assetName = assetName,
                        size = sizeBytes,
                        tag = releaseTag,
                        result = Error("Package name mismatch"),
                    )
                    return
                }

                is ApkValidationResult.Valid -> {
                    validatedApkInfo = validationResult.apkInfo
                    val fpResult =
                        installationManager.checkSigningFingerprint(validationResult.apkInfo)
                    if (fpResult is FingerprintCheckResult.Mismatch) {
                        _state.update { state ->
                            state.copy(
                                signingKeyWarning =
                                    SigningKeyWarning(
                                        packageName = validationResult.apkInfo.packageName,
                                        expectedFingerprint = fpResult.expectedFingerprint,
                                        actualFingerprint = fpResult.actualFingerprint,
                                        pendingDownloadUrl = downloadUrl,
                                        pendingAssetName = assetName,
                                        pendingSizeBytes = sizeBytes,
                                        pendingReleaseTag = releaseTag,
                                        pendingIsUpdate = isUpdate,
                                        pendingFilePath = filePath,
                                        pendingApkInfo = validationResult.apkInfo,
                                    ),
                            )
                        }
                        appendLog(
                            assetName = assetName,
                            size = sizeBytes,
                            tag = releaseTag,
                            result = Error("Signing key changed"),
                        )
                        return
                    }
                }
            }
        }

        val gatePackageName =
            if (platform == Platform.ANDROID) validatedApkInfo?.packageName else null
        if (gatePackageName != null) {
            systemInstallSerializer.awaitFreeAndMarkPending(gatePackageName)
        }
        val installOutcome =
            try {
                installer.install(filePath, ext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (gatePackageName != null) {
                    systemInstallSerializer.markCompleted(gatePackageName)
                }
                throw e
            }

        launchAttestationCheck(filePath)

        if (platform == Platform.ANDROID && validatedApkInfo != null) {
            saveInstalledAppToDatabase(
                apkInfo = validatedApkInfo,
                assetName = assetName,
                assetUrl = downloadUrl,
                assetSize = sizeBytes,
                releaseTag = releaseTag,
                isUpdate = isUpdate,
                installOutcome = installOutcome,
                parkedFilePath = filePath,
            )
        } else if (platform != Platform.ANDROID) {
            viewModelScope.launch {
                _events.send(DetailsEvent.OnMessage(getString(Res.string.installer_saved_downloads)))
            }
        }

        _state.value = _state.value.copy(downloadStage = DownloadStage.IDLE)
        currentAssetName = null
        appendLog(
            assetName = assetName,
            size = sizeBytes,
            tag = releaseTag,
            result =
                if (isUpdate) {
                    LogResult.Updated
                } else {
                    LogResult.Installed
                },
        )
    }

    private fun launchAttestationCheck(filePath: String) {
        val repo = _state.value.repository ?: return
        val owner = repo.owner.login
        val repoName = repo.name

        _state.update { it.copy(attestationStatus = AttestationStatus.CHECKING) }

        viewModelScope.launch {
            val result = attestationVerifier.verify(owner, repoName, filePath)
            _state.update {
                it.copy(
                    attestationStatus = when (result) {
                        is VerificationResult.Verified -> AttestationStatus.VERIFIED
                        is VerificationResult.Unverified -> AttestationStatus.UNVERIFIED
                        is VerificationResult.Error -> AttestationStatus.UNABLE_TO_VERIFY
                    },
                )
            }
        }
    }

    private suspend fun saveInstalledAppToDatabase(
        apkInfo: ApkPackageInfo,
        assetName: String,
        assetUrl: String,
        assetSize: Long,
        releaseTag: String,
        isUpdate: Boolean,
        installOutcome: InstallOutcome,
        parkedFilePath: String? = null,
    ) {
        val repo = _state.value.repository ?: return
        val isPending = installOutcome != InstallOutcome.COMPLETED && platform == Platform.ANDROID

        val pendingPath = parkedFilePath?.takeIf { isPending }

        // The asset of that same release — installableAssets follows the current selection.
        val sourceRelease =
            _state.value.selectedRelease?.takeIf { it.tagName == releaseTag }
                ?: _state.value.allReleases.firstOrNull { it.tagName == releaseTag }
        val sourceAsset = sourceRelease?.assets?.firstOrNull { it.name == assetName }
        val identityKnown = sourceRelease != null && sourceAsset != null

        if (isUpdate) {
            installationManager.updateInstalledAppVersion(
                UpdateInstalledAppParams(
                    apkInfo = apkInfo,
                    assetName = assetName,
                    assetUrl = assetUrl,
                    releaseTag = releaseTag,
                    releaseId = sourceRelease?.id.takeIf { identityKnown },
                    assetId = sourceAsset?.id.takeIf { identityKnown },
                    assetDigest = sourceAsset?.digest.takeIf { identityKnown },
                    isPendingInstall = isPending,
                ),
            )

            if (pendingPath != null) {
                runCatching {
                    installedAppsRepository.setPendingInstallFilePath(
                        packageName = apkInfo.packageName,
                        path = pendingPath,
                        version = releaseTag,
                        assetName = assetName,
                    )
                }.onFailure { t ->
                    logger.warn("Failed to park pending install path on update: ${t.message}")
                }
            }
        } else {

            val installable = _state.value.installableAssets
            val pickedIndex = installable
                .indexOfFirst { it.name == assetName }
                .takeIf { it >= 0 }
            val reloaded =
                installationManager.saveNewInstalledApp(
                    SaveInstalledAppParams(
                        repo = repo,
                        apkInfo = apkInfo,
                        assetName = assetName,
                        assetUrl = assetUrl,
                        assetSize = assetSize,
                        releaseTag = releaseTag,
                        releaseId = sourceRelease?.id.takeIf { identityKnown },
                        assetId = sourceAsset?.id.takeIf { identityKnown },
                        assetDigest = sourceAsset?.digest.takeIf { identityKnown },
                        isPendingInstall = isPending,
                        isFavourite = _state.value.isFavourite,
                        siblingAssetCount = installable.size,
                        pickedAssetIndex = pickedIndex,
                        pendingInstallFilePath = pendingPath,
                        sourceHost = sourceHostParam,
                    ),
                )
            _state.value = _state.value.copy(installedApp = reloaded)
        }
    }

    private fun downloadAsset(
        downloadUrl: String,
        assetName: String,
        sizeBytes: Long,
        releaseTag: String,
    ) {
        currentDownloadJob?.cancel()
        val packageKey = orchestratorKey()
        val repository = _state.value.repository ?: return

        val asset = _state.value.selectedRelease?.assets
            ?.find { it.downloadUrl == downloadUrl }
            ?: _state.value.primaryAsset
            ?: return
        currentAssetName = assetName

        appendLog(
            assetName = assetName,
            size = sizeBytes,
            tag = releaseTag,
            result = LogResult.DownloadStarted,
        )
        _state.value =
            _state.value.copy(
                isDownloading = true,
                downloadError = null,
                installError = null,
                downloadProgressPercent = null,
            )

        currentDownloadJob = viewModelScope.launch {
            try {
                downloadOrchestrator.enqueue(
                    DownloadSpec(
                        packageName = packageKey,
                        repoOwner = repository.owner.login,
                        repoName = repository.name,
                        repoOwnerAvatarUrl = repository.owner.avatarUrl,
                        asset = asset,
                        displayAppName = repository.name,
                        installPolicy = InstallPolicy.DeferUntilUserAction,
                        releaseTag = releaseTag,
                    ),
                )
                observeOrchestratorEntry(
                    packageKey = packageKey,
                    downloadUrl = downloadUrl,
                    assetName = assetName,
                    sizeBytes = sizeBytes,
                    releaseTag = releaseTag,
                    isUpdate = false,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.value =
                    _state.value.copy(
                        isDownloading = false,
                        downloadError = t.message,
                    )
                currentAssetName = null
                appendLog(
                    assetName = assetName,
                    size = sizeBytes,
                    tag = releaseTag,
                    result = Error(t.message),
                )
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun appendLog(
        assetName: String,
        size: Long,
        tag: String,
        result: LogResult,
    ) {
        val now =
            System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .format(
                    LocalDateTime.Format {
                        year()
                        char('-')
                        monthNumber()
                        char('-')
                        day()
                        char(' ')
                        hour()
                        char(':')
                        minute()
                        char(':')
                        second()
                    },
                )
        val newItem =
            InstallLogItem(
                timeIso = now,
                assetName = assetName,
                assetSizeBytes = size,
                releaseTag = tag,
                result = result,
            )
        _state.value =
            _state.value.copy(
                installLogs = listOf(newItem) + _state.value.installLogs,
            )
    }

    override fun onCleared() {
        super.onCleared()

        currentDownloadJob?.cancel()

        val packageKey = orchestratorKey()
        viewModelScope.launch(NonCancellable) {
            try {
                downloadOrchestrator.downgradeToDeferred(packageKey)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Failed to downgrade orchestrator on screen leave: ${t.message}")
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun loadInitial() {
        viewModelScope.launch {
            try {
                rateLimited.set(false)

                _state.value = _state.value.copy(isLoading = true, errorMessage = null)

                val syncResult = syncInstalledAppsUseCase()
                if (syncResult.isFailure) {
                    logger.warn("Sync had issues but continuing: ${syncResult.exceptionOrNull()?.message}")
                }

                val repo =
                    when {

                        sourceHostParam != null -> {
                            if (ownerParam.isBlank() || repoParam.isBlank()) {
                                error("Foreign-source Details opened without owner/repo for host=$sourceHostParam")
                            }
                            detailsRepository.getRepositoryByOwnerAndName(
                                owner = ownerParam,
                                name = repoParam,
                                sourceHost = sourceHostParam,
                            )
                        }

                        ownerParam.isNotEmpty() && repoParam.isNotEmpty() ->
                            detailsRepository.getRepositoryByOwnerAndName(
                                owner = ownerParam,
                                name = repoParam,
                                sourceHost = null,
                            )

                        else -> detailsRepository.getRepositoryById(repositoryId)
                    }
                launch { seenReposRepository.markAsSeen(repo) }

                val isFavoriteDeferred =
                    async {
                        try {
                            favouritesRepository.isFavoriteSync(repo.id)
                        } catch (_: RateLimitException) {
                            rateLimited.set(true)
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            logger.error("Failed to load if repo is favourite: ${t.localizedMessage}")
                            false
                        }
                    }
                val isStarredDeferred =
                    async {
                        try {
                            starredRepository.isStarred(repo.id)
                        } catch (_: RateLimitException) {
                            rateLimited.set(true)
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            logger.error("Failed to load if repo is starred: ${t.localizedMessage}")
                            false
                        }
                    }
                val isFavorite = isFavoriteDeferred.await()
                val isStarred = isStarredDeferred.await()

                val owner = repo.owner.login
                val name = repo.name

                _state.value =
                    _state.value.copy(
                        repository = repo,
                        isFavourite = isFavorite == true,
                        isStarred = isStarred == true,
                    )

                val allReleasesDeferred =
                    async {
                        val cachedReleases =
                            detailsRepository.getCachedReleases(
                                owner = owner,
                                repo = name,
                                sourceHost = sourceHostParam,
                            )
                        if (cachedReleases != null) {
                            return@async Triple(cachedReleases.releases, false, true)
                        }
                        try {
                            Triple(
                                detailsRepository.getAllReleases(
                                    owner = owner,
                                    repo = name,
                                    defaultBranch = repo.defaultBranch,
                                    sourceHost = sourceHostParam,
                                ),
                                false,
                                false,
                            )
                        } catch (_: RateLimitException) {
                            rateLimited.set(true)
                            Triple(emptyList<GithubRelease>(), true, false)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            logger.warn("Failed to load releases: ${t.message}")
                            Triple(emptyList<GithubRelease>(), true, false)
                        }
                    }

                val statsDeferred =
                    async {
                        try {
                            detailsRepository.getRepoStats(
                                owner = owner,
                                repo = name,
                                sourceHost = sourceHostParam,
                            )
                        } catch (_: RateLimitException) {
                            rateLimited.set(true)
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Throwable) {
                            null
                        }
                    }

                val readmeDeferred =
                    async {
                        try {
                            detailsRepository.getReadme(
                                owner = owner,
                                repo = name,
                                defaultBranch = repo.defaultBranch,
                                sourceHost = sourceHostParam,
                            )
                        } catch (_: RateLimitException) {
                            rateLimited.set(true)
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Throwable) {
                            null
                        }
                    }

                val userProfileDeferred =
                    async {

                        if (sourceHostParam != null) return@async null
                        try {
                            detailsRepository.getUserProfile(owner)
                        } catch (_: RateLimitException) {
                            rateLimited.set(true)
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            logger.warn("Failed to load user profile: ${t.message}")
                            null
                        }
                    }

                val installedAppsDeferred =
                    async {
                        try {
                            val dbApps = installedAppsRepository.getAppsByRepoId(repo.id)

                            dbApps.map { dbApp ->
                                if (dbApp.isPendingInstall &&
                                    packageMonitor.isPackageInstalled(dbApp.packageName)
                                ) {
                                    installedAppsRepository.updatePendingStatus(
                                        dbApp.packageName,
                                        false,
                                    )
                                    installedAppsRepository.getAppByPackage(dbApp.packageName)
                                        ?: dbApp
                                } else {
                                    dbApp
                                }
                            }
                        } catch (_: RateLimitException) {
                            rateLimited.set(true)
                            emptyList()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            logger.error("Failed to load installed apps: ${t.message}")
                            emptyList()
                        }
                    }

                val isObtainiumEnabled = platform == Platform.ANDROID
                val isAppManagerEnabled = platform == Platform.ANDROID

                val (allReleases, releasesFailed, releasesFromCache) = allReleasesDeferred.await()
                val stats = statsDeferred.await()
                val readme = readmeDeferred.await()
                val userProfile = userProfileDeferred.await()
                val allInstalledApps = installedAppsDeferred.await()
                val installedApp =
                    packageNameParam
                        ?.takeIf { it.isNotBlank() }
                        ?.let { pkg -> allInstalledApps.firstOrNull { it.packageName == pkg } }
                        ?: pickPrimaryInstalledApp(allInstalledApps, null, emptyList())

                if (rateLimited.get()) {

                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = null,
                        releasesLoadFailed = true,
                    )
                    return@launch
                }

                val installedAssetName = installedApp?.let {
                    it.installedAssetName ?: it.latestAssetName ?: it.pendingInstallAssetName
                }
                val installedVersionTag = installedApp?.installedVersion
                val installedRelease =
                    allReleases.firstOrNull {
                        VersionMath.isExactSameVersion(it.tagName, installedVersionTag)
                    } ?: allReleases.firstOrNull {
                        VersionMath.isSameVersion(it.tagName, installedVersionTag)
                    }
                val installedIsPreRelease = installedRelease?.isEffectivelyPreRelease() == true
                val installedChannel =
                    if (installedIsPreRelease) {
                        ReleaseCategory.PRE_RELEASE
                    } else {
                        ReleaseCategory.STABLE
                    }
                val deviceBuildIds = deviceBuildReleaseIds(allReleases)
                val selectedRelease =
                    installedApp?.let { app ->
                        allReleases.firstOwnedBy(
                            app = app,
                            category = installedChannel,
                            repoApps = allInstalledApps,
                            anchorAssetName = installedAssetName,
                        )
                    }
                        ?: allReleases.filter { it.id in deviceBuildIds }.firstInCategory(installedChannel)
                        ?: allReleases.firstInCategory(installedChannel)
                        ?: allReleases.firstInCategory(ReleaseCategory.ALL)
                val resolvedCategory =
                    if (selectedRelease?.isEffectivelyPreRelease() == true) {
                        ReleaseCategory.PRE_RELEASE
                    } else {
                        ReleaseCategory.STABLE
                    }

                val (installable, primary) = recomputeAssetsForRelease(
                    selectedRelease,
                    installedApp,
                    installedAssetName,
                )

                val isObtainiumAvailable = installer.isObtainiumInstalled()
                val isAppManagerAvailable = installer.isAppManagerInstalled()

                logger.debug("Loaded repo: ${repo.name}, installedApp: ${installedApp?.packageName}")

                val insights = computeReleaseInsights(allReleases, installedApp)

                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        errorMessage = null,
                        repository = repo,
                        allReleases = allReleases,
                        releasePlatforms = platformsByRelease(allReleases),
                        deviceBuildReleaseIds = deviceBuildIds,
                        releaseLines = releaseLines(allReleases),
                        releasesLoadFailed = releasesFailed,
                        isRetryingReleases = false,
                        selectedRelease = selectedRelease,
                        selectedReleaseCategory = resolvedCategory,
                        stats = stats,
                        readmeMarkdown = readme?.first,
                        readmeLanguage = readme?.second,
                        installableAssets = installable,
                        primaryAsset = primary,
                        userProfile = userProfile,
                        systemArchitecture = installer.detectSystemArchitecture(),
                        isObtainiumAvailable = isObtainiumAvailable,
                        isObtainiumEnabled = isObtainiumEnabled,
                        isAppManagerAvailable = isAppManagerAvailable,
                        isAppManagerEnabled = isAppManagerEnabled,
                        installedApp = installedApp,
                        deviceLanguageCode = translationRepository.getDeviceLanguageCode(),
                        isComingFromUpdate = isComingFromUpdate,
                        stalledStableSinceDays = insights.stalledStableSinceDays,
                        mergedChangelog = insights.mergedChangelog,
                        mergedChangelogBaseTag = insights.mergedChangelogBaseTag,
                        latestStableHasInstallableAsset =
                            insights.latestStableHasInstallableAsset,
                    )

                if (releasesFromCache) {
                    revalidateReleases()
                }

                observeInstalledApp(repo.id)

                maybeAutoTranslate(
                    readmeBody = readme?.first,
                    releaseDescription = selectedRelease?.description,
                )
            } catch (e: RateLimitException) {
                logger.error("Rate limited: ${e.message}")
                val seconds = e.rateLimitInfo.timeUntilReset().inWholeSeconds
                val signedIn = userSessionRepository.isCurrentlyUserLoggedIn()
                val base = if (seconds > 0L) {
                    getString(Res.string.rate_limit_exceeded_retry_in, seconds.toInt())
                } else {
                    getString(Res.string.rate_limit_exceeded)
                }
                val message = if (!signedIn) {
                    base + " " + getString(Res.string.rate_limit_exceeded_signin_hint)
                } else {
                    base
                }
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        errorMessage = message,
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Details load failed: ${t.message}")
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        errorMessage = t.message ?: getString(Res.string.failed_to_load_details),
                    )
            }
        }
    }

    // The first paint can come from a cached copy that is hours old — the release behind it may
    // have been edited or rebuilt in that window, and the page would keep showing the old text
    // until the cache expired. When the load was served from the cache, quietly read the list
    // once more and converge to it; the pull-to-refresh stays as the way to force the
    // repository itself, not as the only way to see a rebuilt release.
    @OptIn(ExperimentalTime::class)
    private fun revalidateReleases() {
        val repo = _state.value.repository ?: return
        viewModelScope.launch {
            try {
                // Never re-read a list that was just read: the copy in front of the page is
                // younger than the window, and the endpoint is shared with everything else.
                val cached =
                    detailsRepository.getCachedReleases(
                        owner = repo.owner.login,
                        repo = repo.name,
                        sourceHost = sourceHostParam,
                    ) ?: return@launch
                val ageMs = System.now().toEpochMilliseconds() - cached.cachedAtEpochMs
                if (ageMs < RELEASES_REVALIDATE_MIN_AGE_MS) return@launch

                val freshReleases =
                    detailsRepository.getAllReleases(
                        owner = repo.owner.login,
                        repo = repo.name,
                        defaultBranch = repo.defaultBranch,
                        sourceHost = sourceHostParam,
                        bypassCache = true,
                    )
                if (freshReleases.isEmpty()) return@launch

                val previousSelected = _state.value.selectedRelease
                val previousCategory = _state.value.selectedReleaseCategory
                val carried =
                    previousSelected?.let { prev ->
                        freshReleases.firstOrNull { it.id == prev.id }
                            ?: freshReleases.firstOrNull { it.tagName == prev.tagName }
                    }
                val selectedRelease =
                    carried
                        ?: freshReleases.firstInCategory(previousCategory)
                        ?: freshReleases.firstOrNull { !it.isEffectivelyPreRelease() }
                        ?: freshReleases.firstOrNull()

                val resolvedCategory = when {
                    carried != null -> previousCategory
                    selectedRelease?.isEffectivelyPreRelease() == true -> ReleaseCategory.PRE_RELEASE
                    selectedRelease != null -> ReleaseCategory.STABLE
                    else -> previousCategory
                }

                val (installable, primary) = recomputeAssetsForRelease(
                    selectedRelease,
                    _state.value.installedApp,
                )
                val insights = computeReleaseInsights(freshReleases, _state.value.installedApp)

                _state.update {
                    it.copy(
                        allReleases = freshReleases,
                        releasePlatforms = platformsByRelease(freshReleases),
                        deviceBuildReleaseIds = deviceBuildReleaseIds(freshReleases),
                        releaseLines = releaseLines(freshReleases),
                        selectedRelease = selectedRelease,
                        selectedReleaseCategory = resolvedCategory,
                        installableAssets = installable,
                        primaryAsset = primary,
                        stalledStableSinceDays = insights.stalledStableSinceDays,
                        mergedChangelog = insights.mergedChangelog,
                        mergedChangelogBaseTag = insights.mergedChangelogBaseTag,
                        latestStableHasInstallableAsset =
                            insights.latestStableHasInstallableAsset,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.debug("Release re-read failed for ${repo.name}: ${t.message}")
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun refresh() {
        if (_state.value.isRefreshing) return
        val repo = _state.value.repository ?: return
        val owner = repo.owner.login
        val name = repo.name

        // The user asked for a read, so it must end in one of two ways: the list on screen is
        // the repository's, or the user is told the read did not happen. The backend re-poll
        // (cooldown- and budget-gated) is best-effort: it can only skip refreshing the
        // repository block itself, never the releases.
        val nowMs = System.now().toEpochMilliseconds()
        val cooledDown =
            _state.value.refreshCooldownUntilEpochMs?.let { it > nowMs } == true

        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            try {

                val refreshed = if (sourceHostParam != null) {
                    detailsRepository.getRepositoryByOwnerAndName(
                        owner = owner,
                        name = name,
                        sourceHost = sourceHostParam,
                    )
                } else if (cooledDown) {
                    repo
                } else {
                    try {
                        detailsRepository.refreshRepository(owner, name)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: RefreshException) {
                        logger.warn("Refresh: repository re-poll failed (${e.kind}): ${e.message}")
                        val cooldownUntil = e.retryAfterSeconds?.let { sec ->
                            nowMs + sec * 1000L
                        }
                        _state.update {
                            it.copy(
                                refreshCooldownUntilEpochMs =
                                    if (e.kind == RefreshError.COOLDOWN ||
                                        e.kind == RefreshError.BUDGET_EXHAUSTED
                                    ) {
                                        cooldownUntil ?: it.refreshCooldownUntilEpochMs
                                    } else {
                                        it.refreshCooldownUntilEpochMs
                                    },
                            )
                        }
                        repo
                    }
                }
                val releasesDeferred = async {
                    try {
                        detailsRepository.getAllReleases(
                            owner = owner,
                            repo = name,
                            defaultBranch = refreshed.defaultBranch,
                            sourceHost = sourceHostParam,
                            bypassCache = true,
                            allowStale = false,
                            // The user asked for this read: go to the repository host, where an
                            // edit lands, rather than a backend copy that can predate it.
                            preferDirectSource = true,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        logger.warn("Refresh: getAllReleases failed: ${t.message}")
                        null
                    }
                }
                val statsDeferred = async {
                    try {
                        detailsRepository.getRepoStats(
                            owner = owner,
                            repo = name,
                            sourceHost = sourceHostParam,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        logger.warn("Refresh: getRepoStats failed: ${t.message}")
                        null
                    }
                }
                val freshReleases = releasesDeferred.await()
                val freshStats = statsDeferred.await()

                if (freshReleases == null) {
                    // The previous list stays on screen; say so, rather than letting a failed
                    // re-read look like a refresh that found nothing new.
                    _events.send(DetailsEvent.OnRefreshError(kind = RefreshError.UPSTREAM))
                }

                val previousSelected = _state.value.selectedRelease
                val previousCategory = _state.value.selectedReleaseCategory
                val carried = freshReleases?.let { list ->
                    previousSelected?.let { prev ->
                        list.firstOrNull { it.id == prev.id }
                            ?: list.firstOrNull { it.tagName == prev.tagName }
                    }
                }
                val selectedRelease = freshReleases?.let { list ->
                    carried
                        ?: list.firstInCategory(previousCategory)
                        ?: list.firstOrNull { !it.isEffectivelyPreRelease() }
                        ?: list.firstOrNull()
                } ?: previousSelected

                val resolvedCategory = when {
                    carried != null -> previousCategory
                    selectedRelease?.isEffectivelyPreRelease() == true -> ReleaseCategory.PRE_RELEASE
                    selectedRelease != null -> ReleaseCategory.STABLE
                    else -> previousCategory
                }

                val (installable, primary) = recomputeAssetsForRelease(
                    selectedRelease,
                    _state.value.installedApp,
                )
                val insights = computeReleaseInsights(
                    freshReleases ?: _state.value.allReleases,
                    _state.value.installedApp,
                )

                _state.update {
                    it.copy(
                        isRefreshing = false,
                        repository = refreshed,
                        allReleases = freshReleases ?: it.allReleases,
                        releasePlatforms = freshReleases?.let(::platformsByRelease) ?: it.releasePlatforms,
                        deviceBuildReleaseIds =
                            freshReleases?.let(::deviceBuildReleaseIds) ?: it.deviceBuildReleaseIds,
                        releaseLines = freshReleases?.let(::releaseLines) ?: it.releaseLines,
                        releasesLoadFailed = freshReleases == null && it.releasesLoadFailed,
                        selectedRelease = selectedRelease,
                        selectedReleaseCategory = resolvedCategory,
                        stats = freshStats ?: it.stats,
                        installableAssets = installable,
                        primaryAsset = primary,
                        stalledStableSinceDays = insights.stalledStableSinceDays,
                        mergedChangelog = insights.mergedChangelog,
                        mergedChangelogBaseTag = insights.mergedChangelogBaseTag,
                        latestStableHasInstallableAsset =
                            insights.latestStableHasInstallableAsset,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RefreshException) {
                logger.warn("Refresh failed (${e.kind}): ${e.message}")
                val cooldownUntil = e.retryAfterSeconds?.let { sec ->
                    System.now().toEpochMilliseconds() + sec * 1000L
                }
                _state.update {
                    it.copy(
                        isRefreshing = false,
                        refreshCooldownUntilEpochMs =
                            if (e.kind == RefreshError.COOLDOWN ||
                                e.kind == RefreshError.BUDGET_EXHAUSTED
                            ) {
                                cooldownUntil ?: it.refreshCooldownUntilEpochMs
                            } else {
                                it.refreshCooldownUntilEpochMs
                            },
                    )
                }
                _events.send(
                    DetailsEvent.OnRefreshError(
                        kind = e.kind,
                        retryAfterSeconds = e.retryAfterSeconds,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger.error("Refresh failed: ${t.message}")
                _state.update { it.copy(isRefreshing = false) }
                _events.send(
                    DetailsEvent.OnRefreshError(kind = RefreshError.GENERIC),
                )
            }
        }
    }

    private fun maybeAutoTranslate(readmeBody: String?, releaseDescription: String?) {
        viewModelScope.launch {
            val enabled = runCatching {
                tweaksRepository.getAutoTranslateEnabled().first()
            }.getOrDefault(false)
            if (!enabled) return@launch

            val explicit = runCatching {
                tweaksRepository.getAutoTranslateTargetLang().first()
            }.getOrNull()?.takeIf { it.isNotBlank() }
            val app = runCatching {
                tweaksRepository.getAppLanguage().first()
            }.getOrNull()?.takeIf { it.isNotBlank() }
            val target = explicit ?: app ?: translationRepository.getDeviceLanguageCode()
            if (target.isBlank()) return@launch

            val currentReadmeLang = _state.value.readmeLanguage
            if (!readmeBody.isNullOrBlank() &&
                _state.value.aboutTranslation.translatedText == null &&
                currentReadmeLang?.equals(target, ignoreCase = true) != true
            ) {
                aboutTranslationJob?.cancel()
                aboutTranslationJob = translateContent(
                    text = readmeBody,
                    targetLanguageCode = target,
                    updateState = { ts -> _state.update { it.copy(aboutTranslation = ts) } },
                    getCurrentState = { _state.value.aboutTranslation },
                )
            }

            if (!releaseDescription.isNullOrBlank() &&
                _state.value.whatsNewTranslation.translatedText == null &&
                currentReadmeLang?.equals(target, ignoreCase = true) != true
            ) {
                whatsNewTranslationJob?.cancel()
                whatsNewTranslationJob = translateContent(
                    text = releaseDescription,
                    targetLanguageCode = target,
                    updateState = { ts -> _state.update { it.copy(whatsNewTranslation = ts) } },
                    getCurrentState = { _state.value.whatsNewTranslation },
                )
            }
        }
    }

    private fun translateContent(
        text: String,
        targetLanguageCode: String,
        updateState: (TranslationState) -> Unit,
        getCurrentState: () -> TranslationState,
    ): Job = viewModelScope.launch {
        try {
            updateState(
                getCurrentState().copy(
                    isTranslating = true,
                    error = null,
                    targetLanguageCode = targetLanguageCode,
                ),
            )

            val result =
                translationRepository.translate(
                    text = text,
                    targetLanguage = targetLanguageCode,
                )

            val langDisplayName =
                SupportedLanguages.all
                    .find { it.code == targetLanguageCode }
                    ?.displayName
                    ?: targetLanguageCode

            updateState(
                TranslationState(
                    isTranslating = false,
                    translatedText = result.translatedText,
                    isShowingTranslation = true,
                    targetLanguageCode = targetLanguageCode,
                    targetLanguageDisplayName = langDisplayName,
                    detectedSourceLanguage = result.detectedSourceLanguage,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Translation failed: ${e.message}")
            updateState(
                getCurrentState().copy(
                    isTranslating = false,
                    error = e.message,
                ),
            )
            _events.send(
                DetailsEvent.OnMessage(getString(Res.string.translation_failed)),
            )
        }
    }

    private companion object {
        const val OBTAINIUM_REPO_ID: Long = 523534328
        const val APP_MANAGER_REPO_ID: Long = 268006778
        const val STALLED_STABLE_THRESHOLD_DAYS = 180

        // [orchestratorKey] cannot name a package before the repository has loaded. Empty rather
        // than a word: a package name is arbitrary, and one literally called "unknown" would
        // otherwise collide with the sentinel and have its real downloads skipped.
        const val NO_ORCHESTRATOR_KEY = ""
    }
}
