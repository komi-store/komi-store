package zed.rainxch.details.data.utils

import zed.rainxch.core.domain.utils.EmojiShortcodes

fun preprocessMarkdown(
    markdown: String,
    baseUrl: String,
    linkBaseUrl: String = baseUrl,
): String {
    val urls = MarkdownUrlResolver(baseUrl, linkBaseUrl)
    return markdown
        .let { inlineReferenceLinks(it, urls) }
        .let(::convertDetailsBlocks)
        .let { convertImages(it, urls) }
        .replace(INLINE_LINK) { match ->
            "[${match.groupValues[1]}](${urls.resolve(match.groupValues[2].trim(), asImage = false)})"
        }.let { convertVideos(it, urls) }
        .let(::convertHeadings)
        .let { convertInlineHtml(it, urls) }
        .let(::stripLayoutHtml)
        .let(::flattenTables)
        .let(::decodeHtmlEntities)
        .replace(EMPTY_PARAGRAPH, "")
        .replace(BLANK_LINE_RUN, "\n\n")
        .replace(STRAY_LINK_TARGET, "")
        .let(EmojiShortcodes::render)
        .let(::joinAdjacentImageLines)
        .trim()
}

private fun inlineReferenceLinks(
    markdown: String,
    urls: MarkdownUrlResolver,
): String {
    val references =
        REFERENCE_DEFINITION
            .findAll(markdown)
            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
    val badgeReferences = references.filterValues { url -> isBadgeUrl(urls.resolve(url, asImage = true)) }.keys
    val withoutBadges =
        if (badgeReferences.isEmpty()) {
            markdown
        } else {
            markdown.replace(IMAGE_REFERENCE) { match ->
                if (match.groupValues[2].lowercase() in badgeReferences) boldAlt(match.groupValues[1]) else match.value
            }
        }
    return withoutBadges
        .replace(IMAGE_REFERENCE) { match ->
            references[match.groupValues[2].lowercase()]
                ?.let { url -> "![${match.groupValues[1]}](${urls.resolve(url, asImage = true)})" }
                ?: match.value
        }.replace(BOLD_LINK_REFERENCE) { match ->
            val boldText = match.groupValues[1]
            references[match.groupValues[2].lowercase()]
                ?.let { url -> "[$boldText](${urls.resolve(url, asImage = false)})" }
                ?: boldText
        }.replace(EMPTY_LINK_REFERENCE, "")
        .replace(LINK_REFERENCE) { match ->
            val text = match.groupValues[1]
            val url = references[match.groupValues[2].lowercase()]
            if (url != null && !text.startsWith("!")) "[$text](${urls.resolve(url, asImage = false)})" else match.value
        }.replace(REFERENCE_DEFINITION) { match ->
            if (match.groupValues[1].lowercase() in references) "" else match.value
        }
}

private fun convertDetailsBlocks(markdown: String): String =
    markdown.replace(DETAILS_BLOCK) { match ->
        val summary = match.groupValues[1].trim()
        val body = match.groupValues[2].trim()
        val lineStart = markdown.lastIndexOf('\n', match.range.first) + 1
        val isInTableCell = markdown.substring(lineStart, match.range.first).contains('|')
        if (!match.value.contains('\n') || isInTableCell) flatDetails(summary, body) else fencedDetails(summary, body)
    }

private fun flatDetails(
    summary: String,
    body: String,
): String {
    val flatBody =
        body
            .replace(BR_TAG, " ")
            .replace(WHITESPACE_RUN, " ")
            .trim()
    return when {
        summary.isEmpty() && flatBody.isEmpty() -> ""
        flatBody.isEmpty() -> "**$summary**"
        summary.isEmpty() -> flatBody
        else -> "**$summary**: $flatBody"
    }
}

private fun fencedDetails(
    summary: String,
    body: String,
): String {
    val fence = "`".repeat(maxOf(MIN_DETAILS_FENCE, longestBacktickRun(body) + 1))
    return "\n\n${fence}ghs-details|${encodeDetailsSummary(summary)}\n$body\n$fence\n\n"
}

