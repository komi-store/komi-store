package zed.rainxch.core.data.services.dhizuku

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.content.pm.PackageInfoCompat
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DhizukuInstallerServiceImpl : IDhizukuInstallerService.Stub() {
    companion object {
        private const val TAG = "DhizukuService"

        private const val STATUS_SUCCESS = 0
        private const val STATUS_FAILURE = -1

        const val STATUS_PENDING_USER_ACTION_REQUIRED = -2
        private const val INSTALL_TIMEOUT_SECONDS = 120L
        private const val UNINSTALL_TIMEOUT_SECONDS = 30L

        private const val ACTION_INSTALL_RESULT = "zed.rainxch.dhizuku.INSTALL_RESULT"
        private const val ACTION_UNINSTALL_RESULT = "zed.rainxch.dhizuku.UNINSTALL_RESULT"

        private fun log(msg: String) = android.util.Log.d(TAG, msg)

        private fun logW(msg: String) = android.util.Log.w(TAG, msg)

        private fun logE(
            msg: String,
            e: Throwable? = null,
        ) = android.util.Log.e(TAG, msg, e)
    }

    override fun installPackage(
        pfd: ParcelFileDescriptor,
        fileSize: Long,
        expectedPackageName: String?,
        expectedVersionCode: Long,
        installerPackageName: String?,
    ): Int {
        log(
            "installPackage() called — fileSize=$fileSize, expected=$expectedPackageName@$expectedVersionCode, " +
                "installer=$installerPackageName",
        )
        log("Process UID: ${android.os.Process.myUid()}, PID: ${android.os.Process.myPid()}")

        val ctx: Context =
            currentApplicationOrNull() ?: run {
                logE("currentApplication() returned null — no context available")
                return STATUS_FAILURE
            }

        val installer = ctx.packageManager.packageInstaller
        val params = sessionParams(fileSize, installerPackageName)

        var sessionId = -1
        var session: PackageInstaller.Session? = null
        var receiver: BroadcastReceiver? = null
        var committed = false
        return try {
            failureAsStatus("installPackage() exception") {
                sessionId = installer.createSession(params)
                log("createSession() — sessionId=$sessionId")
                val openSession = installer.openSession(sessionId)
                session = openSession
                writeApk(openSession, pfd, fileSize)

                val latch = CountDownLatch(1)
                val resultRef = AtomicInteger(STATUS_FAILURE)
                val token = UUID.randomUUID().toString()
                val action = "$ACTION_INSTALL_RESULT.$token"
                val installReceiver = installResultReceiver(latch, resultRef, installerPackageName)
                receiver = installReceiver
                registerInternalReceiver(ctx, installReceiver, action)

                openSession.commit(resultPendingIntent(ctx, token, action).intentSender)
                committed = true
                log("session.commit() called, awaiting result...")

                if (latch.await(INSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    resultRef.get()
                } else {
                    logE("install timed out after ${INSTALL_TIMEOUT_SECONDS}s — verifying package state")
                    statusAfterInstallTimeout(ctx, installer, sessionId, expectedPackageName, expectedVersionCode)
                }
            }
        } finally {
            if (!committed && sessionId >= 0) {
                try {
                    installer.abandonSession(sessionId)
                } catch (_: Exception) {
                }
            }
            try {
                session?.close()
            } catch (_: Exception) {
            }
            unregisterQuietly(ctx, receiver)
        }
    }

    private fun sessionParams(
        fileSize: Long,
        installerPackageName: String?,
    ): PackageInstaller.SessionParams {
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        if (fileSize > 0) params.setSize(fileSize)
        installerPackageName?.takeIf { it.isNotBlank() }?.let { name ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                params.setInstallerPackageName(name)
            } else {
                logW("setInstallerPackageName needs Android 14+, installing without attribution $name")
            }
        }
        return params
    }

    private fun writeApk(
        session: PackageInstaller.Session,
        pfd: ParcelFileDescriptor,
        fileSize: Long,
    ) {
        session.openWrite("apk", 0, fileSize).use { out ->
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                val copied = input.copyTo(out)
                out.flush()
                session.fsync(out)
                log("APK piped to session: $copied bytes (expected: $fileSize)")
            }
        }
    }

    private fun installResultReceiver(
        latch: CountDownLatch,
        resultRef: AtomicInteger,
        installerPackageName: String?,
    ): BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                val status =
                    intent.getIntExtra(
                        PackageInstaller.EXTRA_STATUS,
                        PackageInstaller.STATUS_FAILURE,
                    )
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                log("install result — status=$status, message='$message'")
                when (status) {
                    PackageInstaller.STATUS_SUCCESS -> {
                        resultRef.set(STATUS_SUCCESS)
                    }

                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        logW(
                            "install requires user action (Android 14+ update-ownership gate) — " +
                                "installerAttribution=$installerPackageName",
                        )
                        resultRef.set(STATUS_PENDING_USER_ACTION_REQUIRED)
                    }

                    else -> {
                        logW("install failed status=$status: $message")
                        resultRef.set(STATUS_FAILURE)
                    }
                }
                latch.countDown()
            }
        }

    private fun statusAfterInstallTimeout(
        ctx: Context,
        installer: PackageInstaller,
        sessionId: Int,
        expectedPackageName: String?,
        expectedVersionCode: Long,
    ): Int =
        if (verifyInstallSucceeded(ctx, installer, sessionId, expectedPackageName, expectedVersionCode)) {
            log("post-timeout verification confirmed install succeeded")
            STATUS_SUCCESS
        } else {
            logE("post-timeout verification could not confirm install")
            STATUS_FAILURE
        }

    override fun uninstallPackage(packageName: String): Int {
        log("uninstallPackage() called for: $packageName")

        val ctx: Context =
            currentApplicationOrNull() ?: run {
                logE("currentApplication() returned null — no context available")
                return STATUS_FAILURE
            }

        val installer = ctx.packageManager.packageInstaller
        val latch = CountDownLatch(1)
        val resultRef = AtomicInteger(STATUS_FAILURE)
        val token = UUID.randomUUID().toString()
        val action = "$ACTION_UNINSTALL_RESULT.$token"
        var receiver: BroadcastReceiver? = null

        return try {
            failureAsStatus("uninstallPackage() exception") {
                val uninstallReceiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(
                            context: Context,
                            intent: Intent,
                        ) {
                            val status =
                                intent.getIntExtra(
                                    PackageInstaller.EXTRA_STATUS,
                                    PackageInstaller.STATUS_FAILURE,
                                )
                            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                            log("uninstall result — status=$status, message='$message'")
                            resultRef.set(if (status == PackageInstaller.STATUS_SUCCESS) STATUS_SUCCESS else STATUS_FAILURE)
                            latch.countDown()
                        }
                    }
                receiver = uninstallReceiver
                registerInternalReceiver(ctx, uninstallReceiver, action)

                installer.uninstall(packageName, resultPendingIntent(ctx, token, action).intentSender)

                if (latch.await(UNINSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    resultRef.get()
                } else {
                    logE("uninstall timed out after ${UNINSTALL_TIMEOUT_SECONDS}s — verifying package state")
                    statusAfterUninstallTimeout(ctx, packageName)
                }
            }
        } finally {
            unregisterQuietly(ctx, receiver)
        }
    }

    private fun statusAfterUninstallTimeout(
        ctx: Context,
        packageName: String,
    ): Int =
        if (!isPackageInstalled(ctx, packageName)) {
            log("post-timeout verification confirmed uninstall succeeded")
            STATUS_SUCCESS
        } else {
            logE("post-timeout verification: package still installed")
            STATUS_FAILURE
        }

    override fun destroy() {
        log("destroy() — service being unbound")
    }

    private inline fun failureAsStatus(
        failure: String,
        block: () -> Int,
    ): Int =
        try {
            block()
        } catch (e: IOException) {
            logE(failure, e)
            STATUS_FAILURE
        } catch (e: SecurityException) {
            logE(failure, e)
            STATUS_FAILURE
        } catch (e: IllegalArgumentException) {
            logE(failure, e)
            STATUS_FAILURE
        } catch (e: IllegalStateException) {
            logE(failure, e)
            STATUS_FAILURE
        } catch (e: InterruptedException) {
            logE(failure, e)
            STATUS_FAILURE
        }

    private fun resultPendingIntent(
        ctx: Context,
        token: String,
        action: String,
    ): PendingIntent {
        val pendingFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        return PendingIntent.getBroadcast(
            ctx,
            token.hashCode(),
            Intent(action).setPackage(ctx.packageName),
            pendingFlags,
        )
    }

    private fun unregisterQuietly(
        ctx: Context,
        receiver: BroadcastReceiver?,
    ) {
        if (receiver != null) {
            try {
                ctx.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    private fun verifyInstallSucceeded(
        ctx: Context,
        installer: PackageInstaller,
        sessionId: Int,
        expectedPackageName: String?,
        expectedVersionCode: Long,
    ): Boolean {
        val targetPackage =
            expectedPackageName?.takeIf { it.isNotBlank() }
                ?: runCatching { installer.getSessionInfo(sessionId)?.appPackageName }
                    .onFailure { logE("getSessionInfo() failed during verification", it) }
                    .getOrNull()
                ?: return false
        val info = packageInfoOrNull(ctx, targetPackage) ?: return false
        if (expectedVersionCode <= 0L) return true
        val installedVersion = PackageInfoCompat.getLongVersionCode(info)
        val matches = installedVersion >= expectedVersionCode
        if (!matches) {
            logW("installed $targetPackage@$installedVersion does not match expected@$expectedVersionCode")
        }
        return matches
    }

    private fun isPackageInstalled(
        ctx: Context,
        packageName: String,
    ): Boolean = packageInfoOrNull(ctx, packageName) != null

    private fun packageInfoOrNull(
        ctx: Context,
        packageName: String,
    ): android.content.pm.PackageInfo? =
        try {
            ctx.packageManager.getPackageInfo(packageName, 0)
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            null
        } catch (e: SecurityException) {
            logE("getPackageInfo($packageName) failed", e)
            null
        }

    private fun currentApplicationOrNull(): Context? =
        try {
            val cls = Class.forName("android.app.ActivityThread")
            val app = cls.getMethod("currentApplication").invoke(null)
            app as? Context
        } catch (e: ReflectiveOperationException) {
            logE("Failed to obtain Application context", e)
            null
        }

    private fun registerInternalReceiver(
        ctx: Context,
        receiver: BroadcastReceiver,
        action: String,
    ) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0
        ctx.registerReceiver(receiver, IntentFilter(action), flags)
    }
}
