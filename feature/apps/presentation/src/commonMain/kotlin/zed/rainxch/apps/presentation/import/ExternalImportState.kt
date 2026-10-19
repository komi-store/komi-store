package zed.rainxch.apps.presentation.import

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import zed.rainxch.apps.presentation.import.model.CandidateUi
import zed.rainxch.apps.presentation.import.model.ImportPhase
import zed.rainxch.apps.presentation.import.model.RepoSuggestionUi

data class ExternalImportState(
    val phase: ImportPhase = ImportPhase.Idle,
    val totalCandidates: Int = 0,
    val autoImported: Int = 0,
    val skipped: Int = 0,
    val manuallyLinked: Int = 0,
    val cards: ImmutableList<CandidateUi> = persistentListOf(),
    val autoLinkedPackages: ImmutableList<String> = persistentListOf(),
    val autoLinkedLabels: ImmutableList<String> = persistentListOf(),
    val expandedPackages: ImmutableSet<String> = persistentSetOf(),
    val activeSearchPackage: String? = null,
    val searchQuery: String = "",
    val searchResults: ImmutableList<RepoSuggestionUi> = persistentListOf(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val isPermissionDenied: Boolean = false,
    val visiblePackageCount: Int = 0,
    val invisiblePackageCountEstimate: Int = 0,
    val showCompletionToast: Boolean = false,
    val errorMessage: String? = null,
    val confettiPlayCount: Int = 0,
    val trackedCount: Int = 0,
    val scanStartedAtMs: Long? = null,
    val isSkipAvailable: Boolean = false,
)
