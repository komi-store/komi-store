package zed.rainxch.details.presentation.handlers

import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.utils.VersionMath
import zed.rainxch.details.domain.util.VersionHelper
import zed.rainxch.details.presentation.DetailsAction
import zed.rainxch.details.presentation.latestStableRelease
import zed.rainxch.details.presentation.model.DowngradeWarning
import zed.rainxch.details.presentation.model.DownloadStage

// The install button and what follows it: downgrade and signing-key prompts, downloads, updates and the switch to stable.
internal class DetailsInstallHandler(
    private val downloads: OrchestratedDownloads,
    private val installStep: ApkInstallStep,
    private val picker: DetailsReleasePicker,
    private val selection: ReleaseSelection,
    private val installedAppsRepository: InstalledAppsRepository,
    private val logger: KomiStoreLogger,
) {
    internal fun onAction(
        session: DetailsSession,
        action: DetailsAction.Install,
    ) {
        when (action) {
            DetailsAction.InstallPrimary -> install(session, ignoreDowngrade = false)
            DetailsAction.UpdateApp -> update(session)
            is DetailsAction.DownloadAsset -> download(session, action)
            DetailsAction.PauseDownload -> downloads.pause(session)
            DetailsAction.ResumeDownload -> downloads.resume(session)
            DetailsAction.DiscardDownload -> downloads.discard(session)
            DetailsAction.OnDismissDowngradeWarning -> session.update { it.copy(downgradeWarning = null) }
            DetailsAction.OnConfirmDowngradeInstall -> confirmDowngradeInstall(session)
            DetailsAction.OnDismissSigningKeyWarning -> dismissSigningKeyWarning(session)
            DetailsAction.OnOverrideSigningKeyWarning -> installStep.overrideSigningKeyWarning(session)
            DetailsAction.SwitchToStable -> switchToStable(session)
        }
    }

    private fun download(
        session: DetailsSession,
        action: DetailsAction.DownloadAsset,
    ) {
        val request =
            InstallRequest(
                downloadUrl = action.downloadUrl,
                assetName = action.assetName,
                sizeBytes = action.sizeBytes,
                releaseTag = session.current.selectedRelease?.tagName ?: "",
                isUpdate = false,
            )
        downloads.download(session, request)
    }

    private fun dismissSigningKeyWarning(session: DetailsSession) {
        session.update { it.copy(signingKeyWarning = null, downloadStage = DownloadStage.IDLE) }
        session.currentAssetName = null
    }

    private fun install(
        session: DetailsSession,
        ignoreDowngrade: Boolean,
    ) {
        val current = session.current
        val primary = current.primaryAsset ?: return
        val release = current.selectedRelease ?: return
        val downgradeFrom = current.installedApp?.takeIf { !ignoreDowngrade && isDowngrade(session, it, release) }
        if (downgradeFrom != null) {
            val warning =
                DowngradeWarning(
                    packageName = downgradeFrom.packageName,
                    currentVersion = downgradeFrom.installedVersion,
                    targetVersion = release.tagName,
                )
            session.update { it.copy(downgradeWarning = warning) }
        } else {
            downloads.install(session, primary.request(release, isUpdate = false))
        }
    }

    // Android refuses an older version over a newer one, so the user is asked first.
    private fun isDowngrade(
        session: DetailsSession,
        app: InstalledApp,
        release: GithubRelease,
    ): Boolean {
        val differs = VersionHelper.normalizeVersion(release.tagName) != VersionHelper.normalizeVersion(app.installedVersion)
        val applies = session.platform == Platform.ANDROID && !app.isPendingInstall
        return applies &&
            differs &&
            VersionHelper.isDowngradeVersion(
                candidate = release.tagName,
                current = app.installedVersion,
                allReleases = session.current.allReleases,
            )
    }

    private fun update(session: DetailsSession) {
        val current = session.current
        val installedApp = current.installedApp?.takeIf { it.isUpdateAvailable } ?: return
        val release = current.selectedRelease ?: return
        val latestAsset =
            current.primaryAsset
                ?: current.installableAssets.firstOrNull { it.name == installedApp.latestAssetName }
                ?: current.installableAssets.firstOrNull { it.name == installedApp.installedAssetName }
        latestAsset?.let { downloads.install(session, it.request(release, isUpdate = true)) }
    }

    // Consent covers the release it was given for; confirming from the latest release also skips that update.
    private fun confirmDowngradeInstall(session: DetailsSession) {
        val warning = session.current.downgradeWarning ?: return
        session.update { it.copy(downgradeWarning = null) }
        val installedApp = session.current.installedApp
        session.launch {
            val consentStillApplies = session.current.selectedRelease?.tagName == warning.targetVersion
            val skipsLatest = installedApp != null && VersionMath.isExactSameVersion(installedApp.latestVersion, warning.currentVersion)
            if (consentStillApplies && skipsLatest) {
                installedAppsRepository.setSkippedReleaseTag(installedApp.packageName, installedApp.latestVersion)
            }
            install(session, ignoreDowngrade = session.current.selectedRelease?.tagName == warning.targetVersion)
        }
    }

    private fun switchToStable(session: DetailsSession) {
        val stable = session.current.latestStableRelease() ?: return
        if (selection.assetsFor(session.current, stable, session.current.installedApp).primary == null) {
            logger.warn("switchToStable: stable ${stable.tagName} has no installable asset; skipping")
            return
        }
        picker.selectRelease(session, stable)
        install(session, ignoreDowngrade = false)
    }

    private fun GithubAsset.request(
        release: GithubRelease,
        isUpdate: Boolean,
    ): InstallRequest =
        InstallRequest(
            downloadUrl = downloadUrl,
            assetName = name,
            sizeBytes = size,
            releaseTag = release.tagName,
            isUpdate = isUpdate,
        )
}
