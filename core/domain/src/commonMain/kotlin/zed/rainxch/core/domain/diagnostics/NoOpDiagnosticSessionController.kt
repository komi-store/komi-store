package zed.rainxch.core.domain.diagnostics

object NoOpDiagnosticSessionController : DiagnosticSessionController {
    override suspend fun buildShareBundle(): String? = null
}
