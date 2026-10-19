package zed.rainxch.core.domain.utils

fun separateAdjacentImageLinks(content: String): String {
    if (content.isEmpty()) return content
    val lines = content.split('\n')
    val out = StringBuilder(content.length + 64)
    var lastWasLinkLine = false
    var lastEmittedWasBlank = true
    for (line in lines) {
        val trimmed = line.trim()
        val isLinkLine = trimmed.isNotEmpty() && isPureImageLinkLine(trimmed)
        if (isLinkLine && lastWasLinkLine && !lastEmittedWasBlank) {
            out.append('\n')
            lastEmittedWasBlank = true
        }
        if (out.isNotEmpty()) out.append('\n')
        out.append(line)
        lastEmittedWasBlank = trimmed.isEmpty()
        if (trimmed.isNotEmpty()) {
            lastWasLinkLine = isLinkLine
        }
    }
    return out.toString()
}

private fun isPureImageLinkLine(line: String): Boolean {
    if ("](" !in line && !line.contains("<a", ignoreCase = true)) return false

    var stripped = line

    stripped =
        stripped.replace(
            Regex("""\[!\[[^\]]*]\([^)]+\)]\([^)]+\)"""),
            "",
        )

    stripped =
        stripped.replace(
            Regex("""!\[[^\]]*]\([^)]+\)"""),
            "",
        )

    stripped =
        stripped.replace(
            Regex("""\[\s*]\([^)]+\)"""),
            "",
        )

    stripped =
        stripped.replace(
            Regex(
                """<a\b[^>]*>\s*(?:<img\b[^>]*/?>|\s)*</a>""",
                RegexOption.IGNORE_CASE,
            ),
            "",
        )

    stripped =
        stripped.replace(
            Regex("""<img\b[^>]*/?>""", RegexOption.IGNORE_CASE),
            "",
        )
    return stripped.isBlank()
}
