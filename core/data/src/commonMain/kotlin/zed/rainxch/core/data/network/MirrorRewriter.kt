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

    fun probeUrl(template: String?): String = mirrorProbeUrl(template) ?: PROBE_URL

    fun mirrorProbeUrl(template: String?): String? = if (template == null) PROBE_URL else applyTemplate(template, PROBE_URL)

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
        val segments =
            parsed.encodedPath
                .trimStart('/')
                .split('/')
                .filter { it.isNotEmpty() }
        return when (parsed.host.lowercase()) {
            "raw.githubusercontent.com" -> decomposeRaw(segments)
            "github.com" -> decomposeRawOrBlob(segments)
            else -> null
        }
    }

    // owner/repo/ref/path...
    private fun decomposeRaw(segments: List<String>): Decomposed? =
        if (segments.size > RAW_PATH_START) {
            Decomposed(
                owner = segments[0],
                repo = segments[1],
                ref = segments[2],
                path = segments.drop(RAW_PATH_START).joinToString("/"),
            )
        } else {
            null
        }

    // owner/repo/{raw|blob}/ref/path...
    private fun decomposeRawOrBlob(segments: List<String>): Decomposed? =
        if (segments.size > BLOB_PATH_START && segments[2] in RAW_OR_BLOB) {
            Decomposed(
                owner = segments[0],
                repo = segments[1],
                ref = segments[BLOB_PATH_START - 1],
                path = segments.drop(BLOB_PATH_START).joinToString("/"),
            )
        } else {
            null
        }

    private const val RAW_PATH_START = 3
    private const val BLOB_PATH_START = 4
    private val RAW_OR_BLOB = setOf("raw", "blob")
}
