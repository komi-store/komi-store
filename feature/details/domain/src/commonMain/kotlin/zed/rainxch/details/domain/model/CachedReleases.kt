package zed.rainxch.details.domain.model

import zed.rainxch.core.domain.model.account.github.GithubRelease

// What the details page can paint before any network read: the stored list and the moment it
// was stored. The age is what decides whether reading it again is worth another request.
data class CachedReleases(
    val releases: List<GithubRelease>,
    val cachedAtEpochMs: Long,
)
