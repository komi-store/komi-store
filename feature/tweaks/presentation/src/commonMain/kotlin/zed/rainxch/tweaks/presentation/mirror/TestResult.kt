package zed.rainxch.tweaks.presentation.mirror

sealed interface TestResult {
    data class Success(
        val latencyMs: Long,
    ) : TestResult

    data class HttpError(
        val code: Int,
    ) : TestResult

    data object Timeout : TestResult

    data object DnsFailure : TestResult

    data object Other : TestResult
}
