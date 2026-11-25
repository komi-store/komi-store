package zed.rainxch.tweaks.presentation.handlers

import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.installation.InstallerAttribution
import zed.rainxch.core.domain.model.installation.InstallerAttributionDefaults
import zed.rainxch.core.domain.model.installation.InstallerType
import zed.rainxch.core.domain.repository.ExternalInstallerRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.AggressiveOemDetector
import zed.rainxch.core.domain.system.InstallerStatusProvider
import zed.rainxch.core.domain.system.UpdateScheduleManager
import zed.rainxch.tweaks.presentation.TweaksAction
import zed.rainxch.tweaks.presentation.TweaksState
import zed.rainxch.tweaks.presentation.model.toUi
import kotlin.time.Duration.Companion.seconds

class InstallsHandler(
    private val tweaksRepository: TweaksRepository,
    private val externalInstallerRepository: ExternalInstallerRepository,
    private val installerStatusProvider: InstallerStatusProvider,
    private val updateScheduleManager: UpdateScheduleManager,
    private val aggressiveOemDetector: AggressiveOemDetector,
    private val logger: KomiStoreLogger,
) {
    internal fun observe(store: TweaksStore) {
        store.observe(tweaksRepository.getExternalInstallerPackage()) { copy(externalInstallerPackage = it) }
        store.observe(tweaksRepository.getInstallerType()) { copy(installerType = it) }
        store.launch { loadExternalInstallers(store) }
        store.observe(installerStatusProvider.shizukuAvailability) { copy(shizukuAvailability = it) }
        store.observe(installerStatusProvider.dhizukuAvailability) { copy(dhizukuAvailability = it) }
        store.observe(installerStatusProvider.rootAvailability) { copy(rootAvailability = it) }
        val attributions =
            tweaksRepository.getInstallerAttribution().catch { e ->
                logger.error("TweaksViewModel: installer attribution flow error", e)
            }
        store.observe(attributions) { withAttribution(it) }
        store.observe(tweaksRepository.getAutoUpdateEnabled()) { copy(autoUpdateEnabled = it) }
        store.observe(tweaksRepository.getUpdateCheckInterval()) { copy(updateCheckIntervalHours = it) }
        store.observe(tweaksRepository.getUpdateCheckEnabled()) { copy(updateCheckEnabled = it) }
        store.observe(tweaksRepository.getIncludePreReleases()) { copy(includePreReleases = it) }
    }

    internal fun onAction(
        store: TweaksStore,
        action: TweaksAction.Installs,
    ) {
        when (action) {
            is TweaksAction.Installer -> onInstallerAction(store, action)
            is TweaksAction.Updates -> onUpdatesAction(store, action)
        }
    }

    internal fun evaluateBatteryOptimizationCard(store: TweaksStore) {
        store.launch {
            val dismissed =
                withTimeoutOrNull(BATTERY_OPT_PREF_READ_TIMEOUT) {
                    tweaksRepository.getBatteryOptimizationPromptDismissed().firstOrNull()
                } ?: false
            val show =
                aggressiveOemDetector.isAggressiveOem() &&
                    !aggressiveOemDetector.isBatteryOptimizationIgnored() &&
                    !dismissed
            store.update { it.copy(showBatteryOptimizationCard = show) }
        }
    }

    private fun onInstallerAction(
        store: TweaksStore,
        action: TweaksAction.Installer,
    ) {
        when (action) {
            is TweaksAction.OnExternalInstallerPickerOpenChanged -> setPickerOpen(store, action.open)
            is TweaksAction.OnExternalInstallerSelected -> selectExternalInstaller(store, action.packageName)
            is TweaksAction.OnInstallerTypeSelected -> selectInstallerType(store, action.type)
            TweaksAction.OnRequestShizukuPermission -> installerStatusProvider.requestShizukuPermission()
            TweaksAction.OnRequestDhizukuPermission -> installerStatusProvider.requestDhizukuPermission()
            TweaksAction.OnRequestRootPermission -> installerStatusProvider.requestRootPermission()
            TweaksAction.OnInstallerAttributionSystemDefault -> persistAttribution(store, InstallerAttribution.SystemDefault)
            is TweaksAction.OnInstallerAttributionPresetSelected -> persistAttribution(store, InstallerAttribution.Preset(action.key))
            TweaksAction.OnInstallerAttributionCustomToggleExpanded -> toggleCustomAttribution(store)
            is TweaksAction.OnInstallerAttributionCustomChanged -> editCustomAttribution(store, action.value)
            TweaksAction.OnInstallerAttributionCustomSave -> saveCustomAttribution(store)
            is TweaksAction.OnAutoUpdateToggled -> store.launch { tweaksRepository.setAutoUpdateEnabled(action.enabled) }
        }
    }

    private fun onUpdatesAction(
        store: TweaksStore,
        action: TweaksAction.Updates,
    ) {
        when (action) {
            is TweaksAction.OnUpdateCheckIntervalChanged -> setUpdateCheckInterval(store, action.hours)
            is TweaksAction.OnUpdateCheckEnabledToggled -> setUpdateCheckEnabled(store, action.enabled)
            is TweaksAction.OnIncludePreReleasesToggled -> store.launch { tweaksRepository.setIncludePreReleases(action.enabled) }
            TweaksAction.OnOpenBatteryOptimizationSettings -> openBatteryOptimizationSettings()
            TweaksAction.OnDismissBatteryOptimizationCard -> dismissBatteryOptimizationCard(store)
            TweaksAction.OnReevaluateBatteryOptimizationCard -> evaluateBatteryOptimizationCard(store)
        }
    }

    private suspend fun loadExternalInstallers(store: TweaksStore) {
        val installers = externalInstallerRepository.listInstallers().map { it.toUi() }.toImmutableList()
        store.update { it.copy(externalInstallers = installers) }
    }

    private fun setPickerOpen(
        store: TweaksStore,
        open: Boolean,
    ) {
        store.update { it.copy(externalInstallerPickerOpen = open) }
        if (open) store.launch { loadExternalInstallers(store) }
    }

    private fun selectExternalInstaller(
        store: TweaksStore,
        packageName: String,
    ) {
        store.launch {
            tweaksRepository.setExternalInstallerPackage(packageName)
            store.update { it.copy(externalInstallerPickerOpen = false) }
        }
    }

    private fun selectInstallerType(
        store: TweaksStore,
        type: InstallerType,
    ) {
        store.launch { tweaksRepository.setInstallerType(type) }
        if (type == InstallerType.THIRD_PARTY && store.current.externalInstallerPackage.isBlank()) {
            setPickerOpen(store, true)
        }
    }

    private fun persistAttribution(
        store: TweaksStore,
        attribution: InstallerAttribution,
    ) {
        store.launch {
            tweaksRepository.setInstallerAttribution(attribution)
            store.update { it.copy(installerAttributionCustomExpanded = false, isInstallerAttributionCustomInvalid = false) }
        }
    }

    private fun toggleCustomAttribution(store: TweaksStore) {
        store.update {
            it.copy(
                installerAttributionCustomExpanded = !it.installerAttributionCustomExpanded,
                isInstallerAttributionCustomInvalid = false,
            )
        }
    }

    private fun editCustomAttribution(
        store: TweaksStore,
        value: String,
    ) {
        store.update { it.copy(installerAttributionCustomDraft = value, isInstallerAttributionCustomInvalid = false) }
    }

    private fun saveCustomAttribution(store: TweaksStore) {
        val draft = store.current.installerAttributionCustomDraft.trim()
        if (InstallerAttributionDefaults.isValidPackageName(draft)) {
            store.launch {
                tweaksRepository.setInstallerAttribution(InstallerAttribution.Custom(draft))
                store.update { it.copy(isInstallerAttributionCustomInvalid = false) }
            }
        } else {
            store.update { it.copy(isInstallerAttributionCustomInvalid = true) }
        }
    }

    private fun setUpdateCheckInterval(
        store: TweaksStore,
        hours: Long,
    ) {
        store.launch {
            tweaksRepository.setUpdateCheckInterval(hours)
            if (store.current.updateCheckEnabled) updateScheduleManager.reschedule(hours)
        }
    }

    private fun setUpdateCheckEnabled(
        store: TweaksStore,
        enabled: Boolean,
    ) {
        store.launch {
            tweaksRepository.setUpdateCheckEnabled(enabled)
            if (enabled) {
                updateScheduleManager.reschedule(store.current.updateCheckIntervalHours)
            } else {
                updateScheduleManager.cancel()
            }
        }
    }

    private fun openBatteryOptimizationSettings() {
        if (!aggressiveOemDetector.openBatteryOptimizationSettings()) {
            logger.warn("TweaksViewModel: failed to launch battery optimization settings")
        }
    }

    private fun dismissBatteryOptimizationCard(store: TweaksStore) {
        store.launch {
            tweaksRepository.setBatteryOptimizationPromptDismissed(true)
            store.update { it.copy(showBatteryOptimizationCard = false) }
        }
    }

    private fun TweaksState.withAttribution(attribution: InstallerAttribution): TweaksState {
        val custom = attribution as? InstallerAttribution.Custom
        return copy(
            installerAttribution = attribution.toUi(),
            installerAttributionCustomDraft = custom?.packageName ?: installerAttributionCustomDraft,
            installerAttributionCustomExpanded = custom != null && installerAttributionCustomExpanded,
        )
    }

    private companion object {
        val BATTERY_OPT_PREF_READ_TIMEOUT = 1.seconds
    }
}
