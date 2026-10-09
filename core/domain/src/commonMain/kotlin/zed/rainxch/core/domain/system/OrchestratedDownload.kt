package zed.rainxch.core.domain.system

data class OrchestratedDownload(
    val id: String,
    val packageName: String,
    val repoOwner: String,
    val repoName: String,

    // Carried through from the spec so the card can lead back to the repository it stands for.
    val repoId: Long? = null,

    val sourceHost: String? = null,

    val repoOwnerAvatarUrl: String? = null,
    val repoDescription: String? = null,
    val displayAppName: String,
    val assetName: String,
    val assetSize: Long,

    val assetId: Long = 0L,
    val assetDigest: String? = null,

    val downloadUrl: String,
    val releaseTag: String,

    val filePath: String?,

    val installPolicy: InstallPolicy,
    val stage: DownloadStage,

    val progressPercent: Int?,

    val bytesDownloaded: Long = 0L,

    val totalBytes: Long? = null,

    val errorMessage: String? = null,
    val installOutcome: InstallOutcome? = null,
)
