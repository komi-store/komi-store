package zed.rainxch.apps.presentation.import

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.apps.presentation.import.model.RepoSuggestionUi
import zed.rainxch.apps.presentation.import.model.SuggestionSource
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.repository.RepositorySource
import zed.rainxch.core.domain.repository.ExternalImportRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.utils.RepositoryUrlParser
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.external_import_search_error_default
import kotlin.coroutines.cancellation.CancellationException

// A card's own repo search: a pasted repo URL is taken as is, anything else asks the backend's search.
internal class RepoSearch(
    private val externalImportRepository: ExternalImportRepository,
    private val tweaksRepository: TweaksRepository,
    private val logger: KomiStoreLogger,
) {
    private var searchJob: Job? = null

    fun onAction(
        holder: ImportStateHolder,
        action: ExternalImportAction.Search,
    ) {
        when (action) {
            is ExternalImportAction.OnSearchOverrideChanged -> {
                holder.update {
                    if (it.activeSearchPackage != action.packageName) {
                        it.withoutSearch().copy(activeSearchPackage = action.packageName, searchQuery = action.query)
                    } else {
                        it.copy(searchQuery = action.query)
                    }
                }
            }

            is ExternalImportAction.OnSearchOverrideSubmit -> {
                submit(holder, action.packageName)
            }
        }
    }

    private fun submit(
        holder: ImportStateHolder,
        packageName: String,
    ) {
        val current = holder.current
        if (current.activeSearchPackage != packageName) return

        val query = current.searchQuery.trim()
        searchJob?.cancel()
        if (query.isEmpty()) {
            holder.update { it.copy(isSearching = false, searchError = null, searchResults = persistentListOf()) }
            return
        }

        holder.update { it.copy(isSearching = true, searchError = null) }
        searchJob =
            holder.scope.launch {
                val parsed = RepositoryUrlParser.parse(query, tweaksRepository.getCustomForgeHosts().first())
                if (parsed != null) {
                    val result =
                        RepoSuggestionUi(
                            owner = parsed.owner,
                            repo = parsed.repo,
                            confidence = 1.0,
                            source = SuggestionSource.MANUAL,
                            sourceHost = (parsed.source as? RepositorySource.Forgejo)?.host,
                        )
                    holder.showResults(packageName, persistentListOf(result))
                } else {
                    search(holder, packageName, query)
                }
            }
    }

    private suspend fun search(
        holder: ImportStateHolder,
        packageName: String,
        query: String,
    ) {
        externalImportRepository.searchRepos(query).fold(
            onSuccess = { suggestions -> holder.showResults(packageName, suggestions.map { it.toUi() }.toImmutableList()) },
            onFailure = { e ->
                if (e is CancellationException) throw e
                logger.error("Search override failed for '$query': ${e.message}")
                val message = e.message ?: getString(Res.string.external_import_search_error_default)
                holder.updateActive(packageName) {
                    it.copy(isSearching = false, searchError = message, searchResults = persistentListOf())
                }
            },
        )
    }

    private fun ImportStateHolder.showResults(
        packageName: String,
        results: ImmutableList<RepoSuggestionUi>,
    ) {
        updateActive(packageName) { it.copy(isSearching = false, searchError = null, searchResults = results) }
    }

    // A search that finishes after its card lost focus changes nothing.
    private inline fun ImportStateHolder.updateActive(
        packageName: String,
        crossinline transform: (ExternalImportState) -> ExternalImportState,
    ) {
        update { if (it.activeSearchPackage != packageName) it else transform(it) }
    }
}
