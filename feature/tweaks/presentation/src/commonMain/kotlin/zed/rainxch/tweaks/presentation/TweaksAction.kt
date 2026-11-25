package zed.rainxch.tweaks.presentation

import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.core.domain.model.appearance.AppPersonality
import zed.rainxch.core.domain.model.appearance.AppTheme
import zed.rainxch.core.domain.model.appearance.ContentWidth
import zed.rainxch.core.domain.model.appearance.FontTheme
import zed.rainxch.core.domain.model.appearance.MangaPaperId
import zed.rainxch.core.domain.model.installation.InstallerType
import zed.rainxch.core.domain.model.installation.PresetKey
import zed.rainxch.core.domain.model.settings.ProxyScope
import zed.rainxch.core.domain.model.settings.TranslationProvider
import zed.rainxch.tweaks.presentation.components.desktop.DesktopSection
import zed.rainxch.tweaks.presentation.model.ProxyType

sealed interface TweaksAction {
    sealed interface LookAndFeel : TweaksAction

    sealed interface Appearance : LookAndFeel

    sealed interface Language : LookAndFeel

    sealed interface Connection : TweaksAction

    sealed interface ScopeProxy : Connection

    sealed interface MainProxy : Connection

    sealed interface CustomForge : Connection

    sealed interface Installs : TweaksAction

    sealed interface Installer : Installs

    sealed interface Updates : Installs

    sealed interface Translation : TweaksAction

    sealed interface YoudaoCredentials : Translation

    sealed interface LibreTranslateCredentials : Translation

    sealed interface DeeplCredentials : Translation

    sealed interface MicrosoftCredentials : Translation

    sealed interface Privacy : TweaksAction

    data class OnPersonalitySelected(
        val personality: AppPersonality,
    ) : Appearance

    data class OnAccentSelected(
        val accent: AccentId,
    ) : Appearance

    data class OnMangaPaperSelected(
        val paper: MangaPaperId,
    ) : Appearance

    data class OnThemeColorSelected(
        val themeColor: AppTheme,
    ) : Appearance

    data class OnAmoledThemeToggled(
        val enabled: Boolean,
    ) : Appearance

    data class OnRepoLayoutSelected(
        val grid: Boolean,
    ) : Appearance

    data class OnDarkThemeChange(
        val isDarkTheme: Boolean?,
    ) : Appearance

    data class OnFontThemeSelected(
        val fontTheme: FontTheme,
    ) : Appearance

    data class OnScrollbarToggled(
        val enabled: Boolean,
    ) : Appearance

    data class OnContentWidthSelected(
        val width: ContentWidth,
    ) : Appearance

    data class OnDisplayModeSelected(
        val modeId: Int,
    ) : Appearance

    data class OnProxyTypeSelected(
        val scope: ProxyScope,
        val type: ProxyType,
    ) : ScopeProxy

    data class OnProxyHostChanged(
        val scope: ProxyScope,
        val host: String,
    ) : ScopeProxy

    data class OnProxyPortChanged(
        val scope: ProxyScope,
        val port: String,
    ) : ScopeProxy

    data class OnProxyUsernameChanged(
        val scope: ProxyScope,
        val username: String,
    ) : ScopeProxy

    data class OnProxyPasswordChanged(
        val scope: ProxyScope,
        val password: String,
    ) : ScopeProxy

    data class OnProxyPasswordVisibilityToggle(
        val scope: ProxyScope,
    ) : ScopeProxy

    data class OnProxySave(
        val scope: ProxyScope,
    ) : ScopeProxy

    data class OnProxyTest(
        val scope: ProxyScope,
    ) : ScopeProxy

    data class OnExternalInstallerPickerOpenChanged(
        val open: Boolean,
    ) : Installer

    data class OnExternalInstallerSelected(
        val packageName: String,
    ) : Installer

    data class OnInstallerTypeSelected(
        val type: InstallerType,
    ) : Installer

    data object OnRequestShizukuPermission : Installer

    data object OnRequestDhizukuPermission : Installer

    data object OnRequestRootPermission : Installer

    data object OnInstallerAttributionSystemDefault : Installer

    data class OnInstallerAttributionPresetSelected(
        val key: PresetKey,
    ) : Installer

    data object OnInstallerAttributionCustomToggleExpanded : Installer

    data class OnInstallerAttributionCustomChanged(
        val value: String,
    ) : Installer

    data object OnInstallerAttributionCustomSave : Installer

    data class OnAutoUpdateToggled(
        val enabled: Boolean,
    ) : Installer

    data class OnUpdateCheckIntervalChanged(
        val hours: Long,
    ) : Updates

    data class OnUpdateCheckEnabledToggled(
        val enabled: Boolean,
    ) : Updates

    data class OnIncludePreReleasesToggled(
        val enabled: Boolean,
    ) : Updates

