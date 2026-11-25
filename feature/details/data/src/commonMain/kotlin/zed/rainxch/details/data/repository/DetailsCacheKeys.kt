package zed.rainxch.details.data.repository

internal object DetailsCacheKeys {
    fun repoById(id: Long): String = "details:repo_id:$id"

    fun repo(
        owner: String,
        name: String,
    ): String = "details:repo:$owner/$name"

    fun stats(
        owner: String,
        repo: String,
    ): String = "details:stats:v3:$owner/$repo"

    fun profile(username: String): String = "details:profile:$username"

    fun latestRelease(
        owner: String,
        repo: String,
    ): String = "details:latest_release:$owner/$repo"

    fun releases(
        owner: String,
        repo: String,
    ): String = "details:releases:$owner/$repo"

    fun readme(
        owner: String,
        repo: String,
    ): String = "details:readme:v4:$owner/$repo"
}
