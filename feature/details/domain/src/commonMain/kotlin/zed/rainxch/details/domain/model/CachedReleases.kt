package zed.rainxch.details.domain.model

import zed.rainxch.core.domain.model.account.github.GithubRelease

data class CachedReleases(
    val releases: List<GithubRelease>,
    val cachedAtEpochMs: Long,
)
