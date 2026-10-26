package zed.rainxch.core.domain.utils

fun applyThemeAwareImages(
    content: String,
    isDark: Boolean,
): String {
    if (content.isEmpty()) return content
    var processed = content

    processed =
        processed.replace(
            Regex("""!\[(?<alt>[^\]]*)\]\((?<url>[^)]*?)#gh-(?<mode>dark|light)-mode-only(?<trailing>[^)]*)\)"""),
        ) { match ->
            if (isHiddenInTheme(match.group("mode"), isDark)) {
                ""
            } else {
                "![${match.group("alt")}](${match.group("url")}${match.group("trailing")})"
            }
        }

    processed =
        processed.replace(
            Regex(
                """<img(?<pre>\s[^>]*?)src\s*=\s*(?<quote>["'])(?<url>[^"']*?)""" +
                    """#gh-(?<mode>dark|light)-mode-only(?<trailing>[^"']*?)\k<quote>(?<post>[^>]*?)>""",
                RegexOption.IGNORE_CASE,
            ),
        ) { match ->
            if (isHiddenInTheme(match.group("mode"), isDark)) {
                ""
            } else {
                val quote = match.group("quote")
                "<img${match.group("pre")}src=$quote${match.group("url")}${match.group("trailing")}$quote${match.group("post")}>"
            }
        }

    return processed
}

private fun MatchResult.group(name: String): String = groups[name]?.value.orEmpty()

private fun isHiddenInTheme(
    mode: String,
    isDark: Boolean,
): Boolean = mode == if (isDark) "light" else "dark"
