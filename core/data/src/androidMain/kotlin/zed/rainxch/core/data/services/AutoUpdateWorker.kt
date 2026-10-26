package zed.rainxch.core.data.services

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import zed.rainxch.core.data.download.AssetSourceGoneException
import zed.rainxch.core.data.network.BackgroundRequest
import zed.rainxch.core.data.services.dhizuku.DhizukuServiceManager
import zed.rainxch.core.data.services.dhizuku.model.DhizukuStatus
import zed.rainxch.core.data.services.root.RootServiceManager
import zed.rainxch.core.data.services.root.model.RootStatus
import zed.rainxch.core.data.services.shizuku.ShizukuServiceManager
import zed.rainxch.core.data.services.shizuku.model.ShizukuStatus
import zed.rainxch.core.domain.model.apk.ApkPackageInfo
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.InstallerType
import zed.rainxch.core.domain.model.installation.markPending
import zed.rainxch.core.domain.model.installation.withLatestSnapshot
import zed.rainxch.core.domain.network.Downloader
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.SystemInstallSerializer
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class AutoUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val installedAppsRepository: InstalledAppsRepository by inject()
    private val installer: Installer by inject()
    private val downloader: Downloader by inject()
    private val tweaksRepository: TweaksRepository by inject()
    private val shizukuServiceManager: ShizukuServiceManager by inject()
    private val dhizukuServiceManager: DhizukuServiceManager by inject()
    private val rootServiceManager: RootServiceManager by inject()
    private val systemInstallSerializer: SystemInstallSerializer by inject()

    override suspend fun doWork(): Result = withContext(BackgroundRequest()) { runAutoUpdate() }

    private suspend fun runAutoUpdate(): Result =
        try {
            Logger.i { "AutoUpdateWorker: Starting auto-update" }
            val appsWithUpdates = appsToUpdate()
            if (appsWithUpdates.isNotEmpty()) updateAll(appsWithUpdates)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            retryOrFail(e)
        } catch (e: LocalStoreException) {
            retryOrFail(e)
        } catch (e: IllegalStateException) {
            retryOrFail(e)
        } catch (e: SecurityException) {
            retryOrFail(e)
        }

    private fun retryOrFail(e: Exception): Result {
        Logger.e { "AutoUpdateWorker: Fatal error: ${e.message}" }
        return if (runAttemptCount < 2) {
            Result.retry()
        } else {
            Result.failure()
        }
    }

    private suspend fun appsToUpdate(): List<InstalledApp> {
        val autoUpdateEnabled = tweaksRepository.getAutoUpdateEnabled().first()
        val installerType = tweaksRepository.getInstallerType().first()
        val silentReady = isSilentInstallerReady(installerType)

        if (!autoUpdateEnabled || !silentReady) {
            Logger.i {
                "AutoUpdateWorker: Conditions not met (autoUpdate=$autoUpdateEnabled, installer=$installerType, " +
                    "silentReady=$silentReady), skipping"
            }
            return emptyList()
        }

        val appsWithUpdates = installedAppsRepository.getAppsWithUpdates().first()
        if (appsWithUpdates.isEmpty()) {
            Logger.d { "AutoUpdateWorker: No apps need updating" }
        }
        return appsWithUpdates
    }

    private fun isSilentInstallerReady(installerType: InstallerType): Boolean =
        when (installerType) {
            InstallerType.THIRD_PARTY -> {
                false
            }

            InstallerType.SHIZUKU -> {
                shizukuServiceManager.refreshStatus()
                shizukuServiceManager.status.value == ShizukuStatus.READY
            }

            InstallerType.DHIZUKU -> {
                dhizukuServiceManager.refreshStatus()
                dhizukuServiceManager.status.value == DhizukuStatus.READY
            }

            InstallerType.ROOT -> {
                rootServiceManager.refreshStatus()
                rootServiceManager.status.value == RootStatus.READY
            }

            InstallerType.DEFAULT -> {
                false
            }
        }

    private suspend fun updateAll(appsWithUpdates: List<InstalledApp>) {
        setForegroundIfAllowed(createForegroundInfo("Updating apps...", 0, appsWithUpdates.size))

        val successfulApps = mutableListOf<String>()
        val failedApps = mutableListOf<String>()

        appsWithUpdates.forEachIndexed { index, app ->
            setForegroundIfAllowed(
                createForegroundInfo(
                    "Updating ${app.appName}...",
                    index + 1,
                    appsWithUpdates.size,
                ),
            )

            if (tryUpdate(app)) {
                successfulApps.add(app.appName)
            } else {
                failedApps.add(app.appName)
            }
        }

        showSummaryNotification(successfulApps, failedApps)

        Logger.i { "AutoUpdateWorker: Completed. Success: ${successfulApps.size}, Failed: ${failedApps.size}" }
    }

    private suspend fun tryUpdate(app: InstalledApp): Boolean =
        try {
            updateApp(app)
            Logger.i { "AutoUpdateWorker: Successfully updated ${app.appName}" }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            onUpdateFailed(app, e)
        } catch (e: IllegalStateException) {
            onUpdateFailed(app, e)
        } catch (e: IllegalArgumentException) {
            onUpdateFailed(app, e)
        } catch (e: AssetSourceGoneException) {
            onUpdateFailed(app, e)
        } catch (e: LocalStoreException) {
            onUpdateFailed(app, e)
        } catch (e: SecurityException) {
            onUpdateFailed(app, e)
        }

    private suspend fun onUpdateFailed(
        app: InstalledApp,
        e: Exception,
    ): Boolean {
        Logger.e { "AutoUpdateWorker: Failed to update ${app.appName}: ${e.message}" }
        try {
            installedAppsRepository.updatePendingStatus(app.packageName, false)
        } catch (clearEx: LocalStoreException) {
            Logger.e { "AutoUpdateWorker: Failed to clear pending status: ${clearEx.message}" }
        }
        return false
    }

    private suspend fun updateApp(app: InstalledApp) {
        val assetUrl = app.latestAssetUrl ?: error("No asset URL for ${app.appName}")
        val assetName = app.latestAssetName ?: error("No asset name for ${app.appName}")
        val latestVersion = app.latestVersion ?: error("No latest version for ${app.appName}")

        val ext = assetName.substringAfterLast('.', "").lowercase()

        discardStaleDownload(app, assetName, latestVersion)

        Logger.d { "AutoUpdateWorker: Downloading $assetName for ${app.appName}" }
        downloader.download(assetUrl, assetName).collect { }

        val filePath =
            downloader.getDownloadedFilePath(assetName)
                ?: error("Downloaded file not found for ${app.appName}")

        val apkInfo =
            installer.getApkInfoExtractor().extractPackageInfo(filePath)

        checkPackageName(app, apkInfo)

        val currentApp = installedAppsRepository.getAppByPackage(app.packageName)

        checkSigner(app, apkInfo, currentApp)

        if (apkInfo == null) {
            Logger.w { "AutoUpdateWorker: APK info extraction failed for ${app.appName}, installing without validation" }
        }

        if (currentApp != null) {
            installedAppsRepository.updateApp(
                currentApp
                    .markPending(
                        releaseId = currentApp.latestReleaseId,
                        assetId = currentApp.latestAssetId,
                        assetDigest = currentApp.latestAssetDigest,
                        releasePublishedAt = currentApp.latestReleasePublishedAt,
                    ).withLatestSnapshot(
                        version = latestVersion,
                        assetName = assetName,
                        assetUrl = assetUrl,
                        versionName = apkInfo?.versionName ?: latestVersion,
                        versionCode = apkInfo?.versionCode ?: 0L,
                    ),
            )
        }

        installDownloaded(app, filePath, ext)
    }

    private suspend fun discardStaleDownload(
        app: InstalledApp,
        assetName: String,
        latestVersion: String,
    ) {
        val existingPath = downloader.getDownloadedFilePath(assetName) ?: return
        val apkInfo = installer.getApkInfoExtractor().extractPackageInfo(existingPath)
        val normalizedExisting =
            apkInfo?.versionName?.removePrefix("v")?.removePrefix("V") ?: ""
        val normalizedLatest = latestVersion.removePrefix("v").removePrefix("V")
        if (normalizedExisting != normalizedLatest) {
            java.io.File(existingPath).delete()
            Logger.d { "AutoUpdateWorker: Deleted mismatched existing file for ${app.appName}" }
        }
    }

    private fun checkPackageName(
        app: InstalledApp,
        apkInfo: ApkPackageInfo?,
    ) {
        if (apkInfo == null || apkInfo.packageName == app.packageName) return
        Logger.e {
            "AutoUpdateWorker: Package name mismatch for ${app.appName}! " +
                "Expected: ${app.packageName}, got: ${apkInfo.packageName}. " +
                "Skipping auto-update."
        }
        error("Package name mismatch for ${app.appName}: expected ${app.packageName}, got ${apkInfo.packageName}")
    }

    private fun checkSigner(
        app: InstalledApp,
        apkInfo: ApkPackageInfo?,
        currentApp: InstalledApp?,
    ) {
        val storedSigner = currentApp?.signingFingerprint
        val expected = storedSigner?.trim()?.uppercase()
        val actual = apkInfo?.signingFingerprint?.trim()?.uppercase()
        if (apkInfo == null || expected == null || actual == expected) return
        Logger.e {
            "AutoUpdateWorker: Signing key mismatch for ${app.appName}! " +
                "Expected: $storedSigner, got: ${apkInfo.signingFingerprint}. " +
                "Skipping auto-update."
        }
        error("Signing fingerprint verification failed for ${app.appName}, blocking auto-update")
    }

    private suspend fun installDownloaded(
        app: InstalledApp,
        filePath: String,
        ext: String,
    ) {
        val installerLabel = tweaksRepository.getInstallerType().first().name
        Logger.d { "AutoUpdateWorker: Installing ${app.appName} via $installerLabel" }
        var handedOver = false
        try {
            systemInstallSerializer.awaitFreeAndMarkPending(app.packageName)
            installer.install(filePath, ext)
            handedOver = true
        } finally {
            if (!handedOver) {
                systemInstallSerializer.markCompleted(app.packageName)
                installedAppsRepository.updatePendingStatus(app.packageName, false)
            }
        }

        Logger.d {
            "AutoUpdateWorker: Install command completed for ${app.appName}, waiting for system confirmation via broadcast"
        }
    }

    private fun createForegroundInfo(
        message: String,
        current: Int,
        total: Int,
    ): ForegroundInfo {
        val builder =
            NotificationCompat
                .Builder(applicationContext, UPDATE_SERVICE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Komi Store")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setSilent(true)

        if (total > 0) {
            builder.setProgress(total, current, false)
        }

        val notification = builder.build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun showSummaryNotification(
        successfulApps: List<String>,
        failedApps: List<String>,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted =
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        if (successfulApps.isEmpty() && failedApps.isEmpty()) return

        val notification =
            NotificationCompat
                .Builder(applicationContext, UPDATES_CHANNEL_ID)
                .setSmallIcon(
                    if (failedApps.isEmpty()) {
                        android.R.drawable.stat_sys_download_done
                    } else {
                        android.R.drawable.stat_notify_error
                    },
                ).setContentTitle(summaryTitle(successfulApps, failedApps))
                .setContentText(summaryText(successfulApps, failedApps))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(launchAppPendingIntent())
                .setAutoCancel(true)
                .build()

        NotificationManagerCompat
            .from(applicationContext)
            .notify(SUMMARY_NOTIFICATION_ID, notification)
    }

    private fun summaryTitle(
        successfulApps: List<String>,
        failedApps: List<String>,
    ): String =
        when {
            failedApps.isEmpty() -> "${successfulApps.size} app${if (successfulApps.size > 1) "s" else ""} updated"
            successfulApps.isEmpty() -> "Failed to update ${failedApps.size} app${if (failedApps.size > 1) "s" else ""}"
            else -> "${successfulApps.size} updated, ${failedApps.size} failed"
        }

    private fun summaryText(
        successfulApps: List<String>,
        failedApps: List<String>,
    ): String =
        when {
            failedApps.isEmpty() -> successfulApps.joinToString(", ")

            successfulApps.isEmpty() -> failedApps.joinToString(", ")

            else -> "Updated: ${successfulApps.joinToString(", ")}. Failed: ${
                failedApps.joinToString(
                    ", ",
                )
            }"
        }

    private fun launchAppPendingIntent(): PendingIntent? =
        applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }?.let {
                PendingIntent.getActivity(
                    applicationContext,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

    companion object {
        const val WORK_NAME = "github_store_auto_update"
        private const val UPDATES_CHANNEL_ID = "app_updates"
        private const val UPDATE_SERVICE_CHANNEL_ID = "update_service"
        private const val FOREGROUND_NOTIFICATION_ID = 1004
        private const val SUMMARY_NOTIFICATION_ID = 1005
    }
}
