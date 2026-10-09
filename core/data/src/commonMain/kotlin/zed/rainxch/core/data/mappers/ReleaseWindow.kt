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

// The window a check judges from when the list was fetched by someone else: the details
// refresh holds the repository host's own answer, and the check must not pay for a second
// read. Newest first, capped before the pre-release filter — the fetch path's cap is the
// server-side per_page over the raw window, so filtering first would let a longer list reach
// releases the fetch-driven check can never see — and the same pre-release filter as that
// path, so the two cannot disagree about what is judgeable.
fun List<GithubRelease>.toUpdateCheckWindow(
    includePreReleases: Boolean,
    limit: Int,
): List<GithubRelease> =
    asSequence()
        .sortedByDescending { it.publishedAt }
        .take(limit)
        .filter { includePreReleases || !it.isEffectivelyPreRelease() }
        .toList()
