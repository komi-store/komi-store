package zed.rainxch.details.domain.repository

interface RepoContentReader {
    suspend fun getReadme(
        owner: String,
        repo: String,
        defaultBranch: String,
        sourceHost: String? = null,
    ): Triple<ReadmeContent, LanguageCode?, ReadmePath>?

    suspend fun fetchRawMarkdown(url: String): String?

    suspend fun checkAttestations(
        owner: String,
        repo: String,
        sha256Digest: String,
    ): Boolean
}
