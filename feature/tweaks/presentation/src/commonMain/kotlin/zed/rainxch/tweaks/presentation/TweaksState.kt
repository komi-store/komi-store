package zed.rainxch.tweaks.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.core.domain.model.appearance.AppPersonality
import zed.rainxch.core.domain.model.appearance.AppTheme
import zed.rainxch.core.domain.model.appearance.ContentWidth
import zed.rainxch.core.domain.model.appearance.MangaPaperId
import zed.rainxch.core.domain.model.installation.DhizukuAvailability
import zed.rainxch.core.domain.model.appearance.FontTheme
import zed.rainxch.core.domain.model.installation.InstallerAttribution
import zed.rainxch.core.domain.model.installation.InstallerType
import zed.rainxch.core.domain.model.settings.AppLanguages
import zed.rainxch.core.domain.model.settings.ProxyScope
import zed.rainxch.core.domain.model.installation.RootAvailability
import zed.rainxch.core.domain.model.installation.ShizukuAvailability
import zed.rainxch.core.domain.model.settings.SupportedTranslationLanguages
import zed.rainxch.core.domain.model.settings.TranslationProvider
import zed.rainxch.tweaks.presentation.components.desktop.DesktopSection
import zed.rainxch.tweaks.presentation.model.LanguagePickerOption
import zed.rainxch.tweaks.presentation.model.ProxyScopeFormState

data class TweaksState(
    val selectedPersonality: AppPersonality = AppPersonality.MANGA,
    val selectedAccent: AccentId = AccentId.CRIMSON,
    val mangaPaper: MangaPaperId = MangaPaperId.DAY,
    val selectedThemeColor: AppTheme = AppTheme.NORD,
    val selectedFontTheme: FontTheme = FontTheme.CUSTOM,
    val isAmoledThemeEnabled: Boolean = false,
    val isDarkTheme: Boolean? = null,
    val versionName: String = "",
    val proxyForms: ImmutableMap<ProxyScope, ProxyScopeFormState> =
        ProxyScope.entries.associateWith { ProxyScopeFormState() }.toImmutableMap(),
    val autoDetectClipboardLinks: Boolean = true,
    val cacheSize: String = "",
    val isClearDownloadsDialogVisible: Boolean = false,
    val installerType: InstallerType = InstallerType.DEFAULT,
    val installerAttribution: InstallerAttribution = InstallerAttribution.SystemDefault,
    val installerAttributionCustomDraft: String = "",
    val installerAttributionCustomExpanded: Boolean = false,
    val installerAttributionCustomError: String? = null,
    val shizukuAvailability: ShizukuAvailability = ShizukuAvailability.UNAVAILABLE,
    val dhizukuAvailability: DhizukuAvailability = DhizukuAvailability.UNAVAILABLE,
    val rootAvailability: RootAvailability = RootAvailability.UNAVAILABLE,
    val autoUpdateEnabled: Boolean = false,
    val updateCheckEnabled: Boolean = true,
    val updateCheckIntervalHours: Long = 12L,
    val includePreReleases: Boolean = false,
    val isHideSeenEnabled: Boolean = false,
    val isScrollbarEnabled: Boolean = false,
    val contentWidth: ContentWidth = ContentWidth.COMPACT,
    val translationProvider: TranslationProvider = TranslationProvider.Default,

    val draftTranslationProvider: TranslationProvider? = null,
    val youdaoAppKey: String = "",
    val youdaoAppSecret: String = "",
    val isYoudaoAppSecretVisible: Boolean = false,
    val libreTranslateBaseUrl: String = "",
    val libreTranslateApiKey: String = "",
    val isLibreTranslateApiKeyVisible: Boolean = false,
    val deeplAuthKey: String = "",
    val isDeeplAuthKeyVisible: Boolean = false,
    val microsoftTranslatorKey: String = "",
    val microsoftTranslatorRegion: String = "",
    val isMicrosoftTranslatorKeyVisible: Boolean = false,

    val selectedAppLanguage: String? = null,
    val autoTranslateEnabled: Boolean = false,
    val autoTranslateTargetLang: String? = null,
    val isFeedbackSheetVisible: Boolean = false,
    val isLanguageSheetVisible: Boolean = false,
    val languagePickerOptions: ImmutableList<LanguagePickerOption> =
        AppLanguages.ALL
            .map { LanguagePickerOption(id = it.tag, title = it.displayName, tag = it.tag) }
            .toImmutableList(),
    val languageQuery: String = "",
    val translationProviderExpanded: Boolean = false,
    val translationTargetPickerOpen: Boolean = false,
    val translationLanguageOptions: ImmutableList<LanguagePickerOption> =
        SupportedTranslationLanguages.all
            .map { LanguagePickerOption(id = it.code, title = it.displayName, tag = it.code) }
            .toImmutableList(),
    val connectionPasteSheetOpen: Boolean = false,
    val proxyPasteUrlInput: String = "",
    val proxyPasteUrlError: Boolean = false,
    val connectionMasterExpanded: Boolean = false,
    val expandedProxyScope: ProxyScope? = null,
    val desktopSection: DesktopSection = DesktopSection.LOOK,

    val showBatteryOptimizationCard: Boolean = false,
    val customForgeHosts: ImmutableSet<String> = persistentSetOf(),
    val showCustomForgesDialog: Boolean = false,
    val customForgeDraft: String = "",
    val customForgeError: String? = null,
    val restartBannerSessionDismissed: Boolean = false,
    val masterProxyForm: ProxyScopeFormState = ProxyScopeFormState(),
    val useMasterByScope: ImmutableMap<ProxyScope, Boolean> =
        ProxyScope.entries.associateWith { false }.toImmutableMap(),
    val isClearSeenHistoryDialogVisible: Boolean = false,
) {
    fun useMain(scope: ProxyScope): Boolean = useMasterByScope[scope] ?: false

    val displayedTranslationProvider: TranslationProvider
        get() = draftTranslationProvider ?: translationProvider

    fun formFor(scope: ProxyScope): ProxyScopeFormState =
        proxyForms[scope] ?: ProxyScopeFormState()
}
