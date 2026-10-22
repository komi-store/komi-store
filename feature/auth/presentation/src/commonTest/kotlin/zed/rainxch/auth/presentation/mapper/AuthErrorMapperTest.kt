package zed.rainxch.auth.presentation.mapper

import zed.rainxch.auth.domain.repository.AuthException
import zed.rainxch.auth.domain.repository.PatRejectedException
import zed.rainxch.auth.domain.repository.RejectedKind
import zed.rainxch.auth.presentation.model.AuthError
import zed.rainxch.auth.presentation.model.PatError
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthErrorMapperTest {
    private val cause = IllegalStateException("raw text that must not reach the screen")

    @Test
    fun auth_exceptions_map_to_their_screen_errors() {
        assertEquals(AuthError.Unreachable, AuthException.Unreachable(cause).toAuthError())
        assertEquals(AuthError.RateLimited, AuthException.RateLimited(cause).toAuthError())
        assertEquals(AuthError.Denied, AuthException.Denied().toAuthError())
        assertEquals(AuthError.CodeExpired, AuthException.CodeExpired().toAuthError())
    }

    @Test
    fun anything_else_is_unknown() {
        assertEquals(AuthError.Unknown, AuthException.Failed(cause).toAuthError())
        assertEquals(AuthError.Unknown, cause.toAuthError())
    }

    @Test
    fun pat_failures_map_to_pat_errors() {
        assertEquals(PatError.InvalidFormat, IllegalArgumentException("Token format not recognized").toPatError())
        assertEquals(PatError.BadCredentials, PatRejectedException(RejectedKind.BadCredentials).toPatError())
        assertEquals(PatError.InsufficientScope, PatRejectedException(RejectedKind.InsufficientScope).toPatError())
        assertEquals(PatError.Generic, PatRejectedException(RejectedKind.Other(422)).toPatError())
        assertEquals(PatError.Generic, cause.toPatError())
    }
}
