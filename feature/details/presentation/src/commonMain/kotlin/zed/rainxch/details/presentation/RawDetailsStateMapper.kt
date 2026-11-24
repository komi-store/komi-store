package zed.rainxch.details.presentation

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.isEffectivelyPreRelease
import zed.rainxch.core.domain.utils.AssetOwnership
import zed.rainxch.core.domain.utils.AssetVariant
import zed.rainxch.core.domain.utils.PlatformRelease
import zed.rainxch.core.domain.utils.VersionMath
import zed.rainxch.core.domain.utils.assetPlatformOf
import zed.rainxch.core.domain.utils.newestReleasePerPlatform
import zed.rainxch.core.presentation.components.reviews.toUi
import zed.rainxch.details.domain.model.ReleaseCategory
import zed.rainxch.details.presentation.mappers.toHandoffUi
import zed.rainxch.details.presentation.mappers.toUi
import zed.rainxch.details.presentation.model.TranslationState
import zed.rainxch.details.presentation.utils.releaseLineLabel

internal fun RawDetailsState.toView(): DetailsState {
    val releases = releaseView()
    return DetailsState()
        .withPage(this)
        .withAssets(this)
        .withReleases(this, releases)
        .withInstallFlow(this)
        .withContent(this)
        .withPrompts(this, releases)
}

internal fun RawDetailsState.latestStableRelease(): GithubRelease? =
    allReleases
        .filter { !it.isEffectivelyPreRelease() }
        .maxByOrNull { it.publishedAt }

// What the release controls derive from the channel, the selected release's line and the device builds.
private class ReleaseView(
    val filtered: List<GithubRelease>,
    val selectedLine: String?,
    val platformReleases: List<PlatformRelease>,
    val selectedHasDeviceBuild: Boolean,
    val newerReleasesLackDeviceBuild: Boolean,
    val deviceBuildTarget: GithubRelease?,
)

private fun RawDetailsState.releaseView(): ReleaseView {
    val filtered =
        when (selectedReleaseCategory) {
            ReleaseCategory.STABLE -> allReleases.filter { !it.isEffectivelyPreRelease() }
            ReleaseCategory.PRE_RELEASE -> allReleases.filter { it.isEffectivelyPreRelease() }
            ReleaseCategory.ALL -> allReleases
        }
    val selectedLine = selectedRelease?.let { releaseLines[it.id] }
    val appReleases = if (selectedLine == null) filtered else filtered.filter { releaseLines[it.id] == selectedLine }
    val selectedHasDeviceBuild = selectedRelease?.let { it.id in deviceBuildReleaseIds } ?: true
    return ReleaseView(
        filtered = filtered,
        selectedLine = selectedLine,
        platformReleases =
            newestReleasePerPlatform(
                releases = appReleases,
                releasePlatforms = { releasePlatforms[it.id].orEmpty() },
                devicePlatform = devicePlatform,
            ),
        selectedHasDeviceBuild = selectedHasDeviceBuild,
        newerReleasesLackDeviceBuild = selectedHasDeviceBuild && newerReleasesLackDeviceBuild(appReleases),
        deviceBuildTarget = if (selectedHasDeviceBuild) null else deviceBuildTarget(appReleases, selectedLine),
    )
}

private fun RawDetailsState.newerReleasesLackDeviceBuild(appReleases: List<GithubRelease>): Boolean {
    val selectedIndex = selectedRelease?.let { selected -> appReleases.indexOfFirst { it.id == selected.id } } ?: -1
    return selectedIndex > 0 && appReleases.subList(0, selectedIndex).none { it.id in deviceBuildReleaseIds }
}

private fun RawDetailsState.deviceBuildTarget(
    appReleases: List<GithubRelease>,
    selectedLine: String?,
): GithubRelease? =
    appReleases.firstOrNull { it.id in deviceBuildReleaseIds }
        ?: allReleases.firstOrNull {
            it.id in deviceBuildReleaseIds && (selectedLine == null || releaseLines[it.id] == selectedLine)
        }

