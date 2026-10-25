package zed.rainxch.core.data.mappers

import zed.rainxch.core.domain.model.repository.DiscoveryPlatform

// Platforms are stored as their names, comma separated; a name this build doesn't know is skipped.
// They read back in the enum's order, so every row lists them the same way whoever wrote it.
fun List<DiscoveryPlatform>.toPlatformsColumn(): String? = takeIf { it.isNotEmpty() }?.joinToString(",") { it.name }

fun String?.toPlatforms(): List<DiscoveryPlatform> =
    this
        ?.split(",")
        ?.mapNotNull { name -> DiscoveryPlatform.entries.firstOrNull { it.name == name } }
        ?.sorted()
        .orEmpty()
