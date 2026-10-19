package zed.rainxch.auth.data.repository

import zed.rainxch.auth.data.network.BackendHttpException
import zed.rainxch.auth.domain.repository.AuthException
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

class AuthErrorsTest {
    @Test
    fun a_refused_proxy_connection_is_unreachable() {
        val cause = ConnectException("Failed to connect to /127.0.0.1:8898")

        val error = cause.toAuthException()

        assertIs<AuthException.Unreachable>(error)
        assertSame(cause, error.cause)
    }

    @Test
    fun a_socket_timeout_is_unreachable() {
        assertIs<AuthException.Unreachable>(SocketTimeoutException("Read timed out").toAuthException())
    }

    @Test
    fun a_backend_server_error_is_unreachable() {
        assertIs<AuthException.Unreachable>(BackendHttpException(503, "device/start HTTP 503").toAuthException())
    }

    @Test
    fun a_backend_429_is_rate_limited() {
        assertIs<AuthException.RateLimited>(BackendHttpException(429, "device/start HTTP 429").toAuthException())
    }

    @Test
    fun other_backend_answers_are_failures() {
        assertIs<AuthException.Failed>(BackendHttpException(404, "oauth/handoff HTTP 404").toAuthException())
    }

    @Test
    fun an_unexpected_body_is_a_failure() {
        assertIs<AuthException.Failed>(IllegalStateException("Unexpected response from GitHub: <html>").toAuthException())
    }

    @Test
    fun an_auth_exception_passes_through_unchanged() {
        val denied = AuthException.Denied()

        assertSame(denied, denied.toAuthException())
    }
}
