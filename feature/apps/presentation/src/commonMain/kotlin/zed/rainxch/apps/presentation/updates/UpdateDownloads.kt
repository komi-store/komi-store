package zed.rainxch.apps.presentation.updates

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import zed.rainxch.apps.presentation.model.InstalledAppUi
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.installation.InstallerType
import zed.rainxch.core.domain.network.Downloader
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.DownloadOrchestrator
import zed.rainxch.core.domain.system.DownloadSpec
import zed.rainxch.core.domain.system.DownloadStage
import zed.rainxch.core.domain.system.InstallPolicy
import zed.rainxch.core.domain.system.Installer
import java.io.File

// The download half of an update: permissions, a stale local copy, and the orchestrator run until the file is ready.
class UpdateDownloads(
    private val installer: Installer,
    private val downloader: Downloader,
    private val downloadOrchestrator: DownloadOrchestrator,
    private val tweaksRepository: TweaksRepository,
    private val logger: KomiStoreLogger,
    private val appScope: CoroutineScope,
) {
    suspend fun ensurePermissions(target: UpdateTarget) {
        installer.ensurePermissionsOrThrow(target.extension)
    }

    // A copy left by an earlier download is kept only when it is the version this update installs.
    suspend fun discardStaleCopy(target: UpdateTarget) {
        val existingPath = downloader.getDownloadedFilePath(target.asset.name) ?: return
        val file = File(existingPath)
        recoverUpdateFailure({ e ->
            logger.debug("Failed to extract APK info for existing file: ${e.message}")
            val deleted = file.delete()
            logger.debug("Deleted unextractable existing file: $deleted")
        }) {
            val apkInfo = installer.getApkInfoExtractor().extractPackageInfo(existingPath)
            val normalizedExisting = apkInfo?.versionName?.removePrefix("v")?.removePrefix("V") ?: ""
            val normalizedLatest = target.tag.removePrefix("v").removePrefix("V")
            if (normalizedExisting != normalizedLatest) {
                val deleted = file.delete()
                logger.debug("Deleted mismatched existing file ($normalizedExisting != $normalizedLatest): $deleted")
            }
        }
    }

    suspend fun installPolicy(): InstallPolicy =
        when (tweaksRepository.getInstallerType().first()) {
            InstallerType.SHIZUKU, InstallerType.DHIZUKU, InstallerType.ROOT -> InstallPolicy.AlwaysInstall
            InstallerType.DEFAULT, InstallerType.THIRD_PARTY -> InstallPolicy.InstallWhileForeground
        }

    suspend fun download(
        app: InstalledAppUi,
        target: UpdateTarget,
        policy: InstallPolicy,
        onProgress: (Int) -> Unit,
    ): UpdateDownloadResult {
        downloadOrchestrator.enqueue(
            DownloadSpec(
                packageName = app.packageName,
                repoOwner = app.repoOwner,
                repoName = app.repoName,
                repoId = app.repoId,
                sourceHost = app.sourceHost,
                repoOwnerAvatarUrl = app.repoOwnerAvatarUrl,
                repoDescription = app.repoDescription,
                asset = target.asset,
                displayAppName = app.appName,
                installPolicy = policy,
                releaseTag = target.tag,
                releaseId = target.releaseId,
            ),
        )
        return awaitReady(app.packageName, onProgress)
    }

    suspend fun cleanup(
        packageName: String,
        assetName: String?,
    ) {
        if (assetName == null) return
        recoverUpdateFailure({ e -> logger.error("Cleanup failed for $packageName: ${e.message}") }) {
            val deleted = downloader.cancelDownload(assetName)
            logger.debug("Cleanup for $packageName - file deleted: $deleted")
        }
    }

    // Runs on the app scope, as it outlives the screen that started these downloads.
    fun deferAll(packageNames: List<String>) {
        appScope.launch {
            for (packageName in packageNames) {
                recoverUpdateFailure({ e -> logger.error("Failed to downgrade orchestrator for $packageName: ${e.message}") }) {
                    downloadOrchestrator.downgradeToDeferred(packageName)
                }
            }
        }
    }

    private suspend fun awaitReady(
        packageName: String,
        onProgress: (Int) -> Unit,
    ): UpdateDownloadResult {
        val terminal =
            downloadOrchestrator
                .observe(packageName)
                .onEach { entry -> entry?.progressPercent?.let(onProgress) }
                .first { entry -> entry == null || entry.stage in TERMINAL_STAGES }
        return when {
            terminal == null -> {
                UpdateDownloadResult.Cancelled
            }

            terminal.stage == DownloadStage.AwaitingInstall -> {
                terminal.filePath?.let { UpdateDownloadResult.Ready(it, terminal) }
                    ?: UpdateDownloadResult.Failed("Downloaded file path missing")
            }

            terminal.stage == DownloadStage.Completed -> {
                UpdateDownloadResult.AlreadyInstalled
            }

            terminal.stage == DownloadStage.Failed -> {
                UpdateDownloadResult.Failed(terminal.errorMessage)
            }

            else -> {
                UpdateDownloadResult.Cancelled
            }
        }
    }

    private companion object {
        val TERMINAL_STAGES =
            setOf(
                DownloadStage.AwaitingInstall,
                DownloadStage.Completed,
                DownloadStage.Cancelled,
                DownloadStage.Failed,
            )
    }
}
