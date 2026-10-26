package zed.rainxch.core.data.repository

import androidx.sqlite.SQLiteException
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import zed.rainxch.core.data.local.db.dao.ExternalLinkDao
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.entities.ExternalLinkEntity
import zed.rainxch.core.domain.repository.ExternalImportRepository
import zed.rainxch.core.domain.system.ExternalAppCandidate
import zed.rainxch.core.domain.system.ExternalAppScanner
import zed.rainxch.core.domain.system.ExternalDecisionSnapshot
import zed.rainxch.core.domain.system.ExternalLinkState
import zed.rainxch.core.domain.system.InstallerKind
import zed.rainxch.core.domain.system.RepoMatchResult
import zed.rainxch.core.domain.system.RepoMatchSuggestion
import zed.rainxch.core.domain.system.ScanResult
import kotlin.time.Clock

class ExternalImportRepositoryImpl internal constructor(
    private val scanner: ExternalAppScanner,
    private val externalLinkDao: ExternalLinkDao,
    private val installedAppDao: InstalledAppDao,
    private val fingerprints: SigningFingerprintIndex,
    private val matchResolver: ExternalMatchResolver,
    private val initialScan: InitialScanMarker,
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
        val firstLaunch = initialScan.isFirstLaunch()
        try {
            runFullScan()
            if (firstLaunch) initialScan.markComplete()
        } catch (e: SQLiteException) {
            Logger.w(e) { "External scan failed; will retry on next launch." }
        }
    }

    override suspend fun runFullScan(includeUnverified: Boolean): ScanResult {
        val started = nowMillis()
        val granted = scanner.isPermissionGranted()
        val rawCandidates = scanner.snapshot()
        // Tracked apps aren't link candidates: re-linking one would create a conflicting entry (GH#729).
        val trackedPackages = trackedPackageNames()
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

        pruneStalePendingReviews(candidates.map { it.packageName }.toSet())

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
        val trackedPackages = trackedPackageNames()

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

    override suspend fun resolveMatches(candidates: List<ExternalAppCandidate>): List<RepoMatchResult> =
        matchResolver.resolveMatches(candidates)

    override suspend fun linkManually(
        packageName: String,
        owner: String,
        repo: String,
        source: String,
    ): Result<Unit> {
        val now = nowMillis()
        return try {
            val existing = externalLinkDao.get(packageName)
            val base =
                existing ?: ExternalLinkEntity(
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
            Result.success(Unit)
        } catch (e: SQLiteException) {
            Result.failure(e)
        }
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
            (
                existing ?: ExternalLinkEntity(
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
                )
            ).copy(
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

    override suspend fun searchRepos(query: String): Result<List<RepoMatchSuggestion>> = matchResolver.searchRepos(query)

    override suspend fun syncSigningFingerprintSeed() {
        fingerprints.syncSeed()
    }

    override suspend fun pruneExpiredSkips() {
        externalLinkDao.pruneExpiredSkips(nowMillis())
    }

    override suspend fun isPermissionGranted(): Boolean = scanner.isPermissionGranted()

    private suspend fun trackedPackageNames(): Set<String> =
        try {
            installedAppDao.getTrackedPackageNames().toSet()
        } catch (_: SQLiteException) {
            emptySet()
        }

    private suspend fun pruneStalePendingReviews(livePackages: Set<String>) {
        try {
            externalLinkDao.prunePendingReviewNotIn(livePackages)
        } catch (e: SQLiteException) {
            Logger.d { "prune pending failed: ${e.message}" }
        }
    }

    private suspend fun hasPositiveEvidence(candidate: ExternalAppCandidate): Boolean {
        if (candidate.installerKind in TRUSTED_GITHUB_INSTALLERS) return true
        if (candidate.manifestHint != null) return true
        val fp = candidate.signingFingerprint ?: return false
        return fingerprints.lookup(fp) != null
    }

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    companion object {
        private const val SKIP_TTL_MILLIS: Long = 7L * 24 * 60 * 60 * 1000

        private val TRUSTED_GITHUB_INSTALLERS =
            setOf(
                InstallerKind.STORE_OBTAINIUM,
                InstallerKind.STORE_FDROID,
            )
    }
}
