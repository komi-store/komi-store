package zed.rainxch.core.data.network

import io.ktor.http.Url
import zed.rainxch.core.domain.model.mirror.TrafficKind

object MirrorRewriter {
    private val rewriteHosts =
        setOf(
            "github.com",
            "raw.githubusercontent.com",
            "objects.githubusercontent.com",
        )

    fun shouldRewrite(url: String): Boolean =
        runCatching {
            val host = Url(url).host.lowercase()
            host in rewriteHosts
        }.getOrDefault(false)

    fun classify(url: String): TrafficKind? =
        runCatching {
            val parsed = Url(url)
            val host = parsed.host.lowercase()
            val path = parsed.encodedPath
            when {
                host == "objects.githubusercontent.com" -> TrafficKind.RELEASE_ASSET
                host == "github.com" && "/releases/download/" in path -> TrafficKind.RELEASE_ASSET
                host == "raw.githubusercontent.com" -> TrafficKind.RAW_FILE
                host == "github.com" && ("/raw/" in path || "/blob/" in path) -> TrafficKind.RAW_FILE
                else -> null
            }
        }.getOrNull()

    fun applyTemplate(
        template: String,
        githubUrl: String,
    ): String? =
        when {
            "{url}" in template -> template.replace("{url}", githubUrl)
            "{owner}" in template -> applyDecomposedTemplate(template, githubUrl)
            else -> null
        }

    // Small public raw file every GitHub proxy is expected to serve. Used to time a
    // mirror from the device instead of trusting the latency the backend measured.
    fun probeUrl(template: String?): String =
        template?.let { applyTemplate(it, PROBE_URL) } ?: PROBE_URL

    private const val PROBE_URL = "https://raw.githubusercontent.com/octocat/Hello-World/master/README"

    private fun applyDecomposedTemplate(
        template: String,
        githubUrl: String,
    ): String? {
        val parts = decompose(githubUrl) ?: return null
        return template
            .replace("{owner}", parts.owner)
            .replace("{repo}", parts.repo)
            .replace("{ref}", parts.ref)
            .replace("{path}", parts.path)
    }

    private data class Decomposed(
        val owner: String,
        val repo: String,
        val ref: String,
        val path: String,
    )

    private fun decompose(githubUrl: String): Decomposed? {
        val parsed = runCatching { Url(githubUrl) }.getOrNull() ?: return null
        val host = parsed.host.lowercase()
        val segments = parsed.encodedPath.trimStart('/').split('/').filter { it.isNotEmpty() }
        return when (host) {
            "raw.githubusercontent.com" -> {
                if (segments.size < 4) return null
                Decomposed(
                    owner = segments[0],
                    repo = segments[1],
                    ref = segments[2],
                    path = segments.drop(3).joinToString("/"),
                )
            }
            "github.com" -> {
                if (segments.size < 5) return null
                val verb = segments[2]
                if (verb != "raw" && verb != "blob") return null
                Decomposed(
                    owner = segments[0],
                    repo = segments[1],
                    ref = segments[3],
                    path = segments.drop(4).joinToString("/"),
                )
            }
            else -> null
        }
    }
}
