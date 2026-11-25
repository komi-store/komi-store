package zed.rainxch.apps.presentation.import

import zed.rainxch.apps.presentation.import.model.PendingUndo
import zed.rainxch.core.domain.system.ExternalAppCandidate
import zed.rainxch.core.domain.system.ExternalDecisionSnapshot
import zed.rainxch.core.domain.system.RepoMatchResult

// What one import run found and decided, kept so a card or an auto-link can be rebuilt or undone later.
internal class ImportSession {
    var candidatesByPackage: Map<String, ExternalAppCandidate> = emptyMap()
    var lastResolvedMatches: List<RepoMatchResult> = emptyList()
    var autoLinkedHadInstalledRow: Map<String, Boolean> = emptyMap()
    var autoLinkedPreSnapshots: Map<String, ExternalDecisionSnapshot?> = emptyMap()
    var pendingUndo: PendingUndo? = null

    fun forgetAutoLinks() {
        autoLinkedHadInstalledRow = emptyMap()
        autoLinkedPreSnapshots = emptyMap()
    }
}
