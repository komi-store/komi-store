package zed.rainxch.core.domain.model.error

// A remote call failed: no connection, an error status, a body that would not decode.
open class NetworkException(
    message: String?,
    cause: Throwable?,
) : Exception(message, cause) {
    constructor(cause: Throwable) : this(cause.message, cause)
}
