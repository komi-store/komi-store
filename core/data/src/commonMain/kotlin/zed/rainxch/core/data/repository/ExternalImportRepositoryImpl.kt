package zed.rainxch.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import co.touchlab.kermit.Logger
import eu.anifantakis.lib.ksafe.KSafe
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import zed.rainxch.core.data.dto.ExternalMatchRequest
import zed.rainxch.core.data.local.db.dao.ExternalLinkDao
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.dao.SigningFingerprintDao
import zed.rainxch.core.data.local.db.entities.ExternalLinkEntity
import zed.rainxch.core.data.local.db.entities.SigningFingerprintEntity
import zed.rainxch.core.data.mappers.toRepoMatchResults
import zed.rainxch.core.data.mappers.toRequestItem
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.network.ExternalMatchApi
import zed.rainxch.core.data.network.ForgejoClientRegistry
import zed.rainxch.core.domain.repository.ExternalImportRepository
import zed.rainxch.core.domain.system.ExternalAppCandidate
import zed.rainxch.core.domain.system.ExternalAppScanner
import zed.rainxch.core.domain.system.ExternalDecisionSnapshot
import zed.rainxch.core.domain.system.ExternalLinkState
import zed.rainxch.core.domain.system.RepoMatchResult
import zed.rainxch.core.domain.system.RepoMatchSource
import zed.rainxch.core.domain.system.RepoMatchSuggestion
import zed.rainxch.core.domain.system.ScanResult
import zed.rainxch.core.data.secure.safeGet
import zed.rainxch.core.data.secure.safePut
import zed.rainxch.core.domain.repository.StarredRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.InstallerKind

