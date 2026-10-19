package zed.rainxch.auth.domain.repository

class PatRejectedException(
    val kind: RejectedKind,
) : Exception("PAT rejected: $kind")
