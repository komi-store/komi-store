package zed.rainxch.core.data.repository

import zed.rainxch.core.domain.model.repository.StarredRepository
import zed.rainxch.core.domain.system.ExternalAppCandidate
import zed.rainxch.core.domain.system.RepoMatchSource
import zed.rainxch.core.domain.system.RepoMatchSuggestion

private const val STARRED_EXACT_CONFIDENCE = 0.78
private const val STARRED_CONTAINS_CONFIDENCE = 0.6
private const val MIN_MATCH_TOKEN_LEN = 4

private val GENERIC_MATCH_TOKENS =
    setOf(
        "android",
        "application",
        "mobile",
        "client",
        "free",
        "lite",
        "beta",
        "alpha",
        "debug",
        "release",
        "demo",
        "sample",
        "test",
        "plus",
        "apps",
        "main",
        "core",
    )

internal fun starredMatches(
    candidate: ExternalAppCandidate,
    starred: List<StarredRepository>,
): List<RepoMatchSuggestion> {
    val label = normalizeMatchToken(candidate.appLabel)
    val pkgTail = normalizeMatchToken(candidate.packageName.substringAfterLast('.'))
    val needles =
        listOf(label, pkgTail)
            .filter { it.length >= MIN_MATCH_TOKEN_LEN && it !in GENERIC_MATCH_TOKENS }
    if (starred.isEmpty() || needles.isEmpty()) return emptyList()

    return starred.mapNotNull { repo ->
        if (repo.latestReleaseUrl.isNullOrBlank()) return@mapNotNull null
        val repoName = normalizeMatchToken(repo.repoName)
        if (repoName.length < MIN_MATCH_TOKEN_LEN || repoName in GENERIC_MATCH_TOKENS) return@mapNotNull null
        val confidence =
            needles.maxOf { needle ->
                when {
                    needle == repoName -> STARRED_EXACT_CONFIDENCE
                    needle.contains(repoName) || repoName.contains(needle) -> STARRED_CONTAINS_CONFIDENCE
                    else -> 0.0
                }
            }
        if (confidence <= 0.0) return@mapNotNull null
        RepoMatchSuggestion(
            owner = repo.repoOwner,
            repo = repo.repoName,
            confidence = confidence,
            source = RepoMatchSource.STARRED,
            stars = repo.stargazersCount,
            description = repo.repoDescription,
            sourceHost = null,
        )
    }
}

private fun normalizeMatchToken(value: String): String = value.lowercase().filter { it.isLetterOrDigit() }
