package zed.rainxch.search.presentation.mappers

import zed.rainxch.search.domain.model.SearchSource
import zed.rainxch.search.presentation.model.SearchSourceUi

fun SearchSourceUi.toDomain(): SearchSource =
    when (this) {
        SearchSourceUi.GitHub -> SearchSource.GitHub
        SearchSourceUi.Codeberg -> SearchSource.Codeberg
        is SearchSourceUi.CustomForge -> SearchSource.Forgejo(host)
    }
