package zed.rainxch.apps.presentation.settings

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.apps.presentation.AppsAction
import zed.rainxch.apps.presentation.AppsState
import zed.rainxch.apps.presentation.AppsStateHolder
import zed.rainxch.apps.presentation.mappers.toUi
import zed.rainxch.apps.presentation.model.AdvancedPreviewMessage
import zed.rainxch.apps.presentation.model.InstalledAppUi
import zed.rainxch.apps.presentation.model.VariantOption
import zed.rainxch.apps.presentation.model.VariantPickerError
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.smartdetect.MatchingPreview
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.utils.AssetFilter
import zed.rainxch.core.domain.utils.AssetVariant
import zed.rainxch.core.presentation.utils.formatFileSize
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.apps_variant_subtitle
import kotlin.time.Duration.Companion.milliseconds

// Which asset a tracked app installs: the advanced filter sheet (regex plus older-release fallback) and the variant pin.
class AssetMatchSettings(
    private val installedAppsRepository: InstalledAppsRepository,
    private val logger: KomiStoreLogger,
) {
    private var previewJob: Job? = null

    fun onAction(
        holder: AppsStateHolder,
        action: AppsAction.AdvancedFilter,
    ) {
        when (action) {
            is AppsAction.OnOpenAdvancedSettings -> {
                holder.update { it.withAdvancedSheetFor(action.app) }
                refreshPreview(holder)
            }

            AppsAction.OnDismissAdvancedSettings -> {
                holder.update { it.withAdvancedSheetClosed() }
                previewJob?.cancel()
                previewJob = null
            }

            is AppsAction.OnAdvancedFilterChanged -> {
                val error = AssetFilter.parse(action.filter)?.exceptionOrNull()?.let { INVALID_FILTER }
                holder.update { it.copy(advancedFilterDraft = action.filter, advancedFilterError = error) }
                if (error == null) schedulePreviewRefresh(holder)
            }

            is AppsAction.OnAdvancedFallbackToggled -> {
                holder.update { it.copy(advancedFallbackDraft = action.enabled) }
                schedulePreviewRefresh(holder)
            }

            AppsAction.OnAdvancedSaveFilter -> {
                saveAdvancedSettings(holder)
            }

            AppsAction.OnAdvancedClearFilter -> {
                holder.update { it.copy(advancedFilterDraft = "", advancedFilterError = null) }
                schedulePreviewRefresh(holder)
            }

            AppsAction.OnAdvancedRefreshPreview -> {
                refreshPreview(holder)
            }
        }
    }

    fun onAction(
        holder: AppsStateHolder,
        action: AppsAction.Variant,
        onResumeUpdate: (InstalledAppUi) -> Unit,
    ) {
        when (action) {
            is AppsAction.OnOpenVariantPicker -> openVariantPicker(holder, action.app, action.resumeUpdateAfterPick)
            AppsAction.OnDismissVariantPicker -> holder.update { it.withVariantPickerClosed() }
            is AppsAction.OnVariantSelected -> saveVariantSelection(holder, action.variant, onResumeUpdate)
            AppsAction.OnResetVariantToAuto -> saveVariantSelection(holder, null, onResumeUpdate)
        }
    }

    fun openVariantPicker(
        holder: AppsStateHolder,
        app: InstalledAppUi,
        resumeUpdateAfterPick: Boolean,
    ) {
        holder.update {
            it.copy(
                variantPickerApp = app,
                variantPickerLoading = true,
                variantPickerOptions = persistentListOf(),
                variantPickerCurrentVariant = app.preferredAssetVariant,
                variantPickerError = null,
                variantPickerResumeUpdateAfterPick = resumeUpdateAfterPick,
            )
        }
        holder.scope.launch {
            try {
                val preview = previewFor(app, app.assetFilterRegex, app.fallbackToOlderReleases)
                val options = variantOptions(preview)
                holder.update {
                    it.copy(
                        variantPickerLoading = false,
                        variantPickerOptions = options.toImmutableList(),
                        variantPickerError =
                            when {
                                preview.matchedAssets.isEmpty() -> VariantPickerError.NoAssets
                                options.isEmpty() -> VariantPickerError.NoPinnableVariants
                                else -> null
                            },
                    )
                }
            } catch (e: RateLimitException) {
                logger.error("Failed to load variant picker for ${app.packageName}: ${e.message}")
                holder.update { it.copy(variantPickerLoading = false, variantPickerError = VariantPickerError.LoadFailed) }
            }
        }
    }

    private suspend fun variantOptions(preview: MatchingPreview): List<VariantOption> =
        preview.matchedAssets.mapNotNull { asset ->
            AssetVariant
                .extract(asset.name, preview.release?.tagName)
                ?.takeIf { it.isNotEmpty() }
                ?.let { variant ->
                    VariantOption(
                        assetId = asset.id,
                        variant = variant,
                        subtitle = getString(Res.string.apps_variant_subtitle, asset.name, formatFileSize(asset.size)),
                    )
                }
        }

    private fun saveVariantSelection(
        holder: AppsStateHolder,
        variant: String?,
        onResumeUpdate: (InstalledAppUi) -> Unit,
    ) {
        val app = holder.current.variantPickerApp ?: return
        val resume = holder.current.variantPickerResumeUpdateAfterPick

        holder.scope.launch {
            try {
                installedAppsRepository.setPreferredVariant(packageName = app.packageName, variant = variant)
            } catch (e: LocalStoreException) {
                logger.error("Failed to save preferred variant for ${app.packageName}: ${e.message}")
                holder.update { it.copy(variantPickerError = VariantPickerError.SaveFailed) }
                return@launch
            }

            holder.update { it.withVariantPickerClosed() }
            if (resume) {
                refreshedApp(app.packageName)?.let(onResumeUpdate)
            }
        }
    }

    private suspend fun refreshedApp(packageName: String): InstalledAppUi? =
        try {
            installedAppsRepository.getAppByPackage(packageName)?.toUi()
        } catch (e: LocalStoreException) {
            logger.error("Could not reload $packageName to resume its update: ${e.message}")
            null
        }

    private fun schedulePreviewRefresh(holder: AppsStateHolder) {
        previewJob?.cancel()
        previewJob =
            holder.scope.launch {
                delay(PREVIEW_DEBOUNCE)
                refreshPreview(holder)
            }
    }

    private fun refreshPreview(holder: AppsStateHolder) {
        val app = holder.current.advancedSettingsApp ?: return
        val draftFilter = holder.current.advancedFilterDraft
        val draftFallback = holder.current.advancedFallbackDraft

        if (AssetFilter.parse(draftFilter)?.isFailure == true) {
            holder.update {
                it.copy(
                    advancedPreviewLoading = false,
                    advancedPreviewMatched = persistentListOf(),
                    advancedPreviewTag = null,
                    advancedPreviewMessage = null,
                    advancedFilterError = INVALID_FILTER,
                )
            }
            return
        }

        previewJob?.cancel()
        previewJob =
            holder.scope.launch {
                holder.update { it.copy(advancedPreviewLoading = true) }
                try {
                    val preview = previewFor(app, draftFilter.takeIf { it.isNotBlank() }, draftFallback)
                    holder.update { it.withPreview(preview) }
                } catch (e: RateLimitException) {
                    logger.error("Failed to preview matching assets: ${e.message}")
                    holder.update {
                        it.copy(
                            advancedPreviewLoading = false,
                            advancedPreviewMatched = persistentListOf(),
                            advancedPreviewTag = null,
                            advancedPreviewMessage = AdvancedPreviewMessage.PreviewFailed,
                        )
                    }
                }
            }
    }

    private suspend fun previewFor(
        app: InstalledAppUi,
        regex: String?,
        fallbackToOlderReleases: Boolean,
    ): MatchingPreview =
        installedAppsRepository.previewMatchingAssets(
            owner = app.repoOwner,
            repo = app.repoName,
            regex = regex,
            includePreReleases = app.includePreReleases,
            fallbackToOlderReleases = fallbackToOlderReleases,
        )

    private fun saveAdvancedSettings(holder: AppsStateHolder) {
        val app = holder.current.advancedSettingsApp ?: return
        val draftFilter = holder.current.advancedFilterDraft.trim()
        val draftFallback = holder.current.advancedFallbackDraft

        if (AssetFilter.parse(draftFilter)?.isFailure == true) {
            holder.update { it.copy(advancedFilterError = INVALID_FILTER) }
            return
        }

        holder.scope.launch {
            holder.update { it.copy(advancedSavingFilter = true) }
            try {
                installedAppsRepository.setAssetFilter(
                    packageName = app.packageName,
                    regex = draftFilter.takeIf { it.isNotEmpty() },
                    fallbackToOlderReleases = draftFallback,
                )
                holder.update { it.withAdvancedSheetClosed() }
            } catch (e: LocalStoreException) {
                logger.error("Failed to save advanced settings: ${e.message}")
                holder.update { it.copy(advancedSavingFilter = false, advancedPreviewMessage = AdvancedPreviewMessage.SaveFailed) }
            }
        }
    }

    private fun AppsState.withAdvancedSheetFor(app: InstalledAppUi): AppsState =
        copy(
            advancedSettingsApp = app,
            advancedFilterDraft = app.assetFilterRegex.orEmpty(),
            advancedFallbackDraft = app.fallbackToOlderReleases,
            advancedFilterError = null,
            advancedPreviewLoading = true,
            advancedPreviewMatched = persistentListOf(),
            advancedPreviewTag = null,
            advancedPreviewMessage = null,
            advancedSavingFilter = false,
        )

    private fun AppsState.withAdvancedSheetClosed(): AppsState =
        copy(
            advancedSettingsApp = null,
            advancedFilterDraft = "",
            advancedFallbackDraft = false,
            advancedFilterError = null,
            advancedPreviewLoading = false,
            advancedPreviewMatched = persistentListOf(),
            advancedPreviewTag = null,
            advancedPreviewMessage = null,
            advancedSavingFilter = false,
        )

    private fun AppsState.withPreview(preview: MatchingPreview): AppsState =
        copy(
            advancedPreviewLoading = false,
            advancedPreviewMatched = preview.matchedAssets.map { asset -> asset.toUi() }.toImmutableList(),
            advancedPreviewTag = preview.release?.tagName,
            advancedPreviewMessage =
                if (preview.matchedAssets.isEmpty() && preview.regexError == null) AdvancedPreviewMessage.NoMatch else null,
            advancedFilterError = if (preview.regexError != null) INVALID_FILTER else null,
        )

    private fun AppsState.withVariantPickerClosed(): AppsState =
        copy(
            variantPickerApp = null,
            variantPickerOptions = persistentListOf(),
            variantPickerCurrentVariant = null,
            variantPickerError = null,
            variantPickerLoading = false,
            variantPickerResumeUpdateAfterPick = false,
        )

    private companion object {
        const val INVALID_FILTER = "invalid"
        val PREVIEW_DEBOUNCE = 350.milliseconds
    }
}
