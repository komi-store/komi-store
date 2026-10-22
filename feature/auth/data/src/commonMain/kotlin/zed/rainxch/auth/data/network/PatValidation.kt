package zed.rainxch.auth.data.network

import zed.rainxch.auth.domain.repository.RejectedKind

sealed interface PatValidation {
    data object Valid : PatValidation

    data class Rejected(
        val kind: RejectedKind,
    ) : PatValidation

    data class Unreachable(
        val reason: String,
    ) : PatValidation
}
