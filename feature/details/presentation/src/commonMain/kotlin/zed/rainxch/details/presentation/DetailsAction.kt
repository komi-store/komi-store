package zed.rainxch.details.presentation

import org.jetbrains.compose.resources.StringResource
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.details.domain.model.ReleaseCategory
import zed.rainxch.details.presentation.model.TranslationTarget

// Grouped by the handler that answers them, so each handler's dispatch stays small.
sealed interface DetailsAction {
    sealed interface Load : DetailsAction

    sealed interface Release : DetailsAction

    sealed interface Install : DetailsAction

    sealed interface Handoff : DetailsAction

    sealed interface App : DetailsAction

    sealed interface Repo : DetailsAction

    sealed interface Content : DetailsAction

    sealed interface Navigation : DetailsAction

    data class OnPlatformChipClick(
        val platform: DiscoveryPlatform,
    ) : Release

    data object OnJumpToDeviceBuild : Release

    data object OnDismissPlatformHandoff : Release

    data class OnShareAssetLink(
        val assetUrl: String,
    ) : Repo

    data object Retry : Load

    data object RetryReleases : Load

    data object Refresh : Load

    data object InstallPrimary : Install

    data object OnDismissDowngradeWarning : Install

    data object OnConfirmDowngradeInstall : Install

    data object OnConfirmDowngradeUninstall : App

    data object OnDismissSigningKeyWarning : Install

    data object OnOverrideSigningKeyWarning : Install

    data object UninstallApp : App

    data object OnRequestUninstall : App

    data object OnDismissUninstallConfirmation : App

    data object OnConfirmUninstall : App

    data object OnUnlinkExternalApp : App

    data object OnDismissUnlinkConfirmation : App

    data object OnConfirmUnlinkExternalApp : App

    data class DownloadAsset(
        val downloadUrl: String,
        val assetName: String,
        val sizeBytes: Long,
    ) : Install

    data object PauseDownload : Install

    data object ResumeDownload : Install

    data object DiscardDownload : Install

    data object OpenRepoInBrowser : Repo

    data object OpenAuthorInBrowser : Repo

    data class OpenDeveloperProfile(
        val username: String,
    ) : Navigation

    data object OpenInObtainium : Handoff

    data object OpenInAppManager : Handoff

    data object InstallWithExternalApp : Handoff

    data object OpenWithExternalInstaller : Handoff

    data object DismissExternalInstallerPrompt : Handoff

    data object OnNavigateBackClick : Navigation

    data object OnToggleFavorite : Repo

    data object OnToggleStar : Repo

    data object OnShareClick : Repo

    data object UpdateApp : Install

    data object OpenApp : App

    data class OnMessage(
        val messageText: StringResource,
    ) : Navigation

    data class SelectReleaseCategory(
        val category: ReleaseCategory,
    ) : Release

    data class SelectRelease(
        val releaseId: Long,
    ) : Release

    data object ToggleVersionPicker : Release

    data object ToggleAboutExpanded : Content

    data object ToggleWhatsNewExpanded : Content

    data class OnAboutMeasured(
        val heightPx: Float,
    ) : Content

    data class OnWhatsNewMeasured(
        val heightPx: Float,
    ) : Content

    data class TranslateAbout(
        val targetLanguageCode: String,
    ) : Content

    data class TranslateWhatsNew(
        val targetLanguageCode: String,
    ) : Content

    data object ToggleAboutTranslation : Content

    data object ToggleWhatsNewTranslation : Content

    data class ShowLanguagePicker(
        val target: TranslationTarget,
    ) : Content

    data object DismissLanguagePicker : Content

    data class SelectDownloadAsset(
        val assetId: Long,
    ) : Release

    data class OnSelectInstalledApp(
        val packageName: String,
    ) : Release

    data object ToggleReleaseAssetsPicker : Release

    data object UnpinPreferredVariant : Release

    data object ToggleIncludeBetas : Release

    data object SwitchToStable : Install

    data object OnInspectApk : App

    data object OnDismissApkInspect : App

    data object OnAcknowledgeApkInspectCoachmark : App

    data object OnAcknowledgeChannelChipCoachmark : Release

    data class OnToggleShowAllPlatforms(
        val enabled: Boolean,
    ) : Release

    data class OnDownloadForTransfer(
        val assetUrl: String,
    ) : Repo
}
