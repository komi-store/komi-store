package zed.rainxch.auth.domain.repository

sealed interface RejectedKind {
    data object BadCredentials : RejectedKind

    data object InsufficientScope : RejectedKind

    data class Other(
        val statusCode: Int,
    ) : RejectedKind
}
