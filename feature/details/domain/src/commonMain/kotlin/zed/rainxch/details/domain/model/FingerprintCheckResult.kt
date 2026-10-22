package zed.rainxch.details.domain.model

sealed interface FingerprintCheckResult {
    data object Ok : FingerprintCheckResult

    data class Mismatch(
        val expectedFingerprint: String,
        val actualFingerprint: String,
    ) : FingerprintCheckResult
}
