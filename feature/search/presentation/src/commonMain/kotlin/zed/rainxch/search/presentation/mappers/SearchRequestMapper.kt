package zed.rainxch.search.presentation.mappers

import zed.rainxch.search.domain.model.SearchRequest
import zed.rainxch.search.presentation.SearchState

internal fun SearchState.toSearchRequest(): SearchRequest =
    SearchRequest(
        query = query,
        platform = selectedSearchPlatform,
        language = selectedLanguage.domain,
        sortBy = selectedSortBy.toDomain(),
        sortOrder = selectedSortOrder.toDomain(),
        source = selectedSource.toDomain(),
    )
