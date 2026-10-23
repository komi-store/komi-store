package zed.rainxch.details.data.utils

fun preprocessMarkdown(
    markdown: String,
    baseUrl: String,
    linkBaseUrl: String = baseUrl,
): String {
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    val normalizedLinkBaseUrl = if (linkBaseUrl.endsWith("/")) linkBaseUrl else "$linkBaseUrl/"

    var processed = markdown

    fun normalizeGitHubUrl(url: String): String =
        if (url.contains("github.com") && url.contains("/blob/")) {
            url
                .replace("github.com", "raw.githubusercontent.com")
                .replace("/blob/", "/")
        } else {
            url
        }

    fun isSvgUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".svg") ||
            lower.contains(".svg?") ||
            lower.contains(".svg#") ||
            lower.contains("/svg-badge") ||
            lower.contains("badge.svg")
    }

    fun isBadgeUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("img.shields.io") ||
            lower.contains("shields.io/badge") ||
            lower.contains("badge.fury.io") ||
            lower.contains("badgen.net") ||
            lower.contains("repology.org/badge") ||
            lower.contains("hosted.weblate.org/widget") ||
            lower.contains("codecov.io") ||
            lower.contains("coveralls.io") ||
            lower.contains("travis-ci.") ||
            lower.contains("circleci.com") ||
            lower.contains("github.com/workflows") ||
            (lower.contains("/badge") && isSvgUrl(lower))
    }

    fun shouldSkipImage(url: String): Boolean = isBadgeUrl(url)

    fun resolveUrl(
        path: String,
        asImage: Boolean,
    ): String {
        val trimmed = path.trim()
        if (trimmed.startsWith("#") && !asImage) return trimmed

        val isAbsolute =
            trimmed.startsWith("http://") ||
                trimmed.startsWith("https://") ||
                trimmed.startsWith("data:") ||
                trimmed.startsWith("mailto:") ||
                trimmed.startsWith("tel:") ||
                trimmed.startsWith("sms:") ||
                trimmed.startsWith("intent:")

        val baseForPath = if (asImage) normalizedBaseUrl else normalizedLinkBaseUrl

        return if (isAbsolute) {
            if (asImage) normalizeGitHubUrl(trimmed) else trimmed
        } else {
            when {
                trimmed.startsWith("./") -> {
                    "$baseForPath${trimmed.removePrefix("./")}"
                }

                trimmed.startsWith("/") -> {
                    "$baseForPath${trimmed.removePrefix("/")}"
                }

                trimmed.startsWith("../") -> {
                    var base = baseForPath.trimEnd('/')
                    var rel = trimmed
                    while (rel.startsWith("../")) {
                        base = base.substringBeforeLast('/', base)
                        rel = rel.removePrefix("../")
                    }
                    "$base/$rel"
                }

                else -> {
                    "$baseForPath$trimmed"
                }
            }
        }
    }

    val refDefinitionRegex =
        Regex(
            """^\[([^\]]+)\]:\s*(\S+).*$""",
            RegexOption.MULTILINE,
        )
    val referenceMap = mutableMapOf<String, String>()
    for (match in refDefinitionRegex.findAll(processed)) {
        val refName = match.groupValues[1].lowercase()
        val url = match.groupValues[2]
        referenceMap[refName] = url
    }

    val skipRefNames =
        referenceMap
            .filter { (_, url) ->
                shouldSkipImage(resolveUrl(url, asImage = true))
            }.keys

    if (skipRefNames.isNotEmpty()) {
        processed =
            processed.replace(
                Regex("""!\[([^\]]*)\]\[([^\]]+)\]"""),
            ) { match ->
                val alt = match.groupValues[1]
                val refName = match.groupValues[2].lowercase()
                if (refName in skipRefNames) {
                    if (alt.isNotEmpty()) "**$alt**" else ""
                } else {
                    match.value
                }
            }
    }

    processed =
        processed.replace(
            Regex("""!\[([^\]]*)\]\[([^\]]+)\]"""),
        ) { match ->
            val alt = match.groupValues[1]
            val refName = match.groupValues[2].lowercase()
            val url = referenceMap[refName]
            if (url != null) {
                val resolved = resolveUrl(url, asImage = true)
                "![$alt]($resolved)"
            } else {
                match.value
            }
        }

    processed =
        processed.replace(
            Regex("""\[(\*\*[^*]*\*\*)\]\[([^\]]+)\]"""),
        ) { match ->
            val boldText = match.groupValues[1]
            val refName = match.groupValues[2].lowercase()
            val url = referenceMap[refName]
            if (url != null) {
                "[$boldText](${resolveUrl(url, asImage = false)})"
            } else {
                boldText
            }
        }

    processed =
        processed.replace(
            Regex("""\[\s*\]\[([^\]]+)\]"""),
            "",
        )

    processed =
        processed.replace(
            Regex("""\[([^\]]+)\]\[([^\]]+)\]"""),
        ) { match ->
            val text = match.groupValues[1]
            val refName = match.groupValues[2].lowercase()
            val url = referenceMap[refName]

            if (url != null && !text.startsWith("!")) {
                "[$text](${resolveUrl(url, asImage = false)})"
            } else {
                match.value
            }
        }

    processed =
        processed.replace(
            Regex("""^\[([^\]]+)\]:\s*\S+.*$""", RegexOption.MULTILINE),
        ) { match ->
            val refName = match.groupValues[1].lowercase()
            if (refName in referenceMap) "" else match.value
        }

    processed =
        processed.replace(
            Regex(
                """<details\b[^>]*?>.*?<summary[^>]*?>(.*?)</summary>(.*?)</details>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            val summary = match.groupValues[1].trim()
            val body = match.groupValues[2].trim()
            val isInline = !match.value.contains('\n')
            val lineStart = processed.lastIndexOf('\n', match.range.first) + 1
            val linePrefix = processed.substring(lineStart, match.range.first)
            val isInTableCell = linePrefix.contains('|')
            val mustFlatten = isInline || isInTableCell

            if (mustFlatten) {
                val flatBody =
                    body
                        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), " ")
                        .replace(Regex("""\s+"""), " ")
                        .trim()
                when {
                    summary.isEmpty() && flatBody.isEmpty() -> ""
                    flatBody.isEmpty() -> "**$summary**"
                    summary.isEmpty() -> flatBody
                    else -> "**$summary**: $flatBody"
                }
            } else {
                val encodedSummary = encodeDetailsSummary(summary)

                val longestRun = longestBacktickRun(body)
                val fence = "`".repeat(maxOf(4, longestRun + 1))
                "\n\n${fence}ghs-details|$encodedSummary\n$body\n$fence\n\n"
            }
        }

    processed =
        processed.replace(
            Regex(
                """<picture[^>]*>.*?(<img\s[^>]*?>).*?</picture>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            match.groupValues[1]
        }

    processed =
        processed.replace(
            Regex("""<source\s[^>]*?/?>""", RegexOption.IGNORE_CASE),
            "",
        )

    processed =
        processed.replace(
            Regex(
                """<a\s[^>]*?>\s*(<img\s[^>]*?>)\s*</a>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            match.groupValues[1]
        }

    processed =
        processed.replace(
            Regex(
                """<img\s+([^>]*?)\s*/?>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { imgMatch ->
            val imgTag = imgMatch.groupValues[1]

            val srcMatch = Regex("""src\s*=\s*(["'])([^"']+)\1""").find(imgTag)
            val src = srcMatch?.groupValues?.get(2) ?: ""

            val altMatch = Regex("""alt\s*=\s*(["'])([^"']*)\1""").find(imgTag)
            val alt = altMatch?.groupValues?.get(2) ?: ""

            if (src.isNotEmpty()) {
                val normalizedSrc = resolveUrl(src, asImage = true)

                if (shouldSkipImage(normalizedSrc)) {
                    if (alt.isNotEmpty()) "**$alt**" else ""
                } else {
                    "![$alt]($normalizedSrc)"
                }
            } else {
                ""
            }
        }

    processed =
        processed.replace(
            Regex("""!\[([^\]]*)\]\(([^)]+)\)"""),
        ) { match ->
            val alt = match.groupValues[1]
            val originalPath = match.groupValues[2].trim()
            val finalUrl = resolveUrl(originalPath, asImage = true)

            if (shouldSkipImage(finalUrl)) {
                if (alt.isNotEmpty()) "**$alt**" else ""
            } else {
                "![$alt]($finalUrl)"
            }
        }

    processed =
        processed.replace(
            Regex("""(?<!\!)\[([^\]]*)\]\(([^)]+)\)"""),
        ) { match ->
            val text = match.groupValues[1]
            val originalPath = match.groupValues[2].trim()
            val finalUrl = resolveUrl(originalPath, asImage = false)
            "[$text]($finalUrl)"
        }

    processed =
        processed.replace(
            Regex(
                """<video[^>]*?\ssrc=(["'])([^"']+)\1[^>]*>.*?</video>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            val src = match.groupValues[2]
            "[Video](${resolveUrl(src, asImage = true)})"
        }

    processed =
        processed.replace(
            Regex(
                """<video[^>]*>.*?<source\s[^>]*?\ssrc=(["'])([^"']+)\1[^>]*?>.*?</video>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            val src = match.groupValues[2]
            "[Video](${resolveUrl(src, asImage = true)})"
        }

    for (level in 1..6) {
        val hashes = "#".repeat(level)
        processed =
            processed.replace(
                Regex(
                    """<h$level[^>]*>(.*?)</h$level>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                ),
            ) { match ->
                val content = match.groupValues[1].trim()
                "\n$hashes $content\n"
            }
    }

    processed =
        processed.replace(
            Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE),
            "\n",
        )
    processed =
        processed.replace(
            Regex("""<hr\s*/?>""", RegexOption.IGNORE_CASE),
            "\n---\n",
        )

    processed =
        processed.replace(
            Regex(
                """<(b|strong)>(.*?)</\1>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            "**${match.groupValues[2]}**"
        }

    processed =
        processed.replace(
            Regex(
                """<(i|em)>(.*?)</\1>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            "*${match.groupValues[2]}*"
        }

    processed =
        processed.replace(
            Regex(
                """<pre[^>]*>\s*<code(?:\s+[^>]*?class\s*=\s*["'][^"']*?language-(\w+)[^"']*?["'])?[^>]*>(.*?)</code>\s*</pre>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            val lang = match.groupValues[1]
            val code = match.groupValues[2]
            "\n```$lang\n$code\n```\n"
        }

    processed =
        processed.replace(
            Regex(
                """<code>([^<]*?)</code>""",
                RegexOption.IGNORE_CASE,
            ),
        ) { match ->
            "`${match.groupValues[1]}`"
        }

    processed =
        processed.replace(
            Regex(
                """<blockquote[^>]*>(.*?)</blockquote>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            val body = match.groupValues[1].trim()
            body.lineSequence().joinToString("\n") { "> $it" }
        }

    processed =
        processed.replace(
            Regex(
                """<(s|del|strike)>(.*?)</\1>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            "~~${match.groupValues[2]}~~"
        }

    processed =
        processed.replace(
            Regex(
                """<a\s+[^>]*?href\s*=\s*(["'])([^"']+)\1[^>]*>(.*?)</a>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            val url = match.groupValues[2]
            val text = match.groupValues[3].trim()
            val resolvedUrl = resolveUrl(url, asImage = false)
            if (text.isEmpty()) {
                "[$resolvedUrl]($resolvedUrl)"
            } else {
                "[$text]($resolvedUrl)"
            }
        }

    processed =
        processed.replace(
            Regex(
                """<kbd>(.*?)</kbd>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            "`${match.groupValues[1]}`"
        }

    processed =
        processed.replace(
            Regex("""<div[^>]*?>\s*""", RegexOption.IGNORE_CASE),
            "\n\n",
        )
    processed =
        processed.replace(
            Regex("""</div>\s*""", RegexOption.IGNORE_CASE),
            "\n\n",
        )

    processed =
        processed.replace(
            Regex("""<p[^>]*?>""", RegexOption.IGNORE_CASE),
            "\n",
        )
    processed =
        processed.replace(
            Regex("""</p>""", RegexOption.IGNORE_CASE),
            "\n",
        )

    processed =
        processed.replace(
            Regex("""</?details[^>]*?>""", RegexOption.IGNORE_CASE),
            "\n",
        )
    processed =
        processed.replace(
            Regex(
                """<summary[^>]*?>(.*?)</summary>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        ) { match ->
            "**${match.groupValues[1].trim()}**\n"
        }

    processed =
        processed.replace(
            Regex("""<sup[^>]*>(.*?)</sup>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        ) { match ->
            match.groupValues[1].map { SUPERSCRIPTS[it] ?: it }.joinToString("")
        }
    processed =
        processed.replace(
            Regex("""<sub[^>]*>(.*?)</sub>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        ) { match ->
            match.groupValues[1].map { SUBSCRIPTS[it] ?: it }.joinToString("")
        }

    processed =
        processed.replace(
            Regex("""</?span[^>]*?>""", RegexOption.IGNORE_CASE),
            "",
        )

    processed =
        processed.replace(
            Regex(
                """</?(?:center|font|u|section|article|header|footer|nav|main|aside|figure|figcaption)[^>]*?>""",
                RegexOption.IGNORE_CASE,
            ),
            "\n",
        )

    // The renderer can't lay out HTML tables, so each cell becomes its own block. The cells'
    // indentation goes too: after a blank line, 4+ spaces would make Markdown read them as code.
    processed =
        processed.replace(
            Regex("""<table\b[^>]*>(.*?)</table>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        ) { match ->
            val cells =
                match.groupValues[1]
                    .replace(Regex("""</?(?:thead|tbody|tfoot|tr|td|th)\b[^>]*>""", RegexOption.IGNORE_CASE), "\n\n")
                    .lines()
                    .joinToString("\n") { it.trim() }
            "\n\n$cells\n\n"
        }

    HTML_ENTITIES.forEach { (entity, char) -> processed = processed.replace(entity, char) }

    processed =
        processed.replace(Regex("""&#(\d+);""")) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null && code in 32..0x10FFFF) {
                code.toChar().toString()
            } else {
                match.value
            }
        }

    processed =
        processed.replace(Regex("""&#x([0-9A-Fa-f]+);""")) { match ->
            val code = match.groupValues[1].toIntOrNull(16)
            if (code != null && code in 32..0x10FFFF) {
                code.toChar().toString()
            } else {
                match.value
            }
        }

    processed =
        processed.replace(
            Regex("""<p[^>]*?>\s*</p>""", RegexOption.IGNORE_CASE),
            "",
        )
    processed =
        processed.replace(
            Regex("""\n{3,}"""),
            "\n\n",
        )

    processed =
        processed.replace(
            Regex("""^\]\([^)]+\)""", RegexOption.MULTILINE),
            "",
        )

    processed =
        zed.rainxch.core.domain.utils.EmojiShortcodes
            .render(processed)

    processed = joinAdjacentImageLines(processed)

    return processed.trim()
}

private fun longestBacktickRun(text: String): Int {
    var max = 0
    var current = 0
    for (c in text) {
        if (c == '`') {
            current++
            if (current > max) max = current
        } else {
            current = 0
        }
    }
    return max
}

private fun encodeDetailsSummary(text: String): String {
    val safe = StringBuilder()
    text.forEach { c ->
        when (c) {
            '\n', '\r', '\t', '`', ' ', '|' -> {
                safe.append("%").append(
                    c.code
                        .toString(16)
                        .padStart(2, '0')
                        .uppercase(),
                )
            }

            else -> {
                safe.append(c)
            }
        }
    }
    return safe.toString()
}

private fun joinAdjacentImageLines(content: String): String {
    val imageOnlyLine =
        Regex("""^\s*(?:!\[[^\]]*]\([^)]+\)\s*){1,}\s*$""")
    val lines = content.split('\n')
    val out = StringBuilder()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (imageOnlyLine.matches(line)) {
            val group = StringBuilder(line.trim())
            var j = i + 1
            while (j < lines.size && imageOnlyLine.matches(lines[j])) {
                group.append(' ').append(lines[j].trim())
                j++
            }
            out.append(group)
            if (j < lines.size) out.append('\n')
            i = j
        } else {
            out.append(line)
            if (i + 1 < lines.size) out.append('\n')
            i++
        }
    }
    return out.toString()
}

private val HTML_ENTITIES: Map<String, String> =
    mapOf(
        "&amp;" to "&",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&apos;" to "'",
        "&#39;" to "'",
        "&nbsp;" to " ",
        "&ensp;" to " ",
        "&emsp;" to " ",
        "&thinsp;" to " ",
        "&hellip;" to "…",
        "&mdash;" to "—",
        "&ndash;" to "–",
        "&laquo;" to "«",
        "&raquo;" to "»",
        "&ldquo;" to "“",
        "&rdquo;" to "”",
        "&lsquo;" to "‘",
        "&rsquo;" to "’",
        "&sbquo;" to "‚",
        "&bdquo;" to "„",
        "&bull;" to "•",
        "&middot;" to "·",
        "&sect;" to "§",
        "&para;" to "¶",
        "&times;" to "×",
        "&divide;" to "÷",
        "&plusmn;" to "±",
        "&deg;" to "°",
        "&micro;" to "µ",
        "&fnof;" to "ƒ",
        "&infin;" to "∞",
        "&asymp;" to "≈",
        "&ne;" to "≠",
        "&le;" to "≤",
        "&ge;" to "≥",
        "&larr;" to "←",
        "&rarr;" to "→",
        "&uarr;" to "↑",
        "&darr;" to "↓",
        "&harr;" to "↔",
        "&lArr;" to "⇐",
        "&rArr;" to "⇒",
        "&copy;" to "©",
        "&reg;" to "®",
        "&trade;" to "™",
        "&euro;" to "€",
        "&pound;" to "£",
        "&yen;" to "¥",
        "&cent;" to "¢",
    )

private val SUPERSCRIPTS: Map<Char, Char> =
    mapOf(
        '0' to '⁰',
        '1' to '¹',
        '2' to '²',
        '3' to '³',
        '4' to '⁴',
        '5' to '⁵',
        '6' to '⁶',
        '7' to '⁷',
        '8' to '⁸',
        '9' to '⁹',
        '+' to '⁺',
        '-' to '⁻',
        '=' to '⁼',
        '(' to '⁽',
        ')' to '⁾',
        'n' to 'ⁿ',
        'i' to 'ⁱ',
    )

private val SUBSCRIPTS: Map<Char, Char> =
    mapOf(
        '0' to '₀',
        '1' to '₁',
        '2' to '₂',
        '3' to '₃',
        '4' to '₄',
        '5' to '₅',
        '6' to '₆',
        '7' to '₇',
        '8' to '₈',
        '9' to '₉',
        '+' to '₊',
        '-' to '₋',
        '=' to '₌',
        '(' to '₍',
        ')' to '₎',
        'a' to 'ₐ',
        'e' to 'ₑ',
        'o' to 'ₒ',
        'x' to 'ₓ',
        'h' to 'ₕ',
        'k' to 'ₖ',
        'l' to 'ₗ',
        'm' to 'ₘ',
        'n' to 'ₙ',
        'p' to 'ₚ',
        's' to 'ₛ',
        't' to 'ₜ',
    )
