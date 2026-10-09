package zed.rainxch.core.data.services

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
import android.os.Build
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            runCatching {
                val flags =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        GET_SIGNING_CERTIFICATES.toLong()
                    } else {
                        @Suppress("DEPRECATION")
                        PackageManager.GET_SIGNATURES.toLong()
                    }

                val packageInfo =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getPackageInfo(
                            packageName,
                            PackageManager.PackageInfoFlags.of(flags),
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getPackageInfo(packageName, flags.toInt())
                    }

                val versionCode =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toLong()
                    }

                val signingFingerprint = SigningFingerprint.fromPackageInfo(packageInfo)

                SystemPackageInfo(
                    packageName = packageInfo.packageName,
                    versionName = packageInfo.versionName ?: "unknown",
                    versionCode = versionCode,
                    isInstalled = true,
                    signingFingerprint = signingFingerprint,
                    apkPath = packageInfo.applicationInfo?.sourceDir,
                )
            }.getOrElse { e ->
                // null means ONLY "positively not installed" (NameNotFoundException). Any other
                // failure (SecurityException, transient PackageManager error) is rethrown so the
                // caller never mistakes an ambiguous lookup for proof of uninstall and deletes data.
                if (e is PackageManager.NameNotFoundException) {
                    null
                } else {
                    Logger.w(e) { "getInstalledPackageInfo($packageName) failed: ${e.message}" }
                    throw e
                }
            }
        }

    override suspend fun getAllInstalledPackageNames(): Set<String> =
        withContext(Dispatchers.IO) {
            val packages =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getInstalledPackages(0)
                }

            packages.map { it.packageName }.toSet()
        }

    override suspend fun getAllInstalledApps(): List<DeviceApp> =
        withContext(Dispatchers.IO) {
            val packages =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getInstalledPackages(0)
                }

            packages
                .filter { pkg ->
                    val isSystemApp = (pkg.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
                    val isUpdatedSystem = (pkg.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                    !isSystemApp || isUpdatedSystem
                }.map { pkg ->
                    val versionCode =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            pkg.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            pkg.versionCode.toLong()
                        }

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

    private fun resolveInstallerPackageName(pkg: String): String? =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(pkg).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(pkg)
            }
        }.getOrNull()
}
