package zed.rainxch.auth.presentation.mapper

import org.jetbrains.compose.resources.StringResource
import zed.rainxch.auth.domain.repository.AuthException
import zed.rainxch.auth.domain.repository.PatRejectedException
import zed.rainxch.auth.domain.repository.RejectedKind
import zed.rainxch.auth.presentation.model.AuthError
import zed.rainxch.auth.presentation.model.PatError
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.auth_error_browser
import zed.rainxch.githubstore.core.presentation.res.auth_error_code_expired
import zed.rainxch.githubstore.core.presentation.res.auth_error_denied
import zed.rainxch.githubstore.core.presentation.res.auth_error_rate_limited
import zed.rainxch.githubstore.core.presentation.res.auth_error_unreachable
import zed.rainxch.githubstore.core.presentation.res.auth_hint_check_connection
import zed.rainxch.githubstore.core.presentation.res.auth_hint_denied
import zed.rainxch.githubstore.core.presentation.res.auth_hint_other_method
import zed.rainxch.githubstore.core.presentation.res.auth_hint_try_again
import zed.rainxch.githubstore.core.presentation.res.auth_hint_wait
import zed.rainxch.githubstore.core.presentation.res.error_unknown
import zed.rainxch.githubstore.core.presentation.res.pat_error_bad_credentials
import zed.rainxch.githubstore.core.presentation.res.pat_error_empty
import zed.rainxch.githubstore.core.presentation.res.pat_error_generic
import zed.rainxch.githubstore.core.presentation.res.pat_error_insufficient_scope
import zed.rainxch.githubstore.core.presentation.res.pat_error_invalid_format

internal fun Throwable.toAuthError(): AuthError =
    when (this) {
        is AuthException.Unreachable -> AuthError.Unreachable
        is AuthException.RateLimited -> AuthError.RateLimited
        is AuthException.Denied -> AuthError.Denied
        is AuthException.CodeExpired -> AuthError.CodeExpired
        else -> AuthError.Unknown
    }

internal fun Throwable.toPatError(): PatError =
    when (this) {
        is IllegalArgumentException -> {
            PatError.InvalidFormat
        }

        is PatRejectedException -> {
            when (kind) {
                is RejectedKind.BadCredentials -> PatError.BadCredentials
                is RejectedKind.InsufficientScope -> PatError.InsufficientScope
                is RejectedKind.Other -> PatError.Generic
            }
        }

        else -> {
            PatError.Generic
        }
    }

internal fun AuthError.titleRes(): StringResource =
    when (this) {
        AuthError.Unreachable -> Res.string.auth_error_unreachable
        AuthError.RateLimited -> Res.string.auth_error_rate_limited
        AuthError.Denied -> Res.string.auth_error_denied
        AuthError.CodeExpired -> Res.string.auth_error_code_expired
        AuthError.BrowserUnavailable -> Res.string.auth_error_browser
        AuthError.Unknown -> Res.string.error_unknown
    }

internal fun AuthError.hintRes(): StringResource =
    when (this) {
        AuthError.Unreachable -> Res.string.auth_hint_check_connection
        AuthError.RateLimited -> Res.string.auth_hint_wait
        AuthError.Denied -> Res.string.auth_hint_denied
        AuthError.CodeExpired -> Res.string.auth_hint_try_again
        AuthError.BrowserUnavailable -> Res.string.auth_hint_other_method
        AuthError.Unknown -> Res.string.auth_hint_try_again
    }

internal fun PatError.messageRes(): StringResource =
    when (this) {
        PatError.Empty -> Res.string.pat_error_empty
        PatError.InvalidFormat -> Res.string.pat_error_invalid_format
        PatError.BadCredentials -> Res.string.pat_error_bad_credentials
        PatError.InsufficientScope -> Res.string.pat_error_insufficient_scope
        PatError.Generic -> Res.string.pat_error_generic
    }
