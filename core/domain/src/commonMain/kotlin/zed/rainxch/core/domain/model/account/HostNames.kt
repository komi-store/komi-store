package zed.rainxch.core.domain.model.account

object HostNames {
    const val GITHUB = "github.com"
    const val CODEBERG = "codeberg.org"

    fun normalize(raw: String): String {
        val trimmed = raw.trim().lowercase()
        val withoutScheme = trimmed.removePrefix("https://").removePrefix("http://")
        val hostOnly = withoutScheme.substringBefore('/')
        val noAuth = hostOnly.substringAfterLast('@')
        return noAuth.removeSuffix(".").removePrefix("www.").removePrefix("api.")
    }

    fun sanitizePastedToken(raw: String): String =
        raw
            .trim()
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .removePrefix("Token ")
            .removePrefix("token ")
            .removePrefix("Authorization: token ")
            .removePrefix("Authorization: Bearer ")
            .trim()

    fun detectPatKind(token: String): String? =
        when {
            token.startsWith("ghp_") -> "GitHub classic PAT"
            token.startsWith("github_pat_") -> "GitHub fine-grained PAT"
            token.startsWith("gho_") -> "GitHub OAuth token"
            token.startsWith("ghs_") -> "GitHub server token"
            else -> null
        }

    fun apiHostToTokenHost(apiHost: String): String {
        val normalized = normalize(apiHost)
        return when (normalized) {
            "api.github.com", "uploads.github.com", "raw.githubusercontent.com" -> GITHUB
            else -> normalized
        }
    }
}
