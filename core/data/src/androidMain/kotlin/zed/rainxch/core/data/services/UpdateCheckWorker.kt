package zed.rainxch.core.data.services

import android.Manifest
import android.annotation.SuppressLint
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
import zed.rainxch.core.data.local.db.dao.ExternalLinkDao
import zed.rainxch.core.data.network.BackgroundRequest
import zed.rainxch.core.domain.model.installation.InstallerType
import zed.rainxch.core.domain.repository.ExternalImportRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.PackageMonitor
import zed.rainxch.core.domain.use_cases.SyncInstalledAppsUseCase

class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val installedAppsRepository: InstalledAppsRepository by inject()
    private val syncInstalledAppsUseCase: SyncInstalledAppsUseCase by inject()
    private val tweaksRepository: TweaksRepository by inject()
    private val externalImportRepository: ExternalImportRepository by inject()
    private val externalLinkDao: ExternalLinkDao by inject()
    private val packageMonitor: PackageMonitor by inject()

    override suspend fun doWork(): Result = withContext(BackgroundRequest()) { runCheck() }

    private suspend fun runCheck(): Result =
        try {
            Logger.i { "UpdateCheckWorker: Starting periodic update check" }

            setForegroundIfAllowed(createForegroundInfo("Checking for updates..."))

            val syncResult = syncInstalledAppsUseCase()
            if (syncResult.isFailure) {
                Logger.w { "UpdateCheckWorker: Sync had issues: ${syncResult.exceptionOrNull()?.message}" }
            }

            installedAppsRepository.checkAllForUpdates()

            val appsWithUpdates = installedAppsRepository.getAppsWithUpdates().first()

            if (appsWithUpdates.isNotEmpty()) {

                val autoUpdateEnabled = tweaksRepository.getAutoUpdateEnabled().first()
                val installerType = tweaksRepository.getInstallerType().first()

                val isSilentBackend = installerType == InstallerType.SHIZUKU || installerType == InstallerType.DHIZUKU
                if (autoUpdateEnabled && isSilentBackend) {
                    Logger.i {
                        "UpdateCheckWorker: Auto-update enabled with $installerType, scheduling AutoUpdateWorker for ${appsWithUpdates.size} apps"
                    }
                    UpdateScheduler.scheduleAutoUpdate(applicationContext)
                } else {

                    showUpdateNotification(appsWithUpdates)
                }
            } else {
                Logger.d { "UpdateCheckWorker: No updates available" }
            }

            runPeriodicExternalDeltaScan()

            Logger.i { "UpdateCheckWorker: Periodic update check completed successfully" }
            Result.success()
        } catch (e: Exception) {
            Logger.e { "UpdateCheckWorker: Update check failed: ${e.message}" }
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }

    private suspend fun runPeriodicExternalDeltaScan() {
        try {
            val installed = packageMonitor.getAllInstalledPackageNames()
            if (installed.isEmpty()) {
                return
            }

            val trackedFlow = installedAppsRepository.getAllInstalledApps().first()
            val tracked = trackedFlow.map { it.packageName }.toSet()

            val permanent = (
                externalLinkDao.getDoNotRescanPackageNames() +
                    externalLinkDao.getActiveSkippedPackageNames(System.currentTimeMillis())
                ).toSet()

            val delta = (installed - tracked - permanent).take(MAX_DELTA_PACKAGES).toSet()
            if (delta.isEmpty()) {
                Logger.d { "UpdateCheckWorker: external delta scan empty" }
                return
            }

            Logger.d { "UpdateCheckWorker: external delta scan ${delta.size} package(s)" }
            externalImportRepository.runDeltaScan(delta)
        } catch (e: Exception) {
            Logger.w { "UpdateCheckWorker: external delta scan failed: ${e.message}" }
        }
    }

    private fun createForegroundInfo(message: String): ForegroundInfo {
        val notification =
            NotificationCompat
                .Builder(applicationContext, UPDATE_SERVICE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Komi Store")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setSilent(true)
                .build()

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

    @SuppressLint("MissingPermission")
    private suspend fun showUpdateNotification(appsWithUpdates: List<zed.rainxch.core.domain.model.installation.InstalledApp>) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted =
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Logger.w { "UpdateCheckWorker: POST_NOTIFICATIONS permission not granted, skipping notification" }
                return
            }
        }

        val title =
            if (appsWithUpdates.size == 1) {
                "${appsWithUpdates.first().appName} update available"
            } else {
                "${appsWithUpdates.size} app updates available"
            }

        val text =
            if (appsWithUpdates.size == 1) {
                val app = appsWithUpdates.first()
                "${app.installedVersion} → ${app.latestVersion}"
            } else {
                appsWithUpdates.joinToString(", ") { it.appName }
            }

        val launchIntent =
            applicationContext.packageManager
                .getLaunchIntentForPackage(applicationContext.packageName)
                ?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

        val pendingIntent =
            launchIntent?.let {
                PendingIntent.getActivity(
                    applicationContext,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

        val notification =
            NotificationCompat
                .Builder(applicationContext, UPDATES_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        Logger.i { "UpdateCheckWorker: Showed notification for ${appsWithUpdates.size} updates" }
    }

    companion object {
        const val WORK_NAME = "github_store_update_check"
        private const val UPDATES_CHANNEL_ID = "app_updates"
        private const val UPDATE_SERVICE_CHANNEL_ID = "update_service"
        private const val NOTIFICATION_ID = 1001
        private const val FOREGROUND_NOTIFICATION_ID = 1003
        private const val MAX_DELTA_PACKAGES = 50
    }
}
