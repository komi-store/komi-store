package zed.rainxch.details.domain.system

sealed interface VerificationResult {
    data object Verified : VerificationResult

    data object Unverified : VerificationResult

    data class Error(
        val reason: String,
    ) : VerificationResult
}
