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
    fun worker_rate_limit_reason_maps_to_rate_limited() {
        assertEquals(
            AuthError.RateLimited,
            authErrorForWorkerReason("exchange_failed_github_rate_limit"),
        )
    }

    @Test
    fun worker_bad_verification_code_reason_maps_to_code_expired() {
        assertEquals(
            AuthError.CodeExpired,
            authErrorForWorkerReason("exchange_failed_github_bad_verification_code"),
        )
    }

    @Test
    fun worker_expired_state_reasons_map_to_code_expired() {
        listOf(
            "exchange_failed_state_missing_or_expired",
            "exchange_failed_invalid_state",
            "invalid_state",
        ).forEach { reason ->
            assertEquals(AuthError.CodeExpired, authErrorForWorkerReason(reason), reason)
        }
    }

    @Test
    fun worker_unreachable_reasons_map_to_unreachable() {
        listOf(
            "exchange_failed_github_unreachable",
            "exchange_unreachable",
        ).forEach { reason ->
            assertEquals(AuthError.Unreachable, authErrorForWorkerReason(reason), reason)
        }
    }

    @Test
    fun worker_access_denied_reason_maps_to_denied() {
        assertEquals(AuthError.Denied, authErrorForWorkerReason("access_denied"))
    }

    @Test
    fun worker_generic_failure_reasons_map_to_unknown() {
        listOf(
            "unknown",
            "exchange_failed",
        ).forEach { reason ->
            assertEquals(AuthError.Unknown, authErrorForWorkerReason(reason), reason)
        }
    }

    @Test
    fun unrecognized_worker_reason_falls_back_to_unknown() {
        assertEquals(AuthError.Unknown, authErrorForWorkerReason("some_future_reason"))
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
