package zed.rainxch.core.data.repository

import androidx.sqlite.SQLiteException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import zed.rainxch.core.data.local.db.dao.ExternalLinkDao
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.entities.ExternalLinkEntity
import zed.rainxch.core.domain.repository.ExternalImportRepository
import zed.rainxch.core.domain.repository.ExternalImportScans
import zed.rainxch.core.domain.system.ExternalAppCandidate
import zed.rainxch.core.domain.system.ExternalAppScanner
import zed.rainxch.core.domain.system.ExternalDecisionSnapshot
import zed.rainxch.core.domain.system.ExternalLinkState
import zed.rainxch.core.domain.system.RepoMatchResult
import zed.rainxch.core.domain.system.RepoMatchSuggestion
import kotlin.time.Clock

class ExternalImportRepositoryImpl private constructor(
    private val externalLinkDao: ExternalLinkDao,
    private val matchResolver: ExternalMatchResolver,
    private val scans: ExternalImportScansImpl,
) : ExternalImportRepository,
    ExternalImportScans by scans {
    internal constructor(
        scanner: ExternalAppScanner,
        externalLinkDao: ExternalLinkDao,
        installedAppDao: InstalledAppDao,
        fingerprints: SigningFingerprintIndex,
        matchResolver: ExternalMatchResolver,
        initialScan: InitialScanMarker,
    ) : this(
        externalLinkDao = externalLinkDao,
        matchResolver = matchResolver,
        scans = ExternalImportScansImpl(scanner, externalLinkDao, installedAppDao, fingerprints, matchResolver, initialScan),
    )

    override fun pendingCandidatesFlow(): Flow<List<ExternalAppCandidate>> =
        combine(
            scans.candidates,
            externalLinkDao.observePendingReview(),
        ) { snapshot, pendingRows ->
            val pendingPackages = pendingRows.map { it.packageName }.toSet()
            pendingPackages.mapNotNull { snapshot[it] }
        }

    override fun pendingCandidateCountFlow(): Flow<Int> = externalLinkDao.observePendingReviewCount()

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
        scans.forget(packageName)
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

    override suspend fun searchRepos(query: String): Result<List<RepoMatchSuggestion>> = matchResolver.searchRepos(query)

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    companion object {
        private const val SKIP_TTL_MILLIS: Long = 7L * 24 * 60 * 60 * 1000
    }
}
