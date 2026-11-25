package zed.rainxch.apps.presentation.link

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.apps.domain.model.GithubRepoInfo
import zed.rainxch.apps.domain.repository.AppsRepository
import zed.rainxch.apps.presentation.AppsAction
import zed.rainxch.apps.presentation.AppsEvent
import zed.rainxch.apps.presentation.AppsState
import zed.rainxch.apps.presentation.AppsStateHolder
import zed.rainxch.apps.presentation.mappers.toDomain
import zed.rainxch.apps.presentation.mappers.toLinkSuggestionUi
import zed.rainxch.apps.presentation.mappers.toUi
import zed.rainxch.apps.presentation.model.DeviceAppUi
import zed.rainxch.apps.presentation.model.GithubAssetUi
import zed.rainxch.apps.presentation.model.LinkStep
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.error.SystemPackagesException
import zed.rainxch.core.domain.model.repository.RepositorySource
import zed.rainxch.core.domain.repository.ExternalImportRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.ExternalAppCandidate
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.InstallerKind
import zed.rainxch.core.domain.utils.AssetFilter
import zed.rainxch.core.domain.utils.RepositoryUrlParser
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.app_linked_success
import zed.rainxch.githubstore.core.presentation.res.checking_release
import zed.rainxch.githubstore.core.presentation.res.failed_to_link
import zed.rainxch.githubstore.core.presentation.res.failed_to_load_apps
import zed.rainxch.githubstore.core.presentation.res.invalid_github_url
import zed.rainxch.githubstore.core.presentation.res.rate_limit_try_again
import zed.rainxch.githubstore.core.presentation.res.repo_not_found
import zed.rainxch.githubstore.core.presentation.res.validating_repo
import kotlin.time.Clock

