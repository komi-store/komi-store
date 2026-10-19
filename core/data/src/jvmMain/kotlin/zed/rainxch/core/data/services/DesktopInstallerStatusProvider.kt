package zed.rainxch.core.data.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import zed.rainxch.core.domain.model.installation.DhizukuAvailability
import zed.rainxch.core.domain.model.installation.RootAvailability
import zed.rainxch.core.domain.model.installation.ShizukuAvailability
import zed.rainxch.core.domain.system.InstallerStatusProvider

class DesktopInstallerStatusProvider : InstallerStatusProvider {
    override val shizukuAvailability: StateFlow<ShizukuAvailability> =
        MutableStateFlow(ShizukuAvailability.UNAVAILABLE).asStateFlow()

    override val dhizukuAvailability: StateFlow<DhizukuAvailability> =
        MutableStateFlow(DhizukuAvailability.UNAVAILABLE).asStateFlow()

    override val rootAvailability: StateFlow<RootAvailability> =
        MutableStateFlow(RootAvailability.UNAVAILABLE).asStateFlow()

    override fun requestShizukuPermission() = Unit

    override fun requestDhizukuPermission() = Unit

    override fun requestRootPermission() = Unit
}
