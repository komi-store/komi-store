package zed.rainxch.core.data.mappers

import zed.rainxch.core.data.dto.ReleaseNetwork
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.isEffectivelyPreRelease

fun List<ReleaseNetwork>.toReleaseWindow(includePreReleases: Boolean): List<GithubRelease> =
    asSequence()
        .filter { it.draft != true }
        .sortedByDescending { it.publishedAt ?: it.createdAt ?: "" }
        .map { it.toDomain() }
        .filter { includePreReleases || !it.isEffectivelyPreRelease() }
        .toList()

// Capped before the pre-release filter, like the fetch path's per_page, so a longer fed list
// cannot reach releases the fetched check would never see.
fun List<GithubRelease>.toUpdateCheckWindow(
    includePreReleases: Boolean,
    limit: Int,
): List<GithubRelease> =
    asSequence()
        .sortedByDescending { it.publishedAt }
        .take(limit)
        .filter { includePreReleases || !it.isEffectivelyPreRelease() }
        .toList()
