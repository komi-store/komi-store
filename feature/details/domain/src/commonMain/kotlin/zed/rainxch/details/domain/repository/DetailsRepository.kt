package zed.rainxch.details.domain.repository

typealias ReadmeContent = String
typealias ReadmePath = String
typealias LanguageCode = String

interface DetailsRepository :
    RepoSummaryReader,
    ReleaseReader,
    RepoContentReader