class ExternalImportRepositoryImpl(
    private val scanner: ExternalAppScanner,
    private val externalLinkDao: ExternalLinkDao,
    private val installedAppDao: InstalledAppDao,
    private val signingFingerprintDao: SigningFingerprintDao,
    private val ksafe: KSafe,
    private val legacyDataStore: DataStore<Preferences>,
    private val externalMatchApi: ExternalMatchApi,
    private val backendClient: BackendApiClient,
    private val forgejoClientRegistry: ForgejoClientRegistry,
    private val tweaksRepository: TweaksRepository,
    private val starredRepository: StarredRepository,
) : ExternalImportRepository {
    private val candidateSnapshot = MutableStateFlow<Map<String, ExternalAppCandidate>>(emptyMap())

    override fun pendingCandidatesFlow(): Flow<List<ExternalAppCandidate>> =
        combine(
            candidateSnapshot,
            externalLinkDao.observePendingReview(),
        ) { snapshot, pendingRows ->
            val pendingPackages = pendingRows.map { it.packageName }.toSet()
            pendingPackages.mapNotNull { snapshot[it] }
        }

    override fun pendingCandidateCountFlow(): Flow<Int> = externalLinkDao.observePendingReviewCount()

    override suspend fun scheduleInitialScanIfNeeded() {
        migrateLegacyInitialScanFlag()
        val firstLaunch = runCatching {
            ksafe.safeGet<Long?>(K_INITIAL_SCAN_AT, null)
        }.getOrNull() == null
        runCatching {
            runFullScan()
        }.onSuccess {
            if (firstLaunch) markInitialScanComplete()
        }.onFailure {
            Logger.w(it) { "External scan failed; will retry on next launch." }
        }
    }

    override suspend fun runFullScan(includeUnverified: Boolean): ScanResult {
        val started = nowMillis()
        val granted = scanner.isPermissionGranted()
        val rawCandidates = scanner.snapshot()
        // Apps already tracked by the store are not link candidates — offering them
        // would let the user re-link an already-managed app and create a conflicting
        // entry (GH#729). prunePendingReviewNotIn below also clears any stale rows.
        val trackedPackages =
            runCatching { installedAppDao.getTrackedPackageNames().toSet() }.getOrDefault(emptySet())
        val candidates =
            (
                if (includeUnverified) {
                    rawCandidates
                } else {
                    rawCandidates.filter { hasPositiveEvidence(it) }
                }
            ).filterNot { it.packageName in trackedPackages }
        candidateSnapshot.update { candidates.associateBy { it.packageName } }

        val now = nowMillis()
        var newCandidates = 0
        var pendingReview = 0
        var preservedDecisions = 0

        candidates.forEach { candidate ->
            val existing = externalLinkDao.get(candidate.packageName)
            val updated = mergeCandidate(existing, candidate, now)
            if (existing == null) newCandidates++
            if (updated.state == ExternalLinkState.PENDING_REVIEW.name) pendingReview++
            if (existing != null && updated.state != ExternalLinkState.PENDING_REVIEW.name) preservedDecisions++
            externalLinkDao.upsert(updated)
        }

        val livePackages = candidates.map { it.packageName }.toSet()
        runCatching { externalLinkDao.prunePendingReviewNotIn(livePackages) }
            .onFailure { Logger.d { "prune pending failed: ${it.message}" } }

        val durationMs = nowMillis() - started

        return ScanResult(
            totalCandidates = candidates.size,
            newCandidates = newCandidates,
            autoLinked = 0,
            pendingReview = pendingReview,
            durationMillis = durationMs,
            permissionGranted = granted,
        )
    }

    override suspend fun runDeltaScan(changedPackageNames: Set<String>): ScanResult {
        val started = nowMillis()
        val granted = scanner.isPermissionGranted()
        val now = nowMillis()
        var newCandidates = 0
        var pendingReview = 0
        val deltaCandidates = mutableListOf<ExternalAppCandidate>()
        val trackedPackages =
            runCatching { installedAppDao.getTrackedPackageNames().toSet() }.getOrDefault(emptySet())

        changedPackageNames.forEach { pkg ->
            val rawCandidate = scanner.snapshotSingle(pkg)
            val existing = externalLinkDao.get(pkg)

            if (pkg in trackedPackages) {
                if (existing != null) externalLinkDao.deleteByPackageName(pkg)
                return@forEach
            }

            if (rawCandidate == null) {
                if (existing != null) {
                    externalLinkDao.deleteByPackageName(pkg)
                }
                return@forEach
            }

            if (!hasPositiveEvidence(rawCandidate)) {
                if (existing?.state == ExternalLinkState.PENDING_REVIEW.name) {
                    externalLinkDao.deleteByPackageName(pkg)
                }
                return@forEach
            }

            deltaCandidates += rawCandidate
            val updated = mergeCandidate(existing, rawCandidate, now)
            if (existing == null) newCandidates++
            if (updated.state == ExternalLinkState.PENDING_REVIEW.name) pendingReview++
            externalLinkDao.upsert(updated)
        }

        if (deltaCandidates.isNotEmpty()) {
            candidateSnapshot.update { current ->
                current.toMutableMap().apply {
                    deltaCandidates.forEach { put(it.packageName, it) }
                }
            }
        }

        return ScanResult(
            totalCandidates = deltaCandidates.size,
            newCandidates = newCandidates,
            autoLinked = 0,
            pendingReview = pendingReview,
            durationMillis = nowMillis() - started,
            permissionGranted = granted,
        )
    }

    override suspend fun resolveMatches(candidates: List<ExternalAppCandidate>): List<RepoMatchResult> {
        if (candidates.isEmpty()) return emptyList()

        val fingerprintHits = mutableMapOf<String, RepoMatchSuggestion>()
        candidates.forEach { candidate ->
            val fp = candidate.signingFingerprint ?: return@forEach
            val hit = runCatching { signingFingerprintDao.lookup(fp) }
                .onFailure { Logger.d { "signing fingerprint lookup failed: ${it.message}" } }
                .getOrNull() ?: return@forEach
            fingerprintHits[candidate.packageName] = RepoMatchSuggestion(
                owner = hit.repoOwner,
                repo = hit.repoName,
                confidence = FINGERPRINT_CONFIDENCE,
                source = RepoMatchSource.FINGERPRINT,
            )
        }

        val backendResults = mutableMapOf<String, MutableList<RepoMatchSuggestion>>()
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
                        backendResults
                            .getOrPut(result.packageName) { mutableListOf() }
                            .addAll(result.suggestions)
                    }
                }.onFailure { error ->
                    Logger.w(error) { "external-match batch failed; continuing" }
                }
        }

        val forgejoHits = mutableMapOf<String, MutableList<RepoMatchSuggestion>>()
        val forgejoHostList = forgejoSearchHosts()
        val hasUserHosts = runCatching {
            tweaksRepository.getCustomForgeHosts().first().isNotEmpty()
        }.getOrDefault(false)
        if (forgejoHostList.isNotEmpty()) {
            val totalBudget = if (hasUserHosts) FORGEJO_SEARCH_CANDIDATE_BUDGET * 2 else FORGEJO_SEARCH_CANDIDATE_BUDGET
            val eligible = candidates.asSequence()
                .filter { candidate ->
                    val existing = listOfNotNull(
                        candidate.manifestHint?.confidence,
                        fingerprintHits[candidate.packageName]?.confidence,
                        backendResults[candidate.packageName]?.maxOfOrNull { it.confidence },
                    ).maxOrNull() ?: 0.0
                    existing < FORGEJO_SEARCH_SKIP_THRESHOLD
                }
                .mapNotNull { candidate ->
                    val query = candidate.appLabel.trim().takeIf { it.isNotEmpty() }
                    if (query == null) null else candidate to query
                }
                .take(totalBudget)
                .toList()

            data class SearchTask(
                val packageName: String,
                val host: String,
                val query: String,
            )
            val tasks = eligible.flatMap { (candidate, query) ->
                forgejoHostList.map { host ->
                    SearchTask(candidate.packageName, host, query)
                }
            }
            if (tasks.isNotEmpty()) {
                val sem = kotlinx.coroutines.sync.Semaphore(FORGEJO_SEARCH_CONCURRENCY)
                kotlinx.coroutines.coroutineScope {
                    val deferred = tasks.map { task ->
                        async {
                            sem.withPermit {
                                val hits = kotlinx.coroutines.withTimeoutOrNull(FORGEJO_SEARCH_PER_CALL_TIMEOUT_MS) {
                                    searchForgejoHostForSuggestions(task.host, task.query)
                                }.orEmpty()
                                task to hits
                            }
                        }
                    }
                    deferred.awaitAll().forEach { (task, hits) ->
                        if (hits.isNotEmpty()) {
                            forgejoHits
                                .getOrPut(task.packageName) { mutableListOf() }
                                .addAll(hits)
                        }
                    }
                }
            }
        }

        fun bestConfidence(candidate: ExternalAppCandidate): Double =
            listOfNotNull(
                candidate.manifestHint?.confidence,
                fingerprintHits[candidate.packageName]?.confidence,
                backendResults[candidate.packageName]?.maxOfOrNull { it.confidence },
                forgejoHits[candidate.packageName]?.maxOfOrNull { it.confidence },
            ).maxOrNull() ?: 0.0

        val starredEligible = candidates
            .filter { bestConfidence(it) < FORGEJO_SEARCH_SKIP_THRESHOLD }
            .mapTo(mutableSetOf()) { it.packageName }
        val starred = if (starredEligible.isEmpty()) {
            emptyList()
        } else {
            runCatching { starredRepository.getAllStarred().first() }
                .onFailure {
                    if (it is CancellationException) throw it
                    Logger.d { "starred fetch for match failed: ${it.message}" }
                }
                .getOrDefault(emptyList())
        }

        val rawResults = candidates.map { candidate ->
            val suggestions = mutableListOf<RepoMatchSuggestion>()
            candidate.manifestHint?.let { hint ->
                suggestions += RepoMatchSuggestion(
                    owner = hint.owner,
                    repo = hint.repo,
                    confidence = hint.confidence,
                    source = RepoMatchSource.MANIFEST,
                )
            }
            fingerprintHits[candidate.packageName]?.let { suggestions += it }
            backendResults[candidate.packageName]?.let { suggestions += it }
            forgejoHits[candidate.packageName]?.let { suggestions += it }
            if (candidate.packageName in starredEligible) {
                suggestions += starredMatches(candidate, starred)
            }
            // Sort before dedupe so the highest-confidence entry survives when the
            // same repo is surfaced by more than one source (e.g. backend + starred).
            val deduped = suggestions
                .sortedByDescending { it.confidence }
                .distinctBy { suggestionKey(it) }

            RepoMatchResult(packageName = candidate.packageName, suggestions = deduped)
        }

        return dropSuggestionsWithoutReleases(rawResults)
    }

    private fun suggestionKey(suggestion: RepoMatchSuggestion): String =
        "${suggestion.sourceHost ?: "github"}|${suggestion.owner}/${suggestion.repo}"

    private suspend fun dropSuggestionsWithoutReleases(
        results: List<RepoMatchResult>,
    ): List<RepoMatchResult> {
        val unique = results
            .flatMap { it.suggestions }
            .filterNot { it.source in RELEASE_VERIFY_SKIP_SOURCES }
            .associateBy { suggestionKey(it) }
            .values
        if (unique.size > RELEASE_VERIFY_BUDGET) {
            Logger.d { "release verify budget hit: ${unique.size} repos, capping at $RELEASE_VERIFY_BUDGET" }
        }
        val toVerify = unique.take(RELEASE_VERIFY_BUDGET)
        if (toVerify.isEmpty()) return results

        val sem = kotlinx.coroutines.sync.Semaphore(FORGEJO_SEARCH_CONCURRENCY)
        val verdicts = kotlinx.coroutines.coroutineScope {
            toVerify.map { suggestion ->
                async {
                    sem.withPermit {
                        val ok = kotlinx.coroutines.withTimeoutOrNull(RELEASE_VERIFY_TIMEOUT_MS) {
                            runCatching {
                                hasInstallableRelease(
                                    suggestion.sourceHost,
                                    suggestion.owner,
                                    suggestion.repo,
                                )
                            }.getOrElse { if (it is CancellationException) throw it else true }
                        } ?: true
                        suggestionKey(suggestion) to ok
                    }
                }
            }.awaitAll().toMap()
        }

        return results.map { result ->
            result.copy(
                suggestions = result.suggestions.filter { verdicts[suggestionKey(it)] != false },
            )
        }
    }

    private suspend fun hasInstallableRelease(
        host: String?,
        owner: String,
        repo: String,
    ): Boolean {
        val releases =
            if (host == null) {
                backendClient.getReleases(owner, repo, perPage = RELEASE_VERIFY_PAGE_SIZE)
            } else {
                val client = forgejoClientRegistry.clientFor(host)
                client.getReleases(owner, repo, perPage = RELEASE_VERIFY_PAGE_SIZE)
            }
        return releases.fold(
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

    private fun starredMatches(
        candidate: ExternalAppCandidate,
        starred: List<zed.rainxch.core.domain.model.repository.StarredRepository>,
    ): List<RepoMatchSuggestion> {
        if (starred.isEmpty()) return emptyList()
        val label = normalizeMatchToken(candidate.appLabel)
        val pkgTail = normalizeMatchToken(candidate.packageName.substringAfterLast('.'))
        val needles = listOf(label, pkgTail)
            .filter { it.length >= MIN_MATCH_TOKEN_LEN && it !in GENERIC_MATCH_TOKENS }
        if (needles.isEmpty()) return emptyList()

        return starred.mapNotNull { repo ->
            if (repo.latestReleaseUrl.isNullOrBlank()) return@mapNotNull null
            val repoName = normalizeMatchToken(repo.repoName)
            if (repoName.length < MIN_MATCH_TOKEN_LEN || repoName in GENERIC_MATCH_TOKENS) return@mapNotNull null
            val confidence = needles.maxOf { needle ->
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

    private fun normalizeMatchToken(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }

    override suspend fun linkManually(
        packageName: String,
        owner: String,
        repo: String,
        source: String,
    ): Result<Unit> {
        val now = nowMillis()
        return runCatching {
            val existing = externalLinkDao.get(packageName)
            val base = existing ?: ExternalLinkEntity(
                packageName = packageName,
                state = ExternalLinkState.MATCHED.name,
                repoOwner = owner,
                repoName = repo,
                matchSource = source,
                matchConfidence = 1.0,
                signingFingerprint = null,
                installerKind = null,
                firstSeenAt = now,
                lastReviewedAt = now,
                skipExpiresAt = null,
            )
            externalLinkDao.upsert(
                base.copy(
                    state = ExternalLinkState.MATCHED.name,
                    repoOwner = owner,
                    repoName = repo,
                    matchSource = source,
                    matchConfidence = 1.0,
                    lastReviewedAt = now,
                ),
            )
        }.onFailure { if (it is CancellationException) throw it }
    }

    override suspend fun skipPackage(
        packageName: String,
        neverAsk: Boolean,
    ) {
        val existing = externalLinkDao.get(packageName)
        val state = if (neverAsk) ExternalLinkState.NEVER_ASK else ExternalLinkState.SKIPPED
        val now = nowMillis()
        val skipExpiresAt = if (neverAsk) null else now + SKIP_TTL_MILLIS
        val row =
            existing?.copy(
                state = state.name,
                lastReviewedAt = now,
                skipExpiresAt = skipExpiresAt,
            ) ?: ExternalLinkEntity(
                packageName = packageName,
                state = state.name,
                repoOwner = null,
                repoName = null,
                matchSource = null,
                matchConfidence = null,
                signingFingerprint = null,
                installerKind = null,
                firstSeenAt = now,
                lastReviewedAt = now,
                skipExpiresAt = skipExpiresAt,
            )
        externalLinkDao.upsert(row)
    }

    override suspend fun unlink(packageName: String) {
        externalLinkDao.deleteByPackageName(packageName)
        candidateSnapshot.update { it - packageName }
    }

    override suspend fun snapshotDecision(packageName: String): ExternalDecisionSnapshot? {
        val row = externalLinkDao.get(packageName) ?: return null
        return ExternalDecisionSnapshot(
            packageName = row.packageName,
            state = runCatching { ExternalLinkState.valueOf(row.state) }.getOrNull(),
            repoOwner = row.repoOwner,
            repoName = row.repoName,
            matchSource = row.matchSource,
            matchConfidence = row.matchConfidence,
            skipExpiresAt = row.skipExpiresAt,
        )
    }

    override suspend fun restoreDecision(snapshot: ExternalDecisionSnapshot) {
        val now = nowMillis()
        val state = snapshot.state ?: ExternalLinkState.PENDING_REVIEW
        val existing = externalLinkDao.get(snapshot.packageName)
        externalLinkDao.upsert(
            (existing ?: ExternalLinkEntity(
                packageName = snapshot.packageName,
                state = state.name,
                repoOwner = snapshot.repoOwner,
                repoName = snapshot.repoName,
                matchSource = snapshot.matchSource,
                matchConfidence = snapshot.matchConfidence,
                signingFingerprint = null,
                installerKind = null,
                firstSeenAt = now,
                lastReviewedAt = now,
                skipExpiresAt = snapshot.skipExpiresAt,
            )).copy(
                state = state.name,
                repoOwner = snapshot.repoOwner,
                repoName = snapshot.repoName,
                matchSource = snapshot.matchSource,
                matchConfidence = snapshot.matchConfidence,
                skipExpiresAt = snapshot.skipExpiresAt,
                lastReviewedAt = now,
            ),
        )
    }

    override suspend fun rescanSinglePackage(packageName: String): RepoMatchResult? {
        val candidate = scanner.snapshotSingle(packageName) ?: return null
        candidateSnapshot.update { it + (packageName to candidate) }
        return resolveMatches(listOf(candidate)).firstOrNull()
    }

    override suspend fun searchRepos(query: String): Result<List<RepoMatchSuggestion>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())
        val capped = if (trimmed.length > MAX_SEARCH_QUERY_LEN) {
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

    override suspend fun syncSigningFingerprintSeed() {
        val lastSyncedAt = runCatching { ksafe.safeGet<Long?>(K_SIGNING_SEED_SYNCED_AT, null) }.getOrNull()
        if (lastSyncedAt != null && nowMillis() - lastSyncedAt < SIGNING_SEED_SYNC_INTERVAL_MS) return
        var rowsAdded = 0
        var fetched = false
        try {
            val lastObservedAt = runCatching { signingFingerprintDao.lastSyncTimestamp() }
                .getOrNull()
            var cursor: String? = null
            var pages = 0
            paging@ while (pages < MAX_SEED_PAGES) {
                pages++
                val pageResult = backendClient.getSigningSeeds(
                    since = lastObservedAt,
                    cursor = cursor,
                )
                val response = pageResult.getOrElse { error ->
                    if (error is CancellationException) throw error
                    Logger.w(error) { "signing-seeds fetch failed on page $pages; aborting" }
                    break@paging
                }
                fetched = true
                val rows = response.rows.map { row ->
                    SigningFingerprintEntity(
                        fingerprint = row.fingerprint,
                        repoOwner = row.owner,
                        repoName = row.repo,
                        source = SEED_SOURCE_BACKEND,
                        observedAt = row.observedAt,
                    )
                }
                if (rows.isNotEmpty()) {
                    runCatching { signingFingerprintDao.upsertAll(rows) }
                        .onSuccess { rowsAdded += rows.size }
                        .onFailure { e ->
                            if (e is CancellationException) throw e
                            Logger.w(e) { "signing-seeds upsert failed on page $pages; continuing" }
                        }
                }
                cursor = response.nextCursor ?: break@paging
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e) { "signing-seeds sync aborted" }
        }
        if (fetched) ksafe.safePut(K_SIGNING_SEED_SYNCED_AT, nowMillis())
    }

    override suspend fun pruneExpiredSkips() {
        externalLinkDao.pruneExpiredSkips(nowMillis())
    }

    override suspend fun isPermissionGranted(): Boolean = scanner.isPermissionGranted()

    private suspend fun markInitialScanComplete() {
        ksafe.safePut(K_INITIAL_SCAN_AT, nowMillis())
    }

    private suspend fun migrateLegacyInitialScanFlag() {
        val existing = runCatching { ksafe.safeGet<Long?>(K_INITIAL_SCAN_AT, null) }.getOrNull()
        if (existing != null) return
        val legacyValue = runCatching {
            legacyDataStore.data.first()[longPreferencesKey("external_import_initial_scan_at")]
        }.getOrNull() ?: return
        val putOk = ksafe.safePut(K_INITIAL_SCAN_AT, legacyValue)
        if (!putOk) return
        runCatching {
            legacyDataStore.edit { it.remove(longPreferencesKey("external_import_initial_scan_at")) }
        }
    }

    private suspend fun hasPositiveEvidence(candidate: ExternalAppCandidate): Boolean {
        if (candidate.installerKind in TRUSTED_GITHUB_INSTALLERS) return true
        if (candidate.manifestHint != null) return true
        val fp = candidate.signingFingerprint ?: return false
        return runCatching { signingFingerprintDao.lookup(fp) != null }.getOrDefault(false)
    }

    private fun mergeCandidate(
        existing: ExternalLinkEntity?,
        candidate: ExternalAppCandidate,
        now: Long,
    ): ExternalLinkEntity {
        if (existing != null && shouldPreserveDecision(existing, now)) {
            return existing.copy(
                signingFingerprint = candidate.signingFingerprint ?: existing.signingFingerprint,
                installerKind = candidate.installerKind.name,
            )
        }

        val hint = candidate.manifestHint
        return ExternalLinkEntity(
            packageName = candidate.packageName,
            state = ExternalLinkState.PENDING_REVIEW.name,
            repoOwner = hint?.owner ?: existing?.repoOwner,
            repoName = hint?.repo ?: existing?.repoName,
            matchSource = if (hint != null) RepoMatchSource.MANIFEST.name else existing?.matchSource,
            matchConfidence = hint?.confidence ?: existing?.matchConfidence,
            signingFingerprint = candidate.signingFingerprint,
            installerKind = candidate.installerKind.name,
            firstSeenAt = existing?.firstSeenAt ?: now,
            lastReviewedAt = now,
            skipExpiresAt = null,
        )
    }

    private fun shouldPreserveDecision(
        existing: ExternalLinkEntity,
        now: Long,
    ): Boolean =
        when (existing.state) {
            ExternalLinkState.MATCHED.name -> true
            ExternalLinkState.NEVER_ASK.name -> true
            ExternalLinkState.SKIPPED.name -> (existing.skipExpiresAt ?: 0) > now
            else -> false
        }

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    private suspend fun forgejoSearchHosts(): List<String> {
        val canonical = listOf("codeberg.org", "gitea.com", "git.disroot.org")
        val user = runCatching { tweaksRepository.getCustomForgeHosts().first() }
            .getOrNull()
            .orEmpty()
        return (canonical + user).distinct().take(FORGEJO_SEARCH_MAX_HOSTS)
    }

    private suspend fun searchForgejoHostForSuggestions(
        host: String,
        query: String,
    ): List<RepoMatchSuggestion> {
        return try {
            val client = forgejoClientRegistry.clientFor(host)
            val response = client.searchRepositories(
                query = query,
                page = 1,
                limit = FORGEJO_SEARCH_LIMIT,
            ).getOrNull() ?: return emptyList()
            response.data
                .take(FORGEJO_SEARCH_LIMIT)
                .mapIndexed { index, repo ->
                    val confidence = FORGEJO_SEARCH_BASE_CONFIDENCE -
                        (index * FORGEJO_SEARCH_RANK_DECAY)
                    RepoMatchSuggestion(
                        owner = repo.owner.login,
                        repo = repo.name,
                        confidence = confidence.coerceAtLeast(0.05),
                        source = RepoMatchSource.FORGEJO_SEARCH,
                        stars = repo.starsCount,
                        description = repo.description,
                        sourceHost = host,
                    )
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.d { "Forgejo search on $host for '$query' failed: ${e.message}" }
            emptyList()
        }
    }

    companion object {
        private const val K_INITIAL_SCAN_AT = "external_import_initial_scan_at"
        private const val K_SIGNING_SEED_SYNCED_AT = "signing_seed_synced_at"
        private const val SIGNING_SEED_SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L
        private const val SKIP_TTL_MILLIS: Long = 7L * 24 * 60 * 60 * 1000

        private const val FORGEJO_SEARCH_MAX_HOSTS = 5
        private const val FORGEJO_SEARCH_LIMIT = 5

        private const val FORGEJO_SEARCH_CANDIDATE_BUDGET = 12

        private const val FORGEJO_SEARCH_SKIP_THRESHOLD = 0.7

        private const val FORGEJO_SEARCH_CONCURRENCY = 8

        private const val FORGEJO_SEARCH_PER_CALL_TIMEOUT_MS = 4_000L

        private const val FORGEJO_SEARCH_BASE_CONFIDENCE = 0.55
        private const val FORGEJO_SEARCH_RANK_DECAY = 0.08

        private const val RELEASE_VERIFY_BUDGET = 60
        private const val RELEASE_VERIFY_TIMEOUT_MS = 5_000L
        private const val RELEASE_VERIFY_PAGE_SIZE = 10

        private val RELEASE_VERIFY_SKIP_SOURCES =
            setOf(
                RepoMatchSource.STARRED,
                RepoMatchSource.MANIFEST,
                RepoMatchSource.FINGERPRINT,
            )
        private const val STARRED_EXACT_CONFIDENCE = 0.78
        private const val STARRED_CONTAINS_CONFIDENCE = 0.6
        private const val MIN_MATCH_TOKEN_LEN = 4

        private val GENERIC_MATCH_TOKENS =
            setOf(
                "android", "application", "mobile", "client", "free", "lite",
                "beta", "alpha", "debug", "release", "demo", "sample", "test",
                "plus", "apps", "main", "core",
            )
        private const val MATCH_BATCH_SIZE = 25
        private const val FINGERPRINT_CONFIDENCE = 0.92
        private const val SEARCH_OVERRIDE_CONFIDENCE = 0.5
        private const val SEARCH_LIMIT = 10
        private const val MAX_SEARCH_QUERY_LEN = 100
        private const val MAX_SEED_PAGES = 50
        private const val SEED_SOURCE_BACKEND = "backend_seed"

        private val TRUSTED_GITHUB_INSTALLERS =
            setOf(
                InstallerKind.STORE_OBTAINIUM,
                InstallerKind.STORE_FDROID,
            )
    }
}
