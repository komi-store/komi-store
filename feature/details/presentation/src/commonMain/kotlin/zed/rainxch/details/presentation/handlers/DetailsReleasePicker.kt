package zed.rainxch.details.presentation.handlers

import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.error.SystemPackagesException
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.utils.AssetVariant
import zed.rainxch.details.domain.model.ReleaseCategory
import zed.rainxch.details.presentation.DetailsAction
import zed.rainxch.details.presentation.model.TranslationState
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.variant_first_pin_toast
import zed.rainxch.githubstore.core.presentation.res.variant_first_pin_toast_generic
import zed.rainxch.githubstore.core.presentation.res.variant_unpinned_toast

// What the user picks on the page: channel, release, asset, installed app and platform, and the pins and flags behind them.
internal class DetailsReleasePicker(
    private val selection: ReleaseSelection,
    private val installedAppsRepository: InstalledAppsRepository,
    private val tweaksRepository: TweaksRepository,
    private val content: DetailsContentHandler,
    private val logger: KomiStoreLogger,
) {
    internal fun onAction(
        session: DetailsSession,
        action: DetailsAction.Release,
    ) {
        when (action) {
            is DetailsAction.SelectReleaseCategory -> selectCategory(session, action.category)
            is DetailsAction.SelectRelease -> selectRelease(session, action.releaseId)
            is DetailsAction.SelectDownloadAsset -> selectAsset(session, action.assetId)
            is DetailsAction.OnSelectInstalledApp -> switchToInstalledApp(session, action.packageName)
            is DetailsAction.OnPlatformChipClick -> onPlatformChip(session, action.platform)
            DetailsAction.OnJumpToDeviceBuild -> jumpToDeviceBuild(session)
            DetailsAction.OnDismissPlatformHandoff -> session.update { it.copy(handoffPlatform = null) }
            DetailsAction.ToggleVersionPicker -> session.update { it.copy(isVersionPickerVisible = !it.isVersionPickerVisible) }
            DetailsAction.ToggleReleaseAssetsPicker -> session.update { it.copy(isReleaseSelectorVisible = !it.isReleaseSelectorVisible) }
            DetailsAction.UnpinPreferredVariant -> unpinPreferredVariant(session)
            DetailsAction.ToggleIncludeBetas -> toggleIncludeBetas(session)
            DetailsAction.OnAcknowledgeChannelChipCoachmark -> acknowledgeChannelChipCoachmark(session)
            is DetailsAction.OnToggleShowAllPlatforms -> session.launch { tweaksRepository.setShowAllPlatforms(action.enabled) }
        }
    }

    // The settings flows fall back to their defaults on a storage failure, so these reads and writes can't fail.
    internal fun observeChannelChipCoachmark(session: DetailsSession) {
        session.launch {
            if (tweaksRepository.getChannelChipCoachmarkShown().first()) return@launch
            val firstLoaded = session.state.first { !it.isLoading }
            if (firstLoaded.installedApp == null) return@launch
            session.update { it.copy(isChannelChipCoachmarkPending = true) }
        }
    }

    internal fun observeShowAllPlatforms(session: DetailsSession) {
        session.launch {
            tweaksRepository.getShowAllPlatforms().collect { enabled ->
                session.update { it.copy(showAllPlatforms = enabled) }
            }
        }
    }

    private fun acknowledgeChannelChipCoachmark(session: DetailsSession) {
        session.update { it.copy(isChannelChipCoachmarkPending = false) }
        session.launch { tweaksRepository.setChannelChipCoachmarkShown(true) }
    }

    private fun selectCategory(
        session: DetailsSession,
        category: ReleaseCategory,
    ) {
        session.hasUserPickedRelease = true
        val current = session.current
        val filtered = current.allReleases.filter { it.isIn(category) }
        val selected = filtered.firstOrNull { it.id in current.deviceBuildReleaseIds } ?: filtered.firstOrNull()
        val assets = selection.assetsFor(current, selected)
        val installedApp =
            selection.primaryInstalledApp(
                current.installedApps,
                assets.primary?.name,
                assets.installable,
                current.allReleases,
                releaseTag = selected?.tagName,
            )
        session.whatsNewTranslationJob?.cancel()
        session.update {
            it.copy(
                installedApp = installedApp,
                selectedReleaseCategory = category,
                selectedRelease = selected,
                installableAssets = assets.installable,
                primaryAsset = assets.primary,
                whatsNewTranslation = TranslationState(),
            )
        }
        content.maybeAutoTranslate(session, readmeBody = null, releaseDescription = selected?.description)
    }

    private fun selectRelease(
        session: DetailsSession,
        releaseId: Long,
    ) {
        session.current.allReleases
            .firstOrNull { it.id == releaseId }
            ?.let { selectRelease(session, it) }
    }

    internal fun selectRelease(
        session: DetailsSession,
        release: GithubRelease,
    ) {
        session.hasUserPickedRelease = true
        val current = session.current
        val assets = selection.assetsFor(current, release)
        val installedApp =
            selection.primaryInstalledApp(
                current.installedApps,
                assets.primary?.name,
                assets.installable,
                current.allReleases,
                releaseTag = release.tagName,
            )
        session.whatsNewTranslationJob?.cancel()
        session.update {
            it.copy(
                installedApp = installedApp,
                selectedRelease = release,
                installableAssets = assets.installable,
                primaryAsset = assets.primary,
                isVersionPickerVisible = false,
                whatsNewTranslation = TranslationState(),
                whatsNewMeasuredHeightPx = null,
            )
        }
        content.maybeAutoTranslate(session, readmeBody = null, releaseDescription = release.description)
    }

    private fun selectAsset(
        session: DetailsSession,
        assetId: Long,
    ) {
        val current = session.current
        val asset = current.installableAssets.firstOrNull { it.id == assetId } ?: return
        session.hasUserPickedRelease = true
        val installedApp =
            selection.primaryInstalledApp(
                apps = current.installedApps,
                primaryAssetName = asset.name,
                releaseAssets = current.installableAssets,
                releaseHistory = current.allReleases,
                releaseTag = current.selectedRelease?.tagName,
            )
        session.update { it.copy(primaryAsset = asset, installedApp = installedApp) }
        if (installedApp != null) persistPreferredVariant(session, asset)
    }

    private fun switchToInstalledApp(
        session: DetailsSession,
        packageName: String,
    ) {
        session.hasUserPickedRelease = true
        val current = session.current
        val app = current.installedApps.firstOrNull { it.packageName == packageName } ?: return
        val anchor = app.installedAssetName ?: app.latestAssetName ?: app.pendingInstallAssetName
        val category = current.selectedReleaseCategory
        val inCategory = selection.firstOwnedBy(current, app, category, anchor)
        val release = inCategory ?: selection.firstOwnedBy(current, app, ReleaseCategory.ALL, anchor) ?: return
        val assets = selection.assetsFor(current, release, app, anchor)
        val insights = selection.insights(current.allReleases, app)
        session.whatsNewTranslationJob?.cancel()
        session.update {
            it
                .copy(
                    installedApp = app,
                    selectedRelease = release,
                    selectedReleaseCategory = if (inCategory != null) category else release.category(),
                    installableAssets = assets.installable,
                    primaryAsset = assets.primary,
                    isVersionPickerVisible = false,
                    whatsNewTranslation = TranslationState(),
                    whatsNewMeasuredHeightPx = null,
                ).withInsights(insights)
        }
        content.maybeAutoTranslate(session, readmeBody = null, releaseDescription = release.description)
    }

    private fun onPlatformChip(
        session: DetailsSession,
        platform: DiscoveryPlatform,
    ) {
        if (platform == session.current.devicePlatform) {
            jumpToDeviceBuild(session)
        } else {
            session.update { it.copy(handoffPlatform = platform) }
        }
    }

    private fun jumpToDeviceBuild(session: DetailsSession) {
        val current = session.current
        val deviceBuildIds = current.deviceBuildReleaseIds
        if (current.selectedRelease?.id in deviceBuildIds) return
        val selectedLine = current.selectedRelease?.let { current.releaseLines[it.id] }
        val targets =
            current.allReleases.filter {
                it.id in deviceBuildIds && (selectedLine == null || current.releaseLines[it.id] == selectedLine)
            }
        val inCategory = targets.firstInCategory(current.selectedReleaseCategory)
        when {
            inCategory != null -> {
                selectRelease(session, inCategory)
            }

            targets.isNotEmpty() -> {
                selectCategory(session, ReleaseCategory.ALL)
                selectRelease(session, targets.first())
            }
        }
    }

    private fun persistPreferredVariant(
        session: DetailsSession,
        picked: GithubAsset,
    ) {
        val installedApp = session.current.installedApp ?: return
        val pin =
            VariantPin.of(
                picked = picked,
                installable = session.current.installableAssets,
                releaseTag = session.current.selectedRelease?.tagName,
            ) ?: return
        if (pin.matches(installedApp) && !installedApp.preferredVariantStale) return
        session.launch {
            try {
                installedAppsRepository.setPreferredVariant(
                    packageName = installedApp.packageName,
                    variant = pin.variant,
                    tokens = pin.tokens,
                    glob = pin.glob,
                    pickedIndex = pin.pickedIndex,
                    siblingCount = pin.siblingCount,
                )
                if (installedApp.hasNoPin()) session.message(firstPinMessage(pin))
            } catch (e: LocalStoreException) {
                logger.error("Failed to persist preferred variant for ${installedApp.packageName}: ${e.message}")
            }
        }
    }

    private suspend fun firstPinMessage(pin: VariantPin): String {
        val label = pin.label
        return if (label.isNotEmpty()) {
            getString(Res.string.variant_first_pin_toast, label)
        } else {
            getString(Res.string.variant_first_pin_toast_generic)
        }
    }

    private fun InstalledApp.hasNoPin(): Boolean =
        preferredAssetVariant.isNullOrBlank() && preferredAssetTokens.isNullOrBlank() && assetGlobPattern.isNullOrBlank()

    private fun unpinPreferredVariant(session: DetailsSession) {
        val installedApp = session.current.installedApp ?: return
        session.launch {
            try {
                installedAppsRepository.clearPreferredVariant(installedApp.packageName)
                session.message(getString(Res.string.variant_unpinned_toast))
            } catch (e: LocalStoreException) {
                logger.error("Failed to clear preferred variant for ${installedApp.packageName}: ${e.message}")
            }
        }
    }

    private fun toggleIncludeBetas(session: DetailsSession) {
        acknowledgeChannelChipCoachmark(session)
        val app = session.current.installedApp ?: return
        session.launch {
            try {
                installedAppsRepository.setIncludePreReleases(packageName = app.packageName, enabled = !app.includePreReleases)
                installedAppsRepository.checkForUpdates(app.packageName)
            } catch (e: LocalStoreException) {
                logger.warn("toggleIncludeBetas failed for ${app.packageName}: ${e.message}")
            } catch (e: SystemPackagesException) {
                logger.warn("toggleIncludeBetas failed for ${app.packageName}: ${e.message}")
            } catch (e: RateLimitException) {
                logger.warn("toggleIncludeBetas failed for ${app.packageName}: ${e.message}")
            }
        }
    }

    // A picked asset's variant, tokens and glob, plus its position among the release's installable assets.
    private class VariantPin(
        val variant: String?,
        val tokens: String?,
        val glob: String?,
        val pickedIndex: Int?,
        val siblingCount: Int?,
        val label: String,
    ) {
        fun matches(app: InstalledApp): Boolean {
            val sameVariant =
                if (variant == null && app.preferredAssetVariant == null) {
                    true
                } else {
                    variant?.equals(app.preferredAssetVariant, ignoreCase = true) == true
                }
            val samePattern = tokens == app.preferredAssetTokens && glob == app.assetGlobPattern
            val samePosition = pickedIndex == app.pickedAssetIndex && siblingCount == app.pickedAssetSiblingCount
            return sameVariant && samePattern && samePosition
        }

        companion object {
            fun of(
                picked: GithubAsset,
                installable: List<GithubAsset>,
                releaseTag: String?,
            ): VariantPin? =
                AssetVariant.fingerprintFromPickedAsset(picked.name, installable.size, releaseTag)?.let { fingerprint ->
                    VariantPin(
                        variant = fingerprint.variant,
                        tokens = AssetVariant.serializeTokens(fingerprint.tokens),
                        glob = fingerprint.glob,
                        pickedIndex = installable.indexOfFirst { it.id == picked.id }.takeIf { it >= 0 },
                        siblingCount = installable.size.takeIf { it > 0 },
                        label = fingerprint.variant ?: fingerprint.tokens.firstOrNull().orEmpty(),
                    )
                }
        }
    }
}
