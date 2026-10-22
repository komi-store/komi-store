package zed.rainxch.apps.presentation.model

import kotlinx.collections.immutable.ImmutableList

data class AppGroup(
    val key: String,
    val items: ImmutableList<AppItem>,
)
