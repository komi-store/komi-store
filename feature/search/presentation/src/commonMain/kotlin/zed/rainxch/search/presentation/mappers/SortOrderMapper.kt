package zed.rainxch.search.presentation.mappers

import zed.rainxch.domain.model.SortOrder
import zed.rainxch.domain.model.SortOrder.*
import zed.rainxch.search.presentation.model.SortOrderUi

fun SortOrderUi.toDomain(): SortOrder =
    when (this) {
        SortOrderUi.Descending -> Descending
        SortOrderUi.Ascending -> Ascending
    }
