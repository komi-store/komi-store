package zed.rainxch.core.domain.network

sealed interface MirrorTestOutcome {
    data class Success(
        val latencyMs: Long,
    ) : MirrorTestOutcome

    data class HttpError(
        val code: Int,
    ) : MirrorTestOutcome

    data object Timeout : MirrorTestOutcome

    data object DnsFailure : MirrorTestOutcome

    data object Failed : MirrorTestOutcome
}
