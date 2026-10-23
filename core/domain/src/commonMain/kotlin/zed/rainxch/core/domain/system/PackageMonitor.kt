package zed.rainxch.core.domain.system

import zed.rainxch.core.domain.model.installation.DeviceApp
import zed.rainxch.core.domain.model.installation.SystemPackageInfo

interface PackageMonitor {
    suspend fun isPackageInstalled(packageName: String): Boolean

    suspend fun getInstalledPackageInfo(packageName: String): SystemPackageInfo?

    suspend fun getAllInstalledPackageNames(): Set<String>

    suspend fun getAllInstalledApps(): List<DeviceApp>

    // True when the installed package is signed by this certificate fingerprint or rotated
    // away from it, i.e. the certificate is in its signing lineage.
    suspend fun hasSigningCertificate(
        packageName: String,
        fingerprint: String,
    ): Boolean

    fun canEnumerateInstalledPackages(): Boolean
}
