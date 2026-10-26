package zed.rainxch.core.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.system.ExternalAppCandidate
import zed.rainxch.core.domain.system.ExternalDecisionSnapshot
import zed.rainxch.core.domain.system.RepoMatchResult

interface ExternalImportRepository : ExternalImportScans {
    fun pendingCandidatesFlow(): Flow<List<ExternalAppCandidate>>

    fun pendingCandidateCountFlow(): Flow<Int>

    suspend fun resolveMatches(candidates: List<ExternalAppCandidate>): List<RepoMatchResult>

    suspend fun linkManually(
        packageName: String,
        owner: String,
        repo: String,
        source: String,
    ): Result<Unit>

    suspend fun skipPackage(
        packageName: String,
        neverAsk: Boolean = false,
    )

    suspend fun unlink(packageName: String)

    suspend fun snapshotDecision(packageName: String): ExternalDecisionSnapshot?

    suspend fun restoreDecision(snapshot: ExternalDecisionSnapshot)

    suspend fun searchRepos(query: String): Result<List<zed.rainxch.core.domain.system.RepoMatchSuggestion>>
}
