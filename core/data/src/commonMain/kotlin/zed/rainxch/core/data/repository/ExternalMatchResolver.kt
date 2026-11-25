package zed.rainxch.core.data.repository

import co.touchlab.kermit.Logger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import zed.rainxch.core.data.dto.ExternalMatchRequest
import zed.rainxch.core.data.mappers.toRepoMatchResults
import zed.rainxch.core.data.mappers.toRequestItem
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.network.ExternalMatchApi
import zed.rainxch.core.data.network.ForgejoClientRegistry
import zed.rainxch.core.data.network.getReleases
import zed.rainxch.core.data.network.recoverHttpFailure
import zed.rainxch.core.data.network.search
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.repository.StarredRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.ExternalAppCandidate
import zed.rainxch.core.domain.system.RepoMatchResult
import zed.rainxch.core.domain.system.RepoMatchSource
import zed.rainxch.core.domain.system.RepoMatchSuggestion

// Finds repos for apps installed outside the store: fingerprints, the backend, Forgejo search and the user's stars.
internal class ExternalMatchResolver(
    private val fingerprints: SigningFingerprintIndex,
    private val externalMatchApi: ExternalMatchApi,
    private val backendClient: BackendApiClient,
    private val forgejoClientRegistry: ForgejoClientRegistry,
    private val tweaksRepository: TweaksRepository,
    private val starredRepository: StarredRepository,
) {
    suspend fun resolveMatches(candidates: List<ExternalAppCandidate>): List<RepoMatchResult> {
        if (candidates.isEmpty()) return emptyList()
        val sources = MatchSources(fingerprintSuggestions(candidates), backendSuggestions(candidates))
        sources.forgejo = forgejoSuggestions(candidates, sources)
        val starredEligible =
            candidates
                .filter { sources.bestConfidence(it) < FORGEJO_SEARCH_SKIP_THRESHOLD }
                .mapTo(mutableSetOf()) { it.packageName }
        val starred = if (starredEligible.isEmpty()) emptyList() else starredReposOrEmpty()
        val rawResults =
            candidates.map { candidate ->
                val suggestions = sources.suggestionsFor(candidate).toMutableList()
                if (candidate.packageName in starredEligible) {
                    suggestions += starredMatches(candidate, starred)
                }
                // Sorted before dedupe, so the strongest copy survives when two sources name one repo.
                val deduped =
                    suggestions
                        .sortedByDescending { it.confidence }
                        .distinctBy { suggestionKey(it) }
                RepoMatchResult(packageName = candidate.packageName, suggestions = deduped)
            }
        return dropSuggestionsWithoutReleases(rawResults)
    }

    suspend fun searchRepos(query: String): Result<List<RepoMatchSuggestion>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())
        val capped =
            if (trimmed.length > MAX_SEARCH_QUERY_LEN) {
                trimmed.substring(0, MAX_SEARCH_QUERY_LEN)
            } else {
                trimmed
            }
        return backendClient
            .search(query = capped, platform = "android", limit = SEARCH_LIMIT)
            .map { response ->
                response.items.map { item ->
                    RepoMatchSuggestion(
                        owner = item.owner.login,
                        repo = item.name,
                        confidence = SEARCH_OVERRIDE_CONFIDENCE,
                        source = RepoMatchSource.SEARCH,
                        stars = item.stargazersCount,
                        description = item.description,
                    )
                }
            }
    }

    private suspend fun fingerprintSuggestions(candidates: List<ExternalAppCandidate>): Map<String, RepoMatchSuggestion> =
        candidates
            .mapNotNull { candidate ->
                val fingerprint = candidate.signingFingerprint ?: return@mapNotNull null
                val hit = fingerprints.lookup(fingerprint) ?: return@mapNotNull null
                candidate.packageName to
                    RepoMatchSuggestion(
                        owner = hit.repoOwner,
                        repo = hit.repoName,
                        confidence = FINGERPRINT_CONFIDENCE,
                        source = RepoMatchSource.FINGERPRINT,
                    )
            }.toMap()

    private suspend fun backendSuggestions(candidates: List<ExternalAppCandidate>): Map<String, List<RepoMatchSuggestion>> {
        val results = mutableMapOf<String, MutableList<RepoMatchSuggestion>>()
        for (batch in candidates.chunked(MATCH_BATCH_SIZE)) {
            val request =
                ExternalMatchRequest(
                    platform = "android",
                    candidates = batch.map { it.toRequestItem() },
                )
            externalMatchApi
                .match(request)
                .onSuccess { response ->
                    response.toRepoMatchResults().forEach { result ->
                        results.getOrPut(result.packageName) { mutableListOf() }.addAll(result.suggestions)
                    }
                }.onFailure { error ->
                    Logger.w(error) { "external-match batch failed; continuing" }
                }
        }
        return results
    }

    private suspend fun forgejoSuggestions(
        candidates: List<ExternalAppCandidate>,
        known: MatchSources,
    ): Map<String, List<RepoMatchSuggestion>> {
        val hosts = forgejoSearchHosts()
        if (hosts.isEmpty()) return emptyMap()
        val hasUserHosts = tweaksRepository.getCustomForgeHosts().first().isNotEmpty()
        val budget = if (hasUserHosts) FORGEJO_SEARCH_CANDIDATE_BUDGET * 2 else FORGEJO_SEARCH_CANDIDATE_BUDGET
        val tasks =
            candidates
                .asSequence()
                .filter { known.bestConfidence(it) < FORGEJO_SEARCH_SKIP_THRESHOLD }
                .mapNotNull { candidate ->
                    val query = candidate.appLabel.trim()
                    if (query.isEmpty()) null else candidate.packageName to query
                }.take(budget)
                .flatMap { (packageName, query) -> hosts.map { host -> ForgejoSearch(packageName, host, query) } }
                .toList()
        val semaphore = Semaphore(FORGEJO_SEARCH_CONCURRENCY)
        val hits =
            coroutineScope {
                tasks
                    .map { task ->
                        async {
                            semaphore.withPermit {
                                val found = withTimeoutOrNull(FORGEJO_SEARCH_PER_CALL_TIMEOUT_MS) { searchForgejo(task) }
                                task to found.orEmpty()
                            }
                        }
                    }.awaitAll()
            }
        return hits
            .filter { (_, found) -> found.isNotEmpty() }
            .groupBy({ (task, _) -> task.packageName }, { (_, found) -> found })
            .mapValues { (_, lists) -> lists.flatten() }
    }

    private suspend fun forgejoSearchHosts(): List<String> {
        val user = tweaksRepository.getCustomForgeHosts().first()
        return (CANONICAL_FORGEJO_HOSTS + user).distinct().take(FORGEJO_SEARCH_MAX_HOSTS)
    }

    private suspend fun searchForgejo(task: ForgejoSearch): List<RepoMatchSuggestion> =
        recoverHttpFailure(
            { e ->
                Logger.d { "Forgejo search on ${task.host} for '${task.query}' failed: ${e.message}" }
                emptyList()
            },
        ) {
            forgejoClientRegistry
                .clientFor(task.host)
                .searchRepositories(query = task.query, page = 1, limit = FORGEJO_SEARCH_LIMIT)
                .getOrNull()
                ?.data
                .orEmpty()
                .take(FORGEJO_SEARCH_LIMIT)
                .mapIndexed { index, repo ->
                    val confidence = FORGEJO_SEARCH_BASE_CONFIDENCE - index * FORGEJO_SEARCH_RANK_DECAY
                    RepoMatchSuggestion(
                        owner = repo.owner.login,
                        repo = repo.name,
                        confidence = confidence.coerceAtLeast(FORGEJO_SEARCH_MIN_CONFIDENCE),
                        source = RepoMatchSource.FORGEJO_SEARCH,
                        stars = repo.starsCount,
                        description = repo.description,
                        sourceHost = task.host,
                    )
                }
        }

    private suspend fun starredReposOrEmpty() =
        try {
            starredRepository.getAllStarred().first()
        } catch (e: LocalStoreException) {
            Logger.d { "starred fetch for match failed: ${e.message}" }
            emptyList()
        }

    private suspend fun dropSuggestionsWithoutReleases(results: List<RepoMatchResult>): List<RepoMatchResult> {
        val unique =
            results
                .flatMap { it.suggestions }
                .filterNot { it.source in RELEASE_VERIFY_SKIP_SOURCES }
                .associateBy { suggestionKey(it) }
                .values
        if (unique.size > RELEASE_VERIFY_BUDGET) {
            Logger.d { "release verify budget hit: ${unique.size} repos, capping at $RELEASE_VERIFY_BUDGET" }
        }
        val toVerify = unique.take(RELEASE_VERIFY_BUDGET)
        if (toVerify.isEmpty()) return results

        val semaphore = Semaphore(FORGEJO_SEARCH_CONCURRENCY)
        val verdicts =
            coroutineScope {
                toVerify
                    .map { suggestion ->
                        async {
                            semaphore.withPermit {
                                // A check that times out keeps the suggestion.
                                val ok = withTimeoutOrNull(RELEASE_VERIFY_TIMEOUT_MS) { hasInstallableRelease(suggestion) } ?: true
                                suggestionKey(suggestion) to ok
                            }
                        }
                    }.awaitAll()
                    .toMap()
            }

        return results.map { result ->
            result.copy(suggestions = result.suggestions.filter { verdicts[suggestionKey(it)] != false })
        }
    }

    // A failed release fetch keeps the suggestion; only a release list without an installable APK drops it.
    private suspend fun hasInstallableRelease(suggestion: RepoMatchSuggestion): Boolean =
        recoverHttpFailure({ true }) {
            val owner = suggestion.owner
            val repo = suggestion.repo
            val host = suggestion.sourceHost
            val releases =
                if (host == null) {
                    backendClient.getReleases(owner, repo, perPage = RELEASE_VERIFY_PAGE_SIZE)
                } else {
                    forgejoClientRegistry.clientFor(host).getReleases(owner, repo, perPage = RELEASE_VERIFY_PAGE_SIZE)
                }
            releases.fold(
                onSuccess = { list ->
                    list.any { release ->
                        release.draft != true &&
                            release.prerelease != true &&
                            release.assets.any { it.name.endsWith(".apk", ignoreCase = true) }
                    }
                },
                onFailure = { true },
            )
        }

    private class ForgejoSearch(
        val packageName: String,
        val host: String,
        val query: String,
    )

    // Each source's suggestions per package name; forgejo is filled in once the other sources set the skip threshold.
    private class MatchSources(
        val fingerprint: Map<String, RepoMatchSuggestion>,
        val backend: Map<String, List<RepoMatchSuggestion>>,
    ) {
        var forgejo: Map<String, List<RepoMatchSuggestion>> = emptyMap()

        fun bestConfidence(candidate: ExternalAppCandidate): Double =
            listOfNotNull(
                candidate.manifestHint?.confidence,
                fingerprint[candidate.packageName]?.confidence,
                backend[candidate.packageName]?.maxOfOrNull { it.confidence },
                forgejo[candidate.packageName]?.maxOfOrNull { it.confidence },
            ).maxOrNull() ?: 0.0

        fun suggestionsFor(candidate: ExternalAppCandidate): List<RepoMatchSuggestion> =
            buildList {
                candidate.manifestHint?.let { hint ->
                    add(
                        RepoMatchSuggestion(
                            owner = hint.owner,
                            repo = hint.repo,
                            confidence = hint.confidence,
                            source = RepoMatchSource.MANIFEST,
                        ),
                    )
                }
                fingerprint[candidate.packageName]?.let { add(it) }
                backend[candidate.packageName]?.let { addAll(it) }
                forgejo[candidate.packageName]?.let { addAll(it) }
            }
    }

    private companion object {
        val CANONICAL_FORGEJO_HOSTS = listOf("codeberg.org", "gitea.com", "git.disroot.org")
        const val FORGEJO_SEARCH_MAX_HOSTS = 5
        const val FORGEJO_SEARCH_LIMIT = 5
        const val FORGEJO_SEARCH_CANDIDATE_BUDGET = 12
        const val FORGEJO_SEARCH_SKIP_THRESHOLD = 0.7
        const val FORGEJO_SEARCH_CONCURRENCY = 8
        const val FORGEJO_SEARCH_PER_CALL_TIMEOUT_MS = 4_000L
        const val FORGEJO_SEARCH_BASE_CONFIDENCE = 0.55
        const val FORGEJO_SEARCH_RANK_DECAY = 0.08
        const val FORGEJO_SEARCH_MIN_CONFIDENCE = 0.05
        const val RELEASE_VERIFY_BUDGET = 60
        const val RELEASE_VERIFY_TIMEOUT_MS = 5_000L
        const val RELEASE_VERIFY_PAGE_SIZE = 10
        val RELEASE_VERIFY_SKIP_SOURCES =
            setOf(
                RepoMatchSource.STARRED,
                RepoMatchSource.MANIFEST,
                RepoMatchSource.FINGERPRINT,
            )
        const val MATCH_BATCH_SIZE = 25
        const val FINGERPRINT_CONFIDENCE = 0.92
        const val SEARCH_OVERRIDE_CONFIDENCE = 0.5
        const val SEARCH_LIMIT = 10
        const val MAX_SEARCH_QUERY_LEN = 100

        fun suggestionKey(suggestion: RepoMatchSuggestion): String =
            "${suggestion.sourceHost ?: "github"}|${suggestion.owner}/${suggestion.repo}"
    }
}
