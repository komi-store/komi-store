package zed.rainxch.details.presentation.handlers

import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.network.Downloader
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.details.presentation.DetailsAction
import zed.rainxch.details.presentation.DetailsEvent
import zed.rainxch.details.presentation.model.DownloadStage
import zed.rainxch.details.presentation.model.LogResult
import zed.rainxch.details.presentation.utils.recoverStepFailure
import kotlin.coroutines.cancellation.CancellationException

// Hands the release to another installer: Obtainium, App Manager or whichever app opens APK files.
internal class DetailsHandoffHandler(
    private val downloader: Downloader,
    private val installer: Installer,
    private val logger: KomiStoreLogger,
) {
    internal fun onAction(
        session: DetailsSession,
        action: DetailsAction.Handoff,
    ) {
        when (action) {
            DetailsAction.OpenInObtainium -> openInObtainium(session)
            DetailsAction.OpenInAppManager -> openInAppManager(session)
            DetailsAction.InstallWithExternalApp -> installWithExternalApp(session)
            DetailsAction.OpenWithExternalInstaller -> openParkedWithExternalInstaller(session)
            DetailsAction.DismissExternalInstallerPrompt -> dismissExternalInstallerPrompt(session)
        }
    }

    private fun openInObtainium(session: DetailsSession) {
        val repo = session.current.repository ?: return
        installer.openInObtainium(
            repoOwner = repo.owner.login,
            repoName = repo.name,
            onOpenInstaller = { session.launch { session.send(DetailsEvent.OnOpenRepositoryInApp(OBTAINIUM_REPO_ID)) } },
        )
    }

    private fun installWithExternalApp(session: DetailsSession) {
        session.currentDownloadJob?.cancel()
        session.currentDownloadJob =
            session.launch {
                try {
                    handOff(session, LogResult.DownloadStarted, "Failed to install with external app") { filePath ->
                        installer.openWithExternalInstaller(filePath)
                        LogResult.OpenedInExternalInstaller
                    }
                } catch (e: CancellationException) {
                    logger.debug("Install with external app cancelled")
                    session.update { it.copy(downloadStage = DownloadStage.IDLE) }
                    session.currentAssetName = null
                    throw e
                }
            }
    }

    private fun openInAppManager(session: DetailsSession) {
        session.currentDownloadJob?.cancel()
        session.currentDownloadJob =
            session.launch {
                handOff(session, LogResult.PreparingForAppManager, "Failed to open in AppManager") { filePath ->
                    installer.openInAppManager(
                        filePath = filePath,
                        onOpenInstaller = { session.launch { session.send(DetailsEvent.OnOpenRepositoryInApp(APP_MANAGER_REPO_ID)) } },
                    )
                    LogResult.OpenedInAppManager
                }
            }
    }

    // Downloads the selected asset in the page, then gives the file to the other installer.
    private suspend fun handOff(
        session: DetailsSession,
        startLog: LogResult,
        failureLog: String,
        open: (filePath: String) -> LogResult,
    ) {
        val primary = session.current.primaryAsset
        val release = session.current.selectedRelease
        if (primary == null || release == null) return
        recoverStepFailure(
            recover = { error ->
                logger.error("$failureLog: ${error.message}")
                session.update { it.copy(downloadStage = DownloadStage.IDLE, installError = error.message) }
                session.currentAssetName = null
                logCurrent(session, LogResult.Error(error.message))
            },
        ) {
            val filePath = download(session, primary, release, startLog)
            session.appendLog(primary.name, primary.size, release.tagName, open(filePath))
        }
    }

    private suspend fun download(
        session: DetailsSession,
        asset: GithubAsset,
        release: GithubRelease,
        startLog: LogResult,
    ): String {
        session.currentAssetName = asset.name
        session.appendLog(asset.name, asset.size, release.tagName, startLog)
        session.update {
            it.copy(downloadError = null, installError = null, downloadProgressPercent = null, downloadStage = DownloadStage.DOWNLOADING)
        }
        downloader.download(asset.downloadUrl, asset.name).collect { progress ->
            session.update { it.copy(downloadProgressPercent = progress.percent) }
            if (progress.percent == FULL_PERCENT) session.update { it.copy(downloadStage = DownloadStage.VERIFYING) }
        }
        val filePath = checkNotNull(downloader.getDownloadedFilePath(asset.name)) { "Downloaded file not found" }
        session.appendLog(asset.name, asset.size, release.tagName, LogResult.Downloaded)
        session.update { it.copy(downloadStage = DownloadStage.IDLE) }
        session.currentAssetName = null
        return filePath
    }

    private fun openParkedWithExternalInstaller(session: DetailsSession) {
        session.current.pendingInstallFilePath?.let { filePath ->
            recoverStepFailure(
                recover = { error ->
                    logger.error("Failed to open with external installer: ${error.message}")
                    session.update { it.copy(installError = error.message) }
                },
            ) {
                installer.openWithExternalInstaller(filePath)
                logCurrent(session, LogResult.OpenedInExternalInstaller)
            }
        }
        dismissExternalInstallerPrompt(session)
    }

    private fun dismissExternalInstallerPrompt(session: DetailsSession) {
        session.update { it.copy(showExternalInstallerPrompt = false, pendingInstallFilePath = null) }
    }

    private fun logCurrent(
        session: DetailsSession,
        result: LogResult,
    ) {
        val asset = session.current.primaryAsset ?: return
        val release = session.current.selectedRelease ?: return
        session.appendLog(asset.name, asset.size, release.tagName, result)
    }

    private companion object {
        const val OBTAINIUM_REPO_ID: Long = 523534328
        const val APP_MANAGER_REPO_ID: Long = 268006778
        const val FULL_PERCENT = 100
    }
}