private fun DetailsState.withPage(raw: RawDetailsState): DetailsState =
    copy(
        isLoading = raw.isLoading,
        isCurrentUserOwner = raw.isCurrentUserOwner,
        isRefreshing = raw.isRefreshing,
        refreshCooldownUntilEpochMs = raw.refreshCooldownUntilEpochMs,
        errorMessage = raw.errorMessage,
        userProfile = raw.userProfile?.toUi(),
        repository = raw.repository?.toUi(),
        stats = raw.stats?.toUi(),
        installLogs = raw.installLogs.toImmutableList(),
        systemArchitecture = raw.systemArchitecture,
        isObtainiumAvailable = raw.isObtainiumAvailable,
        isObtainiumEnabled = raw.isObtainiumEnabled,
        isAppManagerAvailable = raw.isAppManagerAvailable,
        isAppManagerEnabled = raw.isAppManagerEnabled,
        installedApp = raw.installedApp?.toUi(),
        installedApps = raw.installedApps.map { it.toUi() }.toImmutableList(),
        isFavourite = raw.isFavourite,
        isStarred = raw.isStarred,
        isTrackingApp = raw.isTrackingApp,
        deviceLanguageCode = raw.deviceLanguageCode,
        isComingFromUpdate = raw.isComingFromUpdate,
        devicePlatform = raw.devicePlatform,
        reviewSummary = raw.reviewSummary?.toUi(),
        topReviews = raw.topReviews.map { it.toUi(viewerId = null, votes = emptyMap()) }.toImmutableList(),
    )

private fun DetailsState.withAssets(raw: RawDetailsState): DetailsState {
    val crossPlatformAssets =
        raw.selectedRelease
            ?.assets
            ?.filter { assetPlatformOf(it.name) != null }
            .orEmpty()
    return copy(
        primaryAsset = raw.primaryAsset?.toUi(),
        installableAssets = raw.installableAssets.map { it.toUi() }.toImmutableList(),
        crossPlatformAssets = crossPlatformAssets.map { it.toUi() }.toImmutableList(),
        assetVariantStatuses = raw.assetVariantStatuses(crossPlatformAssets),
        pinnedVariantLabel = raw.pinnedVariantLabel(),
        isReleaseSelectorVisible = raw.isReleaseSelectorVisible,
        showAllPlatforms = raw.showAllPlatforms,
    )
}

// Only the asset sheet shows these, so they are worked out while it is open.
private fun RawDetailsState.assetVariantStatuses(crossPlatformAssets: List<GithubAsset>): ImmutableMap<Long, AssetOwnership.VariantStatus> =
    if (isReleaseSelectorVisible) {
        (installableAssets + crossPlatformAssets)
            .mapNotNull { asset ->
                AssetOwnership.variantStatus(asset.name, selectedRelease?.tagName, installedApps)?.let { asset.id to it }
            }.toMap()
            .toImmutableMap()
    } else {
        persistentMapOf()
    }

private fun RawDetailsState.pinnedVariantLabel(): String? =
    installedApp?.preferredAssetVariant?.let { stored ->
        primaryAsset?.name?.let { name -> AssetVariant.extract(name)?.takeIf { it.isNotBlank() } } ?: stored
    }

private fun DetailsState.withReleases(
    raw: RawDetailsState,
    releases: ReleaseView,
): DetailsState {
    val latestStable = raw.latestStableRelease()
    return copy(
        selectedRelease = raw.displayedRelease(),
        allReleases = raw.allReleases.map { it.toUi() }.toImmutableList(),
        releasesLoadFailed = raw.releasesLoadFailed,
        isRetryingReleases = raw.isRetryingReleases,
        selectedReleaseCategory = raw.selectedReleaseCategory,
        isVersionPickerVisible = raw.isVersionPickerVisible,
        filteredReleases = releases.filtered.map { it.toUi() }.toImmutableList(),
        latestStableRelease = latestStable?.toUi(),
        canSwitchToStable = raw.computeCanSwitchToStable(latestStable),
        platformReleases = releases.platformReleases.map { it.toUi() }.toImmutableList(),
        releasePlatforms = raw.releasePlatforms,
        deviceBuildReleaseIds = raw.deviceBuildReleaseIds,
        selectedHasDeviceBuild = releases.selectedHasDeviceBuild,
        newerReleasesLackDeviceBuild = releases.newerReleasesLackDeviceBuild,
        deviceBuildTarget = releases.deviceBuildTarget?.toUi(),
        releaseLines = raw.releaseLines,
        selectedAppLabel = releases.selectedLine?.let { releaseLineLabel(it, raw.repository?.name.orEmpty()) },
        stalledStableSinceDays = raw.stalledStableSinceDays,
        mergedChangelog = raw.mergedChangelog,
        mergedChangelogBaseTag = raw.mergedChangelogBaseTag,
        latestStableHasInstallableAsset = raw.latestStableHasInstallableAsset,
    )
}

