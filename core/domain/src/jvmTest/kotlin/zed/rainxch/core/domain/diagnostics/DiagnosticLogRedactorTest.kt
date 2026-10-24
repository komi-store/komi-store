package zed.rainxch.core.domain.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals

class DiagnosticLogRedactorTest {
    @Test
    fun bearer_authorization_headers_are_redacted() {
        val input = "GET https://api.github.com/user (Authorization: Bearer ghp_1234567890abcdef)"
        val expected = "GET https://api.github.com/user (Authorization: Bearer [REDACTED])"
        assertEquals(expected, DiagnosticLogRedactor.redact(input))
    }

    @Test
    fun lowercase_authorization_headers_are_redacted() {
        val input = "authorization=token abcdef123456"
        assertEquals("authorization=token [REDACTED]", DiagnosticLogRedactor.redact(input))
    }

    @Test
    fun access_token_parameters_keep_the_rest_of_the_query() {
        val input = "GET /repos/komi-store?access_token=abc123&page=2 HTTP/1.1"
        assertEquals(
            "GET /repos/komi-store?access_token=[REDACTED]&page=2 HTTP/1.1",
            DiagnosticLogRedactor.redact(input),
        )
    }

    @Test
    fun token_parameters_are_redacted() {
        assertEquals(
            "https://example.test/callback?token=[REDACTED]",
            DiagnosticLogRedactor.redact("https://example.test/callback?token=secret-value"),
        )
    }

    @Test
    fun sensitive_token_headers_are_redacted() {
        val input = "X-GitHub-Token: ghp_abcdef123456"
        assertEquals("X-GitHub-Token: [REDACTED]", DiagnosticLogRedactor.redact(input))
    }

    @Test
    fun bare_github_tokens_are_redacted() {
        val input =
            "saved ghp_abcdefghijklmnopqrstuvwxyz0123456789 and " +
                "github_pat_11ABCDEFG0123456789_abcdefghijklmnopqrstuvwxyz for octocat"
        assertEquals("saved [REDACTED] and [REDACTED] for octocat", DiagnosticLogRedactor.redact(input))
    }

    @Test
    fun plain_log_lines_are_unchanged() {
        val input = "TweaksViewModel: loaded update interval of 12 hours"
        assertEquals(input, DiagnosticLogRedactor.redact(input))
    }

    @Test
    fun redaction_is_idempotent() {
        val input =
            "Authorization: Bearer abcdef123456\n" +
                "GET /x?access_token=abc123&page=2\n" +
                "X-GitHub-Token: ghp_abcdef123456"
        val once = DiagnosticLogRedactor.redact(input)
        assertEquals(once, DiagnosticLogRedactor.redact(once))
    }
}
