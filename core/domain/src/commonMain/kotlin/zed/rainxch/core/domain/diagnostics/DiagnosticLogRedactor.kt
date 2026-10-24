package zed.rainxch.core.domain.diagnostics

object DiagnosticLogRedactor {
    private const val REDACTED = "[REDACTED]"

    private const val SECRET_PARAMETER_NAMES =
        "access_token|refresh_token|id_token|token|api_key|apikey|client_secret|password"

    private val authorizationHeader =
        Regex("""(?i)\b(authorization\s*[:=]\s*)(bearer|token|basic)\s+([^\s,;\[\])}"']+)""")

    private val tokenHeader =
        Regex("""(?i)\b(x-github-token|x-access-token|x-auth-token|private-token)\s*[:=]\s*([^\s,;\[\])}"']+)""")

    private val bearerToken = Regex("""(?i)\bbearer\s+([A-Za-z0-9._~+/=-]{8,})""")

    private val credentialParameter = Regex("""(?i)\b($SECRET_PARAMETER_NAMES)=([^&\s'"\[\]()]+)""")

    // GitHub tokens carry their own prefix, so they're caught even without a header or parameter name.
    private val githubToken = Regex("""\b(?:gh[pousr]_[A-Za-z0-9]{16,}|github_pat_[A-Za-z0-9_]{20,})""")

    fun redact(text: String): String {
        if (text.isEmpty()) return text
        var result = text
        result =
            authorizationHeader.replace(result) { match ->
                "${match.groupValues[1]}${match.groupValues[2]} $REDACTED"
            }
        result =
            tokenHeader.replace(result) { match ->
                "${match.groupValues[1]}: $REDACTED"
            }
        result = bearerToken.replace(result, "Bearer $REDACTED")
        result =
            credentialParameter.replace(result) { match ->
                "${match.groupValues[1]}=$REDACTED"
            }
        result = githubToken.replace(result, REDACTED)
        return result
    }
}
