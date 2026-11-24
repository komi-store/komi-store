package zed.rainxch.details.presentation.handlers

import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.apk.ApkPackageInfo
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.InstallOutcome
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.SystemInstallSerializer
import zed.rainxch.details.domain.model.ApkValidationResult
import zed.rainxch.details.domain.model.FingerprintCheckResult
import zed.rainxch.details.domain.model.SaveInstalledAppParams
import zed.rainxch.details.domain.model.UpdateInstalledAppParams
import zed.rainxch.details.domain.system.AttestationVerifier
import zed.rainxch.details.domain.system.InstallationManager
import zed.rainxch.details.domain.system.VerificationResult
import zed.rainxch.details.presentation.RawDetailsState
import zed.rainxch.details.presentation.model.AttestationStatus
import zed.rainxch.details.presentation.model.DownloadStage
import zed.rainxch.details.presentation.model.LogResult
import zed.rainxch.details.presentation.model.SigningKeyWarning
import zed.rainxch.details.presentation.utils.recoverStepFailure
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.installer_saved_downloads
import zed.rainxch.githubstore.core.presentation.res.update_package_mismatch

// Installs a downloaded file: validates the APK and its signing key, hands it to the installer and records the app.
internal class ApkInstallStep(
    private val installer: Installer,
    private val installationManager: InstallationManager,
    private val attestationVerifier: AttestationVerifier,
    private val systemInstallSerializer: SystemInstallSerializer,
    private val installedAppsRepository: InstalledAppsRepository,
    private val logger: KomiStoreLogger,
) {
    private sealed interface Validation {
        data object Unchecked : Validation

        data object Refused : Validation

        class Checked(
            val apkInfo: ApkPackageInfo,
        ) : Validation
    }

    internal suspend fun install(
        session: DetailsSession,
        request: InstallRequest,
        filePath: String,
    ) {
        session.update { it.copy(downloadStage = DownloadStage.INSTALLING) }
        val extension = request.assetName.substringAfterLast('.', "").lowercase()
        val validation = if (extension == APK_EXTENSION) validate(session, request, filePath) else Validation.Unchecked
        if (validation == Validation.Refused) return
        val apkInfo = (validation as? Validation.Checked)?.apkInfo
        val outcome = installGated(session, apkInfo?.packageName, filePath, extension)
        launchAttestationCheck(session, filePath)
        val isAndroid = session.platform == Platform.ANDROID
        if (isAndroid && apkInfo != null) {
            saveInstalledApp(session, apkInfo, request, outcome, parkedFilePath = filePath)
        } else if (!isAndroid) {
            session.launch { session.message(getString(Res.string.installer_saved_downloads)) }
        }
        session.update { it.copy(downloadStage = DownloadStage.IDLE) }
        session.currentAssetName = null
        session.appendLog(request, request.finishedResult())
    }

    private suspend fun validate(
        session: DetailsSession,
        request: InstallRequest,
        filePath: String,
    ): Validation =
        when (
            val result =
                installationManager.validateApk(
                    filePath = filePath,
                    isUpdate = request.isUpdate,
                    trackedPackageName = session.current.installedApp?.packageName,
                )
        ) {
            is ApkValidationResult.ExtractionFailed -> {
                logger.warn("Could not extract APK info for ${request.assetName}, proceeding with unvalidated install")
                Validation.Unchecked
            }

            is ApkValidationResult.PackageMismatch -> {
                refusePackageMismatch(session, request, result)
                Validation.Refused
            }

            is ApkValidationResult.Valid -> {
                checkSigningKey(session, request, filePath, result.apkInfo)
            }
        }

    private suspend fun refusePackageMismatch(
        session: DetailsSession,
        request: InstallRequest,
        mismatch: ApkValidationResult.PackageMismatch,
    ) {
        logger.error(
            "Package name mismatch on update: APK=${mismatch.apkPackageName}, installed=${mismatch.installedPackageName}",
        )
        val message = getString(Res.string.update_package_mismatch, mismatch.apkPackageName, mismatch.installedPackageName)
        session.update { it.copy(downloadStage = DownloadStage.IDLE, installError = message) }
        session.currentAssetName = null
        session.appendLog(request, LogResult.Error("Package name mismatch"))
    }

    private suspend fun checkSigningKey(
        session: DetailsSession,
        request: InstallRequest,
        filePath: String,
        apkInfo: ApkPackageInfo,
    ): Validation {
        val check = installationManager.checkSigningFingerprint(apkInfo)
        if (check !is FingerprintCheckResult.Mismatch) return Validation.Checked(apkInfo)
        val warning =
            SigningKeyWarning(
                packageName = apkInfo.packageName,
                expectedFingerprint = check.expectedFingerprint,
                actualFingerprint = check.actualFingerprint,
                pendingDownloadUrl = request.downloadUrl,
                pendingAssetName = request.assetName,
                pendingSizeBytes = request.sizeBytes,
                pendingReleaseTag = request.releaseTag,
                pendingIsUpdate = request.isUpdate,
                pendingFilePath = filePath,
                pendingApkInfo = apkInfo,
            )
        session.update { it.copy(signingKeyWarning = warning) }
        session.appendLog(request, LogResult.Error("Signing key changed"))
        return Validation.Refused
    }

    // The serializer keeps two installs of one package apart; a failed hand-off frees it again.
    private suspend fun installGated(
        session: DetailsSession,
        packageName: String?,
        filePath: String,
        extension: String,
    ): InstallOutcome {
        val gate = packageName?.takeIf { session.platform == Platform.ANDROID }
        if (gate != null) systemInstallSerializer.awaitFreeAndMarkPending(gate, timeoutMs = 0L)
        return recoverStepFailure(
            recover = { error ->
                if (gate != null) systemInstallSerializer.markCompleted(gate)
                throw error
            },
        ) { installer.install(filePath, extension) }
    }

    private fun launchAttestationCheck(
        session: DetailsSession,
        filePath: String,
    ) {
        val repo = session.current.repository ?: return
        session.update { it.copy(attestationStatus = AttestationStatus.CHECKING) }
        session.launch {
            val status =
                when (attestationVerifier.verify(repo.owner.login, repo.name, filePath)) {
                    is VerificationResult.Verified -> AttestationStatus.VERIFIED
                    is VerificationResult.Unverified -> AttestationStatus.UNVERIFIED
                    is VerificationResult.Error -> AttestationStatus.UNABLE_TO_VERIFY
                }
            session.update { it.copy(attestationStatus = status) }
        }
    }

    // The user accepted a changed signing key, so the parked file is installed as it was.
    internal fun overrideSigningKeyWarning(session: DetailsSession) {
        val warning = session.current.signingKeyWarning ?: return
        session.update { it.copy(signingKeyWarning = null, downgradeWarning = null) }
        val request =
            InstallRequest(
                downloadUrl = warning.pendingDownloadUrl,
                assetName = warning.pendingAssetName,
                sizeBytes = warning.pendingSizeBytes,
                releaseTag = warning.pendingReleaseTag,
                isUpdate = warning.pendingIsUpdate,
            )
        session.launch {
            recoverStepFailure(
                recover = { error ->
                    logger.error("Install after override failed: ${error.message}")
                    session.update { it.copy(downloadStage = DownloadStage.IDLE, installError = error.message) }
                    session.currentAssetName = null
                },
            ) { installOverridden(session, warning, request) }
        }
    }

    private suspend fun installOverridden(
        session: DetailsSession,
        warning: SigningKeyWarning,
        request: InstallRequest,
    ) {
        val extension = warning.pendingAssetName.substringAfterLast('.', "").lowercase()
        val outcome = installGated(session, warning.pendingApkInfo.packageName, warning.pendingFilePath, extension)
        if (session.platform == Platform.ANDROID) {
            saveInstalledApp(session, warning.pendingApkInfo, request, outcome, parkedFilePath = null)
        }
        session.update { it.copy(downloadStage = DownloadStage.IDLE) }
        session.currentAssetName = null
        session.appendLog(request, request.finishedResult())
    }

    // Records an install the download orchestrator finished on its own, once the APK checks out.
    internal suspend fun recordSettledInstall(
        session: DetailsSession,
        request: InstallRequest,
        filePath: String,
        outcome: InstallOutcome,
    ) {
        recoverStepFailure(
            recover = { error -> logger.error("Failed to persist orchestrator install: ${error.message}") },
        ) {
            val validation =
                installationManager.validateApk(
                    filePath = filePath,
                    isUpdate = request.isUpdate,
                    trackedPackageName = session.current.installedApp?.packageName,
                )
            if (validation is ApkValidationResult.Valid) {
                saveInstalledApp(session, validation.apkInfo, request, outcome, parkedFilePath = filePath)
            } else {
                logger.warn("Orchestrator install settled (outcome=$outcome) but APK validation failed: $validation")
            }
        }
    }

    private suspend fun saveInstalledApp(
        session: DetailsSession,
        apkInfo: ApkPackageInfo,
        request: InstallRequest,
        outcome: InstallOutcome,
        parkedFilePath: String?,
    ) {
        val current = session.current
        val repo = current.repository ?: return
        val isPending = outcome != InstallOutcome.COMPLETED && session.platform == Platform.ANDROID
        val pendingPath = parkedFilePath?.takeIf { isPending }
        val source = Source.of(current, request)
        if (request.isUpdate) {
            installationManager.updateInstalledAppVersion(
                UpdateInstalledAppParams(
                    apkInfo = apkInfo,
                    assetName = request.assetName,
                    assetUrl = request.downloadUrl,
                    releaseTag = request.releaseTag,
                    releaseId = source.releaseId,
                    assetId = source.assetId,
                    assetDigest = source.assetDigest,
                    releasePublishedAt = source.publishedAt,
                    isPendingInstall = isPending,
                ),
            )
            pendingPath?.let { parkPendingFile(apkInfo.packageName, it, request) }
        } else {
            val installable = current.installableAssets
            val reloaded =
                installationManager.saveNewInstalledApp(
                    SaveInstalledAppParams(
                        repo = repo,
                        apkInfo = apkInfo,
                        assetName = request.assetName,
                        assetUrl = request.downloadUrl,
                        assetSize = request.sizeBytes,
                        releaseTag = request.releaseTag,
                        releaseId = source.releaseId,
                        assetId = source.assetId,
                        assetDigest = source.assetDigest,
                        releasePublishedAt = source.publishedAt,
                        isPendingInstall = isPending,
                        isFavourite = current.isFavourite,
                        siblingAssetCount = installable.size,
                        pickedAssetIndex = installable.indexOfFirst { it.name == request.assetName }.takeIf { it >= 0 },
                        pendingInstallFilePath = pendingPath,
                        sourceHost = session.args.sourceHost,
                    ),
                )
            session.update { it.copy(installedApp = reloaded) }
        }
    }

    // The release and asset the file came from, identified only when both are known.
    private class Source(
        val releaseId: Long?,
        val assetId: Long?,
        val assetDigest: String?,
        val publishedAt: String?,
    ) {
        companion object {
            fun of(
                state: RawDetailsState,
                request: InstallRequest,
            ): Source {
                // The asset of that same release: installableAssets follows the current selection.
                val release =
                    state.selectedRelease?.takeIf { it.tagName == request.releaseTag }
                        ?: state.allReleases.firstOrNull { it.tagName == request.releaseTag }
                val asset = release?.assets?.firstOrNull { it.name == request.assetName }
                val known = release != null && asset != null
                return Source(
                    releaseId = release?.id?.takeIf { known },
                    assetId = asset?.id?.takeIf { known },
                    assetDigest = asset?.digest?.takeIf { known },
                    publishedAt = release?.publishedAt,
                )
            }
        }
    }

    private suspend fun parkPendingFile(
        packageName: String,
        path: String,
        request: InstallRequest,
    ) {
        try {
            installedAppsRepository.setPendingInstallFilePath(
                packageName = packageName,
                path = path,
                version = request.releaseTag,
                assetName = request.assetName,
            )
        } catch (e: LocalStoreException) {
            logger.warn("Failed to park pending install path on update: ${e.message}")
        }
    }

    private companion object {
        const val APK_EXTENSION = "apk"
    }
}
