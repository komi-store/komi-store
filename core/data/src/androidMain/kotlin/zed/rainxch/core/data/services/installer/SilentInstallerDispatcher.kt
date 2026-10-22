package zed.rainxch.core.data.services.installer

import android.content.Context
import android.os.ParcelFileDescriptor
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zed.rainxch.core.data.services.dhizuku.DhizukuInstallerServiceImpl
import zed.rainxch.core.data.services.dhizuku.DhizukuServiceManager
import zed.rainxch.core.data.services.dhizuku.model.DhizukuStatus
import zed.rainxch.core.data.services.root.RootServiceManager
import zed.rainxch.core.data.services.root.model.RootStatus
import zed.rainxch.core.data.services.shizuku.ShizukuServiceManager
import zed.rainxch.core.data.services.shizuku.model.ShizukuStatus
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.installation.InstallerAttribution
import zed.rainxch.core.domain.model.installation.InstallerType
import zed.rainxch.core.domain.model.system.SystemArchitecture
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.InstallOutcome
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.InstallerInfoExtractor
import java.io.File

class SilentInstallerDispatcher(
    private val androidContext: Context,
    private val androidInstaller: Installer,
    private val shizukuServiceManager: ShizukuServiceManager,
    private val dhizukuServiceManager: DhizukuServiceManager,
    private val rootServiceManager: RootServiceManager,
    private val tweaksRepository: TweaksRepository,
    private val scope: CoroutineScope,
) : Installer {
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

    override suspend fun isSupported(extOrMime: String): Boolean = androidInstaller.isSupported(extOrMime)

    override fun isAssetInstallable(assetName: String): Boolean = androidInstaller.isAssetInstallable(assetName)

    override fun choosePrimaryAsset(assets: List<GithubAsset>): GithubAsset? = androidInstaller.choosePrimaryAsset(assets)

    override fun detectSystemArchitecture(): SystemArchitecture = androidInstaller.detectSystemArchitecture()

    override fun isObtainiumInstalled(): Boolean = androidInstaller.isObtainiumInstalled()

    override fun openInObtainium(
        repoOwner: String,
        repoName: String,
        onOpenInstaller: () -> Unit,
    ) = androidInstaller.openInObtainium(repoOwner, repoName, onOpenInstaller)

    override fun isAppManagerInstalled(): Boolean = androidInstaller.isAppManagerInstalled()

    override fun openInAppManager(
        filePath: String,
        onOpenInstaller: () -> Unit,
    ) = androidInstaller.openInAppManager(filePath, onOpenInstaller)

    override fun getApkInfoExtractor(): InstallerInfoExtractor = androidInstaller.getApkInfoExtractor()

    override fun openApp(packageName: String): Boolean = androidInstaller.openApp(packageName)

    override fun openWithExternalInstaller(filePath: String) = androidInstaller.openWithExternalInstaller(filePath)

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
        val installerAttribution = cachedInstallerAttribution.resolvePackageName()
        return try {
            val file = File(filePath)
            val (expectedPkg, expectedVc) = readApkIdentity(filePath)
            val first =
                withContext(Dispatchers.IO) {
                    runInstall(file, backend, installerAttribution, expectedPkg, expectedVc)
                }

            val resolved =
                if (
                    backend == Backend.DHIZUKU &&
                    first == DhizukuInstallerServiceImpl.STATUS_PENDING_USER_ACTION_REQUIRED &&
                    !installerAttribution.isNullOrBlank()
                ) {
                    Logger.w(TAG) {
                        "Dhizuku returned PENDING_USER_ACTION with attribution=$installerAttribution; retrying without attribution"
                    }
                    withContext(Dispatchers.IO) {
                        runInstall(file, backend, null, expectedPkg, expectedVc)
                    }
                } else {
                    first
                }
            when {
                resolved == null -> {
                    Logger.w(TAG) { "$backend service is null, will fall back" }
                    null
                }

                resolved == 0 -> {
                    InstallOutcome.COMPLETED
                }

                else -> {
                    Logger.w(TAG) { "$backend install returned $resolved, will fall back" }
                    null
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG) { "$backend install exception, falling back: ${e.javaClass.simpleName}: ${e.message}" }
            null
        }
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
                    val service = shizukuServiceManager.getService() ?: return@use null
                    service.installPackage(pfd, file.length(), installerAttribution)
                }
            }

            Backend.DHIZUKU -> {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    val service = dhizukuServiceManager.getService() ?: return@use null
                    service.installPackage(pfd, file.length(), expectedPkg, expectedVc, installerAttribution)
                }
            }

            Backend.ROOT -> {
                rootServiceManager.installPackage(file, installerAttribution)
            }

            Backend.DEFAULT -> {
                null
            }
        }

    private fun readApkIdentity(filePath: String): Pair<String?, Long> {
        val info =
            try {
                androidContext.packageManager.getPackageArchiveInfo(filePath, 0)
            } catch (e: Exception) {
                Logger.w(TAG) { "getPackageArchiveInfo($filePath) failed: ${e.message}" }
                null
            } ?: return null to -1L
        val versionCode =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        return info.packageName to versionCode
    }

    private suspend fun silentUninstall(
        packageName: String,
        backend: Backend,
    ) {
        try {
            val result =
                when (backend) {
                    Backend.SHIZUKU -> {
                        val service = shizukuServiceManager.getService()
                        service?.uninstallPackage(packageName)
                    }

                    Backend.DHIZUKU -> {
                        val service = dhizukuServiceManager.getService()
                        service?.uninstallPackage(packageName)
                    }

                    Backend.ROOT -> {
                        rootServiceManager.uninstallPackage(packageName)
                    }

                    Backend.DEFAULT -> {
                        null
                    }
                }
            if (result == null || result != 0) {
                Logger.w(TAG) { "$backend uninstall failed (result=$result), falling back" }
                androidInstaller.uninstall(packageName)
            }
        } catch (e: Exception) {
            Logger.e(TAG) { "$backend uninstall exception, falling back: ${e.message}" }
            androidInstaller.uninstall(packageName)
        }
    }

    private fun resolveActiveBackend(): Backend =
        when (cachedInstallerType) {
            InstallerType.THIRD_PARTY -> {
                Backend.DEFAULT
            }

            InstallerType.SHIZUKU -> {
                shizukuServiceManager.refreshStatus()
                if (shizukuServiceManager.status.value == ShizukuStatus.READY) Backend.SHIZUKU else Backend.DEFAULT
            }

            InstallerType.DHIZUKU -> {
                dhizukuServiceManager.refreshStatus()
                if (dhizukuServiceManager.status.value == DhizukuStatus.READY) Backend.DHIZUKU else Backend.DEFAULT
            }

            InstallerType.ROOT -> {
                if (rootServiceManager.status.value != RootStatus.READY) {
                    rootServiceManager.refreshStatus()
                }
                if (rootServiceManager.status.value == RootStatus.READY) Backend.ROOT else Backend.DEFAULT
            }

            InstallerType.DEFAULT -> {
                Backend.DEFAULT
            }
        }

    private enum class Backend { DEFAULT, SHIZUKU, DHIZUKU, ROOT }
}
