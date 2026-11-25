package zed.rainxch.details.data.repository

import zed.rainxch.details.domain.repository.DetailsRepository
import zed.rainxch.details.domain.repository.ReleaseReader
import zed.rainxch.details.domain.repository.RepoContentReader
import zed.rainxch.details.domain.repository.RepoSummaryReader

internal class DetailsRepositoryImpl(
    summary: RepoSummaryReader,
    releases: ReleaseReader,
    content: RepoContentReader,
) : DetailsRepository,
    RepoSummaryReader by summary,
    ReleaseReader by releases,
    RepoContentReader by content
