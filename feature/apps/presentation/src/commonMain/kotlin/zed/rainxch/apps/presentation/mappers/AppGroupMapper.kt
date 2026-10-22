package zed.rainxch.apps.presentation.mappers

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.apps.presentation.model.AppGroup
import zed.rainxch.apps.presentation.model.AppItem

fun List<AppItem>.groupedByRepo(): ImmutableList<AppGroup> =
    groupBy { item ->
        val app = item.installedApp
        if (app.repoId != 0L) "repo-${app.repoId}" else "app-${app.packageName}"
    }.map { (key, items) -> AppGroup(key = key, items = items.toImmutableList()) }
        .toImmutableList()
