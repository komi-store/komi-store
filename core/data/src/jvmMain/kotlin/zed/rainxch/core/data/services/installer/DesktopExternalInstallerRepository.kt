package zed.rainxch.core.data.services.installer

import zed.rainxch.core.domain.model.installation.ExternalInstallerApp
import zed.rainxch.core.domain.repository.ExternalInstallerRepository

class DesktopExternalInstallerRepository : ExternalInstallerRepository {
    override suspend fun listInstallers(): List<ExternalInstallerApp> = emptyList()
}
