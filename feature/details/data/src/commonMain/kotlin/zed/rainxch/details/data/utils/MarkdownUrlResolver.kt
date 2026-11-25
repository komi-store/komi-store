package zed.rainxch.details.data.utils

internal class MarkdownUrlResolver(
    baseUrl: String,
    linkBaseUrl: String,
) {
    private val imageBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    private val linkBase = if (linkBaseUrl.endsWith("/")) linkBaseUrl else "$linkBaseUrl/"

    fun resolve(
        path: String,
        asImage: Boolean,
    ): String {
        val trimmed = path.trim()
        return when {
            trimmed.startsWith("#") && !asImage -> trimmed
            ABSOLUTE_PREFIXES.any { trimmed.startsWith(it) } -> if (asImage) rawGitHubUrl(trimmed) else trimmed
            else -> resolveRelative(trimmed, if (asImage) imageBase else linkBase)
        }
    }

    private fun resolveRelative(
        path: String,
        base: String,
    ): String =
        when {
            path.startsWith("./") -> "$base${path.removePrefix("./")}"
            path.startsWith("/") -> "$base${path.removePrefix("/")}"
            path.startsWith("../") -> resolveParentPath(path, base)
            else -> "$base$path"
        }

    private fun resolveParentPath(
        path: String,
        base: String,
    ): String {
        var parent = base.trimEnd('/')
        var rest = path
        while (rest.startsWith("../")) {
            parent = parent.substringBeforeLast('/', parent)
            rest = rest.removePrefix("../")
        }
        return "$parent/$rest"
    }

    private fun rawGitHubUrl(url: String): String =
        if (url.contains("github.com") && url.contains("/blob/")) {
            url
                .replace("github.com", "raw.githubusercontent.com")
                .replace("/blob/", "/")
        } else {
            url
        }

    private companion object {
        val ABSOLUTE_PREFIXES = listOf("http://", "https://", "data:", "mailto:", "tel:", "sms:", "intent:")
    }
}

internal fun isBadgeUrl(url: String): Boolean {
    val lower = url.lowercase()
    return BADGE_MARKERS.any { lower.contains(it) } || (lower.contains("/badge") && isSvgUrl(lower))
}

private fun isSvgUrl(url: String): Boolean = url.endsWith(".svg") || SVG_MARKERS.any { url.contains(it) }

private val BADGE_MARKERS =
    listOf(
        "img.shields.io",
        "shields.io/badge",
        "badge.fury.io",
        "badgen.net",
        "repology.org/badge",
        "hosted.weblate.org/widget",
        "codecov.io",
        "coveralls.io",
        "travis-ci.",
        "circleci.com",
        "github.com/workflows",
    )

private val SVG_MARKERS = listOf(".svg?", ".svg#", "/svg-badge", "badge.svg")
