package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.repository.RepositoryReference
import zed.rainxch.core.domain.model.repository.RepositorySource

object RepositoryUrlParser {
    private val urlRegex =
        Regex(
            """^(?:https?://)?([^/\s]+)/([^/\s]+)/([^/\s?#]+)(?:[/?#].*)?$""",
            RegexOption.IGNORE_CASE,
        )

    private val knownForgejoHosts =
        setOf(
            "codeberg.org",
            "git.disroot.org",
            "gitea.com",
        )

    fun parse(
        rawUrl: String,
        additionalForgejoHosts: Set<String> = emptySet(),
    ): RepositoryReference? {
        val trimmed = rawUrl.trim().trimEnd('/')
        val match = urlRegex.matchEntire(trimmed) ?: return null
        val host = match.groupValues[1].lowercase()
        val owner = match.groupValues[2]
        val repo = stripGitSuffix(match.groupValues[3])
        if (owner.isEmpty() || repo.isEmpty()) return null

        val userHosts = additionalForgejoHosts.map { it.lowercase().trim() }.toSet()

        val source =
            when {
                host == "github.com" || host == "www.github.com" -> RepositorySource.GitHub
                host in knownForgejoHosts -> RepositorySource.Forgejo(host)
                host in userHosts -> RepositorySource.Forgejo(host)
                looksLikeForgejoHost(host) -> RepositorySource.Forgejo(host)
                else -> return null
            }
        return RepositoryReference(source, owner, repo)
    }

    private fun stripGitSuffix(name: String): String =
        if (name.length > 4 && name.regionMatches(name.length - 4, ".git", 0, 4, ignoreCase = true)) {
            name.substring(0, name.length - 4)
        } else {
            name
        }

    private fun looksLikeForgejoHost(host: String): Boolean {
        val lower = host.lowercase()

        return lower.contains("forgejo") ||
            lower.contains("gitea") ||
            lower.startsWith("git.")
    }
}
