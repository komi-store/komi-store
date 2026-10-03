package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.account.github.GithubRelease

object ReleaseLines {

    fun of(tag: String): String {
        val lower = tag.lowercase()
        val prefix = VERSION.find(lower)?.let { lower.substring(0, it.range.first) }
            ?: lower.takeWhile { !it.isDigit() }
        return prefix.split(SEPARATORS)
            .filter { it.isNotEmpty() && it !in NOT_AN_APP }
            .joinToString("-")
    }

    // Lines are separate apps only when they interleave; a tag scheme that changed once
    // (release-1.0 then v1.1) or a lone `nightly` tag is still one app.
    fun isMultiLine(releases: List<GithubRelease>): Boolean {
        val spans = releases.withIndex()
            .groupBy({ of(it.value.tagName) }, { it.index })
            .values
            .map { it.min() to it.max() }
        return spans.indices.any { i ->
            (i + 1 until spans.size).any { j ->
                spans[i].first < spans[j].second && spans[j].first < spans[i].second
            }
        }
    }

    fun sameLine(
        releases: List<GithubRelease>,
        anchor: GithubRelease,
        history: List<GithubRelease> = releases,
    ): List<GithubRelease> {
        if (!isMultiLine(history)) return releases
        val line = of(anchor.tagName)
        return releases.filter { of(it.tagName) == line }
    }

    private val VERSION = Regex("""\d+(?:[._]\d+)+""")
    private val SEPARATORS = Regex("""[-_./@\s]+""")
    private val NOT_AN_APP = setOf(
        "v", "android", "ios", "desktop", "mobile", "windows", "win", "macos", "mac", "osx", "linux",
    )
}