// Auto-translate and the manual toggle only fill the translation states; the page renders one text per section.
private fun RawDetailsState.displayedRelease() =
    selectedRelease?.toUi()?.let { release ->
        whatsNewTranslation.visibleTranslation()?.let { release.copy(description = it) } ?: release
    }

private fun DetailsState.withInstallFlow(raw: RawDetailsState): DetailsState =
    copy(
        isDownloading = raw.isDownloading,
        downloadProgressPercent = raw.downloadProgressPercent,
        downloadedBytes = raw.downloadedBytes,
        totalBytes = raw.totalBytes,
        isInstalling = raw.isInstalling,
        downloadError = raw.downloadError,
        installError = raw.installError,
        downloadStage = raw.downloadStage,
        attestationStatus = raw.attestationStatus,
        isPendingInstallReady = raw.computeIsPendingInstallReady(),
        showExternalInstallerPrompt = raw.showExternalInstallerPrompt,
        pendingInstallFilePath = raw.pendingInstallFilePath,
    )

private fun DetailsState.withContent(raw: RawDetailsState): DetailsState {
    val shownReadme = raw.aboutTranslation.visibleTranslation()
    return copy(
        readmeMarkdown = shownReadme ?: raw.readmeMarkdown,
        readmeLanguage =
            if (shownReadme != null) raw.aboutTranslation.targetLanguageDisplayName ?: raw.readmeLanguage else raw.readmeLanguage,
        isAboutExpanded = raw.isAboutExpanded,
        isWhatsNewExpanded = raw.isWhatsNewExpanded,
        aboutMeasuredHeightPx = raw.aboutMeasuredHeightPx,
        whatsNewMeasuredHeightPx = raw.whatsNewMeasuredHeightPx,
        aboutTranslation = raw.aboutTranslation,
        whatsNewTranslation = raw.whatsNewTranslation,
        isLanguagePickerVisible = raw.isLanguagePickerVisible,
        languagePickerTarget = raw.languagePickerTarget,
    )
}

private fun DetailsState.withPrompts(
    raw: RawDetailsState,
    releases: ReleaseView,
): DetailsState =
    copy(
        downgradeWarning = raw.downgradeWarning,
        signingKeyWarning = raw.signingKeyWarning?.toUi(),
        showUninstallConfirmation = raw.showUninstallConfirmation,
        showUnlinkConfirmation = raw.showUnlinkConfirmation,
        apkInspection = raw.apkInspection?.toUi(),
        isApkInspectSheetVisible = raw.isApkInspectSheetVisible,
        isApkInspectLoading = raw.isApkInspectLoading,
        isApkInspectCoachmarkPending = raw.isApkInspectCoachmarkPending,
        isChannelChipCoachmarkPending = raw.isChannelChipCoachmarkPending,
        handoff =
            raw.handoffPlatform?.let { platform ->
                releases.platformReleases.firstOrNull { it.platform == platform }?.toHandoffUi()
            },
    )

private fun RawDetailsState.computeCanSwitchToStable(latestStable: GithubRelease?): Boolean {
    val installedVersion = installedApp?.installedVersion
    val installedIsPreRelease =
        installedVersion != null &&
            allReleases
                .firstOrNull { VersionMath.isSameVersion(it.tagName, installedVersion) }
                ?.isEffectivelyPreRelease() == true
    val stableOffered = latestStable != null && latestStableHasInstallableAsset
    return installedIsPreRelease && stableOffered && !VersionMath.isSameVersion(latestStable.tagName, installedVersion)
}

private fun RawDetailsState.computeIsPendingInstallReady(): Boolean {
    val parked = installedApp?.takeIf { !it.pendingInstallFilePath.isNullOrBlank() }
    val parkedTag = parked?.pendingInstallVersion
    val parkedAsset = parked?.pendingInstallAssetName
    val tagMatches = parkedTag != null && parkedTag == selectedRelease?.tagName
    val assetMatches = parkedAsset != null && parkedAsset == primaryAsset?.name
    return tagMatches && assetMatches
}

private fun TranslationState.visibleTranslation(): String? =
    if (isShowingTranslation) {
        translatedText?.takeIf { it.isNotBlank() }
    } else {
        null
    }
