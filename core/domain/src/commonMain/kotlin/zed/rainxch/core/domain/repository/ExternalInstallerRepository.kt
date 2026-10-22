package zed.rainxch.core.domain.repository

import zed.rainxch.core.domain.model.installation.ExternalInstallerApp

interface ExternalInstallerRepository {
    suspend fun listInstallers(): List<ExternalInstallerApp>
}
