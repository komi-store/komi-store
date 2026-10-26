package zed.rainxch.core.domain.repository.tweaks

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.installation.InstallerAttribution
import zed.rainxch.core.domain.model.installation.InstallerType

interface InstallerTweaks {
    fun getInstallerType(): Flow<InstallerType>

    suspend fun setInstallerType(type: InstallerType)

    fun getExternalInstallerPackage(): Flow<String>

    suspend fun setExternalInstallerPackage(packageName: String)

    fun getInstallerAttribution(): Flow<InstallerAttribution>

    suspend fun setInstallerAttribution(attribution: InstallerAttribution)
}
