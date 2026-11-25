package zed.rainxch.core.data.repository

import androidx.sqlite.SQLiteException
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import zed.rainxch.core.data.local.db.dao.ExternalLinkDao
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.repository.ExternalImportScans
import zed.rainxch.core.domain.system.ExternalAppCandidate
import zed.rainxch.core.domain.system.ExternalAppScanner
import zed.rainxch.core.domain.system.ExternalLinkState
import zed.rainxch.core.domain.system.InstallerKind
import zed.rainxch.core.domain.system.RepoMatchResult
import zed.rainxch.core.domain.system.ScanResult
import kotlin.time.Clock

// The scans behind ExternalImportRepository; it keeps the latest candidates the repository's review list reads.
internal class ExternalImportScansImpl(
    private val scanner: ExternalAppScanner,
    private val externalLinkDao: ExternalLinkDao,
    private val installedAppDao: InstalledAppDao,
    private val fingerprints: SigningFingerprintIndex,
    private val matchResolver: ExternalMatchResolver,
    private val initialScan: InitialScanMarker,
) : ExternalImportScans {
    private val candidateSnapshot = MutableStateFlow<Map<String, ExternalAppCandidate>>(emptyMap())

    val candidates: StateFlow<Map<String, ExternalAppCandidate>> = candidateSnapshot.asStateFlow()

    fun forget(packageName: String) {
        candidateSnapshot.update { it - packageName }
    }

    override suspend fun scheduleInitialScanIfNeeded() {
        val firstLaunch = initialScan.isFirstLaunch()
        try {
            runFullScan()
            if (firstLaunch) initialScan.markComplete()
        } catch (e: LocalStoreException) {
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

        localStore {
            candidates.forEach { candidate ->
                val existing = externalLinkDao.get(candidate.packageName)
                val updated = mergeCandidate(existing, candidate, now)
                if (existing == null) newCandidates++
                if (updated.state == ExternalLinkState.PENDING_REVIEW.name) pendingReview++
                if (existing != null && updated.state != ExternalLinkState.PENDING_REVIEW.name) preservedDecisions++
                externalLinkDao.upsert(updated)
            }
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

    override suspend fun rescanSinglePackage(packageName: String): RepoMatchResult? {
        val candidate = scanner.snapshotSingle(packageName) ?: return null
        candidateSnapshot.update { it + (packageName to candidate) }
        return matchResolver.resolveMatches(listOf(candidate)).firstOrNull()
    }

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

    private companion object {
        val TRUSTED_GITHUB_INSTALLERS =
            setOf(
                InstallerKind.STORE_OBTAINIUM,
                InstallerKind.STORE_FDROID,
            )
    }
}
