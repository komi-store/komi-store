package zed.rainxch.core.data.services

import co.touchlab.kermit.Logger
import zed.rainxch.core.data.download.catchingStepFailures
import zed.rainxch.core.domain.model.apk.ApkPackageInfo
import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.markPending
import zed.rainxch.core.domain.model.installation.withLatestSnapshot
import zed.rainxch.core.domain.network.DigestVerifier
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.DownloadSpec
import zed.rainxch.core.domain.system.DownloadStage
import zed.rainxch.core.domain.system.InstallOutcome
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.OrchestratedDownload
import zed.rainxch.core.domain.system.PendingInstallNotifier
import zed.rainxch.core.domain.system.SystemInstallSerializer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

class DownloadInstallRunner(
    private val installer: Installer,
    private val installedAppsRepository: InstalledAppsRepository,
    private val pendingInstallNotifier: PendingInstallNotifier,
    private val systemInstallSerializer: SystemInstallSerializer,
    private val digestVerifier: DigestVerifier,
) {
    suspend fun install(
        spec: DownloadSpec,
        filePath: String,
        entries: DownloadEntries,
    ) {
        entries.update(spec.packageName) { it.copy(stage = DownloadStage.Installing) }
        val ext =
            spec.asset.name
                .substringAfterLast('.', "")
                .lowercase()
        var delegated = false
        var parkedTarget = false
        val result =
            try {
                catchingStepFailures {
                    installer.ensurePermissionsOrThrow(ext)
                    parkedTarget = parkTargetIdentity(spec, filePath)
                    systemInstallSerializer.awaitFreeAndMarkPending(spec.packageName)
                    val outcome = installer.install(filePath, ext)
                    delegated = outcome == InstallOutcome.DELEGATED_TO_SYSTEM
                    when (outcome) {
                        InstallOutcome.COMPLETED -> {
                            releaseCompletedInstall(spec.packageName)
                        }

                        InstallOutcome.DELEGATED_TO_SYSTEM -> {
                            Logger.i {
                                "Orchestrator: AlwaysInstall path returned DELEGATED_TO_SYSTEM " +
                                    "for ${spec.packageName}; Completed-with-pending so DB row stays pending"
                            }
                        }
                    }
                    entries.update(spec.packageName) {
                        it.copy(stage = DownloadStage.Completed, installOutcome = outcome)
                    }
                }
            } catch (e: CancellationException) {
                if (!delegated) systemInstallSerializer.markCompleted(spec.packageName)
                throw e
            }
        result.onFailure { failure ->
            if (!delegated) systemInstallSerializer.markCompleted(spec.packageName)
            if (parkedTarget) {
                catchingStepFailures { installedAppsRepository.updatePendingStatus(spec.packageName, false) }
                    .onFailure { Logger.w(it) { "Orchestrator: failed to clear pending for ${spec.packageName}" } }
            }
            Logger.e(failure) { "Orchestrator: install failed for ${spec.packageName}" }
            entries.markFailed(spec.packageName, failure.message)
        }
    }

    suspend fun park(
        spec: DownloadSpec,
        filePath: String,
        notify: Boolean,
        entries: DownloadEntries,
    ) {
        val downloadedDigest =
            catchingStepFailures { digestVerifier.computeSha256(filePath) }
                .onFailure { Logger.w(it) { "Orchestrator: could not hash the parked file for ${spec.packageName}" } }
                .getOrNull()
        val releasePublishedAt = parkedReleasePublishedAt(spec)
        catchingStepFailures {
            installedAppsRepository.markAwaitingInstall(
                packageName = spec.packageName,
                path = filePath,
                version = spec.releaseTag,
                assetName = spec.asset.name,
                releaseId = spec.releaseId,
                assetId = spec.asset.id.takeIf { it > 0L },
                releasePublishedAt = releasePublishedAt,
                assetDigest = downloadedDigest,
            )
        }.onFailure { Logger.e(it) { "Orchestrator: failed to persist pending install path" } }
        if (notify) {
            pendingInstallNotifier.notifyPending(
                packageName = spec.packageName,
                repoOwner = spec.repoOwner,
                repoName = spec.repoName,
                appName = spec.displayAppName,
                versionTag = spec.releaseTag,
            )
        }
        entries.update(spec.packageName) { it.copy(stage = DownloadStage.AwaitingInstall) }
    }

    suspend fun installStandalone(
        packageName: String,
        filePath: String,
        ext: String,
        entry: OrchestratedDownload?,
        entries: DownloadEntries,
    ): InstallOutcome? {
        val entryId = entries[packageName]?.id
        entries.update(packageName) { it.copy(stage = DownloadStage.Installing) }
        var delegated = false
        val result =
            try {
                catchingStepFailures {
                    installer.ensurePermissionsOrThrow(ext)
                    systemInstallSerializer.awaitFreeAndMarkPending(packageName, timeoutMs = 0L)
                    val outcome = installer.install(filePath, ext)
                    delegated = outcome == InstallOutcome.DELEGATED_TO_SYSTEM
                    adoptUntrackedDownload(entry, filePath, outcome)
                    finishStandaloneInstall(packageName, outcome, entryId, entries)
                    outcome
                }
            } catch (e: CancellationException) {
                if (!delegated) systemInstallSerializer.markCompleted(packageName)
                throw e
            }
        return result.getOrElse { failure ->
            if (!delegated) systemInstallSerializer.markCompleted(packageName)
            Logger.e(failure) { "Orchestrator: standalone install failed for $packageName" }
            if (entryId != null) entries.markFailed(packageName, failure.message, onlyId = entryId)
            null
        }
    }

    private suspend fun finishStandaloneInstall(
        packageName: String,
        outcome: InstallOutcome,
        entryId: String?,
        entries: DownloadEntries,
    ) {
        when (outcome) {
            InstallOutcome.COMPLETED -> {
                releaseCompletedInstall(packageName)
                if (entryId != null) {
                    entries.update(packageName, onlyId = entryId) { it.copy(stage = DownloadStage.Completed) }
                }
            }

            InstallOutcome.DELEGATED_TO_SYSTEM -> {
                if (entryId != null) {
                    entries.update(packageName, onlyId = entryId) {
                        it.copy(stage = DownloadStage.Completed, installOutcome = outcome)
                    }
                }
            }
        }
    }

    private suspend fun releaseCompletedInstall(packageName: String) {
        systemInstallSerializer.markCompleted(packageName)
        catchingStepFailures { installedAppsRepository.setPendingInstallFilePath(packageName, null) }
            .onFailure { Logger.w(it) { "Orchestrator: failed to clear pending install path for $packageName" } }
        pendingInstallNotifier.clearPending(packageName)
    }

    // Only callers that pass a release hand the install record to the orchestrator; Details writes its own.
    private suspend fun parkTargetIdentity(
        spec: DownloadSpec,
        filePath: String,
    ): Boolean {
        val releaseId = spec.releaseId ?: return false
        return catchingStepFailures {
            val target = pendingTarget(spec, releaseId, filePath)
            if (target != null) installedAppsRepository.updateApp(target)
            target != null
        }.getOrElse { e ->
            Logger.w(e) { "Orchestrator: could not park install target for ${spec.packageName}" }
            false
        }
    }

    private suspend fun pendingTarget(
        spec: DownloadSpec,
        releaseId: Long,
        filePath: String,
    ): InstalledApp? {
        val app = installedAppsRepository.getAppByPackage(spec.packageName) ?: return null
        val apkInfo = installer.getApkInfoExtractor().extractPackageInfo(filePath) ?: return null
        if (apkInfo.packageName != spec.packageName) return null
        return app
            .markPending(
                releaseId = releaseId,
                assetId = spec.asset.id,
                assetDigest = spec.asset.digest,
                releasePublishedAt =
                    app.latestReleasePublishedAt.takeIf { app.latestReleaseId == spec.releaseId },
            ).withLatestSnapshot(
                version = spec.releaseTag,
                assetName = spec.asset.name,
                assetUrl = spec.asset.downloadUrl,
                versionName = apkInfo.versionName,
                versionCode = apkInfo.versionCode,
            )
    }

    private suspend fun parkedReleasePublishedAt(spec: DownloadSpec): String? =
        catchingStepFailures {
            installedAppsRepository
                .getAppByPackage(spec.packageName)
                ?.takeIf { spec.releaseId != null && it.latestReleaseId == spec.releaseId }
                ?.latestReleasePublishedAt
        }.onFailure { Logger.w(it) { "Orchestrator: could not read the parked release timestamp" } }
            .getOrNull()

    private suspend fun adoptUntrackedDownload(
        entry: OrchestratedDownload?,
        filePath: String,
        outcome: InstallOutcome,
    ) {
        if (entry == null) return
        val repoId = entry.repoId ?: return
        val apkInfo = readPackageInfo(entry, filePath) ?: return
        catchingStepFailures {
            if (installedAppsRepository.getAppByPackage(apkInfo.packageName) == null) {
                val isPending = outcome != InstallOutcome.COMPLETED
                installedAppsRepository.saveInstalledApp(adoptedApp(entry, repoId, apkInfo, filePath, isPending))
                Logger.i { "Orchestrator: adopted ${apkInfo.packageName} into the library (pending=$isPending)" }
            }
        }.onFailure { Logger.w(it) { "Orchestrator: failed to adopt ${entry.packageName}" } }
    }

    private suspend fun readPackageInfo(
        entry: OrchestratedDownload,
        filePath: String,
    ): ApkPackageInfo? =
        catchingStepFailures { installer.getApkInfoExtractor().extractPackageInfo(filePath) }
            .onFailure { Logger.w(it) { "Orchestrator: could not read ${entry.assetName} for adoption" } }
            .getOrNull()

    private fun adoptedApp(
        entry: OrchestratedDownload,
        repoId: Long,
        apkInfo: ApkPackageInfo,
        filePath: String,
        isPending: Boolean,
    ): InstalledApp {
        val now = Clock.System.now().toEpochMilliseconds()
        return InstalledApp(
            packageName = apkInfo.packageName,
            repoId = repoId,
            repoName = entry.repoName,
            repoOwner = entry.repoOwner,
            repoOwnerAvatarUrl = entry.repoOwnerAvatarUrl ?: "",
            repoDescription = entry.repoDescription,
            primaryLanguage = null,
            repoUrl = "https://${entry.sourceHost ?: "github.com"}/${entry.repoOwner}/${entry.repoName}",
            installedVersion = entry.releaseTag,
            installedAssetName = entry.assetName,
            installedAssetUrl = entry.downloadUrl,
            latestVersion = null,
            latestAssetName = null,
            latestAssetUrl = null,
            latestAssetSize = null,
            appName = entry.displayAppName,
            installSource = InstallSource.THIS_APP,
            installedAt = now,
            lastCheckedAt = now,
            lastUpdatedAt = now,
            isUpdateAvailable = false,
            signingFingerprint = apkInfo.signingFingerprint,
            releaseNotes = "",
            systemArchitecture = installer.detectSystemArchitecture().name,
            fileExtension = entry.assetName.substringAfterLast('.', ""),
            isPendingInstall = isPending,
            installedVersionName = apkInfo.versionName,
            installedVersionCode = apkInfo.versionCode,
            installedAssetId = entry.assetId.takeIf { it > 0L },
            installedAssetDigest = entry.assetDigest,
            pendingInstallFilePath = filePath.takeIf { isPending },
            pendingInstallVersion = entry.releaseTag.takeIf { isPending },
            pendingInstallAssetName = entry.assetName.takeIf { isPending },
            sourceHost = entry.sourceHost,
        )
    }
}
