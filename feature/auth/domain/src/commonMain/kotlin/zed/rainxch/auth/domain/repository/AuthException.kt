package zed.rainxch.auth.domain.repository

sealed class AuthException(
    cause: Throwable? = null,
) : Exception(cause) {
    class Unreachable(
        cause: Throwable,
    ) : AuthException(cause)

    class RateLimited(
        cause: Throwable,
    ) : AuthException(cause)

    class Denied : AuthException()

    class CodeExpired : AuthException()

    class Failed(
        cause: Throwable,
    ) : AuthException(cause)
}
