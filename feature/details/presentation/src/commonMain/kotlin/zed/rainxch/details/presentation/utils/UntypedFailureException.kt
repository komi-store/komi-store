package zed.rainxch.details.presentation.utils

internal class UntypedFailureException(
    cause: Exception,
) : Exception(cause.message, cause)
