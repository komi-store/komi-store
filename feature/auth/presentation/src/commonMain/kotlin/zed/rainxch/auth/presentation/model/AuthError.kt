package zed.rainxch.auth.presentation.model

enum class AuthError {
    Unreachable,
    RateLimited,
    Denied,
    CodeExpired,
    BrowserUnavailable,
    Unknown,
}