private fun convertImages(
    markdown: String,
    urls: MarkdownUrlResolver,
): String =
    markdown
        .replace(PICTURE_BLOCK) { it.groupValues[1] }
        .replace(SOURCE_TAG, "")
        .replace(LINKED_IMG_TAG) { it.groupValues[1] }
        .replace(IMG_TAG) { match -> imgTagToMarkdown(match.groupValues[1], urls) }
        .replace(MARKDOWN_IMAGE) { match ->
            markdownImage(match.groupValues[1], urls.resolve(match.groupValues[2].trim(), asImage = true))
        }

private fun imgTagToMarkdown(
    attributes: String,
    urls: MarkdownUrlResolver,
): String {
    val src = IMG_SRC.find(attributes)?.groupValues?.get(2) ?: ""
    val alt = IMG_ALT.find(attributes)?.groupValues?.get(2) ?: ""
    return if (src.isNotEmpty()) markdownImage(alt, urls.resolve(src, asImage = true)) else ""
}

private fun markdownImage(
    alt: String,
    url: String,
): String = if (isBadgeUrl(url)) boldAlt(alt) else "![$alt]($url)"

private fun boldAlt(alt: String): String = if (alt.isNotEmpty()) "**$alt**" else ""

private fun convertVideos(
    markdown: String,
    urls: MarkdownUrlResolver,
): String =
    markdown
        .replace(VIDEO_WITH_SRC) { "[Video](${urls.resolve(it.groupValues[2], asImage = true)})" }
        .replace(VIDEO_WITH_SOURCE) { "[Video](${urls.resolve(it.groupValues[2], asImage = true)})" }

private fun convertHeadings(markdown: String): String =
    (1..MAX_HEADING_LEVEL).fold(markdown) { text, level ->
        val hashes = "#".repeat(level)
        text.replace(Regex("""<h$level[^>]*>(.*?)</h$level>""", BLOCK_OPTIONS)) { match ->
            "\n$hashes ${match.groupValues[1].trim()}\n"
        }
    }

private fun convertInlineHtml(
    markdown: String,
    urls: MarkdownUrlResolver,
): String =
    markdown
        .replace(BR_TAG, "\n")
        .replace(HR_TAG, "\n---\n")
        .replace(BOLD_TAG) { "**${it.groupValues[2]}**" }
        .replace(ITALIC_TAG) { "*${it.groupValues[2]}*" }
        .replace(PRE_CODE_BLOCK) { "\n```${it.groupValues[1]}\n${it.groupValues[2]}\n```\n" }
        .replace(CODE_TAG) { "`${it.groupValues[1]}`" }
        .replace(BLOCKQUOTE) { match ->
            match.groupValues[1]
                .trim()
                .lineSequence()
                .joinToString("\n") { "> $it" }
        }.replace(STRIKE_TAG) { "~~${it.groupValues[2]}~~" }
        .replace(ANCHOR_TAG) { match ->
            val resolvedUrl = urls.resolve(match.groupValues[2], asImage = false)
            val text = match.groupValues[3].trim()
            if (text.isEmpty()) "[$resolvedUrl]($resolvedUrl)" else "[$text]($resolvedUrl)"
        }.replace(KBD_TAG) { "`${it.groupValues[1]}`" }

private fun stripLayoutHtml(markdown: String): String =
    markdown
        .replace(DIV_OPEN, "\n\n")
        .replace(DIV_CLOSE, "\n\n")
        .replace(PARAGRAPH_OPEN, "\n")
        .replace(PARAGRAPH_CLOSE, "\n")
        .replace(DETAILS_TAG, "\n")
        .replace(SUMMARY_TAG) { "**${it.groupValues[1].trim()}**\n" }
        .replace(SUP_TAG) { match -> match.groupValues[1].map { SUPERSCRIPTS[it] ?: it }.joinToString("") }
        .replace(SUB_TAG) { match -> match.groupValues[1].map { SUBSCRIPTS[it] ?: it }.joinToString("") }
        .replace(SPAN_TAG, "")
        .replace(SECTIONING_TAG, "\n")

