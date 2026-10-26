package zed.rainxch.core.data.services

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BadParcelableException
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zed.rainxch.core.domain.model.error.SystemPackagesException
import zed.rainxch.core.domain.model.installation.DeviceApp
import zed.rainxch.core.domain.model.installation.SystemPackageInfo
import zed.rainxch.core.domain.system.PackageMonitor

class AndroidPackageMonitor(
    context: Context,
) : PackageMonitor {
    private val packageManager: PackageManager = context.packageManager

    override suspend fun isPackageInstalled(packageName: String): Boolean = getInstalledPackageInfo(packageName) != null

    override fun canEnumerateInstalledPackages(): Boolean = true

    override suspend fun getInstalledPackageInfo(packageName: String): SystemPackageInfo? =
        withContext(Dispatchers.IO) {
            // Only NameNotFoundException means not installed; any other failure is rethrown so no caller deletes data on it.
            try {
                val flags = SigningFingerprint.SIGNER_FLAG.toLong()

                val packageInfo =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getPackageInfo(
                            packageName,
                            PackageManager.PackageInfoFlags.of(flags),
                        )
                    } else {
                        packageManager.getPackageInfo(packageName, flags.toInt())
                    }

                val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)

                val signingFingerprint = SigningFingerprint.fromPackageInfo(packageInfo)

                SystemPackageInfo(
                    packageName = packageInfo.packageName,
                    versionName = packageInfo.versionName ?: SystemPackageInfo.UNKNOWN_VERSION_NAME,
                    versionCode = versionCode,
                    isInstalled = true,
                    signingFingerprint = signingFingerprint,
                    apkPath = packageInfo.applicationInfo?.sourceDir,
                )
            } catch (_: PackageManager.NameNotFoundException) {
                null
            } catch (e: SecurityException) {
                Logger.w(e) { "getInstalledPackageInfo($packageName) failed: ${e.message}" }
                throw SystemPackagesException(e)
            }
        }

    override suspend fun getAllInstalledPackageNames(): Set<String> =
        withContext(Dispatchers.IO) {
            // A long list arrives in chunks, and a chunk that fails to arrive throws BadParcelableException.
            val packages =
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
                    } else {
                        packageManager.getInstalledPackages(0)
                    }
                } catch (e: BadParcelableException) {
                    throw SystemPackagesException(e)
                }

            packages.map { it.packageName }.toSet()
        }

    override suspend fun getAllInstalledApps(): List<DeviceApp> =
        withContext(Dispatchers.IO) {
            val packages =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
                } else {
                    packageManager.getInstalledPackages(0)
                }

            packages
                .filter { pkg ->
                    val isSystemApp = (pkg.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
                    val isUpdatedSystem = (pkg.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                    !isSystemApp || isUpdatedSystem
                }.map { pkg ->
                    val versionCode = PackageInfoCompat.getLongVersionCode(pkg)

                    val installer = resolveInstallerPackageName(pkg.packageName)
                    val isUpdatedSystem =
                        (pkg.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                    DeviceApp(
                        packageName = pkg.packageName,
                        appName = pkg.applicationInfo?.loadLabel(packageManager)?.toString() ?: pkg.packageName,
                        versionName = pkg.versionName,
                        versionCode = versionCode,
                        signingFingerprint = null,
                        installerPackageName = installer,
                        isUpdatedSystemApp = isUpdatedSystem,
                    )
                }.sortedBy { it.appName.lowercase() }
        }

    private fun resolveInstallerPackageName(pkg: String): String? = runCatching { packageManager.installingPackageNameOf(pkg) }.getOrNull()
}
