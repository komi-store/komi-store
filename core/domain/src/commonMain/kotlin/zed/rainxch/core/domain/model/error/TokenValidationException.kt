package zed.rainxch.core.domain.model.error

sealed class TokenValidationException(
    cause: Throwable? = null,
) : Exception(cause) {
    class HttpStatus(
        val status: Int,
    ) : TokenValidationException()

    class Unreachable(
        cause: Throwable,
    ) : TokenValidationException(cause)
}