// Cells become separate blocks without indentation: after a blank line, 4+ spaces would read as code.
private fun flattenTables(markdown: String): String =
    markdown.replace(TABLE) { match ->
        val cells =
            match.groupValues[1]
                .replace(TABLE_PART_TAG, "\n\n")
                .lines()
                .joinToString("\n") { it.trim() }
        "\n\n$cells\n\n"
    }

private fun decodeHtmlEntities(markdown: String): String =
    HTML_ENTITIES
        .entries
        .fold(markdown) { text, (entity, char) -> text.replace(entity, char) }
        .replace(DECIMAL_ENTITY) { match -> codePointText(match.groupValues[1].toIntOrNull(), match.value) }
        .replace(HEX_ENTITY) { match -> codePointText(match.groupValues[1].toIntOrNull(HEX_RADIX), match.value) }

private fun codePointText(
    code: Int?,
    entity: String,
): String = if (code != null && code in PRINTABLE_CODE_POINTS) code.toChar().toString() else entity

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
                        .toString(HEX_RADIX)
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
    val lines = content.split('\n')
    val out = StringBuilder()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (IMAGE_ONLY_LINE.matches(line)) {
            val group = StringBuilder(line.trim())
            var j = i + 1
            while (j < lines.size && IMAGE_ONLY_LINE.matches(lines[j])) {
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

private const val MIN_DETAILS_FENCE = 4
private const val MAX_HEADING_LEVEL = 6
private const val HEX_RADIX = 16
private val PRINTABLE_CODE_POINTS = 32..0x10FFFF

private val BLOCK_OPTIONS = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

private val REFERENCE_DEFINITION = Regex("""^\[([^\]]+)\]:\s*(\S+).*$""", RegexOption.MULTILINE)
private val IMAGE_REFERENCE = Regex("""!\[([^\]]*)\]\[([^\]]+)\]""")
private val BOLD_LINK_REFERENCE = Regex("""\[(\*\*[^*]*\*\*)\]\[([^\]]+)\]""")
private val EMPTY_LINK_REFERENCE = Regex("""\[\s*\]\[([^\]]+)\]""")
private val LINK_REFERENCE = Regex("""\[([^\]]+)\]\[([^\]]+)\]""")

private val DETAILS_BLOCK = Regex("""<details\b[^>]*?>.*?<summary[^>]*?>(.*?)</summary>(.*?)</details>""", BLOCK_OPTIONS)
private val WHITESPACE_RUN = Regex("""\s+""")

private val PICTURE_BLOCK = Regex("""<picture[^>]*>.*?(<img\s[^>]*?>).*?</picture>""", BLOCK_OPTIONS)
private val SOURCE_TAG = Regex("""<source\s[^>]*?/?>""", RegexOption.IGNORE_CASE)
private val LINKED_IMG_TAG = Regex("""<a\s[^>]*?>\s*(<img\s[^>]*?>)\s*</a>""", BLOCK_OPTIONS)
private val IMG_TAG = Regex("""<img\s+([^>]*?)\s*/?>""", BLOCK_OPTIONS)
private val IMG_SRC = Regex("""src\s*=\s*(["'])([^"']+)\1""")
private val IMG_ALT = Regex("""alt\s*=\s*(["'])([^"']*)\1""")
private val MARKDOWN_IMAGE = Regex("""!\[([^\]]*)\]\(([^)]+)\)""")
private val INLINE_LINK = Regex("""(?<!\!)\[([^\]]*)\]\(([^)]+)\)""")
private val VIDEO_WITH_SRC = Regex("""<video[^>]*?\ssrc=(["'])([^"']+)\1[^>]*>.*?</video>""", BLOCK_OPTIONS)
private val VIDEO_WITH_SOURCE = Regex("""<video[^>]*>.*?<source\s[^>]*?\ssrc=(["'])([^"']+)\1[^>]*?>.*?</video>""", BLOCK_OPTIONS)

private val BR_TAG = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
private val HR_TAG = Regex("""<hr\s*/?>""", RegexOption.IGNORE_CASE)
private val BOLD_TAG = Regex("""<(b|strong)>(.*?)</\1>""", BLOCK_OPTIONS)
private val ITALIC_TAG = Regex("""<(i|em)>(.*?)</\1>""", BLOCK_OPTIONS)
private val PRE_CODE_BLOCK =
    Regex(
        """<pre[^>]*>\s*<code(?:\s+[^>]*?class\s*=\s*["'][^"']*?language-(\w+)[^"']*?["'])?[^>]*>(.*?)</code>\s*</pre>""",
        BLOCK_OPTIONS,
    )
private val CODE_TAG = Regex("""<code>([^<]*?)</code>""", RegexOption.IGNORE_CASE)
private val BLOCKQUOTE = Regex("""<blockquote[^>]*>(.*?)</blockquote>""", BLOCK_OPTIONS)
private val STRIKE_TAG = Regex("""<(s|del|strike)>(.*?)</\1>""", BLOCK_OPTIONS)
private val ANCHOR_TAG = Regex("""<a\s+[^>]*?href\s*=\s*(["'])([^"']+)\1[^>]*>(.*?)</a>""", BLOCK_OPTIONS)
private val KBD_TAG = Regex("""<kbd>(.*?)</kbd>""", BLOCK_OPTIONS)

private val DIV_OPEN = Regex("""<div[^>]*?>\s*""", RegexOption.IGNORE_CASE)
private val DIV_CLOSE = Regex("""</div>\s*""", RegexOption.IGNORE_CASE)
private val PARAGRAPH_OPEN = Regex("""<p[^>]*?>""", RegexOption.IGNORE_CASE)
private val PARAGRAPH_CLOSE = Regex("""</p>""", RegexOption.IGNORE_CASE)
private val DETAILS_TAG = Regex("""</?details[^>]*?>""", RegexOption.IGNORE_CASE)
private val SUMMARY_TAG = Regex("""<summary[^>]*?>(.*?)</summary>""", BLOCK_OPTIONS)
private val SUP_TAG = Regex("""<sup[^>]*>(.*?)</sup>""", BLOCK_OPTIONS)
private val SUB_TAG = Regex("""<sub[^>]*>(.*?)</sub>""", BLOCK_OPTIONS)
private val SPAN_TAG = Regex("""</?span[^>]*?>""", RegexOption.IGNORE_CASE)
private val SECTIONING_TAG =
    Regex(
        """</?(?:center|font|u|section|article|header|footer|nav|main|aside|figure|figcaption)[^>]*?>""",
        RegexOption.IGNORE_CASE,
    )

private val TABLE = Regex("""<table\b[^>]*>(.*?)</table>""", BLOCK_OPTIONS)
private val TABLE_PART_TAG = Regex("""</?(?:thead|tbody|tfoot|tr|td|th)\b[^>]*>""", RegexOption.IGNORE_CASE)

private val DECIMAL_ENTITY = Regex("""&#(\d+);""")
private val HEX_ENTITY = Regex("""&#x([0-9A-Fa-f]+);""")

private val EMPTY_PARAGRAPH = Regex("""<p[^>]*?>\s*</p>""", RegexOption.IGNORE_CASE)
private val BLANK_LINE_RUN = Regex("""\n{3,}""")
private val STRAY_LINK_TARGET = Regex("""^\]\([^)]+\)""", RegexOption.MULTILINE)
private val IMAGE_ONLY_LINE = Regex("""^\s*(?:!\[[^\]]*]\([^)]+\)\s*){1,}\s*$""")
