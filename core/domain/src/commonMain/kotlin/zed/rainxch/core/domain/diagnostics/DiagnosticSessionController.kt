package zed.rainxch.core.domain.diagnostics

interface DiagnosticSessionController {
    suspend fun buildShareBundle(): String?
}
