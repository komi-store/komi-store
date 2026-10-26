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

    private const val GIT_SUFFIX = ".git"

    fun parse(
        rawUrl: String,
        additionalForgejoHosts: Set<String> = emptySet(),
    ): RepositoryReference? {
        val match = urlRegex.matchEntire(rawUrl.trim().trimEnd('/')) ?: return null
        val (rawHost, owner, rawRepo) = match.destructured
        val repo = stripGitSuffix(rawRepo)
        if (owner.isEmpty() || repo.isEmpty()) return null
        return sourceOf(rawHost.lowercase(), additionalForgejoHosts)?.let { RepositoryReference(it, owner, repo) }
    }

    private fun sourceOf(
        host: String,
        additionalForgejoHosts: Set<String>,
    ): RepositorySource? {
        val userHosts = additionalForgejoHosts.map { it.lowercase().trim() }.toSet()
        return when {
            host == "github.com" || host == "www.github.com" -> RepositorySource.GitHub
            host in knownForgejoHosts -> RepositorySource.Forgejo(host)
            host in userHosts -> RepositorySource.Forgejo(host)
            looksLikeForgejoHost(host) -> RepositorySource.Forgejo(host)
            else -> null
        }
    }

    private fun stripGitSuffix(name: String): String =
        if (name.length > GIT_SUFFIX.length && name.endsWith(GIT_SUFFIX, ignoreCase = true)) {
            name.dropLast(GIT_SUFFIX.length)
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
