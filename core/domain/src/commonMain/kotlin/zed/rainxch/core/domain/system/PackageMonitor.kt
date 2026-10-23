package zed.rainxch.core.domain.system

import zed.rainxch.core.domain.model.installation.DeviceApp
import zed.rainxch.core.domain.model.installation.SystemPackageInfo

interface PackageMonitor {
    suspend fun isPackageInstalled(packageName: String): Boolean

    suspend fun getInstalledPackageInfo(packageName: String): SystemPackageInfo?

    suspend fun getAllInstalledPackageNames(): Set<String>

    suspend fun getAllInstalledApps(): List<DeviceApp>

    fun canEnumerateInstalledPackages(): Boolean
}
