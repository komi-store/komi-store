package zed.rainxch.core.data.network

object GithubAssetAuth {
    fun hostOf(url: String): String? {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return null
        return afterScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')
            .lowercase()
            .ifEmpty { null }
    }

    fun isGithubHost(url: String): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) return false
        val host = hostOf(url) ?: return false
        return host == "github.com" ||
            host == "api.github.com" ||
            host == "codeload.github.com" ||
            host.endsWith(".githubusercontent.com")
    }

    fun isGithubApiHost(url: String): Boolean = hostOf(url) == "api.github.com"

    fun assetApiUrl(
        owner: String,
        repo: String,
        assetId: Long,
    ): String = "https://api.github.com/repos/$owner/$repo/releases/assets/$assetId"
}
