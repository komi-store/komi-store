package zed.rainxch.core.domain.system

import kotlinx.coroutines.flow.StateFlow
import zed.rainxch.core.domain.model.installation.DhizukuAvailability
import zed.rainxch.core.domain.model.installation.RootAvailability
import zed.rainxch.core.domain.model.installation.ShizukuAvailability

interface InstallerStatusProvider {
    val shizukuAvailability: StateFlow<ShizukuAvailability>
    val dhizukuAvailability: StateFlow<DhizukuAvailability>
    val rootAvailability: StateFlow<RootAvailability>

    fun requestShizukuPermission()

    fun requestDhizukuPermission()

    fun requestRootPermission()
}