    data class OnAutoDetectClipboardToggled(
        val enabled: Boolean,
    ) : Privacy

    data class OnHideSeenToggled(
        val enabled: Boolean,
    ) : Privacy

    data object OnClearSeenRepos : Privacy

    data object OnRefreshCacheSize : Privacy

    data object OnClearCacheClick : Privacy

    data object OnClearDownloadsConfirm : Privacy

    data object OnClearDownloadsDismiss : Privacy

    data object OnFeedbackClick : TweaksAction

    data object OnFeedbackDismiss : TweaksAction

    data object OnLanguagePickerOpen : Language

    data object OnLanguagePickerDismiss : Language

    data class OnLanguageQueryChange(
        val query: String,
    ) : Language

    data object OnTranslationProviderExpandToggle : Translation

    data object OnTranslationTargetPickerOpen : Translation

    data object OnTranslationTargetPickerDismiss : Translation

    data object OnConnectionPasteSheetOpen : MainProxy

    data object OnConnectionPasteSheetDismiss : MainProxy

    data object OnConnectionMasterExpandToggle : MainProxy

    data class OnProxyScopeExpandToggle(
        val scope: ProxyScope,
    ) : ScopeProxy

    data class OnDesktopSectionSelected(
        val section: DesktopSection,
    ) : TweaksAction

    data class OnTranslationProviderSelected(
        val provider: TranslationProvider,
    ) : Translation

    data class OnYoudaoAppKeyChanged(
        val appKey: String,
    ) : YoudaoCredentials

    data class OnYoudaoAppSecretChanged(
        val appSecret: String,
    ) : YoudaoCredentials

    data object OnYoudaoAppSecretVisibilityToggle : YoudaoCredentials

    data object OnYoudaoCredentialsSave : YoudaoCredentials

    data class OnLibreTranslateBaseUrlChanged(
        val url: String,
    ) : LibreTranslateCredentials

    data class OnLibreTranslateApiKeyChanged(
        val apiKey: String,
    ) : LibreTranslateCredentials

    data object OnLibreTranslateApiKeyVisibilityToggle : LibreTranslateCredentials

    data object OnLibreTranslateCredentialsSave : LibreTranslateCredentials

    data class OnDeeplAuthKeyChanged(
        val authKey: String,
    ) : DeeplCredentials

    data object OnDeeplAuthKeyVisibilityToggle : DeeplCredentials

    data object OnDeeplCredentialsSave : DeeplCredentials

    data class OnMicrosoftTranslatorKeyChanged(
        val key: String,
    ) : MicrosoftCredentials

    data class OnMicrosoftTranslatorRegionChanged(
        val region: String,
    ) : MicrosoftCredentials

    data object OnMicrosoftTranslatorKeyVisibilityToggle : MicrosoftCredentials

    data object OnMicrosoftTranslatorCredentialsSave : MicrosoftCredentials

    data class OnAutoTranslateEnabledToggle(
        val enabled: Boolean,
    ) : Translation

    data class OnAutoTranslateTargetSelected(
        val tag: String?,
    ) : Translation

    data class OnAppLanguageSelected(
        val tag: String?,
    ) : Language

    data object OnOpenBatteryOptimizationSettings : Updates

    data object OnDismissBatteryOptimizationCard : Updates

    data object OnReevaluateBatteryOptimizationCard : Updates

    data object OnOpenCustomForgesDialog : CustomForge

    data object OnDismissCustomForgesDialog : CustomForge

    data class OnCustomForgeDraftChanged(
        val draft: String,
    ) : CustomForge

    data object OnAddCustomForge : CustomForge

    data class OnRemoveCustomForge(
        val host: String,
    ) : CustomForge

    data class OnMasterProxyTypeSelected(
        val type: ProxyType,
    ) : MainProxy

    data class OnMasterProxyHostChanged(
        val host: String,
    ) : MainProxy

    data class OnMasterProxyPortChanged(
        val port: String,
    ) : MainProxy

    data class OnMasterProxyUsernameChanged(
        val username: String,
    ) : MainProxy

    data class OnMasterProxyPasswordChanged(
        val password: String,
    ) : MainProxy

    data object OnMasterProxyPasswordVisibilityToggle : MainProxy

    data object OnMasterProxySave : MainProxy

    data object OnMasterProxyTest : MainProxy

    data class OnProxyPasteUrlChanged(
        val value: String,
    ) : MainProxy

    data object OnProxyPasteUrlSubmit : MainProxy

    data class OnScopeUseMainToggled(
        val scope: ProxyScope,
        val useMain: Boolean,
    ) : ScopeProxy

    data object OnClearSeenHistoryRequest : Privacy

    data object OnClearSeenHistoryDismiss : Privacy

    data object OnClearSeenHistoryConfirm : Privacy

    data object OnShareDiagnosticLogs : TweaksAction
}