// The link sheet: pick a device app, take a suggested repo or enter a URL, then pick the asset to track.
class LinkSheetActions(
    private val appsRepository: AppsRepository,
    private val externalImportRepository: ExternalImportRepository,
    private val tweaksRepository: TweaksRepository,
    private val installer: Installer,
    private val logger: KomiStoreLogger,
) {
    private var smartMatchJob: Job? = null

    fun onAction(
        holder: AppsStateHolder,
        action: AppsAction.LinkNavigation,
    ) {
        when (action) {
            AppsAction.OnAddByLinkClick -> {
                openLinkSheet(holder)
            }

            AppsAction.OnDismissLinkSheet -> {
                smartMatchJob?.cancel()
                smartMatchJob = null
                holder.update { it.withLinkSheetClosed() }
            }

            AppsAction.OnLinkEnterUrlManually -> {
                holder.update {
                    it.copy(linkStep = LinkStep.EnterUrl, repoUrl = "", repoValidationError = null, fetchedRepoInfo = null)
                }
            }

            AppsAction.OnRetryLinkSearch -> {
                holder.current.selectedDeviceApp?.let { startSmartMatch(holder, it) }
            }

            AppsAction.OnBackToAppPicker -> {
                holder.update { it.backToAppPicker() }
            }

            AppsAction.OnBackToSmartMatch -> {
                holder.update { it.backToSmartMatch() }
            }

            AppsAction.OnBackToEnterUrl -> {
                holder.update { it.backToEnterUrl() }
            }
        }
    }

    fun onAction(
        holder: AppsStateHolder,
        action: AppsAction.LinkInput,
    ) {
        when (action) {
            is AppsAction.OnDeviceAppSearchChange -> {
                holder.update { it.copy(deviceAppSearchQuery = action.query) }
            }

            is AppsAction.OnDeviceAppSelected -> {
                startSmartMatch(holder, action.app)
            }

            is AppsAction.OnLinkSuggestionSelected -> {
                val baseHost = action.sourceHost ?: RepositorySource.GitHub.host
                holder.update {
                    it.copy(repoUrl = "https://$baseHost/${action.owner}/${action.repo}", repoValidationError = null)
                }
                validateAndLinkRepo(holder)
            }

            is AppsAction.OnRepoUrlChanged -> {
                holder.update { it.copy(repoUrl = action.url, repoValidationError = null) }
            }

            AppsAction.OnValidateAndLinkRepo -> {
                validateAndLinkRepo(holder)
            }

            is AppsAction.OnLinkAssetSelected -> {
                linkWithAsset(holder, action.asset)
            }

            is AppsAction.OnLinkAssetFilterChanged -> {
                val error = AssetFilter.parse(action.filter)?.exceptionOrNull()?.let { INVALID_FILTER }
                holder.update { it.copy(linkAssetFilter = action.filter, linkAssetFilterError = error) }
            }

            is AppsAction.OnLinkFallbackToggled -> {
                holder.update { it.copy(linkFallbackToOlder = action.enabled) }
            }
        }
    }

    private fun openLinkSheet(holder: AppsStateHolder) {
        holder.scope.launch {
            holder.update { it.withLinkSheetOpened() }
            try {
                val trackedPackages = appsRepository.getTrackedPackageNames()
                val deviceApps =
                    appsRepository
                        .getDeviceApps()
                        .filter { it.packageName !in trackedPackages }
                        .map { it.toUi() }
                        .toImmutableList()
                holder.update { it.copy(deviceApps = deviceApps) }
            } catch (e: LocalStoreException) {
                deviceAppsFailed(holder, e)
            } catch (e: SystemPackagesException) {
                deviceAppsFailed(holder, e)
            }
        }
    }

    private suspend fun deviceAppsFailed(
        holder: AppsStateHolder,
        cause: Exception,
    ) {
        logger.error("Failed to load device apps: ${cause.message}")
        holder.send(AppsEvent.ShowError(getString(Res.string.failed_to_load_apps)))
    }

    private fun startSmartMatch(
        holder: AppsStateHolder,
        app: DeviceAppUi,
    ) {
        smartMatchJob?.cancel()
        holder.update { it.withSmartMatchStarted(app) }
        smartMatchJob =
            holder.scope.launch {
                try {
                    val suggestions =
                        externalImportRepository
                            .resolveMatches(listOf(app.toCandidate()))
                            .firstOrNull()
                            ?.suggestions
                            ?.sortedByDescending { it.confidence }
                            ?.take(MAX_SUGGESTIONS)
                            .orEmpty()
                            .map { it.toLinkSuggestionUi() }
                            .toImmutableList()
                    holder.update { it.copy(linkSearchLoading = false, linkSuggestions = suggestions) }
                } catch (e: RateLimitException) {
                    smartMatchFailed(holder, app, e)
                } catch (e: LocalStoreException) {
                    smartMatchFailed(holder, app, e)
                }
            }
    }

    private fun smartMatchFailed(
        holder: AppsStateHolder,
        app: DeviceAppUi,
        cause: Exception,
    ) {
        logger.warn("Smart match failed for ${app.packageName}: ${cause.message}")
        holder.update { it.copy(linkSearchLoading = false, linkSearchError = cause.message ?: "search failed") }
    }

    private fun validateAndLinkRepo(holder: AppsStateHolder) {
        val selectedApp = holder.current.selectedDeviceApp ?: return
        val url = holder.current.repoUrl.trim()

        holder.scope.launch {
            val parsedRef = RepositoryUrlParser.parse(url, tweaksRepository.getCustomForgeHosts().first())
            if (parsedRef == null) {
                holder.update { it.copy(repoValidationError = getString(Res.string.invalid_github_url)) }
                return@launch
            }
            val sourceHost = (parsedRef.source as? RepositorySource.Forgejo)?.host
            holder.update { it.copy(isValidatingRepo = true, repoValidationError = null, linkValidationStatus = null) }
            try {
                linkRepo(holder, selectedApp, RepoRef(parsedRef.owner, parsedRef.repo, sourceHost))
            } catch (_: RateLimitException) {
                holder.update {
                    it.copy(
                        isValidatingRepo = false,
                        linkValidationStatus = null,
                        repoValidationError = getString(Res.string.rate_limit_try_again),
                    )
                }
            } catch (e: LocalStoreException) {
                logger.error("Failed to link app: ${e.message}")
                holder.update {
                    it.copy(
                        isValidatingRepo = false,
                        linkValidationStatus = null,
                        repoValidationError = getString(Res.string.failed_to_link, e.message ?: ""),
                    )
                }
            }
        }
    }

    private data class RepoRef(
        val owner: String,
        val repo: String,
        val sourceHost: String?,
    )

    // A repo with no release, or none with an installable asset, is linked straight away; otherwise the user picks an asset.
    private suspend fun linkRepo(
        holder: AppsStateHolder,
        selectedApp: DeviceAppUi,
        ref: RepoRef,
    ) {
        holder.update { it.copy(linkValidationStatus = getString(Res.string.validating_repo)) }
        val repoInfo = appsRepository.fetchRepoInfo(ref.owner, ref.repo, ref.sourceHost)
        if (repoInfo == null) {
            val notFound = getString(Res.string.repo_not_found, ref.owner, ref.repo)
            holder.update { it.copy(isValidatingRepo = false, linkValidationStatus = null, repoValidationError = notFound) }
            return
        }

        val checking = getString(Res.string.checking_release)
        holder.update { it.copy(fetchedRepoInfo = repoInfo.toUi(), linkSourceHost = ref.sourceHost, linkValidationStatus = checking) }
        val latestRelease = appsRepository.getLatestRelease(ref.owner, ref.repo, sourceHost = ref.sourceHost)
        val installableAssets =
            latestRelease
                ?.assets
                .orEmpty()
                .filter { installer.isAssetInstallable(it.name) }
                .map { it.toUi() }
                .toImmutableList()
        if (latestRelease == null || installableAssets.isEmpty()) {
            appsRepository.linkAppToRepo(selectedApp.toDomain(), repoInfo, sourceHost = ref.sourceHost)
            holder.update { it.copy(isValidatingRepo = false, linkValidationStatus = null, showLinkSheet = false) }
            announceLinked(holder, selectedApp, repoInfo.owner, repoInfo.name)
        } else {
            showAssetPicker(holder, selectedApp, installableAssets, latestRelease.tagName)
        }
    }

    private fun showAssetPicker(
        holder: AppsStateHolder,
        selectedApp: DeviceAppUi,
        installableAssets: ImmutableList<GithubAssetUi>,
        releaseTag: String,
    ) {
        val suggestedFilter =
            suggestFilterForLink(
                deviceAppName = selectedApp.appName,
                packageName = selectedApp.packageName,
                firstAssetName = installableAssets.firstOrNull()?.name,
                assets = holder.current.linkInstallableAssets,
            )
        holder.update {
            it.copy(
                isValidatingRepo = false,
                linkValidationStatus = null,
                linkStep = LinkStep.PickAsset,
                linkInstallableAssets = installableAssets,
                linkAssetsReleaseTag = releaseTag,
                linkAssetFilter = suggestedFilter.orEmpty(),
                linkAssetFilterError = null,
                linkFallbackToOlder = false,
            )
        }
    }

    private suspend fun announceLinked(
        holder: AppsStateHolder,
        selectedApp: DeviceAppUi,
        owner: String,
        repo: String,
    ) {
        holder.send(AppsEvent.ShowSuccess(getString(Res.string.app_linked_success, selectedApp.appName, owner, repo)))
    }

    // Matches the assets already listed when the step opens, then falls back to a guess from the first asset's name.
    private fun suggestFilterForLink(
        deviceAppName: String,
        packageName: String,
        firstAssetName: String?,
        assets: List<GithubAssetUi>,
    ): String? {
        val tokens =
            listOf(packageName.substringAfterLast('.').lowercase()) +
                deviceAppName.split(' ', '-', '_').map { it.lowercase().trim() }
        return tokens.firstNotNullOfOrNull { matchingToken(it, assets) }
            ?: firstAssetName?.let { AssetFilter.suggestFromAssetName(it) }
    }

    private fun matchingToken(
        rawToken: String,
        assets: List<GithubAssetUi>,
    ): String? {
        if (rawToken.length < MIN_FILTER_TOKEN_LENGTH) return null
        val escaped = Regex.escape(rawToken)
        val regex = Regex(escaped, RegexOption.IGNORE_CASE)
        return escaped.takeIf { assets.any { regex.containsMatchIn(it.name) } }
    }

    private fun linkWithAsset(
        holder: AppsStateHolder,
        asset: GithubAssetUi,
    ) {
        val state = holder.current
        val selectedApp = state.selectedDeviceApp ?: return
        val repoInfo = state.fetchedRepoInfo?.toDomain() ?: return
        val pick =
            AssetPick(
                siblingCount = state.linkInstallableAssets.size,
                releaseTag = state.linkAssetsReleaseTag,
                index = state.linkInstallableAssets.indexOfFirst { it.id == asset.id }.takeIf { it >= 0 },
                filterRegex = state.linkAssetFilter.takeIf { it.isNotBlank() },
                fallbackToOlder = state.linkFallbackToOlder,
            )

        holder.scope.launch {
            holder.update {
                it.copy(linkSelectedAsset = asset, linkDownloadProgress = null, linkValidationStatus = null, repoValidationError = null)
            }
            try {
                linkPickedAsset(holder, selectedApp, repoInfo, asset, pick)
            } catch (e: LocalStoreException) {
                logger.error("Failed to link app: ${e.message}")
                holder.update {
                    it.copy(
                        linkDownloadProgress = null,
                        linkValidationStatus = null,
                        repoValidationError = getString(Res.string.failed_to_link, e.message ?: ""),
                    )
                }
            }
        }
    }

    private data class AssetPick(
        val siblingCount: Int,
        val releaseTag: String?,
        val index: Int?,
        val filterRegex: String?,
        val fallbackToOlder: Boolean,
    )

    private suspend fun linkPickedAsset(
        holder: AppsStateHolder,
        selectedApp: DeviceAppUi,
        repoInfo: GithubRepoInfo,
        asset: GithubAssetUi,
        pick: AssetPick,
    ) {
        appsRepository.linkAppToRepo(
            deviceApp = selectedApp.toDomain(),
            repoInfo = repoInfo,
            assetFilterRegex = pick.filterRegex,
            fallbackToOlderReleases = pick.fallbackToOlder,
            pickedAssetName = asset.name,
            pickedAssetSiblingCount = pick.siblingCount,
            pickedAssetReleaseTag = pick.releaseTag,
            pickedAssetIndex = pick.index,
            sourceHost = holder.current.linkSourceHost,
        )
        holder.update { it.copy(linkDownloadProgress = null, linkValidationStatus = null, showLinkSheet = false) }
        announceLinked(holder, selectedApp, repoInfo.owner, repoInfo.name)
    }

    private fun DeviceAppUi.toCandidate(): ExternalAppCandidate =
        ExternalAppCandidate(
            packageName = packageName,
            appLabel = appName,
            versionName = versionName,
            versionCode = versionCode,
            signingFingerprint = signingFingerprint,
            installerKind = InstallerKind.UNKNOWN,
            manifestHint = null,
            firstSeenAt = Clock.System.now().toEpochMilliseconds(),
        )

    private fun AppsState.withLinkSheetOpened(): AppsState =
        copy(
            showLinkSheet = true,
            linkStep = LinkStep.PickApp,
            deviceApps = persistentListOf(),
            deviceAppSearchQuery = "",
            selectedDeviceApp = null,
            repoUrl = "",
            repoValidationError = null,
            fetchedRepoInfo = null,
            linkSuggestions = persistentListOf(),
            linkSearchError = null,
            linkSearchLoading = false,
        )

    private fun AppsState.withLinkSheetClosed(): AppsState =
        copy(
            showLinkSheet = false,
            linkStep = LinkStep.PickApp,
            deviceApps = persistentListOf(),
            deviceAppSearchQuery = "",
            selectedDeviceApp = null,
            repoUrl = "",
            repoValidationError = null,
            linkValidationStatus = null,
            linkInstallableAssets = persistentListOf(),
            linkSelectedAsset = null,
            linkDownloadProgress = null,
            fetchedRepoInfo = null,
            isValidatingRepo = false,
            linkAssetFilter = "",
            linkAssetFilterError = null,
            linkFallbackToOlder = false,
            linkSuggestions = persistentListOf(),
            linkSearchError = null,
            linkSearchLoading = false,
        )

    private fun AppsState.withSmartMatchStarted(app: DeviceAppUi): AppsState =
        copy(
            selectedDeviceApp = app,
            linkStep = LinkStep.SmartMatch,
            linkSearchLoading = true,
            linkSuggestions = persistentListOf(),
            linkSearchError = null,
            repoUrl = "",
            repoValidationError = null,
            fetchedRepoInfo = null,
        )

    private fun AppsState.backToAppPicker(): AppsState =
        copy(
            linkStep = LinkStep.PickApp,
            selectedDeviceApp = null,
            repoUrl = "",
            repoValidationError = null,
            fetchedRepoInfo = null,
            linkInstallableAssets = persistentListOf(),
            linkSelectedAsset = null,
            linkDownloadProgress = null,
            linkSuggestions = persistentListOf(),
            linkSearchError = null,
            linkSearchLoading = false,
        )

    private fun AppsState.backToSmartMatch(): AppsState =
        copy(
            linkStep = LinkStep.SmartMatch,
            repoUrl = "",
            repoValidationError = null,
            fetchedRepoInfo = null,
            linkInstallableAssets = persistentListOf(),
            linkSelectedAsset = null,
            linkDownloadProgress = null,
        )

    private fun AppsState.backToEnterUrl(): AppsState =
        copy(
            linkStep = LinkStep.EnterUrl,
            linkInstallableAssets = persistentListOf(),
            linkSelectedAsset = null,
            linkDownloadProgress = null,
            linkValidationStatus = null,
            repoValidationError = null,
            linkAssetFilter = "",
            linkAssetFilterError = null,
            linkFallbackToOlder = false,
        )

    private companion object {
        const val INVALID_FILTER = "invalid"
        const val MAX_SUGGESTIONS = 8
        const val MIN_FILTER_TOKEN_LENGTH = 3
    }
}
