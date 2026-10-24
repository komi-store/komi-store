package zed.rainxch.githubstore.app.diagnostics

import zed.rainxch.core.domain.diagnostics.DiagnosticSessionController
import zed.rainxch.core.domain.system.AppVersionInfo

internal class AndroidDiagnosticSessionController(
    private val appVersionInfo: AppVersionInfo,
) : DiagnosticSessionController {
    override suspend fun buildShareBundle(): String? = DiagnosticSession.buildShareBundle(appVersionInfo.versionName)
}
