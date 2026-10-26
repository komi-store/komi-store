package zed.rainxch.core.data.services.installer

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import androidx.core.content.pm.PackageInfoCompat
import co.touchlab.kermit.Logger
import com.topjohnwu.superuser.NoShellException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zed.rainxch.core.data.services.dhizuku.DhizukuInstallerServiceImpl
import zed.rainxch.core.data.services.dhizuku.model.DhizukuStatus
import zed.rainxch.core.data.services.root.model.RootStatus
import zed.rainxch.core.data.services.shizuku.model.ShizukuStatus
import zed.rainxch.core.domain.model.installation.InstallerAttribution
import zed.rainxch.core.domain.model.installation.InstallerType
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.InstallOutcome
import zed.rainxch.core.domain.system.Installer
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class SilentInstallerDispatcher(
    private val androidContext: Context,
    private val androidInstaller: Installer,
    private val backends: SilentInstallBackends,
    private val tweaksRepository: TweaksRepository,
    private val scope: CoroutineScope,
) : Installer by androidInstaller {
    companion object {
        private const val TAG = "SilentInstaller"
    }

    @Volatile
    private var cachedInstallerType: InstallerType = InstallerType.DEFAULT

    @Volatile
    private var cachedInstallerAttribution: InstallerAttribution = InstallerAttribution.SystemDefault

    @Volatile
    private var cachedExternalInstallerPackage: String = ""

    fun observeInstallerPreference() {
        scope.launch {
            tweaksRepository.getInstallerType().collect { type ->
                cachedInstallerType = type
                Logger.d(TAG) { "Installer type changed to: $type" }
            }
        }
        scope.launch {
            tweaksRepository.getInstallerAttribution().collect { attribution ->
                cachedInstallerAttribution = attribution
                Logger.d(TAG) { "Installer attribution changed to: $attribution" }
            }
        }
        scope.launch {
            tweaksRepository.getExternalInstallerPackage().collect { packageName ->
                cachedExternalInstallerPackage = packageName
                Logger.d(TAG) { "External installer package changed to: $packageName" }
            }
        }
    }

    override suspend fun ensurePermissionsOrThrow(extOrMime: String) {
        Logger.d(TAG) {
            "ensurePermissionsOrThrow() — extOrMime=$extOrMime, cachedType=$cachedInstallerType"
        }
        if (resolveActiveBackend() != Backend.DEFAULT) {
            Logger.d(TAG) { "Silent backend active — skipping unknown sources permission check" }
            return
        }
        // The picked app installs; runStandardInstall still checks if handing off fails.
        if (cachedInstallerType == InstallerType.THIRD_PARTY && cachedExternalInstallerPackage.isNotBlank()) {
            Logger.d(TAG) { "Third-party installer picked - skipping unknown sources permission check" }
            return
        }
        androidInstaller.ensurePermissionsOrThrow(extOrMime)
    }

    override suspend fun install(
        filePath: String,
        extOrMime: String,
    ): InstallOutcome {
        Logger.d(TAG) { "install() called - filePath=$filePath, extOrMime=$extOrMime, cached=$cachedInstallerType" }
        return tryThirdPartyInstall(filePath)
            ?: trySilentInstallWithBackend(filePath)
            ?: runStandardInstall(filePath, extOrMime)
    }

    private fun tryThirdPartyInstall(filePath: String): InstallOutcome? {
        val packageName = cachedExternalInstallerPackage
        if (cachedInstallerType != InstallerType.THIRD_PARTY || packageName.isBlank()) return null
        val launched = ExternalInstallerLauncher(androidContext).launch(filePath, packageName)
        if (!launched) {
            Logger.d(TAG) { "Third-party installer $packageName could not be opened, falling back" }
        }
        return if (launched) InstallOutcome.DELEGATED_TO_SYSTEM else null
    }

    private suspend fun trySilentInstallWithBackend(filePath: String): InstallOutcome? {
        val backend = resolveActiveBackend()
        if (backend == Backend.DEFAULT) return null
        return trySilentInstall(filePath, backend)
    }

    private suspend fun runStandardInstall(
        filePath: String,
        extOrMime: String,
    ): InstallOutcome {
        Logger.d(TAG) { "Falling back to standard AndroidInstaller for: $filePath" }
        androidInstaller.ensurePermissionsOrThrow(extOrMime)
        return androidInstaller.install(filePath, extOrMime)
    }

    override fun uninstall(packageName: String) {
        Logger.d(TAG) { "uninstall() called — packageName=$packageName, cached=$cachedInstallerType" }

        when (val backend = resolveActiveBackend()) {
            Backend.SHIZUKU, Backend.DHIZUKU, Backend.ROOT -> {
                scope.launch(Dispatchers.IO) {
                    silentUninstall(packageName, backend)
                }
            }

            Backend.DEFAULT -> {
                androidInstaller.uninstall(packageName)
            }
        }
    }

    private suspend fun trySilentInstall(
        filePath: String,
        backend: Backend,
    ): InstallOutcome? {
        Logger.d(TAG) { "Routing install through $backend" }
        return try {
            outcomeOf(backend, installSilently(filePath, backend))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            silentInstallFailed(backend, e)
        } catch (e: IllegalStateException) {
            silentInstallFailed(backend, e)
        } catch (e: RemoteException) {
            silentInstallFailed(backend, e)
        } catch (e: SecurityException) {
            silentInstallFailed(backend, e)
        } catch (e: IllegalArgumentException) {
            silentInstallFailed(backend, e)
        } catch (e: NoShellException) {
            silentInstallFailed(backend, e)
        }
    }

    private suspend fun installSilently(
        filePath: String,
        backend: Backend,
    ): Int? {
        val installerAttribution = cachedInstallerAttribution.resolvePackageName()
        val file = File(filePath)
        val (expectedPkg, expectedVc) = readApkIdentity(filePath)
        val first =
            withContext(Dispatchers.IO) {
                runInstall(file, backend, installerAttribution, expectedPkg, expectedVc)
            }
        val retryWithoutAttribution =
            backend == Backend.DHIZUKU &&
                first == DhizukuInstallerServiceImpl.STATUS_PENDING_USER_ACTION_REQUIRED &&
                !installerAttribution.isNullOrBlank()
        if (!retryWithoutAttribution) return first
        Logger.w(TAG) {
            "Dhizuku returned PENDING_USER_ACTION with attribution=$installerAttribution; retrying without attribution"
        }
        return withContext(Dispatchers.IO) {
            runInstall(file, backend, null, expectedPkg, expectedVc)
        }
    }

    private fun outcomeOf(
        backend: Backend,
        resolved: Int?,
    ): InstallOutcome? =
        when (resolved) {
            null -> {
                Logger.w(TAG) { "$backend service is null, will fall back" }
                null
            }

            0 -> {
                InstallOutcome.COMPLETED
            }

            else -> {
                Logger.w(TAG) { "$backend install returned $resolved, will fall back" }
                null
            }
        }

    private fun silentInstallFailed(
        backend: Backend,
        e: Exception,
    ): InstallOutcome? {
        Logger.e(TAG) { "$backend install exception, falling back: ${e.javaClass.simpleName}: ${e.message}" }
        return null
    }

    private suspend fun runInstall(
        file: File,
        backend: Backend,
        installerAttribution: String?,
        expectedPkg: String?,
        expectedVc: Long,
    ): Int? =
        when (backend) {
            Backend.SHIZUKU -> {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    val service = backends.shizuku.getService() ?: return@use null
                    service.installPackage(pfd, file.length(), installerAttribution)
                }
            }

            Backend.DHIZUKU -> {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    val service = backends.dhizuku.getService() ?: return@use null
                    service.installPackage(pfd, file.length(), expectedPkg, expectedVc, installerAttribution)
                }
            }

            Backend.ROOT -> {
                backends.root.installPackage(file, installerAttribution)
            }

            Backend.DEFAULT -> {
                null
            }
        }

    private fun readApkIdentity(filePath: String): Pair<String?, Long> {
        val info =
            try {
                androidContext.packageManager.getPackageArchiveInfo(filePath, 0)
            } catch (e: SecurityException) {
                Logger.w(TAG) { "getPackageArchiveInfo($filePath) failed: ${e.message}" }
                null
            } ?: return null to -1L
        return info.packageName to PackageInfoCompat.getLongVersionCode(info)
    }

    private suspend fun silentUninstall(
        packageName: String,
        backend: Backend,
    ) {
        try {
            val result =
                when (backend) {
                    Backend.SHIZUKU -> {
                        val service = backends.shizuku.getService()
                        service?.uninstallPackage(packageName)
                    }

                    Backend.DHIZUKU -> {
                        val service = backends.dhizuku.getService()
                        service?.uninstallPackage(packageName)
                    }

                    Backend.ROOT -> {
                        backends.root.uninstallPackage(packageName)
                    }

                    Backend.DEFAULT -> {
                        null
                    }
                }
            if (result == null || result != 0) {
                Logger.w(TAG) { "$backend uninstall failed (result=$result), falling back" }
                androidInstaller.uninstall(packageName)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RemoteException) {
            silentUninstallFailed(packageName, backend, e)
        } catch (e: SecurityException) {
            silentUninstallFailed(packageName, backend, e)
        } catch (e: IllegalStateException) {
            silentUninstallFailed(packageName, backend, e)
        } catch (e: IllegalArgumentException) {
            silentUninstallFailed(packageName, backend, e)
        } catch (e: NoShellException) {
            silentUninstallFailed(packageName, backend, e)
        }
    }

    private fun silentUninstallFailed(
        packageName: String,
        backend: Backend,
        e: Exception,
    ) {
        Logger.e(TAG) { "$backend uninstall exception, falling back: ${e.message}" }
        androidInstaller.uninstall(packageName)
    }

    private fun resolveActiveBackend(): Backend =
        when (cachedInstallerType) {
            InstallerType.THIRD_PARTY -> {
                Backend.DEFAULT
            }

            InstallerType.SHIZUKU -> {
                backends.shizuku.refreshStatus()
                if (backends.shizuku.status.value == ShizukuStatus.READY) Backend.SHIZUKU else Backend.DEFAULT
            }

            InstallerType.DHIZUKU -> {
                backends.dhizuku.refreshStatus()
                if (backends.dhizuku.status.value == DhizukuStatus.READY) Backend.DHIZUKU else Backend.DEFAULT
            }

            InstallerType.ROOT -> {
                if (backends.root.status.value != RootStatus.READY) {
                    backends.root.refreshStatus()
                }
                if (backends.root.status.value == RootStatus.READY) Backend.ROOT else Backend.DEFAULT
            }

            InstallerType.DEFAULT -> {
                Backend.DEFAULT
            }
        }

    private enum class Backend { DEFAULT, SHIZUKU, DHIZUKU, ROOT }
}
