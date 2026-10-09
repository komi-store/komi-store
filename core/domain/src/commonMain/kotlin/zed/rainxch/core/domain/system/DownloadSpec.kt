package zed.rainxch.core.domain.system

import zed.rainxch.core.domain.model.account.github.GithubAsset

data class DownloadSpec(

    val packageName: String,

    val repoOwner: String,

    val repoName: String,

    // The repository's own id and host, carried so a download can lead back to it — the card
    // opens the repo by id rather than re-deriving it from the display name. Null when the
    // spec was built without a repository behind it.
    val repoId: Long? = null,

    val sourceHost: String? = null,

    val repoOwnerAvatarUrl: String? = null,

    val repoDescription: String? = null,

    val asset: GithubAsset,

    val displayAppName: String,

    val installPolicy: InstallPolicy,

    val releaseTag: String,

    val releaseId: Long? = null,
)
